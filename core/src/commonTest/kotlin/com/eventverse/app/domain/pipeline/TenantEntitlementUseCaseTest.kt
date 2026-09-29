package com.eventverse.app.domain.pipeline

import com.eventverse.app.domain.pack.GarmentSlots

import com.eventverse.app.domain.pipeline.usecases.GetTenantEntitlementUseCase
import com.eventverse.app.domain.pipeline.usecases.GetTenantPipelineUseCase
import com.eventverse.app.domain.pipeline.usecases.InstallCustomModuleUseCase
import com.eventverse.app.domain.pipeline.usecases.RenameTenantModuleUseCase
import com.eventverse.app.domain.pipeline.usecases.SetTenantEntitlementUseCase
import com.eventverse.app.domain.rbac.BusinessModule
import com.eventverse.app.domain.tenant.SubscriptionTier
import com.eventverse.app.domain.tenant.TenantId
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class FakeTenantEntitlementRepository : TenantEntitlementRepository {
    private val grants = mutableMapOf<TenantId, TenantEntitlementGrants>()

    override suspend fun findByTenantId(tenantId: TenantId): TenantEntitlementGrants? =
        grants[tenantId]

    override suspend fun save(
        tenantId: TenantId,
        grants: TenantEntitlementGrants
    ): Result<Unit> {
        this.grants[tenantId] = grants
        return Result.success(Unit)
    }
}

class TenantEntitlementUseCaseTest {

    private lateinit var pipelineRepository: FakeTenantPipelineRepository
    private lateinit var entitlementRepository: FakeTenantEntitlementRepository
    private lateinit var getEntitlement: GetTenantEntitlementUseCase
    private lateinit var setEntitlement: SetTenantEntitlementUseCase
    private lateinit var getPipeline: GetTenantPipelineUseCase
    private lateinit var installCustom: InstallCustomModuleUseCase
    private lateinit var renameModule: RenameTenantModuleUseCase

    private val tenantId = TenantId("ten-entitlement-usecase")

    @BeforeTest
    fun setUp() {
        pipelineRepository = FakeTenantPipelineRepository()
        entitlementRepository = FakeTenantEntitlementRepository()
        getEntitlement = GetTenantEntitlementUseCase(entitlementRepository)
        setEntitlement = SetTenantEntitlementUseCase(entitlementRepository, pipelineRepository)
        getPipeline = GetTenantPipelineUseCase(pipelineRepository)
        installCustom = InstallCustomModuleUseCase(pipelineRepository, entitlementRepository)
        renameModule = RenameTenantModuleUseCase(pipelineRepository)
    }

    private fun customPlugin() = DynamicModuleDescriptor(
        moduleId = "sablon_bordir_custom",
        archetype = GarmentSlots.FINISHING,
        name = "Sablon Manual & Bordir Komputer",
        description = "Stasiun dekorasi kustom.",
        acceptedInputDataTypes = setOf("CutPiecesBundle"),
        producedOutputDataType = "DecoratedGarmentBundle",
        isCustomTenantPlugin = true
    )

    @Test
    fun getEntitlement_withNoStoredGrants_shouldFallBackToPlanDefaults() = runTest {
        val entitlement = getEntitlement(tenantId, SubscriptionTier.PRO).getOrThrow()

        assertEquals(SubscriptionTier.PRO, entitlement.tier)
        assertEquals(BusinessModule.entries.toSet(), entitlement.grantedModules)
        assertTrue(entitlement.grantedCustomModuleIds.isEmpty())
        assertFalse(entitlement.allowsCustomPlugins)
    }

    @Test
    fun installCustomModule_shouldPersistTheGrant() = runTest {
        getPipeline(tenantId, GarmentBusinessPreset.BRAND_D2C).getOrThrow()
        val enterprise = getEntitlement(tenantId, SubscriptionTier.ENTERPRISE).getOrThrow()

        installCustom(tenantId, customPlugin(), enterprise).getOrThrow()

        val stored = entitlementRepository.findByTenantId(tenantId)
        assertNotNull(stored, "Grant harus tersimpan, bukan hanya hidup selama satu request")
        assertTrue(stored.grantedCustomModuleIds.contains("sablon_bordir_custom"))
    }

    @Test
    fun afterInstall_laterEditsShouldPassEntitlementCheck() = runTest {
        // The regression: a resolved entitlement on a *later* request must still know about
        // the plugin, otherwise unrelated edits fail with a plan-limit error.
        getPipeline(tenantId, GarmentBusinessPreset.BRAND_D2C).getOrThrow()
        val enterprise = getEntitlement(tenantId, SubscriptionTier.ENTERPRISE).getOrThrow()
        installCustom(tenantId, customPlugin(), enterprise).getOrThrow()

        val laterEntitlement = getEntitlement(tenantId, SubscriptionTier.ENTERPRISE).getOrThrow()
        assertTrue(laterEntitlement.grantedCustomModuleIds.contains("sablon_bordir_custom"))

        val inventoryNodeId = pipelineRepository.findByTenantId(tenantId)!!
            .nodes.first { it.moduleId == "inventory" }.nodeId

        val result = renameModule(
            tenantId = tenantId,
            nodeId = inventoryNodeId,
            newDisplayName = "Stok Katalog SKU & Bahan Brand",
            entitlement = laterEntitlement
        )

        assertTrue(result.isSuccess, "Edit tak terkait harus lolos: ${result.exceptionOrNull()?.message}")
    }

