package com.eventverse.app

import com.eventverse.app.domain.discovery.DeterministicDiscoveryAgent
import com.eventverse.app.domain.discovery.DiscoveryRequest
import com.eventverse.app.domain.discovery.scenario.ScenarioId
import com.eventverse.app.domain.discovery.scenario.ScenarioSketch
import com.eventverse.app.domain.discovery.scenario.ScenarioSource
import com.eventverse.app.domain.discovery.scenario.ScenarioStep
import com.eventverse.app.domain.pack.GarmentDomainPack
import com.eventverse.app.domain.pack.ModuleId
import com.eventverse.app.domain.tenant.SubscriptionTier
import com.eventverse.app.domain.tenant.Tenant
import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.domain.tenant.TenantName
import com.eventverse.app.domain.tenant.TenantSlug
import com.eventverse.app.domain.tenant.TenantStatus
import com.eventverse.app.infrastructure.DatabaseFactory
import com.eventverse.app.infrastructure.InMemoryDepartmentRepository
import com.eventverse.app.infrastructure.InMemoryDiscoveryDemandRepository
import com.eventverse.app.infrastructure.InMemoryDiscoveryDraftRepository
import com.eventverse.app.infrastructure.InMemoryDomainPackRepository
import com.eventverse.app.infrastructure.InMemoryEmployeeRepository
import com.eventverse.app.infrastructure.InMemoryModuleAssignmentRepository
import com.eventverse.app.infrastructure.InMemoryRoleRepository
import com.eventverse.app.infrastructure.InMemoryTenantEntitlementRepository
import com.eventverse.app.infrastructure.InMemoryTenantPipelineRepository
import com.eventverse.app.infrastructure.InMemoryTenantRepository
import com.eventverse.app.shared.discovery.DiscoveryDraftCodec
import com.eventverse.app.shared.json.JsonParser
import com.eventverse.app.shared.json.JsonValue
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** TRD-BLDR-002 SB4: ringkasan draf menyertakan `scenarios`; menyimpan ulang lewat PUT tidak menghapusnya. */
class DiscoveryScenarioSummaryApiTest {

    private val slug = "garment-uji"
    private val scenario = ScenarioSketch(
        ScenarioId("scn-1"), "Lead ke invoice",
        listOf(ScenarioStep(ModuleId("crm"), null, "Lead"), ScenarioStep(ModuleId("sales"), null, "Invoice")),
        ScenarioSource.AI
    )

    private fun document(withScenarios: Boolean) = runBlocking {
        val draft = DeterministicDiscoveryAgent().draft(DiscoveryRequest("Kami klinik dengan jadwal dokter dan tagihan.", industryHint = "klinik")).getOrThrow()
        DiscoveryDraftCodec.encodeToString(if (withScenarios) draft.copy(scenarios = listOf(scenario)) else draft)
    }

    @Test
    fun `scenarios ikut ringkasan dan bertahan setelah PUT berikutnya`() = testApplication {
        DatabaseFactory.init()
        val tenants = InMemoryTenantRepository().also {
            runBlocking { it.save(Tenant(TenantId("ten-g"), TenantSlug(slug), TenantName(slug), TenantStatus.ACTIVE, SubscriptionTier.PRO, domainPack = GarmentDomainPack.CODE)) }
        }
        val drafts = InMemoryDiscoveryDraftRepository()
        application {
            module(tenantRepository = tenants, pipelineRepository = InMemoryTenantPipelineRepository(),
                entitlementRepository = InMemoryTenantEntitlementRepository(), roleRepository = InMemoryRoleRepository(),
                moduleAssignmentRepository = InMemoryModuleAssignmentRepository(), departmentRepository = InMemoryDepartmentRepository(),
                employeeRepository = InMemoryEmployeeRepository(), domainPackRepository = InMemoryDomainPackRepository(),
                discoveryDraftRepository = drafts, discoveryDemandRepository = InMemoryDiscoveryDemandRepository())
        }
        val created = client.post("/api/discovery/drafts") {
            asTenant(slug); contentType(ContentType.Application.Json)
            setBody("""{"id":"draft-scn","narrative":"Kami klinik dengan jadwal dokter dan tagihan.","industryHint":"klinik"}""")
        }
        assertEquals(HttpStatusCode.Created, created.status)
        assertNull((JsonParser.parseObject(created.bodyAsText()))["scenarios"], "draf tanpa skenario: tidak ada kunci baru")

        val put = client.put("/api/discovery/drafts/draft-scn") { asTenant(slug); contentType(ContentType.Application.Json); setBody(document(true)) }
        assertEquals(HttpStatusCode.OK, put.status)
        val scenarios = JsonParser.parseObject(put.bodyAsText()).array("scenarios").filterIsInstance<JsonValue.Obj>()
        assertEquals(listOf("scn-1"), scenarios.map { it.string("id") })

        val got = JsonParser.parseObject(client.get("/api/discovery/drafts/draft-scn") { asTenant(slug) }.bodyAsText())
        assertEquals(listOf("scn-1"), got.array("scenarios").filterIsInstance<JsonValue.Obj>().map { it.string("id") })
        assertEquals(listOf(scenario), runBlocking { drafts.findById(com.eventverse.app.domain.discovery.DiscoveryDraftId("draft-scn")) }?.draft?.scenarios)
    }
}
