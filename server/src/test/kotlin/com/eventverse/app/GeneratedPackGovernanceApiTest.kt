package com.eventverse.app

import com.eventverse.app.domain.auth.Role
import com.eventverse.app.domain.auth.UserId
import com.eventverse.app.domain.discovery.DeterministicDiscoveryAgent
import com.eventverse.app.domain.discovery.DiscoveryDraftId
import com.eventverse.app.domain.discovery.DiscoveryRequest
import com.eventverse.app.domain.discovery.usecases.CreateDiscoveryDraftUseCase
import com.eventverse.app.domain.discovery.usecases.HandoffDiscoveryDraftUseCase
import com.eventverse.app.domain.discovery.usecases.LockDiscoveryDraftUseCase
import com.eventverse.app.domain.pack.DomainPackRegistry
import com.eventverse.app.domain.pack.DomainPackStatus
import com.eventverse.app.domain.pack.GarmentModules
import com.eventverse.app.domain.pack.StoredDomainPack
import com.eventverse.app.domain.rbac.ModuleKind
import com.eventverse.app.domain.tenant.SubscriptionTier
import com.eventverse.app.domain.tenant.Tenant
import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.domain.tenant.TenantName
import com.eventverse.app.domain.tenant.TenantSlug
import com.eventverse.app.domain.tenant.TenantStatus
import com.eventverse.app.infrastructure.DatabaseFactory
import com.eventverse.app.infrastructure.InMemoryAuditLogRepository
import com.eventverse.app.infrastructure.InMemoryDepartmentRepository
import com.eventverse.app.infrastructure.InMemoryDiscoveryDraftRepository
import com.eventverse.app.infrastructure.InMemoryDomainPackRepository
import com.eventverse.app.infrastructure.InMemoryEmployeeRepository
import com.eventverse.app.infrastructure.InMemoryModuleAssignmentRepository
import com.eventverse.app.infrastructure.InMemoryRoleRepository
import com.eventverse.app.infrastructure.InMemoryTenantEntitlementRepository
import com.eventverse.app.infrastructure.InMemoryTenantPipelineRepository
import com.eventverse.app.infrastructure.InMemoryTenantRepository
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * TRD-PLAT-009: tenant ber-pack **hasil handoff** (klinik, non-garment) bisa mengelola peran dan struktur organisasi.
 * Mesin tidak dilonggarkan: peran tak berwenang tetap 403, dan pack lama tanpa modul tata kelola tetap `NOT_ENTITLED`.
 */
class GeneratedPackGovernanceApiTest {

    private val slug = "klinik-handoff"
    private val owner = UserId("usr-pemilik")
    private val packs = InMemoryDomainPackRepository()
    private val tenants = InMemoryTenantRepository()

    @AfterTest
    fun cleanup() = DomainPackRegistry.unregister(com.eventverse.app.domain.pack.DomainPackCode("klinik"))

    private fun ApplicationTestBuilder.boot() {
        DatabaseFactory.init()
        application {
            module(
                tenantRepository = tenants, pipelineRepository = InMemoryTenantPipelineRepository(),
                entitlementRepository = InMemoryTenantEntitlementRepository(), roleRepository = InMemoryRoleRepository(),
                moduleAssignmentRepository = InMemoryModuleAssignmentRepository(), departmentRepository = InMemoryDepartmentRepository(),
                employeeRepository = InMemoryEmployeeRepository(), auditLogRepository = InMemoryAuditLogRepository(),
                domainPackRepository = packs
            )
        }
    }

    private suspend fun handoffKlinik() {
        val drafts = InMemoryDiscoveryDraftRepository()
        val id = DiscoveryDraftId("draft-klinik")
        CreateDiscoveryDraftUseCase(DeterministicDiscoveryAgent(), drafts)(
            DiscoveryRequest("Kami klinik dengan antrean pasien dan tagihan kasir.", industryHint = "klinik"), owner, id
        ).getOrThrow()
        LockDiscoveryDraftUseCase(drafts)(id, owner, isPlatformSuperadmin = false).getOrThrow()
        HandoffDiscoveryDraftUseCase(drafts, tenants, packs) { false }(id, true, slug, "Klinik Handoff").getOrThrow()
    }

    @Test
    fun handoffTenant_ownerManagesRolesAndAssignments_unauthorizedRoleStillForbidden() = testApplication {
        runBlocking { handoffKlinik() }
        boot()

        val saved = runBlocking { packs.findLatest(tenants.findBySlug(TenantSlug(slug))!!.domainPack)!!.pack }
        assertEquals(ModuleKind.GOVERNANCE, saved.module(GarmentModules.DYNAMIC_RBAC)?.kind)

        assertEquals(HttpStatusCode.OK, client.get("/api/tenant/roles") { asTenant(slug) }.status)
        assertEquals(HttpStatusCode.OK, client.get("/api/tenant/module-assignments") { asTenant(slug) }.status)
        // Fail-closed: peran tanpa wewenang tetap ditolak walau modulnya kini ter-entitle.
        assertEquals(HttpStatusCode.Forbidden, client.get("/api/tenant/roles") { asTenant(slug, Role.SALES) }.status)
        assertEquals(HttpStatusCode.Forbidden, client.get("/api/tenant/module-assignments") { asTenant(slug, Role.SALES) }.status)
    }

    @Test
    fun legacyPackWithoutGovernance_staysForbiddenForOwner() = testApplication {
        runBlocking {
            handoffKlinik()
            val stored = packs.findLatest(tenants.findBySlug(TenantSlug(slug))!!.domainPack)!!
            val legacy = stored.pack.copy(modules = stored.pack.modules.filterNot { it.kind == ModuleKind.GOVERNANCE })
            packs.save(StoredDomainPack(legacy, stored.version + 1, DomainPackStatus.LOCKED, stored.ownerTenantId))
            DomainPackRegistry.register(legacy)
            tenants.save(tenants.findBySlug(TenantSlug(slug))!!.copy(domainPackVersion = stored.version + 1))
        }
        boot()

        val res = client.get("/api/tenant/roles") { asTenant(slug) }
        assertEquals(HttpStatusCode.Forbidden, res.status, res.bodyAsText())
        assertTrue(client.get("/api/tenant/module-assignments") { asTenant(slug) }.status == HttpStatusCode.Forbidden)
    }
}
