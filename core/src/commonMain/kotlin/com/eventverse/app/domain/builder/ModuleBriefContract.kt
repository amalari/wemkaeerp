package com.eventverse.app.domain.builder

import com.eventverse.app.domain.tenant.TenantId

/** Satu temuan validator brief; [path] menunjuk bagian yang salah (mis. `stages[2].reworkTo`) agar umpan balik ke model berpath. */
data class BriefIssue(val path: String, val message: String)

/**
 * Validator keluaran [ModuleBriefer] (aturan TRD-BLDR-001 FR-4, angka dari [ModuleBriefLimits]). Murni, tanpa I/O.
 * Daftar kosong = sah. Implementasi: Track A. Kontrak ini dibekukan agar use case dan tes server memakai fake.
 */
fun interface ModuleBriefValidator {
    fun validate(brief: ModuleBrief): List<BriefIssue>
}

/** Hasil pembuatan brief satu modul. [source] `null` bila tidak ada brief baru (FRESH/SKIPPED). */
enum class BriefStatus { CREATED, FRESH, SKIPPED }

data class ModuleBriefOutcome(val moduleId: String, val status: BriefStatus, val source: BriefSource? = null) {
    init { require((status == BriefStatus.CREATED) == (source != null)) { "source hanya untuk status CREATED" } }
}

/**
 * Layanan pembuat brief — yang dipanggil rute `POST /api/builder/chat/briefs` dan pemicu setelah rencana wawancara.
 * [moduleId] `null` = semua modul operasional aktif. Idempoten (FR-7). Kegagalan satu modul tidak menggagalkan
 * modul lain; kegagalan AI menghasilkan CREATED dengan [BriefSource.DETERMINISTIC], bukan galat. Implementasi: Track A.
 */
interface ModuleBriefService {
    suspend fun generate(tenantId: TenantId, moduleId: String?): Result<List<ModuleBriefOutcome>>
}

/**
 * Kunci stabil brief. Dibekukan di kontrak karena menentukan idempotensi (FR-7) dan `Clarification.id` follow-up AI;
 * mengubah rumusnya membuat brief/pertanyaan lama dianggap baru.
 */
object BriefKeys {
    /** Id follow-up AI: stabil per (modul, teks pertanyaan ternormalisasi) → pertanyaan yang sama tidak ditanyakan ulang. */
    fun followUpId(moduleId: String, question: String): String = "$AI_ID_PREFIX$moduleId:${fingerprint(question)}"

    /** Awalan id pertanyaan follow-up buatan AI (lawan: celah template [ModuleGapAnalyzer.ID_PREFIX]). */
    const val AI_ID_PREFIX = "ai:"

    fun isAiQuestionId(id: String): Boolean = id.startsWith(AI_ID_PREFIX)

    /** Basis brief: berubah bila narasi, fitur tautan, atau versi agent berubah. */
    fun basisRef(narrative: String, roleFeatures: List<String>, agentRef: String): String =
        "b1:" + fingerprint(listOf(narrative, roleFeatures.joinToString("\u001F"), agentRef).joinToString("\u001E"))

    /** Normalisasi: huruf kecil, spasi beruntun dan tepi dirapikan. Sama-sama dipakai dedup pertanyaan (FR-4). */
    fun normalize(text: String): String = text.trim().lowercase().replace(Regex("\\s+"), " ")

    /** FNV-1a 64-bit atas teks ternormalisasi, heksadesimal 16 digit — murni Kotlin, identik di kelima target. */
    fun fingerprint(text: String): String {
        var h = -0x340d631b7bdddcdbL // 0xcbf29ce484222325
        for (b in normalize(text).encodeToByteArray()) {
            h = (h xor (b.toLong() and 0xFF)) * 0x100000001b3L
        }
        return h.toULong().toString(16).padStart(16, '0')
    }
}
