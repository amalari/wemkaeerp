package com.eventverse.app.domain.pipeline

import com.eventverse.app.domain.rbac.isScopeSupported

import com.eventverse.app.domain.rbac.isFoundation

import com.eventverse.app.domain.rbac.isOperational

import com.eventverse.app.domain.rbac.isGovernance

import com.eventverse.app.domain.rbac.isHierarchical

import com.eventverse.app.domain.rbac.isGlobalOnly

import com.eventverse.app.domain.rbac.category

import com.eventverse.app.domain.rbac.supportedScopes

import com.eventverse.app.domain.rbac.kind

import com.eventverse.app.domain.rbac.scopeCapability

import com.eventverse.app.domain.rbac.iconKey

import com.eventverse.app.domain.rbac.description

import com.eventverse.app.domain.rbac.displayName

import com.eventverse.app.domain.rbac.name

import com.eventverse.app.domain.rbac.code

import com.eventverse.app.domain.rbac.BusinessModules

import com.eventverse.app.domain.pack.GarmentModules

import com.eventverse.app.domain.pack.GarmentBlueprints

import com.eventverse.app.domain.pack.GarmentSlots

import com.eventverse.app.domain.rbac.BusinessModule
import com.eventverse.app.domain.tenant.SubscriptionTier
import com.eventverse.app.domain.tenant.TenantId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TenantModuleEntitlementTest {

    private val tenantId = TenantId("ten-entitlement-test")

    private fun fobPipeline() =
        CustomTenantPipeline.fromPreset(tenantId, GarmentBlueprints.FOB_FULL_PACKAGE)

    private fun customPlugin(moduleId: String = "sablon_bordir_custom") = DynamicModuleDescriptor(
        moduleId = moduleId,
        archetype = GarmentSlots.FINISHING,
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
            grantedModules = BusinessModules.entries.toSet() - GarmentModules.COSTING_HPP
        )

        val violations = entitlement.validate(fobPipeline())

        assertTrue(
            violations.any { it.contains(GarmentModules.COSTING_HPP.displayName) },
            "Violations: $violations"
        )
    }

    @Test
    fun validate_shouldReportEveryViolationNotJustTheFirst() {
        val entitlement = TenantModuleEntitlement(
            tier = SubscriptionTier.STARTER,
            grantedModules = setOf(GarmentModules.CRM_SALES)
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
            archetype = GarmentSlots.CUSTOM_EXTENSION,
            isCustomPlugin = false
        )

        assertFalse(entitlement.permits(ghostNode))
    }
}

/**
 * Perilaku yang lahir ketika modul tata kelola masuk ke `BusinessModule`: keduanya harus ikut
 * di-grant, tetapi tidak boleh ikut memakan kuota modul produksi.
 */
class GovernanceEntitlementTest {

    @Test
    fun defaultGrant_shouldIncludeGovernanceModules() {
        val entitlement = TenantModuleEntitlement.forTier(SubscriptionTier.STARTER)

        BusinessModules.governance.forEach { module ->
            assertTrue(
                entitlement.permitsModule(module),
                "${module.code} harus aktif secara bawaan; paket membatasi jumlah modul produksi, " +
                    "bukan akses ke layar pengaturan"
            )
        }
    }

    @Test
    fun revokedGovernanceModule_shouldNotBePermitted() {
        val entitlement = TenantModuleEntitlement.resolve(
            tier = SubscriptionTier.PRO,
            grants = TenantEntitlementGrants(
                grantedModules = BusinessModules.entries.toSet() - GarmentModules.FACTORY_FLOW
            )
        )

        assertFalse(entitlement.permitsModule(GarmentModules.FACTORY_FLOW))
        assertTrue(entitlement.permitsModule(GarmentModules.ORG_CHART))
    }

    @Test
    fun withModule_fromAllGranted_shouldRemoveOnlyTheNamedModule() {
        // Jebakan utamanya: grantedModules == null berarti "semua", bukan "kosong". Pengurangan
        // himpunan tanpa memadatkannya lebih dulu akan mencabut seluruh modul lain sekaligus.
        val grants = TenantEntitlementGrants().withModule(GarmentModules.FACTORY_FLOW, enabled = false)

        val modules = grants.grantedModules
        assertNotNull(modules)
        assertFalse(GarmentModules.FACTORY_FLOW in modules)
        assertEquals(BusinessModules.entries.size - 1, modules.size)
    }

    @Test
    fun withModule_restoringTheLastMissingModule_shouldCollapseBackToNull() {
        // null disimpan kembali supaya tenant ikut mewarisi modul yang dirilis kemudian, tanpa
        // perlu migrasi data lagi seperti V18.
        val grants = TenantEntitlementGrants()
            .withModule(GarmentModules.ORG_CHART, enabled = false)
            .withModule(GarmentModules.ORG_CHART, enabled = true)

        assertEquals(null, grants.grantedModules)
    }

    @Test
    fun planQuota_shouldCountOperationalModulesOnly() {
        // STARTER hanya mengizinkan 5 modul aktif. Alur preset CMT harus dinilai dengan angka yang
        // sama seperti sebelum modul tata kelola ada.
        val starter = TenantModuleEntitlement.forTier(SubscriptionTier.STARTER)
        val pipeline = CustomTenantPipeline.fromPreset(
            TenantId("ten-demo-001"),
            GarmentBlueprints.CMT_MAKLOON
        )

        val activeOperational = pipeline.activeNodes.count { it.standardModule?.isOperational == true }
        assertEquals(
            activeOperational,
            pipeline.activeNodes.size,
            "Modul tata kelola tidak boleh pernah muncul sebagai node di kanvas pabrik"
        )

        val violations = starter.validate(pipeline)
        val quotaViolations = violations.filter { it.contains("modul aktif") }
        assertEquals(
            if (activeOperational > starter.maxActiveModules) 1 else 0,
            quotaViolations.size
        )
    }
}
