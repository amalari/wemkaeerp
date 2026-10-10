package com.eventverse.app.routes

import com.eventverse.app.asStaff
import com.eventverse.app.domain.crm.CrmLead
import com.eventverse.app.domain.crm.LeadId
import com.eventverse.app.domain.customfield.CustomAttributes
import com.eventverse.app.domain.customfield.CustomFieldId
import com.eventverse.app.domain.discovery.handoff.InMemoryPrototypeRowRepository
import com.eventverse.app.domain.discovery.handoff.PrototypeRowRepository
import com.eventverse.app.domain.pack.GarmentModules
import com.eventverse.app.domain.prototype.PrototypeRow
import com.eventverse.app.domain.rbac.AccessLevel
import com.eventverse.app.domain.rbac.CustomRole
import com.eventverse.app.domain.rbac.DataScope
import com.eventverse.app.domain.rbac.ModuleAccessConfig
import com.eventverse.app.domain.rbac.RoleId
import com.eventverse.app.domain.storage.FileRef
import com.eventverse.app.domain.storage.ObjectStorage
import com.eventverse.app.domain.tenant.SubscriptionTier
import com.eventverse.app.domain.tenant.Tenant
import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.domain.tenant.TenantName
import com.eventverse.app.domain.tenant.TenantSlug
import com.eventverse.app.domain.tenant.TenantStatus
import com.eventverse.app.infrastructure.InMemoryCrmLeadRepository
import com.eventverse.app.infrastructure.InMemoryModuleAssignmentRepository
import com.eventverse.app.infrastructure.InMemoryRoleRepository
import com.eventverse.app.infrastructure.InMemoryTenantEntitlementRepository
import com.eventverse.app.infrastructure.InMemoryTenantPipelineRepository
import com.eventverse.app.infrastructure.InMemoryTenantRepository
import com.eventverse.app.module
import com.eventverse.app.shared.json.JsonParser
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.utils.io.ByteReadChannel
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Instant
import java.util.concurrent.ConcurrentHashMap
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Gerbang & kontrak endpoint berkas field FILE (C8, TRD-FIELD-002 FR-3/FR-4) — tanpa Postgres,
 * storage = fake in-memory. Fokus: RBAC paling awal (sebelum body), urutan tolakan
 * 503/400/415/413/404, dan sukses = ref yang lolos `FileRef.isValid`.
 */
class FieldFileRoutesTest {
    private val slug = "field-file-uji"
    private val tenantId = TenantId("ten-field-file-uji")
    private val roleId = "role-uji"
    private val base = "/api/tenant/modules/quality_control/records/rec-1/fields/lampiran"
    private val crmBase = "/api/tenant/crm/leads/lead-1/fields/lampiran"

    /** Storage in-memory: pencatatan put + URL presigned tiruan; `configured=false` mensimulasikan env S3 kosong. */
    private class FakeObjectStorage(private val configured: Boolean = true) : ObjectStorage {
        val stored = ConcurrentHashMap<String, Pair<ByteArray, String>>()

        override val isConfigured: Boolean get() = configured

        override suspend fun put(key: String, bytes: ByteArray, contentType: String): Result<Unit> {
            if (!configured) return Result.failure(IllegalStateException("storage belum dikonfigurasi"))
            stored[key] = bytes to contentType
            return Result.success(Unit)
        }

        override suspend fun downloadUrl(key: String): Result<String> {
            if (!configured) return Result.failure(IllegalStateException("storage belum dikonfigurasi"))
            return Result.success("https://presigned.example.com/$key")
        }
    }

    private fun ApplicationTestBuilder.installApp(
        level: AccessLevel,
        storage: FakeObjectStorage = FakeObjectStorage(),
        leads: InMemoryCrmLeadRepository = InMemoryCrmLeadRepository(),
        // Default: record rec-1 ada (gerbang record TRD-FIELD-004 mensyaratkannya untuk unggah).
        rows: Map<String, PrototypeRowRepository> = rowsWith()
    ): FakeObjectStorage {
        val tenants = InMemoryTenantRepository()
        val roles = InMemoryRoleRepository()
        runBlocking {
            tenants.save(Tenant(tenantId, TenantSlug(slug), TenantName("Tenant Uji Field File"), TenantStatus.ACTIVE, SubscriptionTier.PRO))
            roles.save(CustomRole(
                id = RoleId(roleId), tenantId = tenantId, name = "Staf Uji", description = "",
                modulePermissions = mapOf(
                    GarmentModules.QUALITY_CONTROL to ModuleAccessConfig(level, DataScope.ALL_TENANT_DATA),
                    GarmentModules.CRM_SALES to ModuleAccessConfig(level, DataScope.ALL_TENANT_DATA)
                )
            ))
        }
        application {
            module(
                tenantRepository = tenants,
                pipelineRepository = InMemoryTenantPipelineRepository(),
                entitlementRepository = InMemoryTenantEntitlementRepository(),
                roleRepository = roles,
                moduleAssignmentRepository = InMemoryModuleAssignmentRepository(),
                crmLeadRepository = leads,
                objectStorage = storage,
                fieldFileRecordRows = rows
            )
        }
        return storage
    }

