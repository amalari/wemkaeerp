package com.eventverse.app.routes

import com.eventverse.app.asStaff
import com.eventverse.app.domain.crm.CrmLead
import com.eventverse.app.domain.crm.LeadId
import com.eventverse.app.domain.customfield.CustomAttributes
import com.eventverse.app.domain.customfield.CustomFieldDefinition
import com.eventverse.app.domain.customfield.CustomFieldId
import com.eventverse.app.domain.customfield.FieldKey
import com.eventverse.app.domain.customfield.FieldType
import com.eventverse.app.domain.customfield.OwnerResource
import com.eventverse.app.domain.discovery.handoff.InMemoryPrototypeRowRepository
import com.eventverse.app.domain.discovery.handoff.PrototypeRowRepository
import com.eventverse.app.domain.pack.DomainPackCode
import com.eventverse.app.domain.pack.DomainPackRegistry
import com.eventverse.app.domain.pack.DomainPackStatus
import com.eventverse.app.domain.pack.StoredDomainPack
import com.eventverse.app.domain.pack.GarmentDomainPack
import com.eventverse.app.domain.pack.GarmentModules
import com.eventverse.app.domain.pack.ModuleId
import com.eventverse.app.domain.pack.tenant.layanan.LayananPilotPack
import com.eventverse.app.domain.prototype.PrototypeRow
import com.eventverse.app.domain.rbac.AccessLevel
import com.eventverse.app.domain.rbac.CustomRole
import com.eventverse.app.domain.rbac.DataScope
import com.eventverse.app.domain.rbac.ModuleAccessConfig
import com.eventverse.app.domain.rbac.RoleId
import com.eventverse.app.domain.storage.ObjectStorage
import com.eventverse.app.domain.tenant.SubscriptionTier
import com.eventverse.app.domain.tenant.Tenant
import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.domain.tenant.TenantName
import com.eventverse.app.domain.tenant.TenantSlug
import com.eventverse.app.domain.tenant.TenantStatus
import com.eventverse.app.infrastructure.InMemoryCrmLeadRepository
import com.eventverse.app.infrastructure.InMemoryCustomFieldDefinitionRepository
import com.eventverse.app.infrastructure.InMemoryDomainPackRepository
import com.eventverse.app.infrastructure.InMemoryModuleAssignmentRepository
import com.eventverse.app.infrastructure.InMemoryRoleRepository
import com.eventverse.app.infrastructure.InMemoryTenantEntitlementRepository
import com.eventverse.app.infrastructure.InMemoryTenantPipelineRepository
import com.eventverse.app.infrastructure.InMemoryTenantRepository
import com.eventverse.app.module
import com.eventverse.app.shared.json.JsonParser
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Instant
import java.util.concurrent.ConcurrentHashMap
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Isolasi tenant untuk tipe FILE (TRD-FIELD-002, hardening): `FileRef.isValid` hanya memeriksa BENTUK,
 * jadi tanpa pemeriksaan kepemilikan, tenant A bisa menulis `fields/<tenantB>/...` ke sel FILE-nya lalu
 * meminta URL unduh bertanda tangan untuk objek tenant B. Tes ini memakai id tenant `abc` dan `abcd`
 * (satu awalan teks) supaya `startsWith` tanpa pemisah segmen juga tertangkap.
 */
class FieldFileTenantIsolationTest {
    private val slug = "iso-abc"
    private val tenantId = TenantId("abc")
    private val roleId = "role-iso"
    private val ownRef = "fields/abc/quality_control/rec-1/lampiran-abc123-scan.pdf"
    private val foreignRef = "fields/abcd/quality_control/rec-1/lampiran-abc123-scan.pdf"
    private val fieldId = "cf-lampiran"
    private val crmBase = "/api/tenant/crm/leads/lead-1/fields/$fieldId"
    @AfterTest
    fun cleanRegistry() { DomainPackRegistry.unregister(LayananPilotPack.CODE) }

