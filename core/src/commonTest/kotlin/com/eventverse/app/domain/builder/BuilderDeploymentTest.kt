package com.eventverse.app.domain.builder

import com.eventverse.app.domain.pack.DomainPackCode
import com.eventverse.app.domain.tenant.TenantId
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Agregat Deployment (PLAN-builder-console M0). Fixture memakai dua pack — `garment` (default) dan
 * `bordir` (non-default, Kontrak 6 tenant-variability-rules) — supaya aturannya terbukti netral industri.
 */
class BuilderDeploymentTest {

    private val garmentTenant = TenantId("ten-wemade-demo")
    private val bordirTenant = TenantId("ten-bordir-uji")

    private fun imported(tenant: TenantId, pack: String, number: Int = 1) = Deployment(
        id = DeploymentId("dep-${tenant.value}-$number"),
        tenantId = tenant,
        number = DeploymentNumber(number),
        packCode = DomainPackCode(pack),
        appBuild = "build-2026.09.30",
        status = DeploymentStatus.IMPORTED
    )

    @Test
    fun imported_withoutAppBuild_isRejected_forBothPackFixtures() {
        listOf("garment", "bordir").forEach { pack ->
            assertFailsWith<IllegalArgumentException> {
                imported(garmentTenant, pack).copy(appBuild = null)
            }
        }
    }

    @Test
    fun active_withoutLockedPackVersion_isRejected() {
        assertFailsWith<IllegalArgumentException> {
            imported(bordirTenant, "bordir").copy(status = DeploymentStatus.ACTIVE)
        }
    }

    @Test
    fun imported_carriesNoPackVersion_whileActiveRequiresOne() {
        val snapshot = imported(garmentTenant, "garment")
        assertTrue(snapshot.packVersion == null && snapshot.isActive)

        val activated = imported(bordirTenant, "bordir", number = 2)
            .copy(packVersion = 3)
            .activate(Instant.parse("2026-09-30T00:00:00Z"))
        assertTrue(activated.isActive)
        assertEquals(3, activated.packVersion)
    }

    @Test
    fun numbers_startAtOne_perTenant_notGlobally() = runRepoTest { repo ->
        assertEquals(1, repo.nextNumber(garmentTenant).value)
        assertEquals(1, repo.nextNumber(bordirTenant).value, "tenant kedua mulai dari #1, bukan lanjutan tenant pertama")
        repo.save(imported(garmentTenant, "garment")).getOrThrow()
        repo.save(imported(bordirTenant, "bordir")).getOrThrow()
        assertEquals(2, repo.nextNumber(garmentTenant).value)
        assertEquals(2, repo.nextNumber(bordirTenant).value)
    }

    @Test
    fun secondActiveDeployment_forSameTenant_isRejected() = runRepoTest { repo ->
        repo.save(imported(garmentTenant, "garment")).getOrThrow()
        val duplicate = imported(garmentTenant, "garment", number = 2)
        val conflict = assertFailsWith<DeploymentConflictException> { repo.save(imported(garmentTenant, "garment", number = 2)).getOrThrow() }
        assertTrue(conflict.message!!.contains("sudah ada deployment aktif"))
    }

    @Test
    fun repository_neverLeaksOtherTenantsDeployments() = runRepoTest { repo ->
        repo.save(imported(garmentTenant, "garment")).getOrThrow()
        assertEquals(1, repo.findByTenant(garmentTenant).size)
        assertEquals(0, repo.findByTenant(bordirTenant).size, "deployment tenant lain tidak bocor")
        assertEquals(null, repo.findActive(bordirTenant))
    }

    private fun runRepoTest(block: suspend (InMemoryBuilderDeploymentRepository) -> Unit) =
        kotlinx.coroutines.test.runTest { block(InMemoryBuilderDeploymentRepository()) }
}

/** Fake repository untuk test domain: menegakkan invarian tepat-satu-aktif & nomor per tenant. */
class InMemoryBuilderDeploymentRepository : BuilderDeploymentRepository {
    private val rows = mutableListOf<Deployment>()

    override suspend fun findByTenant(tenantId: TenantId): List<Deployment> =
        rows.filter { it.tenantId == tenantId }.sortedByDescending { it.number.value }

    override suspend fun findActive(tenantId: TenantId): Deployment? =
        findByTenant(tenantId).firstOrNull { it.isActive }

    override suspend fun nextNumber(tenantId: TenantId): DeploymentNumber =
        DeploymentNumber((rows.filter { it.tenantId == tenantId }.maxOfOrNull { it.number.value } ?: 0) + 1)

    override suspend fun save(deployment: Deployment): Result<Deployment> = runCatching {
        if (deployment.isActive) {
            val conflicting = rows.firstOrNull { it.tenantId == deployment.tenantId && it.isActive && it.id != deployment.id }
            if (conflicting != null) {
                throw DeploymentConflictException(
                    "Tenant ${deployment.tenantId.value} sudah ada deployment aktif #${conflicting.number.value}"
                )
            }
        }
        rows.removeAll { it.id == deployment.id }
        rows.add(deployment)
        deployment
    }
}
