package com.eventverse.app.domain.stageflow

import com.eventverse.app.domain.pipeline.ModuleArchetype
import com.eventverse.app.domain.workqueue.WorkExecutionMode
import kotlin.jvm.JvmInline

/**
 * Kode stabil satu tahap alur. Dipakai sebagai key tersimpan (`current_stage`,
 * `FlowNodeRef` `STAGE:<code>`), jadi **tidak pernah** berubah setelah dibuat — yang boleh
 * diganti tenant hanya [StageDefinition.displayName].
 *
 * Huruf besar + underscore supaya kode template rajut identik dengan nama enum
 * `SamplingPipelineStage` lama: data yang sudah tersimpan terbaca tanpa migrasi.
 */
@JvmInline
value class StageCode(val value: String) {
    init {
        require(PATTERN.matches(value)) { "StageCode tidak valid: \"$value\" (A-Z, 0-9, _; 2–48 karakter)" }
    }

    override fun toString(): String = value

    companion object {
        private val PATTERN = Regex("^[A-Z][A-Z0-9_]{1,47}$")

        fun parseOrNull(raw: String?): StageCode? =
            raw?.takeIf { PATTERN.matches(it) }?.let(::StageCode)
    }
}

/**
 * Posisi struktural tahap. Anchor masuk/keluar ada di setiap template karena invoice, surat
 * jalan, custody penyimpanan, dan traceability bergantung padanya; hanya [WORK] yang bebas
 * disusun tenant.
 */
enum class StageKind { ENTRY_ANCHOR, WORK, EXIT_ANCHOR }

/**
 * Arti khusus sebuah tahap, dinyatakan sebagai label alih-alih rentang urutan — supaya tahap
 * yang disisipkan tenant ikut terhitung tanpa ada `when` yang lupa diperbarui.
 */
enum class StageTrait {
    /** Barangnya ada di lantai penyelesaian akhir (antrean meja finishing). */
    FINISHING_FLOOR,

    /** Kerja basah/panas: barang sedang dikerjakan, belum layak diperiksa. */
    WET_OR_PRESS,

    /** Boleh dipilah per fase Sampling / Produksi (tag `[Sampling ×] [Produksi ×]`). */
    PHASE_TAGGABLE
}

/** Asal tahap: bawaan template industri, atau sisipan tenant. */
enum class StageOrigin { TEMPLATE, OPTIONAL }

/** Kerangka industri yang bisa dipakai tenant sebagai titik awal. */
enum class IndustryTemplateCode(val displayName: String) {
    KNIT_SWEATER("Rajut / Sweater");

    companion object {
        fun parseOrNull(raw: String?): IndustryTemplateCode? = entries.firstOrNull { it.name == raw }
    }
}

/** Satu tahap pada kerangka alur tenant. */
data class StageDefinition(
    val code: StageCode,
    val displayName: String,
    val kind: StageKind,
    val archetype: ModuleArchetype,
    val traits: Set<StageTrait> = emptySet(),
    val origin: StageOrigin = StageOrigin.TEMPLATE,
    val executionMode: WorkExecutionMode = WorkExecutionMode.IN_HOUSE
) {
    init {
        require(displayName.isNotBlank()) { "Nama tahap ${code.value} tidak boleh kosong" }
    }

    fun has(trait: StageTrait): Boolean = trait in traits
}
