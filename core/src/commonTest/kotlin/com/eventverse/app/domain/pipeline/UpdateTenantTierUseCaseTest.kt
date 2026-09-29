package com.eventverse.app.domain.pipeline

import com.eventverse.app.domain.pack.GarmentBlueprints

import com.eventverse.app.domain.pack.GarmentSlots

import com.eventverse.app.domain.pipeline.usecases.GetTenantPipelineUseCase
import com.eventverse.app.domain.pipeline.usecases.UpdateTenantTierUseCase
import com.eventverse.app.domain.tenant.FakeTenantRepository
import com.eventverse.app.domain.tenant.SubscriptionTier
import com.eventverse.app.domain.tenant.Tenant
import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.domain.tenant.TenantName
import com.eventverse.app.domain.tenant.TenantSlug
import com.eventverse.app.domain.tenant.TenantStatus
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UpdateTenantTierUseCaseTest {

    private lateinit var tenantRepository: FakeTenantRepository
    private lateinit var pipelineRepository: FakeTenantPipelineRepository
    private lateinit var entitlementRepository: FakeTenantEntitlementRepository
    private lateinit var getPipeline: GetTenantPipelineUseCase
    private lateinit var updateTier: UpdateTenantTierUseCase

    private val tenantId = TenantId("ten-tier-test")

    private fun seedTenant(tier: SubscriptionTier = SubscriptionTier.ENTERPRISE): Tenant {
        val tenant = Tenant(
            id = tenantId,
            slug = TenantSlug("pabrik-tier-test"),
            name = TenantName("PT Uji Paket"),
            status = TenantStatus.ACTIVE,
            tier = tier
        )
        kotlinx.coroutines.runBlocking { tenantRepository.save(tenant) }
        return tenant
    }

    @BeforeTest
    fun setUp() {
        tenantRepository = FakeTenantRepository()
        pipelineRepository = FakeTenantPipelineRepository()
        entitlementRepository = FakeTenantEntitlementRepository()
        getPipeline = GetTenantPipelineUseCase(pipelineRepository)
        updateTier = UpdateTenantTierUseCase(tenantRepository, pipelineRepository, entitlementRepository)
    }

    @Test
    fun updateTier_forTenantWithoutAPipeline_shouldSucceed() = runTest {
        seedTenant(tier = SubscriptionTier.STARTER)

        val result = updateTier(tenantId, SubscriptionTier.ENTERPRISE)

        assertTrue(result.isSuccess)
        assertEquals(SubscriptionTier.ENTERPRISE, result.getOrThrow().tier)
    }

    @Test
    fun updateTier_shouldPersistTheChange() = runTest {
        seedTenant(tier = SubscriptionTier.STARTER)

        updateTier(tenantId, SubscriptionTier.PRO).getOrThrow()

        assertEquals(SubscriptionTier.PRO, tenantRepository.findById(tenantId)?.tier)
    }

    @Test
    fun updateTier_forUnknownTenant_shouldFail() = runTest {
        val result = updateTier(TenantId("ten-does-not-exist"), SubscriptionTier.PRO)

        assertTrue(result.isFailure)
    }

    @Test
    fun downgrade_thatBreaksTheCurrentlyRunningPipeline_shouldBeRejected() = runTest {
        // The D2C-shaped scenario: nine built-in modules plus a custom plugin, which only
        // ENTERPRISE allows. Downgrading to PRO would leave the tenant locked out of its
        // own flow on the very next save.
        seedTenant(tier = SubscriptionTier.ENTERPRISE)
        val pipeline = getPipeline(tenantId, GarmentBlueprints.BRAND_D2C).getOrThrow()
        val descriptor = DynamicModuleDescriptor(
            moduleId = "sablon_bordir_custom",
            archetype = GarmentSlots.FINISHING,
            name = "Sablon & Bordir",
            description = "Stasiun dekorasi kustom.",
            acceptedInputDataTypes = setOf("CutPiecesBundle"),
            producedOutputDataType = "DecoratedGarmentBundle",
            isCustomTenantPlugin = true
        )
        pipelineRepository.save(pipeline.addNode(descriptor.toPipelineNode()))
        entitlementRepository.save(
            tenantId,
            TenantEntitlementGrants(grantedCustomModuleIds = setOf("sablon_bordir_custom"))
        )

        val result = updateTier(tenantId, SubscriptionTier.PRO)

        assertTrue(result.isFailure)
        assertTrue(
            result.exceptionOrNull()?.message?.contains("tidak valid") == true,
            "Pesan: ${result.exceptionOrNull()?.message}"
        )
        // Refused, so the tier must not have changed.
        assertEquals(SubscriptionTier.ENTERPRISE, tenantRepository.findById(tenantId)?.tier)
    }

    @Test
    fun downgrade_afterDeactivatingTheOffendingModules_shouldSucceed() = runTest {
        seedTenant(tier = SubscriptionTier.ENTERPRISE)
        val pipeline = getPipeline(tenantId, GarmentBlueprints.BRAND_D2C).getOrThrow()
        val descriptor = DynamicModuleDescriptor(
            moduleId = "sablon_bordir_custom",
            archetype = GarmentSlots.FINISHING,
            name = "Sablon & Bordir",
            description = "Stasiun dekorasi kustom.",
            acceptedInputDataTypes = setOf("CutPiecesBundle"),
            producedOutputDataType = "DecoratedGarmentBundle",
            isCustomTenantPlugin = true
        )
        val withPlugin = pipeline.addNode(descriptor.toPipelineNode(nodeId = "custom-sablon"))
        // Bypass the plugin so it no longer occupies a licence.
        pipelineRepository.save(withPlugin.setNodeBypassed("custom-sablon", true))

        val result = updateTier(tenantId, SubscriptionTier.PRO)

        assertTrue(result.isSuccess, "Pesan: ${result.exceptionOrNull()?.message}")
    }

    @Test
    fun upgrade_shouldNeverBeRejectedByExistingPipeline() = runTest {
        seedTenant(tier = SubscriptionTier.STARTER)
        getPipeline(tenantId, GarmentBlueprints.FOB_FULL_PACKAGE).getOrThrow()

        // STARTER only grants five modules; the FOB preset activates all nine. That is
        // already a violation of the *current* tier, but an upgrade must never be blocked
        // by it — an upgrade can only relax limits, never tighten them.
        val result = updateTier(tenantId, SubscriptionTier.ENTERPRISE)

        assertTrue(result.isSuccess, "Pesan: ${result.exceptionOrNull()?.message}")
    }
}
