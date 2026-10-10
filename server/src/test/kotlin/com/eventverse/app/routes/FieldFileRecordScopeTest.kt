package com.eventverse.app.routes

import com.eventverse.app.asStaff
import com.eventverse.app.domain.customfield.CustomAttributes
import com.eventverse.app.domain.customfield.CustomFieldId
import com.eventverse.app.domain.customfield.CrmFieldType
import com.eventverse.app.domain.discovery.handoff.PrototypeRowRepository
import com.eventverse.app.domain.discovery.handoff.RecordOwnerSource
import com.eventverse.app.domain.orgchart.OrgNodeId
import com.eventverse.app.domain.pack.DomainPackRegistry
import com.eventverse.app.domain.pack.GarmentModules
import com.eventverse.app.domain.pack.tenant.layanan.LayananPilotPack
import com.eventverse.app.domain.prototype.FieldType
import com.eventverse.app.domain.prototype.PrototypeRow
import com.eventverse.app.domain.rbac.AccessLevel
import com.eventverse.app.domain.rbac.DataScope
import com.eventverse.app.domain.rbac.ModuleAccessConfig
import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.infrastructure.InMemoryCrmLeadRepository
import com.eventverse.app.infrastructure.InMemoryCustomFieldDefinitionRepository
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Gerbang record/jangkauan FILE (TRD-FIELD-004 Track A, F1/F3): unggah-unduh generik wajib mensyaratkan
 * record ada + jangkauan data (modul HIERARCHICAL), dan ref tersimpan terikat ke record pemiliknya.
 * Dijalankan di pack garment dan pack `layanan` (non-default); peran tak berwenang wajib 403.
 */
class FieldFileRecordScopeTest {
    private val g = FieldGate
    private val qcBase = "/api/tenant/modules/quality_control/records/rec-1/fields/lampiran"
    private val soBase = "/api/tenant/modules/sampling_order/records/rec-1/fields/lampiran"
    private val upload = "/upload?fileName=scan.pdf&contentType=application/pdf"
    private val fieldId = "cf-lampiran"
    private val qcManage = mapOf(GarmentModules.QUALITY_CONTROL to ModuleAccessConfig(AccessLevel.MANAGE, DataScope.ALL_TENANT_DATA))
    private val ref1 = "fields/abc/quality_control/rec-1/lampiran-abc123-scan.pdf"
    private val ref2 = "fields/abc/quality_control/rec-2/lampiran-abc123-scan.pdf"

    @AfterTest fun cleanRegistry() { DomainPackRegistry.unregister(LayananPilotPack.CODE) }

    private fun row(id: String, value: String? = null) =
        PrototypeRow(id, if (value == null) emptyMap() else mapOf("lampiran" to value))

    // ---- Unggah generik: record wajib ada --------------------------------------------------------------

    @Test fun `upload_recordMissing_404`() = testApplication {
        val storage = installFieldGateApp(qcManage, rows = mapOf("quality_control" to g.rows(row("rec-1"))))
        val r = client.post("/api/tenant/modules/quality_control/records/rec-9/fields/lampiran$upload") {
            asStaff(g.SLUG, g.ROLE_ID); setBody(ByteArray(8))
        }
        assertEquals(HttpStatusCode.NotFound, r.status, r.bodyAsText())
        assertTrue(storage.stored.isEmpty(), "tidak boleh ada objek yatim: ${storage.stored.keys}")
    }

    @Test fun `upload_noRecordSource_global_404`() = testApplication {
        val storage = installFieldGateApp(qcManage)
        val r = client.post("$qcBase$upload") { asStaff(g.SLUG, g.ROLE_ID); setBody(ByteArray(8)) }
        assertEquals(HttpStatusCode.NotFound, r.status, r.bodyAsText())
        assertTrue(storage.stored.isEmpty())
    }

    @Test fun `upload_ownRecord_201`() = testApplication {
        val storage = installFieldGateApp(qcManage, rows = mapOf("quality_control" to g.rows(row("rec-1"))))
        val r = client.post("$qcBase$upload") { asStaff(g.SLUG, g.ROLE_ID); setBody(ByteArray(8)) }
        assertEquals(HttpStatusCode.Created, r.status, r.bodyAsText())
        assertEquals(1, storage.stored.size)
    }

    @Test fun `upload_unauthorizedRole_403_beforeBody`() = testApplication {
        val view = mapOf(GarmentModules.QUALITY_CONTROL to ModuleAccessConfig(AccessLevel.VIEW, DataScope.ALL_TENANT_DATA))
        val storage = installFieldGateApp(view, rows = mapOf("quality_control" to g.rows(row("rec-1"))))
        val r = client.post("$qcBase$upload") { asStaff(g.SLUG, g.ROLE_ID); setBody(ByteArray(8)) }
        assertEquals(HttpStatusCode.Forbidden, r.status)
        assertTrue(storage.stored.isEmpty())
    }

