package com.eventverse.app.domain.crm.prefill.usecases

import com.eventverse.app.domain.crm.prefill.DeterministicLeadDraftExtractor
import com.eventverse.app.domain.crm.prefill.LeadDraft
import com.eventverse.app.domain.crm.prefill.LeadDraftExtractor
import com.eventverse.app.domain.crm.prefill.LeadDraftSanitizer
import com.eventverse.app.domain.crm.prefill.PiiMasker
import com.eventverse.app.domain.customfield.CustomFieldDefinition

/**
 * Teks bebas → draf form lead (TRD-HELP-002 FR-1..FR-6). Urutan yang tidak boleh ditukar:
 * **samarkan → ekstrak → kembalikan → validasi**. Ekstraktor (LLM) hanya pernah melihat teks tersamar; nilai asli
 * dikembalikan di sini, lalu setiap nilai melewati [LeadDraftSanitizer]. Use case ini tidak menyimpan apa pun.
 *
 * Wewenang (CRM OPERATE) dan opt-in tenant diperiksa route **sebelum** use case dipanggil.
 */
class ExtractLeadDraftUseCase(
    private val extractor: LeadDraftExtractor,
    private val fallback: LeadDraftExtractor = DeterministicLeadDraftExtractor(),
) {
    suspend operator fun invoke(text: String, definitions: List<CustomFieldDefinition>): Result<LeadDraft> = runCatching {
        val input = text.trim()
        require(input.isNotEmpty()) { "Teks kosong" }
        require(input.length <= MAX_TEXT_LENGTH) { "Teks melebihi $MAX_TEXT_LENGTH karakter" }

        val masked = PiiMasker.mask(input)
        val specs = LeadDraftSanitizer.specsFor(definitions)
        val primary = extractor.extract(masked.text, specs).getOrNull()
        val (raw, used) = if (primary != null) primary to extractor else fallback.extract(masked.text, specs).getOrThrow() to fallback

        val restored = raw.mapValues { (_, v) -> masked.unmask(v) }
            // Placeholder yang tersisa setelah dikembalikan = karangan ekstraktor, bukan nilai dari teks.
            .filterValues { v -> !LEFTOVER_PLACEHOLDER.containsMatchIn(v) }
        LeadDraftSanitizer.sanitize(restored, definitions, used.agentRef, partial = used !== extractor)
    }

    companion object {
        const val MAX_TEXT_LENGTH = 4000
        private val LEFTOVER_PLACEHOLDER = Regex("""\{(TELP|EMAIL)_\d+}""")
    }
}
