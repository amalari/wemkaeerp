package com.eventverse.app.domain.crm.prefill

/**
 * Ekstraksi tanpa LLM (FR-6): hanya pola yang pasti — placeholder telepon/email dari [PiiMasker] dan angka
 * yang diikuti "pcs"/"potong"/"lusin". Nama, brand, dan kategori sengaja tidak ditebak.
 */
class DeterministicLeadDraftExtractor : LeadDraftExtractor {
    override val agentRef: String = "deterministic/lead-draft-v1"

    private val phone = Regex("""\{TELP_\d+}""")
    private val email = Regex("""\{EMAIL_\d+}""")
    private val qty = Regex("""(\d{1,3}(?:[.,]\d{3})+|\d+)\s*(pcs|pc|potong|lusin|dozen)\b""", RegexOption.IGNORE_CASE)

    override suspend fun extract(maskedText: String, fields: List<DraftFieldSpec>): Result<Map<String, String>> = runCatching {
        buildMap {
            phone.find(maskedText)?.let { put(LeadDraftFields.WHATSAPP, it.value) }
            email.find(maskedText)?.let { put(LeadDraftFields.EMAIL, it.value) }
            qty.find(maskedText)?.let { m ->
                val n = m.groupValues[1].replace(".", "").replace(",", "").toLongOrNull() ?: return@let
                val pcs = if (m.groupValues[2].lowercase() in setOf("lusin", "dozen")) n * 12 else n
                if (pcs <= Int.MAX_VALUE) put(LeadDraftFields.ESTIMATED_PCS, pcs.toString())
            }
        }
    }
}
