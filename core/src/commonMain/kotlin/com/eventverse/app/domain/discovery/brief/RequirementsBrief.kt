package com.eventverse.app.domain.discovery.brief

import com.eventverse.app.domain.prototype.SpecOp

/** Satu entri log perubahan dari sesi prototype (kontrak v1). [at] = waktu ISO-8601 dari pemanggil. */
data class CaptureEntry(val at: String, val op: SpecOp, val ok: Boolean, val message: String?)

data class BriefField(val label: String, val type: String, val required: Boolean, val options: List<String>)

data class BriefEntity(
    val id: String,
    val label: String,
    val fields: List<BriefField>,
    val statusField: String?,
    val transitions: Map<String, List<String>>
)

data class BriefScreen(val title: String, val widget: String, val entityId: String?)

data class BriefModule(val moduleId: String, val displayName: String, val screens: List<BriefScreen>, val entities: List<BriefEntity>)

/** Per modul: sudah ada di katalog ([covered], [monthlyIdr]) atau perlu dibangun (rentang bulanan). */
data class BriefCoverage(
    val moduleId: String,
    val displayName: String,
    val covered: Boolean,
    val monthlyIdr: Long?,
    val gapLowIdr: Long?,
    val gapHighIdr: Long?
)

/**
 * Revisi brief: brief ini **menggantikan** versi sebelumnya (deploy ulang saat permintaan belum selesai). [added]/[removed] =
 * baris isi yang berubah dibanding versi sebelumnya (tanpa judul), supaya developer langsung tahu apa yang berubah.
 */
data class BriefRevision(
    val version: Int,
    val supersedes: String,
    val previousStatus: String,
    val added: List<String>,
    val removed: List<String>
)

/** Satu tanya-jawab klarifikasi. [moduleId] null = pertanyaan tingkat alur penuh. */
data class BriefQa(val moduleId: String?, val question: String, val answer: String)

/** Satu keputusan yang **sudah diterapkan** dari chat Builder: ringkasan perubahan beserta waktunya (ISO-8601, bila tercatat). */
data class BriefDecision(val moduleId: String?, val at: String?, val summary: List<String>)

/** Pertanyaan yang **belum terjawab** saat brief dibuat — hal yang masih belum jelas bagi developer. */
data class BriefOpenQuestion(val moduleId: String?, val question: String)

/**
 * Konteks dari percakapan Builder (opsional): cerita asli, tanya-jawab, keputusan yang diterapkan, dan yang belum jelas.
 * Tanpa ini developer hanya melihat hasil akhir, bukan *mengapa* — lalu bertanya ulang apa yang sudah dijawab di chat.
 */
data class BriefContext(
    val narrative: String?,
    val answered: List<BriefQa>,
    val decisions: List<BriefDecision>,
    val open: List<BriefOpenQuestion>
) {
    val isEmpty: Boolean get() = narrative.isNullOrBlank() && answered.isEmpty() && decisions.isEmpty() && open.isEmpty()
}

/**
 * Ringkasan kebutuhan hasil sesi prototype — bahan kerja tim developer (kontrak v1). Data murni;
 * susunan teks ada di [BriefRenderer]. [customNeeds] = kebutuhan di luar blok standar (CUSTOM_EXTENSION).
 */
data class RequirementsBrief(
    val packCode: String,
    val modules: List<BriefModule>,
    val changes: List<CaptureEntry>,
    val coverage: List<BriefCoverage>,
    val customNeeds: List<String> = emptyList(),
    /** Konteks dari chat Builder; null = brief lama/tanpa chat (keluaran Markdown & JSON identik dengan sebelumnya). */
    val context: BriefContext? = null,
    /** Revisi atas brief sebelumnya; null = brief pertama (keluaran identik dengan sebelum fitur ini). */
    val revision: BriefRevision? = null
)