    // ---- Modul HIERARCHICAL tanpa sumber pemilik di rute generik = fail-closed 403 (Q2) -----------------

    private val soOwnData = mapOf(GarmentModules.SAMPLING_ORDER to ModuleAccessConfig(AccessLevel.OPERATE, DataScope.OWN_DATA_ONLY))

    @Test fun `upload_noOwnerSource_hierarchical_403`() = testApplication {
        val storage = installFieldGateApp(soOwnData, rows = mapOf("sampling_order" to g.rows(row("rec-1"))))
        val r = client.post("$soBase$upload") { asStaff(g.SLUG, g.ROLE_ID); setBody(ByteArray(8)) }
        assertEquals(HttpStatusCode.Forbidden, r.status, r.bodyAsText())
        assertTrue(storage.stored.isEmpty(), "objek tidak boleh tersimpan: ${storage.stored.keys}")
    }

    @Test fun `upload_noRowsAtAll_hierarchical_403`() = testApplication {
        val storage = installFieldGateApp(soOwnData)
        val r = client.post("$soBase$upload") { asStaff(g.SLUG, g.ROLE_ID); setBody(ByteArray(8)) }
        assertEquals(HttpStatusCode.Forbidden, r.status, r.bodyAsText())
        assertTrue(storage.stored.isEmpty())
    }

    @Test fun `download_noOwnerSource_hierarchical_403`() = testApplication {
        val own = "fields/abc/sampling_order/rec-1/lampiran-abc123-scan.pdf"
        val view = mapOf(GarmentModules.SAMPLING_ORDER to ModuleAccessConfig(AccessLevel.VIEW, DataScope.OWN_DATA_ONLY))
        val storage = installFieldGateApp(view, rows = mapOf("sampling_order" to g.rows(row("rec-1", own))))
        val r = client.get("$soBase/download") { asStaff(g.SLUG, g.ROLE_ID) }
        assertEquals(HttpStatusCode.Forbidden, r.status, r.bodyAsText())
        assertTrue(storage.downloadCalls.isEmpty())
    }

    /** Penyimpan baris yang juga sumber pemilik: prasyarat modul HIERARCHICAL di rute generik (A2). */
    private class OwnedRows(
        private val inner: PrototypeRowRepository,
        private val owners: Map<String, OrgNodeId>
    ) : PrototypeRowRepository by inner, RecordOwnerSource {
        override suspend fun ownerOf(tenantId: TenantId, recordId: String): OrgNodeId? = owners[recordId]
    }

    private fun soRows(owner: OrgNodeId?, value: String? = null) = mapOf(
        "sampling_order" to OwnedRows(g.rows(row("rec-1", value)), listOfNotNull(owner?.let { "rec-1" to it }).toMap())
    )

    private val soRef = "fields/abc/sampling_order/rec-1/lampiran-abc123-scan.pdf"

    @Test fun `upload_hierarchical_otherOwnersRecord_403`() = testApplication {
        val storage = installFieldGateApp(soOwnData, rows = soRows(g.otherId))
        val r = client.post("$soBase$upload") { asStaff(g.SLUG, g.ROLE_ID); setBody(ByteArray(8)) }
        assertEquals(HttpStatusCode.Forbidden, r.status, r.bodyAsText())
        assertTrue(storage.stored.isEmpty())
    }

    @Test fun `upload_hierarchical_ownRecord_201`() = testApplication {
        val storage = installFieldGateApp(soOwnData, rows = soRows(g.staffId))
        val r = client.post("$soBase$upload") { asStaff(g.SLUG, g.ROLE_ID); setBody(ByteArray(8)) }
        assertEquals(HttpStatusCode.Created, r.status, r.bodyAsText())
        assertEquals(1, storage.stored.size)
    }

    @Test fun `upload_hierarchical_recordWithoutOwner_ownDataScope_403`() = testApplication {
        installFieldGateApp(soOwnData, rows = soRows(owner = null))
        val r = client.post("$soBase$upload") { asStaff(g.SLUG, g.ROLE_ID); setBody(ByteArray(8)) }
        assertEquals(HttpStatusCode.Forbidden, r.status, r.bodyAsText())
    }

