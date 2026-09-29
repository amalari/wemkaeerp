package com.eventverse.app.domain.stageflow

import com.eventverse.app.domain.pipeline.code

import com.eventverse.app.domain.pack.GarmentSlots

import com.eventverse.app.domain.pipeline.ModuleArchetype
import com.eventverse.app.domain.sampling.ExitStages
import com.eventverse.app.domain.sampling.FinishingDeposit
import com.eventverse.app.domain.sampling.QcInspectionReport
import com.eventverse.app.domain.sampling.SamplingOrder
import com.eventverse.app.domain.sampling.SamplingOrderId
import com.eventverse.app.domain.sampling.SpkNumber
import com.eventverse.app.domain.sampling.currentStage
import com.eventverse.app.domain.sampling.firstWorkWith
import com.eventverse.app.domain.sampling.freezeStageFlow
import com.eventverse.app.domain.sampling.rdProgress
import com.eventverse.app.domain.sampling.samplingRoute
import com.eventverse.app.domain.sampling.startStageWork
import com.eventverse.app.domain.tenant.TenantId
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlin.test.*

/**
 * Setiap template industri (TRD-FLOW-001 Tahap 3) harus valid dan bisa dijalani penuh oleh
 * aturan domain — dari SPK Masuk sampai siap disimpan — tanpa menyentuh jalur enum rajut.
 */
class IndustryTemplateWalkTest {

    private val now = Instant.parse("2026-09-29T00:00:00Z")
    private val tenant = TenantId("ten-walk")

    private fun orderOn(template: IndustryTemplateCode) = SamplingOrder(
        id = SamplingOrderId("smp_walk_${template.name.lowercase()}"),
        tenantId = tenant,
        spkNumber = SpkNumber("SPK-WLK-0001"),
        clientName = "Buyer",
        styleName = template.displayName,
        sampleQuantity = 2,
        createdAt = now,
        updatedAt = now
    ).freezeStageFlow(IndustryStageTemplates.instantiate(tenant, template))

    @Test
    fun everyTemplate_shouldBuildValidFlowWithSharedAnchorsAndRoles() {
        IndustryTemplateCode.entries.forEach { template ->
            val stages = IndustryStageTemplates.instantiate(tenant, template).stages
            val codes = stages.map { it.code.value }
            assertEquals(listOf("NEW_INTAKE", "FLOW_REVIEW"), codes.take(2), "$template masuk")
            assertEquals(listOf("STORAGE_HOLDING", "IN_DELIVERY", "ACC_APPROVED"), codes.takeLast(3), "$template keluar")
            assertNotNull(stages.firstWorkWith(GarmentSlots.QUALITY_CONTROL), "$template QC")
            assertNotNull(stages.firstWorkWith(GarmentSlots.FULFILLMENT), "$template kemas")
            assertTrue(stages.any { it.has(StageTrait.OPERATOR_DESK) }, "$template meja")
            val factors = stages.map { it.remainingWorkFactor }
            assertEquals(factors.sortedDescending(), factors, "$template sisa kerja harus menurun")
        }
    }

    @Test
    fun newTemplates_shouldWalkFromIntakeToPackingThroughDomainRules() {
        // Rajut dikecualikan: gerbang lembar Program CAM (benar) menahannya, dan jalur rajut sudah
        // dijaga paritas enum + SamplingOrderTest. Yang diuji di sini: template baru tidak buntu.
        (IndustryTemplateCode.entries - IndustryTemplateCode.KNIT_SWEATER).forEach { template ->
            var order = orderOn(template)
            // Maju tahap demi tahap lewat rute, sampai meja pemeriksaan akhir.
            val qc = order.firstWorkWithRole(GarmentSlots.QUALITY_CONTROL)
            while (order.stageCode != qc) {
                val next = assertNotNull(order.samplingRoute.nextAfter(order.stageCode), "$template buntu di ${order.stageCode}")
                order = order.advancePipelineStage(next, now)
                if (order.currentStage.has(StageTrait.OPERATOR_DESK)) {
                    order = order.startStageWork("Operator", "op@x", now)
                }
            }
            // Setoran di mana pun tidak boleh meledak — sablon tidak punya peran perakitan.
            order.addFinishingDeposit(FinishingDeposit(depositDate = LocalDate(2026, 9, 29), qtyPcs = 2, operatorName = "Operator"), now)

            val passed = order.completeQcInspection(QcInspectionReport(inspectorName = "QC", inspectedAt = now), now)
            assertEquals(order.firstWorkWithRole(GarmentSlots.FULFILLMENT), passed.stageCode, "$template QC lolos → kemas")
            assertTrue(passed.rdProgress(emptyList()).isNotEmpty(), "$template jejak R&D")
            assertNotEquals(ExitStages.STORAGE, passed.stageCode)
        }
    }

    private fun SamplingOrder.firstWorkWithRole(archetype: ModuleArchetype): StageCode =
        assertNotNull(frozenStageFlow?.firstWorkWith(archetype)).code
}