    private val qcBase = "/api/tenant/modules/quality_control/records/rec-1/fields/lampiran"

    /** Mencatat setiap panggilan `downloadUrl`: ref asing tidak boleh pernah sampai ke storage. */
    private class RecordingStorage : ObjectStorage {
        val stored = ConcurrentHashMap<String, Pair<ByteArray, String>>()
        val downloadCalls = mutableListOf<String>()
        override val isConfigured: Boolean get() = true
        override suspend fun put(key: String, bytes: ByteArray, contentType: String): Result<Unit> {
            stored[key] = bytes to contentType
            return Result.success(Unit)
        }
        override suspend fun downloadUrl(key: String): Result<String> {
            downloadCalls += key
            return Result.success("https://presigned.example.com/$key")
        }
    }

    private fun ApplicationTestBuilder.installApp(
        storage: RecordingStorage = RecordingStorage(),
        pack: DomainPackCode = GarmentDomainPack.CODE,
        module: ModuleId = GarmentModules.QUALITY_CONTROL,
        leads: InMemoryCrmLeadRepository = InMemoryCrmLeadRepository(),
        definitions: InMemoryCustomFieldDefinitionRepository = InMemoryCustomFieldDefinitionRepository(),
        rows: Map<String, PrototypeRowRepository> = emptyMap()
    ): RecordingStorage {
        val tenants = InMemoryTenantRepository()
        val roles = InMemoryRoleRepository()
        // Pack data (non-garment) dimuat malas oleh plugin tenant dari repository, bukan dari kode.
        val packs = InMemoryDomainPackRepository()
        if (pack == LayananPilotPack.CODE) {
            DomainPackRegistry.unregister(LayananPilotPack.CODE)
            runBlocking { packs.save(StoredDomainPack(LayananPilotPack.pack, 1, DomainPackStatus.LOCKED, null)) }
        }
        runBlocking {
            tenants.save(Tenant(tenantId, TenantSlug(slug), TenantName("Tenant Isolasi"), TenantStatus.ACTIVE, SubscriptionTier.PRO, domainPack = pack))
            roles.save(CustomRole(
                id = RoleId(roleId), tenantId = tenantId, name = "Staf Uji", description = "",
                modulePermissions = mapOf(
                    module to ModuleAccessConfig(AccessLevel.MANAGE, DataScope.ALL_TENANT_DATA),
                    GarmentModules.CRM_SALES to ModuleAccessConfig(AccessLevel.MANAGE, DataScope.ALL_TENANT_DATA)
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
                domainPackRepository = packs,
                crmLeadRepository = leads,
                customFieldDefinitionRepository = definitions,
                objectStorage = storage,
                fieldFileRecordRows = rows
            )
        }
        return storage
    }

    private fun rowsFor(moduleCode: String, value: String) = mapOf<String, PrototypeRowRepository>(
        moduleCode to InMemoryPrototypeRowRepository().apply {
            runBlocking { save(tenantId, PrototypeRow("rec-1", mapOf("lampiran" to value))) }
        }
    )

    private suspend fun seedCrm(leads: InMemoryCrmLeadRepository, definitions: InMemoryCustomFieldDefinitionRepository, ref: String? = null) {
        definitions.save(CustomFieldDefinition(
            id = CustomFieldId(fieldId), tenantId = tenantId, ownerResource = OwnerResource.CRM_SALES,
            key = FieldKey("lampiran"), label = "Lampiran", type = FieldType.File, position = 1.0
        ))
        var attrs = CustomAttributes.EMPTY
        if (ref != null) attrs = attrs.with(CustomFieldId(fieldId), CustomAttributes.textCell(ref))
        leads.save(CrmLead(
            id = LeadId("lead-1"), tenantId = tenantId, customAttributes = attrs,
            createdAt = Instant.fromEpochMilliseconds(0), updatedAt = Instant.fromEpochMilliseconds(0)
        ))
    }

    private suspend fun HttpResponse.refOf(): String = JsonParser.parseObject(bodyAsText()).string("ref") ?: error("tanpa ref")

    // ---- Unduh modul generik: ref milik tenant lain ditolak, storage tak tersentuh --------------------

    @Test fun `unduh modul garment dengan ref tenant lain ditolak 403 tanpa menyentuh storage`() = testApplication {
        val storage = installApp(rows = rowsFor("quality_control", foreignRef))
        val r = client.get("$qcBase/download") { asStaff(slug, roleId) }
        assertEquals(HttpStatusCode.Forbidden, r.status)
        assertTrue(storage.downloadCalls.isEmpty(), "downloadUrl tidak boleh dipanggil untuk ref asing: ${storage.downloadCalls}")
    }

    @Test fun `unduh modul pack non-garment dengan ref tenant lain ditolak 403 tanpa menyentuh storage`() = testApplication {
        val code = LayananPilotPack.CHANGE_REQUEST.value
        val storage = installApp(
            pack = LayananPilotPack.CODE, module = LayananPilotPack.CHANGE_REQUEST,
            rows = rowsFor(code, "fields/abcd/$code/rec-1/lampiran-abc123-scan.pdf")
        )
        val r = client.get("/api/tenant/modules/$code/records/rec-1/fields/lampiran/download") { asStaff(slug, roleId) }
        assertEquals(HttpStatusCode.Forbidden, r.status)
        assertTrue(storage.downloadCalls.isEmpty(), "downloadUrl tidak boleh dipanggil: ${storage.downloadCalls}")
    }

    @Test fun `unduh modul pack non-garment dengan ref tenant sendiri tetap sukses`() = testApplication {
        val code = LayananPilotPack.CHANGE_REQUEST.value
        val own = "fields/abc/$code/rec-1/lampiran-abc123-scan.pdf"
        val storage = installApp(pack = LayananPilotPack.CODE, module = LayananPilotPack.CHANGE_REQUEST, rows = rowsFor(code, own))
        val r = client.get("/api/tenant/modules/$code/records/rec-1/fields/lampiran/download") { asStaff(slug, roleId) }
        assertEquals(HttpStatusCode.OK, r.status, "kontrol: 403 di tes asing berasal dari kepemilikan, bukan RBAC/pack")
        assertEquals(listOf(own), storage.downloadCalls)
    }

    @Test fun `unduh modul dengan ref tenant sendiri tetap sukses`() = testApplication {
        val storage = installApp(rows = rowsFor("quality_control", ownRef))
        val r = client.get("$qcBase/download") { asStaff(slug, roleId) }
        assertEquals(HttpStatusCode.OK, r.status)
        assertEquals(listOf(ownRef), storage.downloadCalls)
    }

    @Test fun `unduh modul dengan ref tenant sendiri tapi modul lain ditolak 403`() = testApplication {
        val storage = installApp(rows = rowsFor("quality_control", "fields/abc/procurement/rec-1/lampiran-abc123-scan.pdf"))
        val r = client.get("$qcBase/download") { asStaff(slug, roleId) }
        assertEquals(HttpStatusCode.Forbidden, r.status)
        assertTrue(storage.downloadCalls.isEmpty())
    }

    // ---- Unduh CRM ------------------------------------------------------------------------------------

    @Test fun `unduh CRM dengan ref tenant lain ditolak 403 tanpa menyentuh storage`() = testApplication {
        val leads = InMemoryCrmLeadRepository(); val defs = InMemoryCustomFieldDefinitionRepository()
        val storage = installApp(leads = leads, definitions = defs)
        seedCrm(leads, defs, "fields/abcd/crm_sales/lead-1/$fieldId-abc123-a.pdf")
        val r = client.get("$crmBase/download") { asStaff(slug, roleId) }
        assertEquals(HttpStatusCode.Forbidden, r.status)
        assertTrue(storage.downloadCalls.isEmpty(), "downloadUrl tidak boleh dipanggil: ${storage.downloadCalls}")
    }

    @Test fun `unduh CRM dengan ref tenant sendiri tetap sukses`() = testApplication {
        val leads = InMemoryCrmLeadRepository(); val defs = InMemoryCustomFieldDefinitionRepository()
        val storage = installApp(leads = leads, definitions = defs)
        val own = "fields/abc/crm_sales/lead-1/$fieldId-abc123-a.pdf"
        seedCrm(leads, defs, own)
        assertEquals(HttpStatusCode.OK, client.get("$crmBase/download") { asStaff(slug, roleId) }.status)
        assertEquals(listOf(own), storage.downloadCalls)
    }

    // ---- Tulis CRM: nilai sel FILE berawalan tenant lain ditolak --------------------------------------

    @Test fun `tulis CRM PATCH sel FILE dengan ref tenant lain ditolak 400 dan tidak tersimpan`() = testApplication {
        val leads = InMemoryCrmLeadRepository(); val defs = InMemoryCustomFieldDefinitionRepository()
        installApp(leads = leads, definitions = defs)
        seedCrm(leads, defs)
        val body = """{"customAttributes":{"$fieldId":{"t":"text","v":"fields/abcd/crm_sales/lead-1/$fieldId-abc123-a.pdf"}}}"""
        val r = client.patch("/api/tenant/crm/leads/lead-1") { asStaff(slug, roleId); setBody(body) }
        assertEquals(HttpStatusCode.BadRequest, r.status, r.bodyAsText())
        val stored = leads.findById(tenantId, LeadId("lead-1"))
        assertEquals(null, stored?.customAttributes?.rawCell(CustomFieldId(fieldId)), "ref asing tidak boleh tersimpan")
    }

    @Test fun `tulis CRM PATCH sel FILE dengan ref tenant sendiri diterima`() = testApplication {
        val leads = InMemoryCrmLeadRepository(); val defs = InMemoryCustomFieldDefinitionRepository()
        installApp(leads = leads, definitions = defs)
        seedCrm(leads, defs)
        val body = """{"customAttributes":{"$fieldId":{"t":"text","v":"fields/abc/crm_sales/lead-1/$fieldId-abc123-a.pdf"}}}"""
        val r = client.patch("/api/tenant/crm/leads/lead-1") { asStaff(slug, roleId); setBody(body) }
        assertEquals(HttpStatusCode.OK, r.status, r.bodyAsText())
    }

    // ---- Unggah: ref selalu berawalan tenant pemanggil ----------------------------------------------

    @Test fun `unggah menghasilkan ref berawalan tenant pemanggil sebagai segmen utuh`() = testApplication {
        val storage = installApp()
        val r = client.post("$qcBase/upload?fileName=scan.pdf&contentType=application/pdf") {
            asStaff(slug, roleId); setBody(ByteArray(8))
        }
        assertEquals(HttpStatusCode.Created, r.status)
        val ref = r.refOf()
        assertTrue(ref.startsWith("fields/abc/quality_control/rec-1/lampiran-"), ref)
        assertTrue(storage.stored.containsKey(ref))
    }

    // ---- Adapter S3: ref tak sah gagal keras, tidak diam-diam jadi key lain ----------------------------

    @Test fun `bucketKey menolak ref tak sah dan memetakan ref sah tenant-first`() {
        val s3 = com.eventverse.app.infrastructure.storage.S3ObjectStorage(endpoint = null, accessKey = "k", secretKey = "s")
        assertEquals("abc/fields/quality_control/rec-1/lampiran-abc123-scan.pdf", s3.bucketKey(ownRef))
        listOf("uploads/abc/x/y/z", "fields/abc/../abcd/r-1/x.pdf", "fields//x/y/z", "fields/abc", "/fields/abc/m/r/f").forEach { bad ->
            val e = runCatching { s3.bucketKey(bad) }.exceptionOrNull()
            assertTrue(e is IllegalArgumentException, "'$bad' harus ditolak keras")
        }
    }
}