    @Test fun `upload_hierarchical_allTenantScope_otherOwner_201`() = testApplication {
        val all = mapOf(GarmentModules.SAMPLING_ORDER to ModuleAccessConfig(AccessLevel.OPERATE, DataScope.ALL_TENANT_DATA))
        installFieldGateApp(all, rows = soRows(g.otherId))
        val r = client.post("$soBase$upload") { asStaff(g.SLUG, g.ROLE_ID); setBody(ByteArray(8)) }
        assertEquals(HttpStatusCode.Created, r.status, r.bodyAsText())
    }

    @Test fun `upload_hierarchical_recordMissing_404`() = testApplication {
        installFieldGateApp(soOwnData, rows = soRows(g.staffId))
        val r = client.post("/api/tenant/modules/sampling_order/records/rec-9/fields/lampiran$upload") {
            asStaff(g.SLUG, g.ROLE_ID); setBody(ByteArray(8))
        }
        assertEquals(HttpStatusCode.NotFound, r.status, r.bodyAsText())
    }

    private val soViewOwnData = mapOf(GarmentModules.SAMPLING_ORDER to ModuleAccessConfig(AccessLevel.VIEW, DataScope.OWN_DATA_ONLY))

    @Test fun `download_hierarchical_outOfScope_403`() = testApplication {
        val storage = installFieldGateApp(soViewOwnData, rows = soRows(g.otherId, soRef))
        val r = client.get("$soBase/download") { asStaff(g.SLUG, g.ROLE_ID) }
        assertEquals(HttpStatusCode.Forbidden, r.status, r.bodyAsText())
        assertTrue(storage.downloadCalls.isEmpty())
    }

    @Test fun `download_hierarchical_ownRecord_200`() = testApplication {
        val storage = installFieldGateApp(soViewOwnData, rows = soRows(g.staffId, soRef))
        val r = client.get("$soBase/download") { asStaff(g.SLUG, g.ROLE_ID) }
        assertEquals(HttpStatusCode.OK, r.status, r.bodyAsText())
        assertEquals(listOf(soRef), storage.downloadCalls)
    }

    // ---- Pack non-default (layanan, GLOBAL_ONLY) -------------------------------------------------------

    private val layananCode = LayananPilotPack.CHANGE_REQUEST.value
    private val layananBase = "/api/tenant/modules/$layananCode/records/rec-1/fields/lampiran"
    private val layananManage = mapOf(LayananPilotPack.CHANGE_REQUEST to ModuleAccessConfig(AccessLevel.MANAGE, DataScope.ALL_TENANT_DATA))

    @Test fun `upload_layananOwnRecord_201`() = testApplication {
        val storage = installFieldGateApp(layananManage, pack = LayananPilotPack.CODE, rows = mapOf(layananCode to g.rows(row("rec-1"))))
        val r = client.post("$layananBase$upload") { asStaff(g.SLUG, g.ROLE_ID); setBody(ByteArray(8)) }
        assertEquals(HttpStatusCode.Created, r.status, r.bodyAsText())
        assertEquals(1, storage.stored.size)
    }

    @Test fun `upload_otherTenantRecordId_404`() = testApplication {
        // Record rec-1 hanya ada milik tenant lain: bagi tenant `abc` record itu tidak ada.
        val storage = installFieldGateApp(
            layananManage, pack = LayananPilotPack.CODE,
            rows = mapOf(layananCode to g.rows(row("rec-1"), forTenant = TenantId("tenant-lain")))
        )
        val r = client.post("$layananBase$upload") { asStaff(g.SLUG, g.ROLE_ID); setBody(ByteArray(8)) }
        assertEquals(HttpStatusCode.NotFound, r.status, r.bodyAsText())
        assertTrue(storage.stored.isEmpty())
    }

    // ---- Unduh generik: ikatan recordId ----------------------------------------------------------------

    @Test fun `download_ownRecord_200`() = testApplication {
        val storage = installFieldGateApp(qcManage, rows = mapOf("quality_control" to g.rows(row("rec-1", ref1))))
        val r = client.get("$qcBase/download") { asStaff(g.SLUG, g.ROLE_ID) }
        assertEquals(HttpStatusCode.OK, r.status, r.bodyAsText())
        assertEquals(listOf(ref1), storage.downloadCalls)
    }

    @Test fun `download_refOfAnotherRecord_403`() = testApplication {
        val storage = installFieldGateApp(qcManage, rows = mapOf("quality_control" to g.rows(row("rec-1", ref2), row("rec-2", ref2))))
        val r = client.get("$qcBase/download") { asStaff(g.SLUG, g.ROLE_ID) }
        assertEquals(HttpStatusCode.Forbidden, r.status, r.bodyAsText())
        assertTrue(storage.downloadCalls.isEmpty(), "storage tak boleh tersentuh: ${storage.downloadCalls}")
    }

