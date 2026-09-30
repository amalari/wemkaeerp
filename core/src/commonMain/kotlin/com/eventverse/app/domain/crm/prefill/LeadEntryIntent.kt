package com.eventverse.app.domain.crm.prefill

/**
 * Apakah pesan chat adalah **permintaan input lead** ("catat lead PT Maju, WA 0812…"), bukan pertanyaan cara pakai
 * ("gimana cara bikin lead baru?"). Deterministik supaya pesan berisi data pelanggan tidak perlu dikirim ke LLM
 * hanya untuk mengklasifikasikannya.
 *
 * Aturannya sengaja ketat: kata kerja perintah + kata benda lead, **dan** ada data (telepon/email, atau teks cukup
 * panjang setelah perintah). Pertanyaan ("gimana", "cara", tanda tanya) tidak pernah dianggap perintah.
 */
object LeadEntryIntent {
    private val COMMAND = Regex("""^\s*(tolong\s+)?(catat|catatkan|tambah|tambahkan|buat|buatkan|bikin|bikinkan|input|masukkan|simpan)\b""", RegexOption.IGNORE_CASE)
    private val LEAD_NOUN = Regex("""\b(lead|prospek|calon\s+(pembeli|customer|pelanggan)|buyer|customer|pelanggan)\b""", RegexOption.IGNORE_CASE)
    private val QUESTION = Regex("""\b(gimana|bagaimana|cara|caranya|kenapa|dimana|di\s+mana|apakah)\b|\?""", RegexOption.IGNORE_CASE)
    private const val MIN_WORDS_WITHOUT_CONTACT = 6

    fun matches(text: String): Boolean {
        if (!COMMAND.containsMatchIn(text) || !LEAD_NOUN.containsMatchIn(text) || QUESTION.containsMatchIn(text)) return false
        val hasContact = PiiMasker.mask(text).placeholders.isNotEmpty()
        return hasContact || text.trim().split(Regex("\\s+")).size >= MIN_WORDS_WITHOUT_CONTACT
    }
}
