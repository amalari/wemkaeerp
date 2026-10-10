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
        require(PATTERN.matches(value)) { "StageCode tidak valid: \"$value\" (A-Z, 0-9, _; 2-48 karakter)" }
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
    PHASE_TAGGABLE,

    /** Dikerjakan di meja operator lantai produksi (klaim "Mulai", serah terima, rework). */
    OPERATOR_DESK
}

/** Asal tahap: bawaan template industri, atau sisipan tenant. */
enum class StageOrigin { TEMPLATE, OPTIONAL }

/** Kerangka industri yang bisa dipakai tenant sebagai titik awal. */
enum class IndustryTemplateCode(val displayName: String) {
    KNIT_SWEATER("Rajut / Sweater"),
    CUT_AND_SEW("Konveksi Potong-Jahit"),
    EMBROIDERY("Bordir"),
    SCREEN_PRINT("Sablon");

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
    val executionMode: WorkExecutionMode = WorkExecutionMode.IN_HOUSE,
    /** Label ringkas untuk badge/chip yang sempit (mis. "CAM", "Rajut"). */
    val shortLabel: String = displayName,
    /**
     * Warna identitas tahap, ARGB. Data tenant, bukan keputusan desain — boleh dipakai langsung
     * sebagai `Color(colorHex)` di presentasi (design-system-rules Kontrak 1, pengecualian 1).
     */
    val colorHex: Long = DEFAULT_COLOR_HEX,
    /**
     * Fraksi pekerjaan yang masih tersisa saat SPK berada di tahap ini (1 = belum dikerjakan),
     * dipakai perhitungan urgensi (`SpkUrgency`). Default 1.0 sengaja konservatif: tahap tanpa
     * nilai membuat SPK tampak lebih genting, bukan diam-diam terlambat.
     */
    val remainingWorkFactor: Double = 1.0
) {
    init {
        require(displayName.isNotBlank()) { "Nama tahap ${code.value} tidak boleh kosong" }
        require(shortLabel.isNotBlank()) { "Label ringkas tahap ${code.value} tidak boleh kosong" }
        require(remainingWorkFactor in 0.0..1.0) { "Sisa kerja tahap ${code.value} harus 0..1" }
    }

    fun has(trait: StageTrait): Boolean = trait in traits

    companion object {
        /** Abu netral (slate-500) untuk tahap yang belum diberi warna oleh tenant. */
        const val DEFAULT_COLOR_HEX: Long = 0xFF64748B
    }
}
