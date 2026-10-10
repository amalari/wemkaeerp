package com.eventverse.app.routes

import com.eventverse.app.asStaff
import com.eventverse.app.domain.crm.LeadId
import com.eventverse.app.domain.customfield.CustomFieldId
import com.eventverse.app.domain.customfield.CrmFieldType
import com.eventverse.app.domain.pack.GarmentModules
import com.eventverse.app.domain.prototype.FieldType
import com.eventverse.app.domain.prototype.PrototypeRow
import com.eventverse.app.domain.rbac.AccessLevel
import com.eventverse.app.domain.rbac.DataScope
import com.eventverse.app.domain.rbac.ModuleAccessConfig
import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.infrastructure.InMemoryCrmLeadRepository
import com.eventverse.app.infrastructure.InMemoryCustomFieldDefinitionRepository
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Guard tulis RELATION CRM (TRD-FIELD-004 F3/FR-3): menautkan record modul target mensyaratkan VIEW atas modul
 * **target** (403) dan jangkauan data target (di luar jangkauan = 400 yang sama dengan "tidak ditemukan",
 * tanpa oracle keberadaan). Tenant kedua: record milik tenant lain tidak pernah ditemukan.
 */
class CrmRelationTargetGateTest {
    private val g = FieldGate
    private val relId = "cf-rujukan"
    private val patchUrl = "/api/tenant/crm/leads/lead-1"

    private fun relBody(recordId: String) = """{"customAttributes":{"$relId":{"t":"text","v":"$recordId"}}}"""

    private fun crm(level: AccessLevel = AccessLevel.OPERATE, scope: DataScope = DataScope.ALL_TENANT_DATA) =
        GarmentModules.CRM_SALES to ModuleAccessConfig(level, scope)

    private fun ApplicationTestBuilder.install(
        target: String,
        permissions: Map<com.eventverse.app.domain.pack.ModuleId, ModuleAccessConfig>,
        leads: InMemoryCrmLeadRepository = InMemoryCrmLeadRepository().also { runBlocking { it.save(g.lead("lead-1", g.staffId)) } },
        rows: Map<String, com.eventverse.app.domain.discovery.handoff.PrototypeRowRepository> = emptyMap()
    ): InMemoryCrmLeadRepository {
        val defs = InMemoryCustomFieldDefinitionRepository()
        runBlocking { defs.save(g.definition(relId, "rujukan", CrmFieldType(FieldType.RELATION, targetResource = target))) }
        installFieldGateApp(permissions, leads = leads, definitions = defs, rows = rows)
        return leads
    }

    private fun qcRows(forTenant: TenantId = g.tenantId) =
        mapOf("quality_control" to g.rows(PrototypeRow("rec-1", mapOf("nama" to "Inspeksi")), forTenant = forTenant))

    private val qcView = GarmentModules.QUALITY_CONTROL to ModuleAccessConfig(AccessLevel.VIEW, DataScope.ALL_TENANT_DATA)

    @Test fun `crmRelation_visibleTarget_200`() = testApplication {
        install("quality_control", mapOf(crm(), qcView), rows = qcRows())
        val r = client.patch(patchUrl) { asStaff(g.SLUG, g.ROLE_ID); setBody(relBody("rec-1")) }
        assertEquals(HttpStatusCode.OK, r.status, r.bodyAsText())
    }

    @Test fun `crmRelation_noViewOnTarget_403`() = testApplication {
        val leads = install("quality_control", mapOf(crm()), rows = qcRows())
        val r = client.patch(patchUrl) { asStaff(g.SLUG, g.ROLE_ID); setBody(relBody("rec-1")) }
        assertEquals(HttpStatusCode.Forbidden, r.status, r.bodyAsText())
        assertNull(leads.findById(g.tenantId, LeadId("lead-1"))?.customAttributes?.rawCell(CustomFieldId(relId)), "tautan tak boleh tersimpan")
    }

