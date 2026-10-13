package com.eventverse.app.domain.discovery

import com.eventverse.app.domain.i18n.LanguageCode

/**
 * Permintaan discovery: narasi prospek apa adanya (verbatim, tidak dinormalkan) + petunjuk industri opsional.
 * [industryHint] boleh kosong — agent yang menentukan kosakata dari narasi; ia **tidak pernah** menentukan
 * kode pack dari teks bebas tanpa aturan slug ketat.
 *
 * [language] (PLAN-i18n Fase 1, B5) = bahasa **keluaran** teks yang dilihat pengguna (nama modul, label
 * field, status, rationale). Bawaan `id` menjaga paritas dengan sebelum fitur ini ada; struktur/kunci JSON
 * tidak ikut berubah.
 */
data class DiscoveryRequest(
    val narrative: String,
    val industryHint: String? = null,
    val displayName: String? = null,
    val language: LanguageCode = LanguageCode.ID
) {
    init { require(narrative.isNotBlank()) { "Narasi discovery tidak boleh kosong" } }
}

/**
 * Port generator draf (plan §1, D4): implementasi LLM (Koog, A8) dan fallback deterministik berada di belakang
 * interface yang sama, sehingga route & use case tidak tahu bedanya.
 *
 * Seperti `FlowTranslator` di domain prospek: implementasi **diasumsikan kadang salah** — keluarannya draf
 * mentah yang wajib melewati [DiscoveryDraftValidator] sebelum disimpan. Interface ini murni domain: tanpa
 * framework, tanpa HTTP, dapat diuji dengan stub deterministik.
 */
interface DiscoveryAgent {
    /** `'<agent>/<versi>'`, dicatat pada setiap draf yang dihasilkannya (jejak audit, pola `translatorRef`). */
    val agentRef: String

    suspend fun draft(request: DiscoveryRequest): Result<DiscoveryDraft>
}
