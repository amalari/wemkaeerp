package com.eventverse.app.domain.pipeline

import com.eventverse.app.domain.rbac.isScopeSupported

import com.eventverse.app.domain.rbac.isFoundation

import com.eventverse.app.domain.rbac.isOperational

import com.eventverse.app.domain.rbac.isGovernance

import com.eventverse.app.domain.rbac.isHierarchical

import com.eventverse.app.domain.rbac.isGlobalOnly


import com.eventverse.app.domain.rbac.supportedScopes

import com.eventverse.app.domain.rbac.kind

import com.eventverse.app.domain.rbac.scopeCapability

import com.eventverse.app.domain.rbac.iconKey

import com.eventverse.app.domain.rbac.description

import com.eventverse.app.domain.rbac.displayName

import com.eventverse.app.domain.rbac.name

import com.eventverse.app.domain.rbac.code

import com.eventverse.app.domain.pack.GarmentModules

import com.eventverse.app.domain.pack.GarmentBlueprints

import com.eventverse.app.domain.pack.GarmentSlots

import com.eventverse.app.domain.pipeline.usecases.GetTenantModuleCatalogUseCase
import com.eventverse.app.domain.pipeline.usecases.GetTenantPipelineUseCase
import com.eventverse.app.domain.pipeline.usecases.InstallCustomModuleUseCase
import com.eventverse.app.domain.pipeline.usecases.RenameTenantModuleUseCase
import com.eventverse.app.domain.pipeline.usecases.SetTenantModuleActivationUseCase
import com.eventverse.app.domain.rbac.BusinessModule
import com.eventverse.app.domain.tenant.SubscriptionTier
import com.eventverse.app.domain.tenant.TenantId
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Covers provisioning modules to a specific tenant: reading the catalogue, switching modules
 * on and off, renaming them, and installing a tenant-only plugin.
 */
class TenantModuleProvisioningUseCaseTest {

    private lateinit var repository: FakeTenantPipelineRepository
    private lateinit var getPipeline: GetTenantPipelineUseCase
    private lateinit var getCatalog: GetTenantModuleCatalogUseCase
    private lateinit var setActivation: SetTenantModuleActivationUseCase
    private lateinit var renameModule: RenameTenantModuleUseCase
    private lateinit var installCustom: InstallCustomModuleUseCase

    private val tenantId = TenantId("ten-provisioning-test")
    private val proPlan = TenantModuleEntitlement.forTier(SubscriptionTier.PRO)
    private val enterprisePlan = TenantModuleEntitlement.forTier(SubscriptionTier.ENTERPRISE)

    @BeforeTest
    fun setUp() {
        repository = FakeTenantPipelineRepository()
        getPipeline = GetTenantPipelineUseCase(repository)
        getCatalog = GetTenantModuleCatalogUseCase(repository)
        setActivation = SetTenantModuleActivationUseCase(repository)
        renameModule = RenameTenantModuleUseCase(repository)
        installCustom = InstallCustomModuleUseCase(repository)
    }

    private fun customPlugin() = DynamicModuleDescriptor(
        moduleId = "sablon_bordir_custom",
        archetype = GarmentSlots.FINISHING,
        name = "Sablon Manual & Bordir Komputer",
        description = "Stasiun sablon plastisol dan bordir komputer.",
        acceptedInputDataTypes = setOf("CutPiecesBundle"),
        producedOutputDataType = "DecoratedGarmentBundle",
        isCustomTenantPlugin = true,
        customConfigSchemaJson = """{"screenColorsMax":6}"""
    )

    @Test
    fun catalog_shouldMarkInstalledActiveAndPlanBlockedModules() = runTest {
        getPipeline(tenantId, GarmentBlueprints.CMT_MAKLOON).getOrThrow()

        val catalog = getCatalog(tenantId, proPlan, GarmentBlueprints.CMT_MAKLOON).getOrThrow()

        // Katalog kanvas memuat modul **operasional** saja, bukan seluruh BusinessModule.
        // Sejak modul tata kelola (Bagan Organisasi, RBAC, Alur Pabrik) masuk enum, dua jumlah itu
        // tidak lagi kebetulan sama — dan memang tidak boleh sama: modul tata kelola tidak berdiri
        // di lini produksi dan tidak memakan kuota paket.
        assertEquals(OperationalModuleCatalog.all.size, catalog.size)
        assertTrue(catalog.all { it.isInstalled }, "Seluruh modul preset harus tercatat terpasang")
        // CMT bypasses procurement, so not every installed module is active.
        assertTrue(catalog.any { !it.isActive })
        assertTrue(catalog.all { it.isGrantedByPlan }, "Paket PRO memberi semua modul bawaan")
    }

