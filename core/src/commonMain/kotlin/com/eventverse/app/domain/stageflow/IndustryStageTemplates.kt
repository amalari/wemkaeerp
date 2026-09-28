package com.eventverse.app.domain.stageflow

import com.eventverse.app.domain.pipeline.ModuleArchetype
import com.eventverse.app.domain.stageflow.StageKind.ENTRY_ANCHOR
import com.eventverse.app.domain.stageflow.StageKind.EXIT_ANCHOR
import com.eventverse.app.domain.stageflow.StageKind.WORK
import com.eventverse.app.domain.stageflow.StageTrait.FINISHING_FLOOR
import com.eventverse.app.domain.stageflow.StageTrait.OPERATOR_DESK
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
        short: String,
        color: Long,
        kind: StageKind,
        archetype: ModuleArchetype,
        remaining: Double,
        vararg traits: StageTrait
    ) = StageDefinition(
        StageCode(code), name, kind, archetype, traits.toSet(),
        shortLabel = short, colorHex = color, remainingWorkFactor = remaining
    )

    // Sisa kerja = DefaultStageWorkProfile lama (paritas dijaga test).
    // Warna = nilai token WeMadeColors yang dulu dipakai samplingStageTint (paritas dijaga test
    // di app/shared). Label ringkas = istilah lantai (meja operator), keputusan 2026-09-29.
    private const val MUTED = 0xFF64748B
    private const val ORANGE = 0xFFEA580C
    private const val BLUE = 0xFF2563EB
    private const val AMBER = 0xFFD97706
    private const val VIOLET = 0xFF7C3AED
    private const val TEAL = 0xFF0D9488
    private const val SLATE = 0xFF475569
    private const val SKY = 0xFF0284C7
    private const val GREEN = 0xFF16A34A

    private val KNIT_SWEATER: List<StageDefinition> = listOf(
        stage("NEW_INTAKE", "SPK Masuk (Sales Deal)", "Draft", MUTED, ENTRY_ANCHOR, ModuleArchetype.ORDER_INGESTION, 1.00),
        stage("FLOW_REVIEW", "Penentuan Alur Desain", "Alur", ORANGE, ENTRY_ANCHOR, ModuleArchetype.PRODUCT_ENGINEERING, 1.00),
        stage("CAM_PROGRAMMING", "Program CAM", "CAM", BLUE, WORK, ModuleArchetype.PRODUCT_ENGINEERING, 0.85),
        stage("MACHINE_KNITTING", "Rajut Turun Mesin", "Rajut", AMBER, WORK, ModuleArchetype.CUTTING, 0.55, OPERATOR_DESK),
        stage(
            "LINKING_ASSEMBLY", "Linking & Tambahan", "Linking", VIOLET, WORK, ModuleArchetype.SEWING, 0.35,
            FINISHING_FLOOR, OPERATOR_DESK
        ),
        stage(
            "CUCI_SOFTENER", "Cuci & Softener", "Cuci", TEAL, WORK, ModuleArchetype.FINISHING, 0.25,
            FINISHING_FLOOR, WET_OR_PRESS, PHASE_TAGGABLE, OPERATOR_DESK
        ),
        stage(
            "SETRIKA_UAP", "Setrika Uap", "Setrika", TEAL, WORK, ModuleArchetype.FINISHING, 0.15,
            FINISHING_FLOOR, WET_OR_PRESS, PHASE_TAGGABLE, OPERATOR_DESK
        ),
        stage(
            "QC_FINISHING", "QC Finishing", "QC", TEAL, WORK, ModuleArchetype.QUALITY_CONTROL, 0.08,
            FINISHING_FLOOR, OPERATOR_DESK
        ),
        stage("PENGEMASAN", "Pengemasan", "Kemas", TEAL, WORK, ModuleArchetype.FULFILLMENT, 0.04, FINISHING_FLOOR, OPERATOR_DESK),
        stage("STORAGE_HOLDING", "Penyimpanan (Siap Kirim)", "Disimpan", SLATE, EXIT_ANCHOR, ModuleArchetype.FULFILLMENT, 0.03),
        stage("IN_DELIVERY", "Terkirim (Tunggu ACC)", "Selesai", SKY, EXIT_ANCHOR, ModuleArchetype.FULFILLMENT, 0.02),
        stage("ACC_APPROVED", "ACC Produksi", "Selesai", GREEN, EXIT_ANCHOR, ModuleArchetype.FULFILLMENT, 0.00)
    )

    fun stagesOf(template: IndustryTemplateCode): List<StageDefinition> = when (template) {
        IndustryTemplateCode.KNIT_SWEATER -> KNIT_SWEATER
    }

    fun instantiate(tenantId: TenantId, template: IndustryTemplateCode): TenantStageFlow =
        TenantStageFlow(tenantId, template, stagesOf(template))
}
