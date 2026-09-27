package com.eventverse.app.domain.sampling

import com.eventverse.app.domain.pipeline.DefectLiability
import kotlinx.datetime.Instant

/**
 * Satu baris input dinamis lembar kerja tahapan sampling.
 *
 * Sengaja *tidak* terikat ke field fix (gramasi/waktu/ukuran): setiap tahap punya kebutuhan
 * kolom yang berbeda dan tenant bisa menamai labelnya bebas ("DEPAN", "P BADAN", dst.).
 * Struktur label + value membuat form tahap manapun bisa dirakit tanpa mengubah skema domain.
 */
data class StageInputRow(
    val label: String,
    val value: String
) {
    val isFilled: Boolean get() = label.isNotBlank() && value.isNotBlank()
}

/** Satu section tabel di dialog tahap (mis. "GRAMASI", "INSTRUKSI PANAH"). */
data class StageInputSection(
    val section: String,
    val rows: List<StageInputRow>
) {
    val hasFilledRow: Boolean get() = rows.any { it.isFilled }

    companion object {
        fun of(section: String, vararg pairs: Pair<String, String>) =
            StageInputSection(section, pairs.map { StageInputRow(it.first, it.second) })
    }
}

/** Kumpulan section input milik satu tahap pipeline. */
data class StageWorkInput(
    val stage: SamplingPipelineStage,
    val sections: List<StageInputSection>
) {
    fun section(name: String): StageInputSection? = sections.firstOrNull { it.section == name }
}

/**
 * Jejak audit perpindahan tahap — "siapa yang memindahkan dan kapan".
 * Diisi server dari JWT (bukan dari body request) setiap kali POST /{id}/stage dijalankan.
 *
 * Kiriman balik rework dicatat di daftar yang sama, ditandai [liability] + [reason], bukan di
 * daftar terpisah: "SPK ini lewat mana saja" adalah satu garis waktu, dan memecahnya dua membuat
 * pembaca harus menjahit ulang urutannya sendiri.
 */
data class StageTransitionAudit(
    val fromStage: SamplingPipelineStage,
    val toStage: SamplingPipelineStage,
    val actorEmail: String,
    val actorRole: String,
    val at: Instant,
    /** Alasan rework; `null` untuk perpindahan maju biasa. */
    val reason: String? = null,
    /** Pihak penanggung cacat. Terisi = entri ini adalah kiriman balik rework. */
    val liability: DefectLiability? = null,
    /**
     * Kapan operator menekan "Mulai" di tahap asal, dan siapa. Disalin dari klaim saat SPK
     * pindah, karena klaimnya sendiri dilepas di saat yang sama — tanpa salinan ini jam mulai
     * hilang dan lama pengerjaan per bagian tidak bisa dihitung. `null` = tidak pernah diklaim.
     */
    val workStartedAt: Instant? = null,
    val operatorName: String? = null,
    /** Operator mengembalikan SPK ke antrian mejanya; tahap tidak berubah. */
    val isRelease: Boolean = false
) {
    val isRework: Boolean get() = liability != null
}

/**
 * Nama section baku yang dipakai dialog per transisi tahap. Nama ini adalah *kontrak gerbang*:
 * [SamplingOrder.advancePipelineStage] menolak CAM -> MACHINE_KNITTING bila section wajib
 * di bawah belum ada satu baris pun yang terisi.
 */
object StageSectionNames {
    const val PROGRAM = "PROGRAM"
    const val FEEDER_INSTRUCTIONS = "INSTRUKSI PANAH"
    const val PATTERN_FORMULAS = "RUMUS POLA"
    const val PANEL_WEIGHTS = "GRAMASI"
    const val PANEL_MINUTES = "WAKTU"
    const val SIZE_CHART = "DETAIL SIZE CHART"
    const val TENSELITY = "TENSELITY"
    const val FINISHED_MEASUREMENTS = "HASIL UKURAN JADI"

    /** Section wajib sebelum boleh masuk Mesin Rajut (gerbang CAM). */
    val CAM_REQUIRED: List<String> = listOf(PROGRAM, FEEDER_INSTRUCTIONS)

    /** Section inputan aktif saat lembar Rajut Mesin. */
    val KNITTING_INPUTS: List<String> = listOf(PANEL_WEIGHTS, PANEL_MINUTES, SIZE_CHART, TENSELITY)
}

/**
 * Transisi menuju tahap [SamplingPipelineStage] ini menuntut lembar kerja dinamis
 * diisi dulu sebelum boleh maju. Satu sumber kebenaran untuk UI (klik kartu / tombol
 * membuka dialog) maupun gerbang domain.
 *
 * Masuk Program CAM menuntut lembar Program CAM (program, instruksi panah, tenselity, rumus pola)
 * diisi oleh tim sampling sebelum kartu masuk ke tahap CAM.
 */
fun SamplingPipelineStage.requiresStageWorksheet(): Boolean =
    this == SamplingPipelineStage.CAM_PROGRAMMING ||
        this == SamplingPipelineStage.LINKING_ASSEMBLY

fun SamplingOrder.hasCompleteCamWorksheet(): Boolean {
    val camInput = stageInputFor(SamplingPipelineStage.CAM_PROGRAMMING) ?: return false
    return StageSectionNames.CAM_REQUIRED.all { name ->
        camInput.section(name)?.hasFilledRow == true
    }
}
