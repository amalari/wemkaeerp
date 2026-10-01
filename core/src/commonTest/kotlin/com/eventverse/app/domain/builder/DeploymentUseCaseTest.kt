package com.eventverse.app.domain.builder

import com.eventverse.app.domain.auth.UserId
import com.eventverse.app.domain.discovery.DiscoveryDraft
import com.eventverse.app.domain.discovery.DiscoveryDraftId
import com.eventverse.app.domain.discovery.DiscoveryDraftRepository
import com.eventverse.app.domain.discovery.DiscoveryDraftStatus
import com.eventverse.app.domain.discovery.StoredDiscoveryDraft
import com.eventverse.app.domain.pack.GarmentBlueprints
import com.eventverse.app.domain.pack.GarmentDomainPack
import com.eventverse.app.domain.pack.usecases.TenantOperationalDataProbe
import com.eventverse.app.domain.tenant.Tenant
import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.domain.tenant.TenantName
import com.eventverse.app.domain.tenant.TenantRepository
import com.eventverse.app.domain.tenant.TenantSlug
import com.eventverse.app.domain.tenant.TenantStatus
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Deploy & rollback (FR-M2-1/2/3): pack shipped → ACTIVE + pin versi; rollback append-only dengan
 * gerbang data. Fixture garment (`wemade-demo`); pack kustom dibahas di discovery-M2 §2.
 */
class DeploymentUseCaseTest {

    private val demo = TenantId("ten-wemade-demo")

    private val drafts = FakeTenantDrafts()
    private val deployments = FakeDeploymentRepo()
    private val buildRequests = FakeBuildRequestRepo()
    private val tenants = FakeTenantRepo()
    private val deploy = DeployTenantUseCase(drafts, deployments, buildRequests, tenants)
    private val rollback = RollbackDeploymentUseCase(deployments, FakeProbe())

    private fun seedDraft() = runTest {
        drafts.rows[DiscoveryDraftId("draft-${demo.value}")] = StoredDiscoveryDraft(
            id = DiscoveryDraftId("draft-${demo.value}"),
            ownerUserId = UserId("usr-owner"),
            draft = DiscoveryDraft(pack = GarmentDomainPack.pack, blueprint = GarmentBlueprints.DEFAULT),
            tenantId = demo
        )
    }

    private fun unlock() {
        val key = DiscoveryDraftId("draft-${demo.value}")
        drafts.rows[key] = drafts.rows.getValue(key).copy(status = DiscoveryDraftStatus.DRAFT)
    }

    @Test
    fun deploy_shippedPack_activates_andPinsVersionAndTenant() = runTest {
        seedDraft()
        tenants.rows[demo] = tenant(status = TenantStatus.TRIAL)

        val deployment = deploy(demo).getOrThrow()

        assertEquals(DeploymentStatus.ACTIVE, deployment.status)
        assertEquals(1, deployment.packVersion)
        assertEquals(1, tenants.rows[demo]?.domainPackVersion, "versi pack dipin di tenant (kolom V81)")
        // Go-live (V89): tenant TETAP TRIAL — jam trial aplikasi baru dimulai di sini.
        assertEquals(TenantStatus.TRIAL, tenants.rows[demo]?.status, "status tetap TRIAL; ACTIVE = pembayaran")
        assertTrue(tenants.rows[demo]?.trialEndsAt != null, "go-live memulai jam trial 14 hari")
        assertEquals(DiscoveryDraftStatus.LOCKED, drafts.rows[DiscoveryDraftId("draft-${demo.value}")]?.status)
    }

    @Test
    fun redeploy_does_not_reset_the_trial_clock() = runTest {
        seedDraft()
        tenants.rows[demo] = tenant(status = TenantStatus.TRIAL)
        deploy(demo).getOrThrow()
        val firstEndsAt = tenants.rows[demo]?.trialEndsAt

        unlock()
        deploy(demo).getOrThrow()

        assertEquals(firstEndsAt, tenants.rows[demo]?.trialEndsAt, "deploy ulang tidak mengatur ulang jam")
    }

    @Test
    fun deploy_lockedDraft_isRejected() = runTest {
        seedDraft()
        val key = DiscoveryDraftId("draft-${demo.value}")
        drafts.rows[key] = drafts.rows.getValue(key).copy(status = DiscoveryDraftStatus.LOCKED)

        assertTrue(deploy(demo).isFailure, "draf beku tidak bisa di-deploy ulang tanpa revisi")
    }


    @Test
    fun deploy_twice_supersedesPrevious_andIncrementsVersion() = runTest {
        seedDraft()
        tenants.rows[demo] = tenant(status = TenantStatus.TRIAL)
        deploy(demo).getOrThrow()
        unlock()

        val second = deploy(demo).getOrThrow()
        assertEquals(2, second.packVersion, "versi pack = versi terakhir + 1")
        assertEquals(2, deployments.rows.count { it.tenantId == demo }, "dua deployment: #1 dan #2 (append-only)")
        assertEquals(DeploymentStatus.SUPERSEDED, deployments.rows.first { it.number.value == 1 }.status, "deployment #1 di-supersede")
    }

