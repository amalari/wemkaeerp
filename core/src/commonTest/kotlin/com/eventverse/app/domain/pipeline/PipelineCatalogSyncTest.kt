package com.eventverse.app.domain.pipeline

import com.eventverse.app.domain.pipeline.usecases.SetTenantModuleActivationUseCase
import com.eventverse.app.domain.pipeline.usecases.SyncTenantPipelineWithCatalogUseCase
import com.eventverse.app.domain.rbac.BusinessModule
import com.eventverse.app.domain.tenant.SubscriptionTier
import com.eventverse.app.domain.tenant.TenantId
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class PipelineCatalogSyncTest {

    private val tenantId = TenantId("ten-catalog-sync")
    private val allModules = BusinessModule.entries.toSet()

    /** A tenant provisioned before QC existed in the catalogue, and who renamed sewing. */
    private fun legacyPipelineWithoutQc(): CustomTenantPipeline {
        val fresh = CustomTenantPipeline.fromPreset(tenantId, GarmentBusinessPreset.FOB_FULL_PACKAGE)
        val qc = fresh.nodes.first { it.moduleId == BusinessModule.QUALITY_CONTROL.code }
        val sewing = fresh.nodes.first { it.moduleId == BusinessModule.OPERATOR_EXEC.code }
        return fresh.removeNode(qc.nodeId).renameNode(sewing.nodeId, "Lini Jahit A")
    }

    @Test
    fun reconcile_whenNothingMissing_shouldReturnSameInstance() {
        val pipeline = CustomTenantPipeline.fromPreset(tenantId, GarmentBusinessPreset.CMT_MAKLOON)

        assertSame(pipeline, PipelineCatalogReconciler.reconcile(pipeline, allModules))
    }

    @Test
    fun reconcile_whenCatalogModuleMissing_shouldInsertItBypassedInCatalogPosition() {
        val synced = PipelineCatalogReconciler.reconcile(legacyPipelineWithoutQc(), allModules)

        val codes = synced.orderedNodes.map { it.moduleId }
        val qcIndex = codes.indexOf(BusinessModule.QUALITY_CONTROL.code)
        assertEquals(codes.indexOf(BusinessModule.OPERATOR_EXEC.code) + 1, qcIndex)
        assertTrue(synced.orderedNodes[qcIndex].isBypassed)
        assertEquals((1..codes.size).toList(), synced.orderedNodes.map { it.stepOrderIndex })
    }

    @Test
    fun reconcile_whenModuleMissing_shouldKeepTenantRenamesAndActiveCount() {
        val legacy = legacyPipelineWithoutQc()
        val synced = PipelineCatalogReconciler.reconcile(legacy, allModules)

        assertTrue(synced.nodes.any { it.customDisplayName == "Lini Jahit A" })
        assertEquals(legacy.activeNodes.size, synced.activeNodes.size)
        // TRD-FLOW-002 Fase 3: edge tenant tetap utuh; edge baru hanya menyentuh modul sisipan, dari port.
        assertTrue(synced.edges.containsAll(legacy.edges))
        val qcNodeId = synced.nodes.single { it.moduleId == BusinessModule.QUALITY_CONTROL.code }.nodeId
        val added = synced.edges - legacy.edges.toSet()
        assertTrue(added.isNotEmpty() && added.all { it.fromNodeId == qcNodeId || it.toNodeId == qcNodeId })
        assertTrue(added.any { it.toNodeId == qcNodeId && it.expectedDataType == "AssembledGarmentBundle" })
    }

    @Test
    fun reconcile_whenModuleNotGranted_shouldNotInsertIt() {
        val granted = allModules - BusinessModule.QUALITY_CONTROL
        val legacy = legacyPipelineWithoutQc()

        assertSame(legacy, PipelineCatalogReconciler.reconcile(legacy, granted))
    }

    @Test
    fun sync_whenModuleMissing_shouldPersistOnce() = runTest {
        val repository = FakeTenantPipelineRepository()
        repository.save(legacyPipelineWithoutQc())
        val sync = SyncTenantPipelineWithCatalogUseCase(repository)
        val entitlement = TenantModuleEntitlement(SubscriptionTier.ENTERPRISE)

        val first = sync(tenantId, entitlement).getOrThrow()
        val second = sync(tenantId, entitlement).getOrThrow()

        assertTrue(first.nodes.any { it.moduleId == BusinessModule.QUALITY_CONTROL.code })
        assertEquals(first, repository.findByTenantId(tenantId))
        assertEquals(first, second)
    }

    @Test
    fun activate_syncedBypassedModule_shouldWireItFromPrecedingActiveNode() = runTest {
        val repository = FakeTenantPipelineRepository()
        repository.save(PipelineCatalogReconciler.reconcile(legacyPipelineWithoutQc(), allModules))
        val activate = SetTenantModuleActivationUseCase(repository)

        val updated = activate(
            tenantId = tenantId,
            moduleId = BusinessModule.QUALITY_CONTROL.code,
            isActive = true,
            entitlement = TenantModuleEntitlement(SubscriptionTier.ENTERPRISE)
        ).getOrThrow()

        val qc = updated.nodes.first { it.moduleId == BusinessModule.QUALITY_CONTROL.code }
        assertFalse(qc.isBypassed)
        assertTrue(updated.edges.any { it.toNodeId == qc.nodeId && !it.isFeedbackReworkLoop })
    }
}
