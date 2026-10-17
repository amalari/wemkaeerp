package com.eventverse.app.domain.builder

import com.eventverse.app.domain.discovery.interview.Clarification
import com.eventverse.app.domain.evidence.EvidenceRef
import com.eventverse.app.domain.tenant.TenantId

/**
 * Asal sebuah brief modul. Enum sah (Uji Variabilitas): mekanik platform — apakah teksnya ditulis model atau dirakit
 * dari data draf — bukan kosakata industri. Ditampilkan ke pengguna supaya fallback tidak menyamar sebagai AI.
 */
enum class BriefSource { AI, DETERMINISTIC }

/**
 * Satu butir fitur pelengkap brief (TRD-BLDR-003 FR-3): teks + bukti asalnya. [evidence] `null` hanya sah pada
 * payload **legacy v1** yang sedang dibaca — keluaran kontrak v2 wajib berevidence (ditegakkan validator, bukan
 * konstruktor, supaya keluaran agent bisa dikoreksi dengan umpan balik berpath).
 */
data class BriefFeature(val text: String, val evidence: EvidenceRef? = null) {
    init { require(text.isNotBlank()) { "BriefFeature.text kosong" } }
}

/**
 * Satu langkah kerja dalam sketsa alur sebuah modul (TRD-BLDR-001). **Deskriptif**, bukan agregat persisten:
 * `TenantStageFlow` terikat alur sampling, jadi tidak dipakai ulang untuk pack hasil generate.
 *
 * [reworkTo] = nama tahap **sebelumnya** tempat dokumen kembali bila ditolak (loop rework); `null` = tidak ada loop.
 * [evidence] = bukti asal tahap (TRD-BLDR-003 FR-3.2); `null` hanya sah pada payload legacy v1.
 * Aturan antar-tahap (urutan, keunikan, batas jumlah) ditegakkan validator (Track A) memakai [ModuleBriefLimits].
 */
data class StageSketch(
    val name: String,
    val optional: Boolean = false,
    val reworkTo: String? = null,
    val evidence: EvidenceRef? = null,
) {
    init { require(name.isNotBlank()) { "StageSketch.name kosong" } }
}

/** Batas keluaran brief (FR-4). Satu sumber angka untuk validator, prompt, dan klien. */
object ModuleBriefLimits {
    const val SUMMARY_MAX_CHARS = 600
    const val FEATURES_MAX = 8
    const val FEATURE_MAX_CHARS = 80
    const val STAGES_MAX = 7
    const val STAGE_NAME_MAX_CHARS = 40
    const val FOLLOW_UPS_MAX = 3
    const val FOLLOW_UP_MAX_CHARS = 160
    /** Kontrak payload: 1 = legacy (fitur berupa teks, tanpa evidence), 2 = butir bertipe berevidence (FR-6). */
    const val CONTRACT_VERSION_LEGACY = 1
    const val CONTRACT_VERSION_EVIDENCE = 2
}

/**
 * Hasil satu brief modul — keluaran model **tidak tepercaya** sampai lolos validator (Track A).
 * [contractVersion] menentukan kewajiban evidence: v2 (AI/DETERMINISTIC) wajib berevidence di setiap fitur dan
 * tahap; v1 = payload legacy yang dibaca apa adanya tanpa diminta bukti (FR-3.3, FR-6).
 */
data class ModuleBrief(
    val summary: String,
    val features: List<BriefFeature> = emptyList(),
    val stages: List<StageSketch> = emptyList(),
    val followUps: List<String> = emptyList(),
    val source: BriefSource,
    val contractVersion: Int = ModuleBriefLimits.CONTRACT_VERSION_LEGACY,
) {
    init {
        require(contractVersion == ModuleBriefLimits.CONTRACT_VERSION_LEGACY || contractVersion == ModuleBriefLimits.CONTRACT_VERSION_EVIDENCE) {
            "contractVersion tak dikenal: $contractVersion"
        }
    }
}

/**
 * Peran + fitur tautan modul sebagai masukan brief (TRD-BLDR-003 FR-3.4): label TETAP membawa [evidence] asalnya
 * sehingga fallback deterministik mewarisi provenance, tidak diturunkan jadi string sebelum dipakai.
 */
data class RoleFeature(val label: String, val evidence: EvidenceRef? = null) {
    init { require(label.isNotBlank()) { "RoleFeature.label kosong" } }
}

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
    /** Peran dan fitur dari `RoleModuleLink` modul ini, membawa bukti asalnya (FR-3.4); boleh kosong. */
    val roleFeatures: List<RoleFeature> = emptyList(),
    val slotInput: String? = null,
    val slotOutput: String? = null,
    /** Label isian layar modul yang sudah diusulkan; boleh kosong. */
    val screenFieldLabels: List<String> = emptyList(),
    val alreadyAsked: List<Clarification> = emptyList(),
    val feedback: String? = null,
    /**
     * Batas lingkup (temuan cek visual 2026-10-16): narasi menceritakan SELURUH usaha, jadi tanpa batas ini ringkasan satu modul
     * memuat langkah modul lain. [upstreamModules]/[downstreamModules] = nama modul yang menyerahkan ke / menerima dari modul
     * ini (serah-terima wawancara); [otherModules] = nama modul operasional aktif lainnya. Semuanya data tenant; boleh kosong.
     */
    val upstreamModules: List<String> = emptyList(),
    val downstreamModules: List<String> = emptyList(),
    val otherModules: List<String> = emptyList()
)

/**
 * Penyusun brief per modul (model kecil di server; fallback deterministik di balik interface yang sama — pola
 * [ModuleEditor]). Hanya **mengusulkan**: use case memvalidasi dan menyimpan; model tidak pernah menulis draf.
 */
fun interface ModuleBriefer {
    suspend fun brief(request: ModuleBriefRequest): Result<ModuleBrief>
}
