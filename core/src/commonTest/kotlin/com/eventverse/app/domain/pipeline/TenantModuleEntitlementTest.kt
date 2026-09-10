package com.eventverse.app.domain.pipeline

import com.eventverse.app.domain.rbac.BusinessModule
import com.eventverse.app.domain.tenant.SubscriptionTier
import com.eventverse.app.domain.tenant.TenantId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TenantModuleEntitlementTest {

    private val tenantId = TenantId("ten-entitlement-test")

    private fun fobPipeline() =
        CustomTenantPipeline.fromPreset(tenantId, GarmentBusinessPreset.FOB_FULL_PACKAGE)

    private fun customPlugin(moduleId: String = "sablon_bordir_custom") = DynamicModuleDescriptor(
        moduleId = moduleId,
        archetype = ModuleArchetype.FINISHING,
        name = "Sablon & Bordir",
        description = "Stasiun dekorasi kustom.",
        acceptedInputDataTypes = setOf("CutPiecesBundle"),
        producedOutputDataType = "DecoratedGarmentBundle",
        isCustomTenantPlugin = true
    )

    @Test
    fun starterPlan_shouldRejectNinePipelineModules() {
        val entitlement = TenantModuleEntitlement.forTier(SubscriptionTier.STARTER)

        val violations = entitlement.validate(fobPipeline())

        assertTrue(violations.isNotEmpty())
        assertTrue(
            violations.any { it.contains("5 modul aktif") },
            "Pesan harus menyebut batas paket: $violations"
        )
    }

    @Test
    fun proPlan_shouldAllowAllNineBuiltInModules() {
        val entitlement = TenantModuleEntitlement.forTier(SubscriptionTier.PRO)

        assertTrue(entitlement.isSatisfiedBy(fobPipeline()))
    }

    @Test
    fun bypassedModules_shouldNotConsumeAPlanSlot() {
        // A five-module plan must still be able to hold a nine-node graph as long as only
        // five are switched on — otherwise switching a module off would be impossible.
        val entitlement = TenantModuleEntitlement.forTier(SubscriptionTier.STARTER)
        val pipeline = fobPipeline().let { original ->
            original.orderedNodes.drop(5).fold(original) { acc, node ->
                acc.setNodeBypassed(node.nodeId, true)
            }
        }

        assertEquals(5, pipeline.activeNodes.size)
        assertTrue(entitlement.isSatisfiedBy(pipeline), "Violations: ${entitlement.validate(pipeline)}")
    }

    @Test
    fun proPlan_shouldRejectCustomPluginModules() {
        val entitlement = TenantModuleEntitlement.forTier(SubscriptionTier.PRO)
        val pipeline = fobPipeline().addNode(customPlugin().toPipelineNode())

        val violations = entitlement.validate(pipeline)

        assertTrue(
            violations.any { it.contains("tidak mendukung modul kustom") },
            "Violations: $violations"
        )
    }

    @Test
    fun enterprisePlan_shouldAllowGrantedCustomPlugin() {
        val descriptor = customPlugin()
        val entitlement = TenantModuleEntitlement
            .forTier(SubscriptionTier.ENTERPRISE)
            .grantCustomModule(descriptor.moduleId)
        val pipeline = fobPipeline().addNode(descriptor.toPipelineNode())

        assertTrue(entitlement.isSatisfiedBy(pipeline), "Violations: ${entitlement.validate(pipeline)}")
    }

    @Test
    fun enterprisePlan_shouldRejectUngrantedCustomPlugin() {
        val entitlement = TenantModuleEntitlement.forTier(SubscriptionTier.ENTERPRISE)
        val pipeline = fobPipeline().addNode(customPlugin().toPipelineNode())

        val violations = entitlement.validate(pipeline)

        assertTrue(
            violations.any { it.contains("belum diaktifkan untuk tenant ini") },
            "Violations: $violations"
        )
    }

    @Test
    fun narrowedCatalogue_shouldRejectModuleOutsidePlan() {
        val entitlement = TenantModuleEntitlement(
            tier = SubscriptionTier.ENTERPRISE,
            grantedModules = BusinessModule.entries.toSet() - BusinessModule.COSTING_HPP
        )

        val violations = entitlement.validate(fobPipeline())

        assertTrue(
            violations.any { it.contains(BusinessModule.COSTING_HPP.displayName) },
            "Violations: $violations"
        )
    }

    @Test
    fun validate_shouldReportEveryViolationNotJustTheFirst() {
        val entitlement = TenantModuleEntitlement(
            tier = SubscriptionTier.STARTER,
            grantedModules = setOf(BusinessModule.CRM_SALES)
        )

        val violations = entitlement.validate(fobPipeline())

        // One count violation plus one per module outside the granted catalogue.
        assertTrue(violations.size > 1, "Diharapkan banyak pelanggaran, dapat: $violations")
    }

    @Test
    fun permits_unknownModuleId_shouldBeRejected() {
        val entitlement = TenantModuleEntitlement.forTier(SubscriptionTier.ENTERPRISE)
        val ghostNode = CustomPipelineNode(
            nodeId = "node-ghost",
            moduleId = "module_that_does_not_exist",
            customDisplayName = "Modul Hantu",
            archetype = ModuleArchetype.CUSTOM_EXTENSION,
            isCustomPlugin = false
        )

        assertFalse(entitlement.permits(ghostNode))
    }
}