    @Test fun `crmRelation_noViewOnTarget_createAlso403`() = testApplication {
        install("quality_control", mapOf(crm()), rows = qcRows())
        val r = client.post("/api/tenant/crm/leads") { asStaff(g.SLUG, g.ROLE_ID); setBody(relBody("rec-1")) }
        assertEquals(HttpStatusCode.Forbidden, r.status, r.bodyAsText())
    }

    @Test fun `crmRelation_missingTarget_400`() = testApplication {
        install("quality_control", mapOf(crm(), qcView), rows = qcRows())
        val r = client.patch(patchUrl) { asStaff(g.SLUG, g.ROLE_ID); setBody(relBody("rec-tidak-ada")) }
        assertEquals(HttpStatusCode.BadRequest, r.status, r.bodyAsText())
    }

    @Test fun `crmRelation_targetOfOtherTenant_400`() = testApplication {
        install("quality_control", mapOf(crm(), qcView), rows = qcRows(forTenant = TenantId("tenant-lain")))
        val r = client.patch(patchUrl) { asStaff(g.SLUG, g.ROLE_ID); setBody(relBody("rec-1")) }
        assertEquals(HttpStatusCode.BadRequest, r.status, r.bodyAsText())
    }

    @Test fun `crmRelation_unauthorizedWrite_403`() = testApplication {
        install("quality_control", mapOf(crm(AccessLevel.VIEW), qcView), rows = qcRows())
        val r = client.patch(patchUrl) { asStaff(g.SLUG, g.ROLE_ID); setBody(relBody("rec-1")) }
        assertEquals(HttpStatusCode.Forbidden, r.status)
    }

    // ---- Target hierarkis (crm_sales): jangkauan data modul target -------------------------------------

    private fun twoLeads() = InMemoryCrmLeadRepository().also {
        runBlocking {
            it.save(g.lead("lead-1", g.staffId))
            it.save(g.lead("lead-own", g.staffId))
            it.save(g.lead("lead-other", g.otherId))
        }
    }

    @Test fun `crmRelation_leadOutOfReach_400`() = testApplication {
        val leads = install("crm_sales", mapOf(crm(scope = DataScope.OWN_DATA_ONLY)), leads = twoLeads())
        val r = client.patch(patchUrl) { asStaff(g.SLUG, g.ROLE_ID, email = g.STAFF_EMAIL); setBody(relBody("lead-other")) }
        assertEquals(HttpStatusCode.BadRequest, r.status, r.bodyAsText())
        assertNull(leads.findById(g.tenantId, LeadId("lead-1"))?.customAttributes?.rawCell(CustomFieldId(relId)))
    }

    @Test fun `crmRelation_leadOutOfReach_sameAnswerAsMissing`() = testApplication {
        install("crm_sales", mapOf(crm(scope = DataScope.OWN_DATA_ONLY)), leads = twoLeads())
        val outOfReach = client.patch(patchUrl) { asStaff(g.SLUG, g.ROLE_ID); setBody(relBody("lead-other")) }
        val missing = client.patch(patchUrl) { asStaff(g.SLUG, g.ROLE_ID); setBody(relBody("lead-hantu")) }
        assertEquals(missing.status, outOfReach.status)
        // Pesan hanya memantulkan id yang dikirim pemanggil sendiri; tidak membedakan "tak ada" vs "di luar jangkauan".
        assertEquals(
            missing.bodyAsText().replace("lead-hantu", "X"),
            outOfReach.bodyAsText().replace("lead-other", "X")
        )
    }

    @Test fun `crmRelation_leadInReach_200`() = testApplication {
        install("crm_sales", mapOf(crm(scope = DataScope.OWN_DATA_ONLY)), leads = twoLeads())
        val r = client.patch(patchUrl) { asStaff(g.SLUG, g.ROLE_ID); setBody(relBody("lead-own")) }
        assertEquals(HttpStatusCode.OK, r.status, r.bodyAsText())
    }
}
