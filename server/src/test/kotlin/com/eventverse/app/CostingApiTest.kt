package com.eventverse.app

import com.eventverse.app.domain.auth.Role
import com.eventverse.app.domain.costing.*
import com.eventverse.app.domain.pipeline.CostingBehavior
import com.eventverse.app.domain.tenant.*
import com.eventverse.app.infrastructure.*
import com.eventverse.app.shared.costing.CostingSheetCodec
import com.eventverse.app.shared.json.JsonParser
import com.eventverse.app.shared.json.JsonValue
import com.eventverse.app.shared.json.jsonObjectOf
import com.eventverse.app.shared.json.jsonOf
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import org.junit.Test
import kotlin.test.*

class CostingApiTest {

    private val tenantSlug = "wemade-demo"
    private val tenantId = TenantId("ten-demo-001")

    private fun setupTestTenantRepo(): InMemoryTenantRepository {
        val repo = InMemoryTenantRepository()
        runBlocking {
            repo.save(
                Tenant(
                    id = tenantId,
                    slug = TenantSlug(tenantSlug),
                    name = TenantName("PT WeMade Demo"),
                    status = TenantStatus.ACTIVE,
                    tier = SubscriptionTier.PRO
                )
            )
        }
        return repo
    }

    @Test
    fun getSheets_initiallyEmpty_shouldReturnEmptyArray() = testApplication {
        val tenantRepo = setupTestTenantRepo()
        val sheetRepo = InMemoryCostingSheetRepository()
        val rateCardRepo = InMemoryCostingRateCardRepository()

        application {
            module(
                tenantRepository = tenantRepo,
                costingSheetRepository = sheetRepo,
                costingRateCardRepository = rateCardRepo
            )
        }

        val token = TestAuth.tenantToken(tenantSlug = tenantSlug, role = Role.TENANT_ADMIN)
        val response = client.get("/api/tenant/costing/sheets") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val json = JsonParser.parse(response.bodyAsText()) as JsonValue.Arr
        assertTrue(json.items.isEmpty())
    }

    @Test
    fun createDraft_validCommand_createsDraftSuccessfully() = testApplication {
        val tenantRepo = setupTestTenantRepo()
        val sheetRepo = InMemoryCostingSheetRepository()
        val rateCardRepo = InMemoryCostingRateCardRepository()

        application {
            module(
                tenantRepository = tenantRepo,
                costingSheetRepository = sheetRepo,
                costingRateCardRepository = rateCardRepo
            )
        }

        val token = TestAuth.tenantToken(tenantSlug = tenantSlug, role = Role.TENANT_ADMIN)
        val payload = jsonObjectOf(
            "techPackId" to jsonOf("tp-001"),
            "orderQuantity" to jsonOf(500L),
            "behavior" to jsonOf("full_package_cogs")
        )

        val response = client.post("/api/tenant/costing/sheets") {
            header(HttpHeaders.Authorization, "Bearer $token")
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(payload.encode())
        }

        assertEquals(HttpStatusCode.Created, response.status)
        val json = JsonParser.parse(response.bodyAsText()) as JsonValue.Obj
        assertEquals("tp-001", json.string("techPackId"))
        assertEquals(500L, json.long("orderQuantity"))
        assertEquals("DRAFT", json.string("status"))
    }

    @Test
    fun approveSheet_withoutApproveCostingPermission_returns403() = testApplication {
        val tenantRepo = setupTestTenantRepo()
        val sheetRepo = InMemoryCostingSheetRepository()
        val rateCardRepo = InMemoryCostingRateCardRepository()

        // Seed a sheet in PENDING_APPROVAL status
        val sheetId = CostingSheetId("sheet-test-01")
        val testSheet = CostingSheet(
            id = sheetId,
            tenantId = tenantId,
            number = CostingNumber("HPP-0001"),
            techPackId = "tp-001",
            orderQuantity = 100L,
            behavior = CostingBehavior.FULL_PACKAGE_COGS,
            status = CostingSheetStatus.PENDING_APPROVAL,
            pricingAsOf = Clock.System.now(),
            createdAt = Clock.System.now(),
            updatedAt = Clock.System.now()
        )
        runBlocking { sheetRepo.save(testSheet) }

        // Lolos gerbang modul (Costing OPERATE, B5) supaya yang diuji benar-benar izin APPROVE_COSTING di handler.
        val roles = com.eventverse.app.infrastructure.InMemoryRoleRepository()
        runBlocking {
            roles.save(
                com.eventverse.app.domain.rbac.CustomRole(
                    com.eventverse.app.domain.rbac.RoleId("role-costing-operator"), tenantId, "Staf HPP", "operasi HPP tanpa approve",
                    modulePermissions = mapOf(
                        com.eventverse.app.domain.rbac.BusinessModule.COSTING_HPP to
                            com.eventverse.app.domain.rbac.ModuleAccessConfig(level = com.eventverse.app.domain.rbac.AccessLevel.OPERATE)
                    )
                )
            )
        }
        application {
            module(
                tenantRepository = tenantRepo,
                costingSheetRepository = sheetRepo,
                costingRateCardRepository = rateCardRepo,
                roleRepository = roles,
                moduleAssignmentRepository = com.eventverse.app.infrastructure.InMemoryModuleAssignmentRepository()
            )
        }

        // Role.SALES does not have APPROVE_COSTING permission
        val salesToken = TestAuth.staffToken(tenantSlug, customRoleId = "role-costing-operator", role = Role.SALES)
        val response = client.post("/api/tenant/costing/sheets/${sheetId.value}/approve") {
            header(HttpHeaders.Authorization, "Bearer $salesToken")
        }

        assertEquals(HttpStatusCode.Forbidden, response.status)
        assertTrue(response.bodyAsText().contains("APPROVE_COSTING"))
    }

    @Test
    fun getTelemetry_returnsMetrics() = testApplication {
        val tenantRepo = setupTestTenantRepo()
        val sheetRepo = InMemoryCostingSheetRepository()
        val rateCardRepo = InMemoryCostingRateCardRepository()

        application {
            module(
                tenantRepository = tenantRepo,
                costingSheetRepository = sheetRepo,
                costingRateCardRepository = rateCardRepo
            )
        }

        val token = TestAuth.tenantToken(tenantSlug = tenantSlug, role = Role.TENANT_ADMIN)
        val response = client.get("/api/tenant/costing/telemetry") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val json = JsonParser.parse(response.bodyAsText()) as JsonValue.Obj
        assertEquals(0, json.int("pendingSheetCount"))
        assertEquals(0, json.int("overdueApprovalCount"))
        assertEquals("HEALTHY", json.string("healthStatus"))
    }
}