    private fun rowsWith(vararg values: Pair<String, String>): Map<String, PrototypeRowRepository> =
        mapOf("quality_control" to InMemoryPrototypeRowRepository().apply {
            runBlocking { save(tenantId, PrototypeRow("rec-1", values.toMap())) }
        })

    private suspend fun seedLead(leads: InMemoryCrmLeadRepository, ref: String? = null) {
        var attrs = CustomAttributes.EMPTY
        if (ref != null) attrs = attrs.with(CustomFieldId("lampiran"), CustomAttributes.textCell(ref))
        leads.save(CrmLead(
            id = LeadId("lead-1"), tenantId = tenantId, customAttributes = attrs,
            createdAt = Instant.fromEpochMilliseconds(0), updatedAt = Instant.fromEpochMilliseconds(0)
        ))
    }

    private suspend fun HttpResponse.refOf(): String = JsonParser.parseObject(bodyAsText()).string("ref")!!

    private suspend fun HttpResponse.urlOf(): String = JsonParser.parseObject(bodyAsText()).string("url")!!

    // ---- Gerbang RBAC modul induk (paling awal, sebelum body dibaca) ----------------------------------

    @Test fun `tanpa kredensial ditolak 401`() = testApplication {
        installApp(AccessLevel.MANAGE)
        assertEquals(HttpStatusCode.Unauthorized, client.get("$base/download") { header("Host", "$slug.wemakeerp.com") }.status)
    }

    @Test fun `unggah tanpa OPERATE ditolak 403 sebelum body dibaca`() = testApplication {
        installApp(AccessLevel.VIEW)
        // Body sampah TANPA fileName: bila handler membaca body/memvalidasi dulu jawabannya 400;
        // 403 membuktikan gerbang RBAC berjalan paling awal (Kontrak 7).
        val r = client.post("$base/upload") { asStaff(slug, roleId); setBody("bukan byte") }
        assertEquals(HttpStatusCode.Forbidden, r.status)
    }

    @Test fun `unduh tanpa VIEW ditolak 403`() = testApplication {
        installApp(AccessLevel.NONE)
        assertEquals(HttpStatusCode.Forbidden, client.get("$base/download") { asStaff(slug, roleId) }.status)
    }

    @Test fun `modul tak dikenal ditolak 403`() = testApplication {
        installApp(AccessLevel.MANAGE)
        val r = client.post("/api/tenant/modules/modul_hantu/records/rec-1/fields/lampiran/upload?fileName=a.pdf&contentType=application/pdf") {
            asStaff(slug, roleId); setBody(ByteArray(4))
        }
        assertEquals(HttpStatusCode.Forbidden, r.status)
    }

    @Test fun `unggah CRM tanpa OPERATE ditolak 403 sebelum body dibaca`() = testApplication {
        installApp(AccessLevel.VIEW)
        val r = client.post(crmBase + "/upload") { asStaff(slug, roleId); setBody("bukan byte") }
        assertEquals(HttpStatusCode.Forbidden, r.status)
    }

    @Test fun `unduh CRM tanpa VIEW ditolak 403`() = testApplication {
        installApp(AccessLevel.NONE)
        assertEquals(HttpStatusCode.Forbidden, client.get("$crmBase/download") { asStaff(slug, roleId) }.status)
    }

    // ---- Urutan tolakan 503 / 400 / 415 / 413 ----------------------------------------------------------

    @Test fun `storage belum dikonfigurasi unggah ditolak 503`() = testApplication {
        installApp(AccessLevel.OPERATE, storage = FakeObjectStorage(configured = false))
        val r = client.post("$base/upload?fileName=a.pdf&contentType=application/pdf") { asStaff(slug, roleId); setBody(ByteArray(4)) }
        assertEquals(HttpStatusCode.ServiceUnavailable, r.status)
    }

    @Test fun `storage belum dikonfigurasi unduh ditolak 503`() = testApplication {
        installApp(AccessLevel.VIEW, storage = FakeObjectStorage(configured = false), rows = rowsWith("lampiran" to "fields/x/y"))
        assertEquals(HttpStatusCode.ServiceUnavailable, client.get("$base/download") { asStaff(slug, roleId) }.status)
    }

    @Test fun `fileName kosong ditolak 400`() = testApplication {
        installApp(AccessLevel.OPERATE)
        val r = client.post("$base/upload?contentType=application/pdf") { asStaff(slug, roleId); setBody(ByteArray(4)) }
        assertEquals(HttpStatusCode.BadRequest, r.status)
    }

