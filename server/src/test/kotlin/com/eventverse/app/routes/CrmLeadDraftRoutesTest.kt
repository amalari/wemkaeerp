package com.eventverse.app.routes

import com.eventverse.app.TestAuth
import com.eventverse.app.infrastructure.auth.JwtTokenService
import com.eventverse.app.domain.auth.Role
import com.eventverse.app.domain.crm.prefill.DeterministicLeadDraftExtractor
import com.eventverse.app.domain.pack.GarmentBlueprints
import com.eventverse.app.domain.pack.GarmentModules
import com.eventverse.app.domain.rbac.AccessLevel
import com.eventverse.app.domain.rbac.CustomRole
import com.eventverse.app.domain.rbac.ModuleAccessConfig
import com.eventverse.app.domain.rbac.RoleId
import com.eventverse.app.domain.tenant.SubscriptionTier
import com.eventverse.app.domain.tenant.Tenant
import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.domain.tenant.TenantName
import com.eventverse.app.domain.tenant.TenantSlug
import com.eventverse.app.domain.tenant.TenantStatus
import com.eventverse.app.infrastructure.InMemoryCrmAiSettingsRepository
import com.eventverse.app.infrastructure.InMemoryCustomFieldDefinitionRepository
import com.eventverse.app.infrastructure.InMemoryModuleAssignmentRepository
import com.eventverse.app.infrastructure.InMemoryRoleRepository
import com.eventverse.app.infrastructure.InMemoryTenantRepository
import com.eventverse.app.plugins.TenantResolutionPlugin
import com.eventverse.app.shared.crm.LeadDraftCodec
import com.eventverse.app.shared.json.JsonParser
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.install
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * TRD-HELP-002 §0.7: gerbang CRM (OPERATE untuk draf, MANAGE untuk opt-in), opt-in per tenant, dan 400 untuk body
 * tidak sah. App Ktor mini (plugin tenant + route ini) — tanpa DB, jadi tidak bergantung pada `Application.kt`.
 * Route ini tidak menerima `CrmLeadRepository` sama sekali: "draf tidak pernah disimpan" dijamin konstruksinya.
 */
class CrmLeadDraftRoutesTest {

    private val slug = "draf-uji"
    private val tenantId = TenantId("ten-draf-uji")

    private fun ApplicationTestBuilder.setUp() {
        val tenants = InMemoryTenantRepository()
        val roles = InMemoryRoleRepository()
        runBlocking {
            tenants.save(Tenant(tenantId, TenantSlug(slug), TenantName("Draf Uji"), TenantStatus.ACTIVE, SubscriptionTier.PRO, businessPreset = GarmentBlueprints.CMT_MAKLOON))
            roles.save(CustomRole(RoleId("role-sales"), tenantId, "Sales", "uji", modulePermissions = mapOf(GarmentModules.CRM_SALES to ModuleAccessConfig(level = AccessLevel.OPERATE))))
            roles.save(CustomRole(RoleId("role-viewer"), tenantId, "Viewer", "uji", modulePermissions = mapOf(GarmentModules.CRM_SALES to ModuleAccessConfig(level = AccessLevel.VIEW))))
        }
        application {
            val app = this
            app.install(TenantResolutionPlugin) {
                this.tenantRepository = tenants
                this.jwtTokenService = JwtTokenService()
            }
            app.routing {
                crmLeadDraftRoutes(roles, InMemoryModuleAssignmentRepository(), InMemoryCustomFieldDefinitionRepository(),
                    InMemoryCrmAiSettingsRepository(), DeterministicLeadDraftExtractor())
            }
        }
    }

    private val sales get() = TestAuth.staffToken(slug, customRoleId = "role-sales", role = Role.OPERATOR)
    private val viewer get() = TestAuth.staffToken(slug, customRoleId = "role-viewer", role = Role.OPERATOR)
    private val owner get() = TestAuth.tenantToken(slug, Role.TENANT_ADMIN)

    private suspend fun ApplicationTestBuilder.draft(token: String?, body: String = LeadDraftCodec.encodeText("WA 0812 3456 7890, 500 pcs kaos")): HttpResponse =
        client.post("/api/tenant/crm/leads/draft") {
            header("X-Tenant-Slug", slug); token?.let { header(HttpHeaders.Authorization, "Bearer $it") }; setBody(body)
        }

    private suspend fun ApplicationTestBuilder.setEnabled(token: String, enabled: Boolean): HttpResponse =
        client.put("/api/tenant/crm/ai-settings") {
            header("X-Tenant-Slug", slug); header(HttpHeaders.Authorization, "Bearer $token"); setBody("""{"leadDraftEnabled":$enabled}""")
        }

    @Test
    fun anonymous_isRejected_andViewer_is403_evenBeforeOptIn() = testApplication {
        setUp()
        assertTrue(draft(null).status in setOf(HttpStatusCode.Unauthorized, HttpStatusCode.Forbidden))
        assertEquals(HttpStatusCode.Forbidden, draft(viewer).status)
    }

    @Test
    fun optInIsOffByDefault_andOnlyManageCanTurnItOn() = testApplication {
        setUp()
        assertEquals(HttpStatusCode.Conflict, draft(sales).status)
        assertEquals(HttpStatusCode.Forbidden, setEnabled(sales, true).status, "OPERATE tidak boleh mengaktifkan pengiriman data ke LLM")
        assertEquals(HttpStatusCode.OK, setEnabled(owner, true).status)

        val response = draft(sales)
        assertEquals(HttpStatusCode.OK, response.status)
        val d = LeadDraftCodec.decodeDraft(JsonParser.parseObject(response.bodyAsText()))
        assertEquals("6281234567890", d.whatsappNumber?.value)
        assertEquals(500, d.estimatedPcs)
    }

    @Test
    fun badBodies_are400() = testApplication {
        setUp()
        setEnabled(owner, true)
        assertEquals(HttpStatusCode.BadRequest, draft(sales, "bukan json").status)
        assertEquals(HttpStatusCode.BadRequest, draft(sales, "{}").status)
        assertEquals(HttpStatusCode.BadRequest, draft(sales, LeadDraftCodec.encodeText("a".repeat(4001))).status)
    }
}
