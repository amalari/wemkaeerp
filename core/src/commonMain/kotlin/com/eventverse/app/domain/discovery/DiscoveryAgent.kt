package com.eventverse.app.domain.discovery

/**
 * Permintaan discovery: narasi prospek apa adanya (verbatim, tidak dinormalkan) + petunjuk industri opsional.
 * [industryHint] boleh kosong — agent yang menentukan kosakata dari narasi; ia **tidak pernah** menentukan
 * kode pack dari teks bebas tanpa aturan slug ketat.
 */
data class DiscoveryRequest(val narrative: String, val industryHint: String? = null, val displayName: String? = null) {
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