    @Test fun `body kosong ditolak 400`() = testApplication {
        installApp(AccessLevel.OPERATE)
        val r = client.post("$base/upload?fileName=a.pdf&contentType=application/pdf") { asStaff(slug, roleId); setBody(ByteArray(0)) }
        assertEquals(HttpStatusCode.BadRequest, r.status)
    }

    @Test fun `tipe konten di luar allowlist ditolak 415`() = testApplication {
        installApp(AccessLevel.OPERATE)
        val r = client.post("$base/upload?fileName=a.exe&contentType=application/x-msdownload") { asStaff(slug, roleId); setBody(ByteArray(4)) }
        assertEquals(HttpStatusCode.UnsupportedMediaType, r.status)
    }

    @Test fun `berkas melebihi 10 MB ditolak 413`() = testApplication {
        installApp(AccessLevel.OPERATE)
        val r = client.post("$base/upload?fileName=besar.pdf&contentType=application/pdf") {
            asStaff(slug, roleId); setBody(ByteArray(10 * 1024 * 1024 + 1))
        }
        assertEquals(HttpStatusCode.PayloadTooLarge, r.status)
    }

    /** Body dengan panjang yang dideklarasikan terpisah dari isinya: membuktikan server percaya-tapi-membatasi. */
    private fun body(declared: Long?, actual: ByteArray) = object : OutgoingContent.ReadChannelContent() {
        override val contentLength: Long? = declared
        override fun readFrom(): ByteReadChannel = ByteReadChannel(actual)
    }

    @Test fun `Content-Length melebihi batas ditolak 413 tanpa membaca body`() = testApplication {
        val storage = installApp(AccessLevel.OPERATE)
        // Isi sebenarnya hanya 16 byte: bila handler membaca body dulu, hasilnya 201 - 413 membuktikan header dicek lebih dulu.
        val r = client.post("$base/upload?fileName=a.pdf&contentType=application/pdf") {
            asStaff(slug, roleId); setBody(body(MAX_FIELD_FILE_BYTES + 1L, ByteArray(16)))
        }
        assertEquals(HttpStatusCode.PayloadTooLarge, r.status)
        assertTrue(storage.stored.isEmpty())
    }

    @Test fun `body tanpa Content-Length yang melebihi batas ditolak 413 dan tidak tersimpan`() = testApplication {
        val storage = installApp(AccessLevel.OPERATE)
        val r = client.post("$base/upload?fileName=a.pdf&contentType=application/pdf") {
            asStaff(slug, roleId); setBody(body(null, ByteArray(MAX_FIELD_FILE_BYTES + 1)))
        }
        assertEquals(HttpStatusCode.PayloadTooLarge, r.status)
        assertTrue(storage.stored.isEmpty())
    }

    @Test fun `body tanpa Content-Length tepat di batas diterima`() = testApplication {
        val storage = installApp(AccessLevel.OPERATE)
        val r = client.post("$base/upload?fileName=a.pdf&contentType=application/pdf") {
            asStaff(slug, roleId); setBody(body(null, ByteArray(MAX_FIELD_FILE_BYTES)))
        }
        assertEquals(HttpStatusCode.Created, r.status)
        assertEquals(1, storage.stored.size)
    }

    @Test fun `Content-Length CRM melebihi batas ditolak 413 tanpa membaca body`() = testApplication {
        installApp(AccessLevel.OPERATE, leads = InMemoryCrmLeadRepository().also { runBlocking { seedLead(it) } })
        val r = client.post("$crmBase/upload?fileName=a.csv&contentType=text/csv") {
            asStaff(slug, roleId); setBody(body(MAX_FIELD_FILE_BYTES + 1L, ByteArray(16)))
        }
        assertEquals(HttpStatusCode.PayloadTooLarge, r.status)
    }

    @Test fun `gerbang modul 403 tetap mendahului pembacaan body besar`() = testApplication {
        installApp(AccessLevel.VIEW)
        val r = client.post("$base/upload?fileName=a.pdf&contentType=application/pdf") {
            asStaff(slug, roleId); setBody(body(MAX_FIELD_FILE_BYTES + 1L, ByteArray(16)))
        }
        assertEquals(HttpStatusCode.Forbidden, r.status)
    }

    @Test fun `tipe konten CRM di luar allowlist ditolak 415`() = testApplication {
        installApp(AccessLevel.OPERATE)
        val r = client.post("$crmBase/upload?fileName=a.html&contentType=text/html") { asStaff(slug, roleId); setBody(ByteArray(4)) }
        assertEquals(HttpStatusCode.UnsupportedMediaType, r.status)
    }

    // ---- Sukses: ref tersusun server & lolos FileRef.isValid ------------------------------------------

