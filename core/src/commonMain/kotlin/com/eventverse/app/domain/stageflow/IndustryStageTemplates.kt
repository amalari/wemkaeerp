package com.eventverse.app.domain.stageflow

import com.eventverse.app.domain.pipeline.ModuleArchetype
import com.eventverse.app.domain.stageflow.StageKind.ENTRY_ANCHOR
import com.eventverse.app.domain.stageflow.StageKind.EXIT_ANCHOR
import com.eventverse.app.domain.stageflow.StageKind.WORK
import com.eventverse.app.domain.stageflow.StageTrait.FINISHING_FLOOR
import com.eventverse.app.domain.stageflow.StageTrait.PHASE_TAGGABLE
import com.eventverse.app.domain.stageflow.StageTrait.WET_OR_PRESS
import com.eventverse.app.domain.tenant.TenantId

/**
 * Template industri read-only yang disalin ke [TenantStageFlow] saat tenant di-provision.
 *
 * `KNIT_SWEATER` wajib identik dengan enum `SamplingPipelineStage` — kode, nama, urutan, dan
 * sifatnya — karena itulah yang membuat Tahap 1 TRD-FLOW-001 nol perubahan perilaku. Paritasnya
 * dijaga oleh `IndustryStageTemplatesTest`; ubah keduanya bersamaan atau jangan sama sekali.
 */
object IndustryStageTemplates {

    private fun stage(
        code: String,
        name: String,
        kind: StageKind,
        archetype: ModuleArchetype,
        vararg traits: StageTrait
    ) = StageDefinition(StageCode(code), name, kind, archetype, traits.toSet())

    private val KNIT_SWEATER: List<StageDefinition> = listOf(
        stage("NEW_INTAKE", "SPK Masuk (Sales Deal)", ENTRY_ANCHOR, ModuleArchetype.ORDER_INGESTION),
        stage("FLOW_REVIEW", "Penentuan Alur Desain", ENTRY_ANCHOR, ModuleArchetype.PRODUCT_ENGINEERING),
        stage("CAM_PROGRAMMING", "Program CAM", WORK, ModuleArchetype.PRODUCT_ENGINEERING),
        stage("MACHINE_KNITTING", "Rajut Turun Mesin", WORK, ModuleArchetype.CUTTING),
        stage("LINKING_ASSEMBLY", "Linking & Tambahan", WORK, ModuleArchetype.SEWING, FINISHING_FLOOR),
        stage(
            "CUCI_SOFTENER", "Cuci & Softener", WORK, ModuleArchetype.FINISHING,
            FINISHING_FLOOR, WET_OR_PRESS, PHASE_TAGGABLE
        ),
        stage(
            "SETRIKA_UAP", "Setrika Uap", WORK, ModuleArchetype.FINISHING,
            FINISHING_FLOOR, WET_OR_PRESS, PHASE_TAGGABLE
        ),
        stage("QC_FINISHING", "QC Finishing", WORK, ModuleArchetype.QUALITY_CONTROL, FINISHING_FLOOR),
        stage("PENGEMASAN", "Pengemasan", WORK, ModuleArchetype.FULFILLMENT, FINISHING_FLOOR),
        stage("STORAGE_HOLDING", "Penyimpanan (Siap Kirim)", EXIT_ANCHOR, ModuleArchetype.FULFILLMENT),
        stage("IN_DELIVERY", "Terkirim (Tunggu ACC)", EXIT_ANCHOR, ModuleArchetype.FULFILLMENT),
        stage("ACC_APPROVED", "ACC Produksi", EXIT_ANCHOR, ModuleArchetype.FULFILLMENT)
    )

    fun stagesOf(template: IndustryTemplateCode): List<StageDefinition> = when (template) {
        IndustryTemplateCode.KNIT_SWEATER -> KNIT_SWEATER
    }

    fun instantiate(tenantId: TenantId, template: IndustryTemplateCode): TenantStageFlow =
        TenantStageFlow(tenantId, template, stagesOf(template))
}
