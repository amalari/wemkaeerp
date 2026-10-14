package com.eventverse.app.domain.builder

import com.eventverse.app.domain.discovery.interview.Clarification
import com.eventverse.app.domain.tenant.TenantId

/**
 * Asal sebuah brief modul. Enum sah (Uji Variabilitas): mekanik platform — apakah teksnya ditulis model atau dirakit
 * dari data draf — bukan kosakata industri. Ditampilkan ke pengguna supaya fallback tidak menyamar sebagai AI.
 */
enum class BriefSource { AI, DETERMINISTIC }

/**
 * Satu langkah kerja dalam sketsa alur sebuah modul (TRD-BLDR-001). **Deskriptif**, bukan agregat persisten:
 * `TenantStageFlow` terikat alur sampling, jadi tidak dipakai ulang untuk pack hasil generate.
 *
 * [reworkTo] = nama tahap **sebelumnya** tempat dokumen kembali bila ditolak (loop rework); `null` = tidak ada loop.
 * Aturan antar-tahap (urutan, keunikan, batas jumlah) ditegakkan validator (Track A) memakai [ModuleBriefLimits].
 */
data class StageSketch(val name: String, val optional: Boolean = false, val reworkTo: String? = null)

/** Batas keluaran brief (FR-4). Satu sumber angka untuk validator, prompt, dan klien. */
object ModuleBriefLimits {
    const val SUMMARY_MAX_CHARS = 600
    const val FEATURES_MAX = 8
    const val FEATURE_MAX_CHARS = 80
    const val STAGES_MAX = 7
    const val STAGE_NAME_MAX_CHARS = 40
    const val FOLLOW_UPS_MAX = 3
    const val FOLLOW_UP_MAX_CHARS = 160
}

/** Hasil satu brief modul — keluaran model **tidak tepercaya** sampai lolos validator (Track A). */
data class ModuleBrief(
    val summary: String,
    val features: List<String> = emptyList(),
    val stages: List<StageSketch> = emptyList(),
    val followUps: List<String> = emptyList(),
    val source: BriefSource
)

/**
 * Masukan satu brief modul. Semua teks adalah **data** (narasi pengguna, label modul/peran dari pack tenant) — bukan
 * instruksi. [alreadyAsked] = pertanyaan yang sudah ada di utas modul (dijawab atau belum) supaya tidak diulang.
 * [feedback] terisi pada percobaan ulang (galat validator sebelumnya, berpath).
 */
data class ModuleBriefRequest(
    val tenantId: TenantId,
    val moduleId: String,
    val moduleName: String,
    val narrative: String,
    /** Peran dan fitur dari `RoleModuleLink` modul ini (label pengguna); boleh kosong. */
    val roleFeatures: List<String> = emptyList(),
    val slotInput: String? = null,
    val slotOutput: String? = null,
    /** Label isian layar modul yang sudah diusulkan; boleh kosong. */
    val screenFieldLabels: List<String> = emptyList(),
    val alreadyAsked: List<Clarification> = emptyList(),
    val feedback: String? = null
)

/**
 * Penyusun brief per modul (model kecil di server; fallback deterministik di balik interface yang sama — pola
 * [ModuleEditor]). Hanya **mengusulkan**: use case memvalidasi dan menyimpan; model tidak pernah menulis draf.
 */
fun interface ModuleBriefer {
    suspend fun brief(request: ModuleBriefRequest): Result<ModuleBrief>
}