    @Test
    fun setEntitlement_shouldNarrowTheGrantedCatalogue() = runTest {
        val grants = TenantEntitlementGrants(
            grantedModules = setOf(BusinessModule.CRM_SALES, BusinessModule.OPERATOR_EXEC)
        )

        val resolved = setEntitlement(tenantId, SubscriptionTier.PRO, grants).getOrThrow()

        assertEquals(2, resolved.grantedModules.size)
        assertEquals(grants, entitlementRepository.findByTenantId(tenantId))
    }

    @Test
    fun setEntitlement_thatWouldBreakARunningPipeline_shouldBeRejected() = runTest {
        // Revoking a module a factory is actively running would lock it out of editing its
        // own flow, and the failure would only appear on that tenant's next save.
        getPipeline(tenantId, GarmentBusinessPreset.FOB_FULL_PACKAGE).getOrThrow()

        val result = setEntitlement(
            tenantId,
            SubscriptionTier.PRO,
            TenantEntitlementGrants(grantedModules = setOf(BusinessModule.CRM_SALES))
        )

        assertTrue(result.isFailure)
        assertTrue(
            result.exceptionOrNull()?.message?.contains("tidak valid") == true,
            "Pesan: ${result.exceptionOrNull()?.message}"
        )
        // Nothing was persisted, so the tenant is left in a working state.
        assertEquals(null, entitlementRepository.findByTenantId(tenantId))
    }

    @Test
    fun setEntitlement_revokingAModuleTheTenantHasBypassed_shouldBeAllowed() = runTest {
        // A bypassed module consumes no licence, so revoking it is safe.
        getPipeline(tenantId, GarmentBusinessPreset.CMT_MAKLOON).getOrThrow()
        val bypassedModule = pipelineRepository.findByTenantId(tenantId)!!
            .bypassedNodes.first().standardModule
        assertNotNull(bypassedModule)

        val result = setEntitlement(
            tenantId,
            SubscriptionTier.PRO,
            TenantEntitlementGrants(grantedModules = BusinessModule.entries.toSet() - bypassedModule)
        )

        assertTrue(result.isSuccess, "Pesan: ${result.exceptionOrNull()?.message}")
    }

    @Test
    fun grants_roundTripThroughToGrants() = runTest {
        val entitlement = TenantModuleEntitlement(
            tier = SubscriptionTier.ENTERPRISE,
            grantedModules = setOf(BusinessModule.CRM_SALES),
            grantedCustomModuleIds = setOf("plugin_a", "plugin_b")
        )

        val restored = TenantModuleEntitlement.resolve(
            SubscriptionTier.ENTERPRISE,
            entitlement.toGrants()
        )

        assertEquals(entitlement, restored)
    }

    @Test
    fun setEntitlement_withAutoBypass_shouldBypassRunningPipelineNodesAndSucceed() = runTest {
        // Setup a running pipeline with all FOB modules active
        getPipeline(tenantId, GarmentBusinessPreset.FOB_FULL_PACKAGE).getOrThrow()
        val initialPipeline = pipelineRepository.findByTenantId(tenantId)!!
        val packingNode = initialPipeline.nodes.first { it.moduleId == BusinessModule.FULFILLMENT.code }
        assertFalse(packingNode.isBypassed)

        // Revoke packing module with autoBypassPipelineModules = true
        val result = setEntitlement(
            tenantId = tenantId,
            tier = SubscriptionTier.PRO,
            grants = TenantEntitlementGrants(grantedModules = BusinessModule.entries.toSet() - BusinessModule.FULFILLMENT),
            autoBypassPipelineModules = true
        )

        assertTrue(result.isSuccess, "Pesan: ${result.exceptionOrNull()?.message}")
        val updatedPipeline = pipelineRepository.findByTenantId(tenantId)!!
        val updatedPackingNode = updatedPipeline.nodes.first { it.moduleId == BusinessModule.FULFILLMENT.code }
        assertTrue(updatedPackingNode.isBypassed, "Packing node harusnya otomatis di-bypass")
        assertNotNull(entitlementRepository.findByTenantId(tenantId))

        // When re-granting the module back to the tenant
        val reGrantResult = setEntitlement(
            tenantId = tenantId,
            tier = SubscriptionTier.PRO,
            grants = TenantEntitlementGrants(grantedModules = BusinessModule.entries.toSet())
        )

        assertTrue(reGrantResult.isSuccess, "Pesan: ${reGrantResult.exceptionOrNull()?.message}")
        val restoredPipeline = pipelineRepository.findByTenantId(tenantId)!!
        val restoredPackingNode = restoredPipeline.nodes.first { it.moduleId == BusinessModule.FULFILLMENT.code }
        assertFalse(restoredPackingNode.isBypassed, "Packing node harusnya otomatis aktif kembali (tidak bypassed)")
    }

    @Test
    fun toGrants_withFullCatalogue_shouldNotPinTheModuleList() = runTest {
        // Storing "all modules" as an explicit list would freeze the catalogue: a module
        // added to the codebase later would not reach existing tenants.
        val entitlement = TenantModuleEntitlement.forTier(SubscriptionTier.PRO)

        assertEquals(null, entitlement.toGrants().grantedModules)
    }
}
