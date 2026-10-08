package com.eventverse.app.domain.prototype

/**
 * Aturan bentuk [TextValidation] — satu tempat murni supaya `FieldSpec.accepts`, validator usulan, `ProposalEdit`, dan
 * route hasil generate tidak bisa berbeda. Common Kotlin saja (tanpa `java.*`, tanpa `Regex`). Nilai kosong selalu sah
 * (belum diisi) dan ditangani pemanggil; di sini [isValid] hanya dipanggil untuk nilai tidak kosong, tapi tetap aman
 * untuk kosong ([TextValidation.NONE] sah, lainnya tidak).
 *
 * Aturan (nilai dinilai **apa adanya**, tanpa trim):
 * - [TextValidation.NONE]: apa saja.
 * - [TextValidation.EMAIL]: `lokal@domain.tld` — tanpa spasi/whitespace, tepat satu `@`, bagian lokal tidak kosong,
 *   domain berisi titik dan setiap label di antara titik tidak kosong, panjang total ≤ 254.
 * - [TextValidation.PHONE]: opsional awalan `+`, sisanya digit dengan pemisah opsional spasi, `-`, `(`, `)`; total
 *   **8–15 digit**.
 */
object TextValidations {
    const val EMAIL_MAX_LENGTH = 254
    const val PHONE_MIN_DIGITS = 8
    const val PHONE_MAX_DIGITS = 15
    private val PHONE_SEPARATORS = setOf(' ', '-', '(', ')')

    fun isValid(validation: TextValidation, value: String): Boolean = when (validation) {
        TextValidation.NONE -> true
        TextValidation.EMAIL -> isEmail(value)
        TextValidation.PHONE -> isPhone(value)
    }

    /** Contoh nilai sah untuk seed/penggantian nilai. */
    fun sample(validation: TextValidation): String = when (validation) {
        TextValidation.NONE -> "contoh"
        TextValidation.EMAIL -> "contoh@contoh.id"
        TextValidation.PHONE -> "+628123456789"
    }

    private fun isEmail(value: String): Boolean {
        if (value.isEmpty() || value.length > EMAIL_MAX_LENGTH || value.any { it.isWhitespace() }) return false
        val at = value.indexOf('@')
        if (at <= 0 || at != value.lastIndexOf('@')) return false
        val domain = value.substring(at + 1)
        return '.' in domain && domain.split('.').all { it.isNotEmpty() }
    }

    /** Hanya 0-9 ASCII; `Char.isDigit()` juga meloloskan digit Unicode lain. */
    private fun isAsciiDigit(c: Char): Boolean = c in '0'..'9'

    private fun isPhone(value: String): Boolean {
        val body = if (value.startsWith('+')) value.substring(1) else value
        if (body.any { !isAsciiDigit(it) && it !in PHONE_SEPARATORS }) return false
        return body.count(::isAsciiDigit) in PHONE_MIN_DIGITS..PHONE_MAX_DIGITS
    }
}
