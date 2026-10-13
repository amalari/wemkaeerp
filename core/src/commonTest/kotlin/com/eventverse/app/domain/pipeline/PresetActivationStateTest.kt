package com.eventverse.app.domain.pipeline

import com.eventverse.app.domain.pack.GarmentBlueprints
import com.eventverse.app.domain.pack.GarmentDomainPack
import com.eventverse.app.domain.rbac.moduleIds
import com.eventverse.app.domain.tenant.TenantId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** PLAN A2: preset hanya saran — tenant baru mulai tanpa modul aktif, dan aktivasi manual tidak ditimpa reconciler. */
class PresetActivationStateTest {

    private val tenantId = TenantId("ten-a2-activation")

    @Test
    fun fromPreset_forEveryStarterPreset_startsWithNoActiveModule() {
        GarmentBlueprints.all.forEach { preset ->
            val pipeline = CustomTenantPipeline.fromPreset(tenantId, preset)
            assertTrue(pipeline.nodes.isNotEmpty(), "${preset.code} harus punya node")
            assertTrue(pipeline.activeNodes.isEmpty(), "preset ${preset.code} tidak boleh mengaktifkan modul sendiri")
        }
    }

    @Test
    fun withPresetActivation_followsNonDefaultPresetSuggestion() {
        val cmt = GarmentBlueprints.CMT_MAKLOON
        val pipeline = CustomTenantPipeline.fromPreset(tenantId, cmt).withPresetActivation(cmt)

        assertTrue(pipeline.activeNodes.isNotEmpty())
        assertTrue(pipeline.nodes.any { it.isBypassed }, "CMT mem-bypass setidaknya satu modul (mis. pengadaan bahan baku)")
    }

    @Test
    fun reconcile_keepsManualActivationOfExistingNodes() {
        val preset = GarmentBlueprints.BRAND_D2C
        val fresh = CustomTenantPipeline.fromPreset(tenantId, preset)
        val activated = fresh.setNodeBypassed(fresh.nodes.first().nodeId, false)
        val allModules = GarmentDomainPack.pack.moduleIds.toSet()

        val reconciled = PipelineCatalogReconciler.reconcile(activated, allModules)

        val stillActive = reconciled.nodes.first { it.nodeId == activated.nodes.first().nodeId }
        assertFalse(stillActive.isBypassed, "reconciler tidak boleh menimpa aktivasi manual")
        assertEquals(1, reconciled.activeNodes.size, "node sisipan katalog selalu bypassed")
    }
}