    // ---- CRM: ikatan recordId di unduh & tulis ---------------------------------------------------------

    private val crmManage = mapOf(GarmentModules.CRM_SALES to ModuleAccessConfig(AccessLevel.MANAGE, DataScope.ALL_TENANT_DATA))
    private val lead1Ref = "fields/abc/crm_sales/lead-1/$fieldId-abc123-a.pdf"
    private val lead2Ref = "fields/abc/crm_sales/lead-2/$fieldId-abc123-a.pdf"

    private fun fileCell(ref: String) = """{"customAttributes":{"$fieldId":{"t":"text","v":"$ref"}}}"""

    private fun crmFixture(leadRef: String? = null): Pair<InMemoryCrmLeadRepository, InMemoryCustomFieldDefinitionRepository> {
        val leads = InMemoryCrmLeadRepository()
        val defs = InMemoryCustomFieldDefinitionRepository()
        runBlocking {
            defs.save(g.definition(fieldId, "lampiran", CrmFieldType(FieldType.FILE)))
            val attrs = leadRef?.let { CustomAttributes.EMPTY.with(CustomFieldId(fieldId), CustomAttributes.textCell(it)) } ?: CustomAttributes.EMPTY
            leads.save(g.lead("lead-1", attrs = attrs))
        }
        return leads to defs
    }

    @Test fun `crmDownload_refOfAnotherLead_403`() = testApplication {
        val (leads, defs) = crmFixture(lead2Ref)
        val storage = installFieldGateApp(crmManage, leads = leads, definitions = defs)
        val r = client.get("/api/tenant/crm/leads/lead-1/fields/$fieldId/download") { asStaff(g.SLUG, g.ROLE_ID) }
        assertEquals(HttpStatusCode.Forbidden, r.status, r.bodyAsText())
        assertTrue(storage.downloadCalls.isEmpty())
    }

    @Test fun `crmDownload_ownLead_200`() = testApplication {
        val (leads, defs) = crmFixture(lead1Ref)
        val storage = installFieldGateApp(crmManage, leads = leads, definitions = defs)
        val r = client.get("/api/tenant/crm/leads/lead-1/fields/$fieldId/download") { asStaff(g.SLUG, g.ROLE_ID) }
        assertEquals(HttpStatusCode.OK, r.status, r.bodyAsText())
        assertEquals(listOf(lead1Ref), storage.downloadCalls)
    }

    @Test fun `write_fileRefOfAnotherRecord_400`() = testApplication {
        val (leads, defs) = crmFixture()
        installFieldGateApp(crmManage, leads = leads, definitions = defs)
        val r = client.patch("/api/tenant/crm/leads/lead-1") { asStaff(g.SLUG, g.ROLE_ID); setBody(fileCell(lead2Ref)) }
        assertEquals(HttpStatusCode.BadRequest, r.status, r.bodyAsText())
        assertNull(leads.findById(g.tenantId, com.eventverse.app.domain.crm.LeadId("lead-1"))?.customAttributes?.rawCell(CustomFieldId(fieldId)))
    }

    @Test fun `put_fileRefSameRecord_200`() = testApplication {
        val (leads, defs) = crmFixture()
        installFieldGateApp(crmManage, leads = leads, definitions = defs)
        val r = client.patch("/api/tenant/crm/leads/lead-1") { asStaff(g.SLUG, g.ROLE_ID); setBody(fileCell(lead1Ref)) }
        assertEquals(HttpStatusCode.OK, r.status, r.bodyAsText())
    }

    @Test fun `create_withFileValue_400`() = testApplication {
        val (leads, defs) = crmFixture()
        installFieldGateApp(crmManage, leads = leads, definitions = defs)
        // Record baru belum punya id saat klien menulis, jadi tidak mungkin sudah punya berkas sendiri.
        val r = client.post("/api/tenant/crm/leads") { asStaff(g.SLUG, g.ROLE_ID); setBody(fileCell("fields/abc/crm_sales/baru/$fieldId-abc123-a.pdf")) }
        assertEquals(HttpStatusCode.BadRequest, r.status, r.bodyAsText())
    }

    @Test fun `write_unauthorizedRole_403`() = testApplication {
        val (leads, defs) = crmFixture()
        val view = mapOf(GarmentModules.CRM_SALES to ModuleAccessConfig(AccessLevel.VIEW, DataScope.ALL_TENANT_DATA))
        installFieldGateApp(view, leads = leads, definitions = defs)
        val r = client.patch("/api/tenant/crm/leads/lead-1") { asStaff(g.SLUG, g.ROLE_ID); setBody(fileCell(lead1Ref)) }
        assertEquals(HttpStatusCode.Forbidden, r.status)
    }
}