    @Test
    fun catalog_onStarterPlan_shouldFlagUngrantedModules() = runTest {
        val starterPlan = TenantModuleEntitlement(
            tier = SubscriptionTier.STARTER,
            grantedModules = setOf(GarmentModules.CRM_SALES, GarmentModules.OPERATOR_EXEC)
        )
        getPipeline(tenantId, GarmentBlueprints.FOB_FULL_PACKAGE).getOrThrow()

        val catalog = getCatalog(tenantId, starterPlan).getOrThrow()

        assertEquals(2, catalog.count { it.isGrantedByPlan })
        assertTrue(catalog.count { it.requiresPlanUpgrade } > 0)
    }

    @Test
    fun deactivateModule_shouldBypassItWithoutDeletingTheNode() = runTest {
        getPipeline(tenantId, GarmentBlueprints.FOB_FULL_PACKAGE).getOrThrow()

        val updated = setActivation(tenantId, "inventory", false, proPlan).getOrThrow()
        val node = updated.nodes.firstOrNull { it.moduleId == "inventory" }

        assertNotNull(node, "Modul yang dinonaktifkan harus tetap ada agar bisa diaktifkan lagi")
        assertTrue(node.isBypassed)
        assertEquals(8, updated.activeNodes.size)
        // Wiring survives, so the flow can be restored intact.
        assertTrue(updated.edges.any { it.toNodeId == node.nodeId })
    }

    @Test
    fun reactivateModule_shouldClearBypass() = runTest {
        getPipeline(tenantId, GarmentBlueprints.CMT_MAKLOON).getOrThrow()
        val bypassedModuleId = repository.findByTenantId(tenantId)!!
            .nodes.first { it.isBypassed }.moduleId

        val updated = setActivation(tenantId, bypassedModuleId, true, proPlan).getOrThrow()

        assertFalse(updated.nodes.first { it.moduleId == bypassedModuleId }.isBypassed)
    }

    @Test
    fun activateModule_beyondPlanLimit_shouldFail() = runTest {
        val starterPlan = TenantModuleEntitlement.forTier(SubscriptionTier.STARTER)
        getPipeline(tenantId, GarmentBlueprints.FOB_FULL_PACKAGE).getOrThrow()

        // Nine active modules already exceeds STARTER's five, so any save must be refused.
        val result = setActivation(tenantId, "inventory", true, starterPlan)

        assertTrue(result.isFailure)
        assertTrue(
            result.exceptionOrNull()?.message?.contains("paket langganan") == true,
            "Pesan: ${result.exceptionOrNull()?.message}"
        )
    }

    @Test
    fun renameModule_shouldPersistPerTenantNameOnly() = runTest {
        val pipeline = getPipeline(tenantId, GarmentBlueprints.FOB_FULL_PACKAGE).getOrThrow()
        val inventoryNode = pipeline.nodes.first { it.moduleId == "inventory" }

        val updated = renameModule(
            tenantId = tenantId,
            nodeId = inventoryNode.nodeId,
            newDisplayName = "  Gudang Kain Roll Impor  ",
            entitlement = proPlan
        ).getOrThrow()

        assertEquals(
            "Gudang Kain Roll Impor",
            updated.nodes.first { it.nodeId == inventoryNode.nodeId }.customDisplayName
        )
        // The built-in module keeps its own catalogue name for every other tenant.
        assertEquals(
            "Bahan Baku & Stok Kain",
            OperationalModuleCatalog.specificationFor(GarmentModules.INVENTORY).module.displayName
        )
    }

    @Test
    fun renameModule_withFormulaParameters_shouldPersistBoth() = runTest {
        val pipeline = getPipeline(tenantId, GarmentBlueprints.CMT_MAKLOON).getOrThrow()
        val sewingNode = pipeline.nodes.first { it.moduleId == "operator_exec" }
        val parameters = mapOf("sewingTariffPerMinuteIdr" to "550")

        val updated = renameModule(
            tenantId = tenantId,
            nodeId = sewingNode.nodeId,
            newDisplayName = "Jahit Borongan Rumahan",
            formulaParameters = parameters,
            entitlement = proPlan
        ).getOrThrow()

        val node = updated.nodes.first { it.nodeId == sewingNode.nodeId }
        assertEquals("Jahit Borongan Rumahan", node.customDisplayName)
        assertEquals(parameters, node.customFormulaParameters)
        assertEquals("550", node.formulaParameter("sewingTariffPerMinuteIdr"))
    }

