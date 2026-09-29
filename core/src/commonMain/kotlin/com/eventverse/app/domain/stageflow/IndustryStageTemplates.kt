package com.eventverse.app.domain.stageflow

import com.eventverse.app.domain.pipeline.code

import com.eventverse.app.domain.pack.GarmentSlots

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

    /** Jangkar masuk & keluar — identik di setiap template (TRD-FLOW-001 FR-1). */
    private val ENTRY_ANCHORS: List<StageDefinition> = listOf(
        stage("NEW_INTAKE", "SPK Masuk (Sales Deal)", "Draft", MUTED, ENTRY_ANCHOR, GarmentSlots.ORDER_INGESTION, 1.00),
        stage("FLOW_REVIEW", "Penentuan Alur Desain", "Alur", ORANGE, ENTRY_ANCHOR, GarmentSlots.PRODUCT_ENGINEERING, 1.00)
    )

    private val EXIT_ANCHORS: List<StageDefinition> = listOf(
        stage("STORAGE_HOLDING", "Penyimpanan (Siap Kirim)", "Disimpan", SLATE, EXIT_ANCHOR, GarmentSlots.FULFILLMENT, 0.03),
        stage("IN_DELIVERY", "Terkirim (Tunggu ACC)", "Selesai", SKY, EXIT_ANCHOR, GarmentSlots.FULFILLMENT, 0.02),
        stage("ACC_APPROVED", "ACC Produksi", "Selesai", GREEN, EXIT_ANCHOR, GarmentSlots.FULFILLMENT, 0.00)
    )

    private val KNIT_SWEATER: List<StageDefinition> = ENTRY_ANCHORS + listOf(
        stage("CAM_PROGRAMMING", "Program CAM", "CAM", BLUE, WORK, GarmentSlots.PRODUCT_ENGINEERING, 0.85),
        stage("MACHINE_KNITTING", "Rajut Turun Mesin", "Rajut", AMBER, WORK, GarmentSlots.CUTTING, 0.55, OPERATOR_DESK),
        stage(
            "LINKING_ASSEMBLY", "Linking & Tambahan", "Linking", VIOLET, WORK, GarmentSlots.SEWING, 0.35,
            FINISHING_FLOOR, OPERATOR_DESK
        ),
        stage(
            "CUCI_SOFTENER", "Cuci & Softener", "Cuci", TEAL, WORK, GarmentSlots.FINISHING, 0.25,
            FINISHING_FLOOR, WET_OR_PRESS, PHASE_TAGGABLE, OPERATOR_DESK
        ),
        stage(
            "SETRIKA_UAP", "Setrika Uap", "Setrika", TEAL, WORK, GarmentSlots.FINISHING, 0.15,
            FINISHING_FLOOR, WET_OR_PRESS, PHASE_TAGGABLE, OPERATOR_DESK
        ),
        stage(
            "QC_FINISHING", "QC Finishing", "QC", TEAL, WORK, GarmentSlots.QUALITY_CONTROL, 0.08,
            FINISHING_FLOOR, OPERATOR_DESK
        ),
        stage("PENGEMASAN", "Pengemasan", "Kemas", TEAL, WORK, GarmentSlots.FULFILLMENT, 0.04, FINISHING_FLOOR, OPERATOR_DESK)
    ) + EXIT_ANCHORS

    // ── Template industri lain — DRAF (TRD-FLOW-001 §4 poin 3 belum divalidasi lantai) ──────
    // Kode yang sama dipakai untuk pekerjaan yang sama (QC_FINISHING, PENGEMASAN, SETRIKA_UAP)
    // supaya antrian QC, tag fase, dan cetak SPK mengenalinya tanpa kasus khusus. Tenant boleh
    // menyunting salinannya; template ini hanya titik awal.

    private val CUT_AND_SEW: List<StageDefinition> = ENTRY_ANCHORS + listOf(
        stage("PATTERN_MAKING", "Pembuatan Pola & Marker", "Pola", BLUE, WORK, GarmentSlots.PRODUCT_ENGINEERING, 0.85),
        stage("CUTTING", "Potong Kain", "Potong", AMBER, WORK, GarmentSlots.CUTTING, 0.65, OPERATOR_DESK),
        stage("SEWING", "Jahit", "Jahit", VIOLET, WORK, GarmentSlots.SEWING, 0.40, FINISHING_FLOOR, OPERATOR_DESK),
        stage("OVERLOCK", "Obras", "Obras", VIOLET, WORK, GarmentSlots.SEWING, 0.30, FINISHING_FLOOR, OPERATOR_DESK),
        stage("BUTTON_ATTACH", "Lubang & Pasang Kancing", "Kancing", TEAL, WORK, GarmentSlots.FINISHING, 0.22, FINISHING_FLOOR, OPERATOR_DESK),
        stage(
            "SETRIKA_UAP", "Setrika Uap", "Setrika", TEAL, WORK, GarmentSlots.FINISHING, 0.15,
            FINISHING_FLOOR, WET_OR_PRESS, PHASE_TAGGABLE, OPERATOR_DESK
        ),
        stage("QC_FINISHING", "QC Finishing", "QC", TEAL, WORK, GarmentSlots.QUALITY_CONTROL, 0.08, FINISHING_FLOOR, OPERATOR_DESK),
        stage("PENGEMASAN", "Pengemasan", "Kemas", TEAL, WORK, GarmentSlots.FULFILLMENT, 0.04, FINISHING_FLOOR, OPERATOR_DESK)
    ) + EXIT_ANCHORS

    private val EMBROIDERY: List<StageDefinition> = ENTRY_ANCHORS + listOf(
        stage("DIGITIZING", "Digitizing Desain", "Digitizing", BLUE, WORK, GarmentSlots.PRODUCT_ENGINEERING, 0.85),
        stage("HOOPING", "Hooping & Pasang Bahan", "Hooping", AMBER, WORK, GarmentSlots.CUTTING, 0.60, OPERATOR_DESK),
        stage("MACHINE_EMBROIDERY", "Bordir Mesin", "Bordir", VIOLET, WORK, GarmentSlots.SEWING, 0.35, FINISHING_FLOOR, OPERATOR_DESK),
        stage("THREAD_TRIMMING", "Buang Benang & Rapikan", "Trimming", TEAL, WORK, GarmentSlots.FINISHING, 0.18, FINISHING_FLOOR, OPERATOR_DESK),
        stage("QC_FINISHING", "QC Finishing", "QC", TEAL, WORK, GarmentSlots.QUALITY_CONTROL, 0.08, FINISHING_FLOOR, OPERATOR_DESK),
        stage("PENGEMASAN", "Pengemasan", "Kemas", TEAL, WORK, GarmentSlots.FULFILLMENT, 0.04, FINISHING_FLOOR, OPERATOR_DESK)
    ) + EXIT_ANCHORS

    // Sablon tidak punya peran SEWING: tidak ada setoran perakitan maupun jalur makloon rakit.
    // Mesin sablonnya CUSTOM_EXTENSION, persis seperti tabel archetype module-integration-rules.
    private val SCREEN_PRINT: List<StageDefinition> = ENTRY_ANCHORS + listOf(
        stage("COLOR_SEPARATION", "Separasi Warna & Film", "Separasi", BLUE, WORK, GarmentSlots.PRODUCT_ENGINEERING, 0.85),
        stage("SCREEN_EXPOSURE", "Afdruk Screen", "Afdruk", BLUE, WORK, GarmentSlots.PRODUCT_ENGINEERING, 0.70),
        stage("SCREEN_PRINTING", "Sablon", "Sablon", AMBER, WORK, GarmentSlots.CUSTOM_EXTENSION, 0.40, OPERATOR_DESK),
        stage("CURING", "Curing / Pengeringan", "Curing", TEAL, WORK, GarmentSlots.FINISHING, 0.20, FINISHING_FLOOR, WET_OR_PRESS, OPERATOR_DESK),
        stage("QC_FINISHING", "QC Finishing", "QC", TEAL, WORK, GarmentSlots.QUALITY_CONTROL, 0.08, FINISHING_FLOOR, OPERATOR_DESK),
        stage("PENGEMASAN", "Pengemasan", "Kemas", TEAL, WORK, GarmentSlots.FULFILLMENT, 0.04, FINISHING_FLOOR, OPERATOR_DESK)
    ) + EXIT_ANCHORS

    fun stagesOf(template: IndustryTemplateCode): List<StageDefinition> = when (template) {
        IndustryTemplateCode.KNIT_SWEATER -> KNIT_SWEATER
        IndustryTemplateCode.CUT_AND_SEW -> CUT_AND_SEW
        IndustryTemplateCode.EMBROIDERY -> EMBROIDERY
        IndustryTemplateCode.SCREEN_PRINT -> SCREEN_PRINT
    }

    /** Semua kode tahap yang dikenal template mana pun — dasar validasi kode tersimpan tanpa kerangka tenant. */
    val knownCodes: Set<StageCode> by lazy { IndustryTemplateCode.entries.flatMap { stagesOf(it) }.mapTo(mutableSetOf()) { it.code } }

    fun instantiate(tenantId: TenantId, template: IndustryTemplateCode): TenantStageFlow =
        TenantStageFlow(tenantId, template, stagesOf(template))
}