    @Test
    fun rollback_pinsPreviousVersion_appendOnly() = runTest {
        seedDraft()
        tenants.rows[demo] = tenant(status = TenantStatus.TRIAL)
        deploy(demo).getOrThrow()
        unlock()
        deploy(demo).getOrThrow()

        rollback(demo).getOrThrow()

        val rows = deployments.rows.sortedBy { it.number.value }
        assertEquals(DeploymentStatus.ROLLED_BACK, rows.last { it.number.value == 2 }.status)
        assertEquals(DeploymentStatus.ACTIVE, rows.first { it.number.value == 1 }.status, "versi 1 aktif kembali")
    }

    @Test
    fun deploy_overImportedSnapshot_supersedesWithInheritedVersion() = runTest {
        seedDraft()
        tenants.rows[demo] = tenant(status = TenantStatus.TRIAL)
        // Snapshot pra-Builder (V81): IMPORTED #1 tanpa packVersion.
        deployments.save(
            Deployment(
                id = DeploymentId("dep-${demo.value}-1"),
                tenantId = demo,
                number = DeploymentNumber(1),
                packCode = GarmentDomainPack.pack.code,
                appBuild = "build-uji",
                status = DeploymentStatus.IMPORTED
            )
        ).getOrThrow()

        val deployment = deploy(demo).getOrThrow()

        assertEquals(2, deployment.number.value)
        assertEquals(DeploymentStatus.ACTIVE, deployment.status)
        assertEquals(
            DeploymentStatus.SUPERSEDED,
            deployments.rows.first { it.number.value == 1 }.status,
            "snapshot pra-Builder di-supersede dengan mewarisi versi terkunci"
        )
        assertTrue(deployments.rows.first { it.number.value == 1 }.packVersion != null)
    }

    @Test
    fun rollback_withoutPreviousPackVersion_isRejected() = runTest {
        seedDraft()
        tenants.rows[demo] = tenant(status = TenantStatus.TRIAL)
        deploy(demo).getOrThrow()

        assertTrue(rollback(demo).isFailure, "snapshot #1 tidak punya packVersion — tidak ada yang di-pin")
    }


    private fun tenant(status: TenantStatus) = Tenant(
        id = demo,
        slug = TenantSlug("wemade-demo"),
        name = TenantName("Pabrik Uji"),
        status = status
    )

    private class FakeProbe : TenantOperationalDataProbe {
        override suspend fun hasOperationalData(tenantId: TenantId) = false
    }

    private class FakeTenantDrafts : DiscoveryDraftRepository {
        val rows = mutableMapOf<DiscoveryDraftId, StoredDiscoveryDraft>()

        override suspend fun findById(id: DiscoveryDraftId) = rows[id]
        override suspend fun findByOwner(owner: UserId) = rows.values.filter { it.ownerUserId == owner }
        override suspend fun findByTenant(tenantId: TenantId) = rows.values.lastOrNull { it.tenantId == tenantId }
        override suspend fun findAll() = rows.values.toList()
        override suspend fun save(stored: StoredDiscoveryDraft): StoredDiscoveryDraft {
            rows[stored.id] = stored
            return stored
        }
    }

    /** Invarian sama dengan Postgres: tepat satu deployment aktif per tenant. */
    private class FakeDeploymentRepo : BuilderDeploymentRepository {
        val rows = mutableListOf<Deployment>()

        override suspend fun findByTenant(tenantId: TenantId) =
            rows.filter { it.tenantId == tenantId }.sortedByDescending { it.number.value }

        override suspend fun findActive(tenantId: TenantId) =
            findByTenant(tenantId).firstOrNull { it.isActive }

        override suspend fun nextNumber(tenantId: TenantId) =
            DeploymentNumber((findByTenant(tenantId).maxOfOrNull { it.number.value } ?: 0) + 1)

        override suspend fun save(deployment: Deployment): Result<Deployment> = runCatching {
            val otherActive = rows.any {
                it.tenantId == deployment.tenantId && it.id != deployment.id && it.isActive
            }
            if (deployment.isActive && otherActive) {
                throw DeploymentConflictException("Tenant ${deployment.tenantId.value} sudah punya deployment aktif")
            }
            rows.removeAll { it.id == deployment.id }
            rows.add(deployment)
            deployment
        }
    }

    private class FakeBuildRequestRepo : BuilderBuildRequestRepository {
        val rows = mutableListOf<BuildRequest>()
        override suspend fun findByTenant(tenantId: TenantId) = rows.filter { it.tenantId == tenantId }
        override suspend fun findAll() = rows.toList()
        override suspend fun save(request: BuildRequest): BuildRequest {
            rows.add(request)
            return request
        }
    }

    private class FakeTenantRepo : TenantRepository {
        val rows = mutableMapOf<TenantId, Tenant>()
        override suspend fun findById(id: TenantId) = rows[id]
        override suspend fun findBySlug(slug: TenantSlug) = rows.values.firstOrNull { it.slug == slug }
        override suspend fun save(tenant: Tenant): Result<Tenant> = runCatching { rows[tenant.id] = tenant; tenant }
        override suspend fun existsBySlug(slug: TenantSlug) = rows.values.any { it.slug == slug }
        override suspend fun findAll() = rows.values.toList()
    }
}