    @Test
    fun renameModule_withBlankName_shouldFail() = runTest {
        val pipeline = getPipeline(tenantId, GarmentBlueprints.FOB_FULL_PACKAGE).getOrThrow()

        val result = renameModule(
            tenantId = tenantId,
            nodeId = pipeline.nodes.first().nodeId,
            newDisplayName = "   ",
            entitlement = proPlan
        )

        assertTrue(result.isFailure)
    }

    @Test
    fun installCustomModule_onEnterprisePlan_shouldPersistAndWireIt() = runTest {
        getPipeline(tenantId, GarmentBlueprints.BRAND_D2C).getOrThrow()
        val descriptor = customPlugin()

        val updated = installCustom(
            tenantId = tenantId,
            descriptor = descriptor,
            entitlement = enterprisePlan,
            formulaParameters = mapOf("screenPrintCostPerPcsIdr" to "6500")
        ).getOrThrow()

        val installed = updated.nodes.first { it.moduleId == descriptor.moduleId }
        assertTrue(installed.isCustomPlugin)
        assertEquals("6500", installed.formulaParameter("screenPrintCostPerPcsIdr"))
        assertNotNull(installed.configSchemaJson)
        assertNull(installed.standardModule, "Plugin kustom bukan modul bawaan")
        assertTrue(
            updated.edges.any { it.toNodeId == installed.nodeId },
            "Modul kustom harus tersambung ke alur, bukan menggantung"
        )
    }

    @Test
    fun installCustomModule_onProPlan_shouldBeRejected() = runTest {
        getPipeline(tenantId, GarmentBlueprints.BRAND_D2C).getOrThrow()

        val result = installCustom(tenantId, customPlugin(), proPlan)

        assertTrue(result.isFailure)
        assertTrue(
            result.exceptionOrNull()?.message?.contains("tidak mendukung") == true,
            "Pesan: ${result.exceptionOrNull()?.message}"
        )
    }

    @Test
    fun installCustomModule_twice_shouldBeRejected() = runTest {
        getPipeline(tenantId, GarmentBlueprints.BRAND_D2C).getOrThrow()
        installCustom(tenantId, customPlugin(), enterprisePlan).getOrThrow()

        val result = installCustom(tenantId, customPlugin(), enterprisePlan)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("sudah terpasang") == true)
    }

    @Test
    fun catalog_shouldListInstalledCustomPlugin() = runTest {
        getPipeline(tenantId, GarmentBlueprints.BRAND_D2C).getOrThrow()
        installCustom(tenantId, customPlugin(), enterprisePlan).getOrThrow()

        val grantedPlan = enterprisePlan.grantCustomModule(customPlugin().moduleId)
        val catalog = getCatalog(tenantId, grantedPlan, GarmentBlueprints.BRAND_D2C).getOrThrow()

        val plugin = catalog.firstOrNull { it.isCustomPlugin }
        assertNotNull(plugin, "Katalog harus memuat modul kustom yang terpasang")
        assertEquals("Sablon Manual & Bordir Komputer", plugin.displayName)
        assertTrue(plugin.isActive)
        assertTrue(plugin.isGrantedByPlan)
    }

    @Test
    fun twoTenants_shouldHoldCompletelyIndependentModuleSets() = runTest {
        val fobTenant = TenantId("ten-fob")
        val cmtTenant = TenantId("ten-cmt")

        getPipeline(fobTenant, GarmentBlueprints.FOB_FULL_PACKAGE).getOrThrow()
        getPipeline(cmtTenant, GarmentBlueprints.CMT_MAKLOON).getOrThrow()

        renameModule(fobTenant, "fob-inventory", "Gudang Kain Roll Impor", entitlement = proPlan)
            .getOrThrow()
        setActivation(cmtTenant, "costing_hpp", false, proPlan).getOrThrow()

        val fob = repository.findByTenantId(fobTenant)!!
        val cmt = repository.findByTenantId(cmtTenant)!!

        assertEquals(
            "Gudang Kain Roll Impor",
            fob.nodes.first { it.moduleId == "inventory" }.customDisplayName
        )
        // The CMT tenant's own inventory name is untouched by the FOB tenant's rename.
        assertTrue(
            cmt.nodes.first { it.moduleId == "inventory" }.customDisplayName != "Gudang Kain Roll Impor"
        )
        assertFalse(fob.nodes.first { it.moduleId == "costing_hpp" }.isBypassed)
        assertTrue(cmt.nodes.first { it.moduleId == "costing_hpp" }.isBypassed)
    }
}
