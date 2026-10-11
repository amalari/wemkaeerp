package com.eventverse.app

import com.eventverse.app.domain.auth.Role
import com.eventverse.app.domain.pack.GarmentBlueprints
import com.eventverse.app.domain.pack.GarmentModules
import com.eventverse.app.domain.rbac.AccessLevel
import com.eventverse.app.domain.tenant.SubscriptionTier
import com.eventverse.app.domain.tenant.Tenant
import com.eventverse.app.domain.tenant.TenantContext
import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.domain.tenant.TenantName
import com.eventverse.app.domain.tenant.TenantSlug
import com.eventverse.app.domain.tenant.TenantStatus
import com.eventverse.app.infrastructure.InMemoryDepartmentRepository
import com.eventverse.app.infrastructure.InMemoryEmployeeRepository
import com.eventverse.app.infrastructure.InMemoryModuleAssignmentRepository
import com.eventverse.app.infrastructure.InMemoryRoleRepository
import com.eventverse.app.infrastructure.InMemoryTenantPipelineRepository
import com.eventverse.app.infrastructure.InMemoryTenantEntitlementRepository
import com.eventverse.app.infrastructure.InMemoryTenantRepository
import com.eventverse.app.routes.callerDecisions
import com.eventverse.app.routes.factoryFlowDecision
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking

/**
 * TRD-PLAT-012 Q3: keputusan wewenang Factory Flow wajib tunggal dengan [callerDecisions].
 *
 * Kasus pemisah: token `TENANT_ADMIN` (owner pabrik) yang **ber-divisi tetapi tanpa jabatan**
 * (`customRoleId == null`, `departmentId != null`). Dulu `factoryFlowDecision` membangun persona
 * sendiri dengan bypass hanya untuk superadmin, sehingga owner dinilai matriks (NONE -> 403) sementara
 * `callerDecisions` (menu klien, `/me/access`, gerbang modul generik) memberi OWNER_BYPASS -> MANAGE.
 * Menu bilang boleh, gerbang menolak — satu pengguna, dua pendapat.
 *
 * Tes ini memanggil keduanya lewat probe route untuk token yang sama dan menuntut hasil identik.
 * Pra-perbaikan: factory = NONE vs caller = MANAGE (merah). Pasca: keduanya MANAGE.
 */
class FactoryFlowTenantAdminParityTest {

    private val tenantId = TenantId("ten-parity-1")
    private val slug = TenantSlug("pabrik-parity")

    private fun tenantRepo() = InMemoryTenantRepository().also { repo ->
        runBlocking {
            repo.save(
                Tenant(
                    tenantId, slug, TenantName("Pabrik Parity"), TenantStatus.ACTIVE,
                    SubscriptionTier.PRO, businessPreset = GarmentBlueprints.CMT_MAKLOON
                )
            )
        }
    }

    private fun ApplicationTestBuilder.installApp(): Pair<InMemoryRoleRepository, InMemoryModuleAssignmentRepository> {
        val roles = InMemoryRoleRepository()
        val assignments = InMemoryModuleAssignmentRepository()
        val ctx = TenantContext(tenantId, slug, SubscriptionTier.PRO, true)
        application {
            module(
                tenantRepository = tenantRepo(), roleRepository = roles,
                pipelineRepository = InMemoryTenantPipelineRepository(),
                entitlementRepository = InMemoryTenantEntitlementRepository(),
                moduleAssignmentRepository = assignments,
                departmentRepository = InMemoryDepartmentRepository(),
                employeeRepository = InMemoryEmployeeRepository()
            )
            routing {
                get("/parity") {
                    val factory = call.factoryFlowDecision(ctx, roles, assignments)
                    val caller = call.callerDecisions(
                        ctx, roles, assignments, listOf(GarmentModules.FACTORY_FLOW)
                    ).getValue(GarmentModules.FACTORY_FLOW)
                    call.respondText("${factory?.config?.level}|${caller.config.level}")
                }
            }
        }
        return roles to assignments
    }

    @Test
    fun tenantAdminWithDepartmentButNoRole_factoryFlowMatchesCallerDecisions() = testApplication {
        installApp()
        // Owner pabrik: peran TENANT_ADMIN, ber-divisi, tanpa jabatan kustom.
        val token = TestAuth.principalToken(
            slug.value, Role.TENANT_ADMIN, customRoleId = null, departmentId = "dept-produksi"
        )
        val body = client.get("/parity") {
            header("X-Tenant-Slug", slug.value)
            header(HttpHeaders.Authorization, "Bearer $token")
        }.bodyAsText()

        val (factoryLevel, callerLevel) = body.split("|")
            .map { runCatching { AccessLevel.valueOf(it) }.getOrNull() }
        assertEquals(AccessLevel.MANAGE, callerLevel, "callerDecisions (satu jalur resmi) memberi owner MANAGE")
        assertEquals(
            callerLevel, factoryLevel,
            "factoryFlowDecision harus sama dengan callerDecisions untuk token owner yang sama; dapat factory=$factoryLevel"
        )
    }
}