    @Test fun `unggah sukses menghasilkan ref FileRef sah`() = testApplication {
        val storage = installApp(AccessLevel.OPERATE)
        val r = client.post("$base/upload?fileName=scan.pdf&contentType=application/pdf") { asStaff(slug, roleId); setBody(ByteArray(10)) }
        assertEquals(HttpStatusCode.Created, r.status)
        val ref = r.refOf()
        assertTrue(FileRef.isValid(ref), "ref harus sah: $ref")
        assertTrue(ref.startsWith("fields/ten-field-file-uji/quality_control/rec-1/lampiran-"), "ref: $ref")
        assertTrue(storage.stored.containsKey(ref), "byte harus tersimpan di storage pada ref itu")
        assertEquals("application/pdf", storage.stored.getValue(ref).second)
    }

    @Test fun `nama berkas hostile disanitasi dalam ref`() = testApplication {
        installApp(AccessLevel.OPERATE)
        val r = client.post("$base/upload?fileName=..%2F..%2Fetc%2Fpasswd.png&contentType=text/plain") { asStaff(slug, roleId); setBody(ByteArray(4)) }
        assertEquals(HttpStatusCode.Created, r.status)
        val ref = r.refOf()
        assertTrue(FileRef.isValid(ref))
        assertFalse(ref.contains(".."))
        assertTrue(ref.endsWith("passwd.png"), "sisa nama setelah pemisah path: $ref")
    }

    @Test fun `unggah CRM sukses menghasilkan ref FileRef sah`() = testApplication {
        val storage = installApp(AccessLevel.OPERATE, leads = InMemoryCrmLeadRepository().also { runBlocking { seedLead(it) } })
        val r = client.post("$crmBase/upload?fileName=lampiran.csv&contentType=text/csv") { asStaff(slug, roleId); setBody(ByteArray(4)) }
        assertEquals(HttpStatusCode.Created, r.status)
        val ref = r.refOf()
        assertTrue(FileRef.isValid(ref))
        assertTrue(ref.startsWith("fields/ten-field-file-uji/crm_sales/lead-1/lampiran-"), "ref: $ref")
        assertTrue(storage.stored.containsKey(ref))
    }

    // ---- Unduh: ref dari nilai field record ------------------------------------------------------------

    @Test fun `unduh sukses memberi URL presigned`() = testApplication {
        val ref = "fields/ten-field-file-uji/quality_control/rec-1/lampiran-abc123-scan.pdf"
        installApp(AccessLevel.VIEW, rows = rowsWith("lampiran" to ref))
        val r = client.get("$base/download") { asStaff(slug, roleId) }
        assertEquals(HttpStatusCode.OK, r.status)
        assertTrue(r.urlOf().contains(ref))
    }

    @Test fun `unduh record tak ada ditolak 404`() = testApplication {
        installApp(AccessLevel.VIEW) // tanpa row store untuk modul ini
        assertEquals(HttpStatusCode.NotFound, client.get("$base/download") { asStaff(slug, roleId) }.status)
    }

    @Test fun `unduh field bukan ref sah ditolak 404`() = testApplication {
        installApp(AccessLevel.VIEW, rows = rowsWith("lampiran" to "../bukan-ref"))
        assertEquals(HttpStatusCode.NotFound, client.get("$base/download") { asStaff(slug, roleId) }.status)
    }

    @Test fun `unduh field kosong ditolak 404`() = testApplication {
        installApp(AccessLevel.VIEW, rows = rowsWith("lain" to "fields/ten-field-file-uji/quality_control/rec-1/lain-abc-a.pdf"))
        assertEquals(HttpStatusCode.NotFound, client.get("$base/download") { asStaff(slug, roleId) }.status)
    }

    @Test fun `unduh CRM lead tak ada ditolak 404`() = testApplication {
        installApp(AccessLevel.VIEW)
        assertEquals(HttpStatusCode.NotFound, client.get("$crmBase/download") { asStaff(slug, roleId) }.status)
    }

    @Test fun `unduh CRM sukses memberi URL presigned`() = testApplication {
        val leads = InMemoryCrmLeadRepository()
        installApp(AccessLevel.VIEW, leads = leads)
        val ref = "fields/ten-field-file-uji/crm_sales/lead-1/lampiran-abc123-lampiran.csv"
        seedLead(leads, ref)
        val r = client.get("$crmBase/download") { asStaff(slug, roleId) }
        assertEquals(HttpStatusCode.OK, r.status)
        assertTrue(r.urlOf().contains(ref))
    }

    @Test fun `unduh CRM tanpa referensi sah ditolak 404`() = testApplication {
        val leads = InMemoryCrmLeadRepository()
        installApp(AccessLevel.VIEW, leads = leads)
        seedLead(leads, "bukan-ref")
        assertEquals(HttpStatusCode.NotFound, client.get("$crmBase/download") { asStaff(slug, roleId) }.status)
    }
}
