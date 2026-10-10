package com.eventverse.app.presentation.discovery.fields

import com.eventverse.app.domain.prototype.FieldSpec
import com.eventverse.app.domain.prototype.FieldType
import com.eventverse.app.domain.prototype.MultiSelectValues
import com.eventverse.app.domain.prototype.NumberFormat
import com.eventverse.app.presentation.common.fileRefDisplayName
import com.eventverse.app.presentation.designsystem.displayIsoDateTime

/**
 * Logika tampil & masukan angka berformat (Irisan 2, C4) sebagai fungsi murni common Kotlin
 * (tanpa `java.text`/`Locale`, agar identik di JVM/Android/Wasm/JS).
 *
 * **Tanda tangan simpan TIDAK berubah**: string angka polos, titik desimal, tanpa pemisah ribuan/simbol
 * (`"12000"`, `"12.5"`); PERCENT disimpan apa adanya (`12.5` = 12,5%). Hanya tampilan & parsing masukan yang beda.
 *
 * Tampilan (UI berbahasa Indonesia): ribuan `.`, desimal `,`; CURRENCY `Rp 12.000` untuk IDR, kode lain diawali
 * kodenya (`USD 1.500`) — hanya ASCII karena font Nunito tak punya glyph simbol non-ASCII; PERCENT `12,5 %`.
 * Desimal yang ada ditampilkan apa adanya (maks 4 sesuai `NUMERIC(18,4)`); hanya nol di ekor pecahan yang dibuang
 * (`12000.0000` -> `12.000`), tidak ada pembulatan dan tidak dipaksa dua desimal.
 * PLAIN tidak pernah diubah (paritas dengan perilaku sebelum C4).
 */

private const val IDR = "IDR"
private val GROUPED_INTEGER = Regex("""\d{1,3}(\.\d{3})+""")
private val TYPING = Regex("""-?\d*[.,]?\d*""")
private const val MAX_FRACTION_DIGITS = 4

/** Prefix/suffix yang menyertai kontrol masukan; bagian dari kontrol, bukan dari nilai simpan. */
data class NumberAffix(val prefix: String = "", val suffix: String = "")

fun numberAffix(format: NumberFormat, currencyCode: String?): NumberAffix = when (format) {
    NumberFormat.PLAIN -> NumberAffix()
    NumberFormat.CURRENCY -> NumberAffix(prefix = if (currencyCode == null || currencyCode == IDR) "Rp" else currencyCode)
    NumberFormat.PERCENT -> NumberAffix(suffix = "%")
}

private data class Parts(val negative: Boolean, val integer: String, val fraction: String)

/** Memecah string simpan kanonik; null bila bukan angka polos (data lama). */
private fun splitStored(stored: String): Parts? {
    val negative = stored.startsWith("-")
    val body = if (negative) stored.substring(1) else stored
    val integer = body.substringBefore('.')
    val hasDot = '.' in body
    val fraction = if (hasDot) body.substringAfter('.') else ""
    if (integer.isEmpty() || !integer.all { it in '0'..'9' }) return null
    if (hasDot && (fraction.isEmpty() || !fraction.all { it in '0'..'9' })) return null
    return Parts(negative, integer, fraction)
}

private fun groupThousands(digits: String): String {
    val sb = StringBuilder()
    digits.forEachIndexed { i, c ->
        if (i > 0 && (digits.length - i) % 3 == 0) sb.append('.')
        sb.append(c)
    }
    return sb.toString()
}

/** Nilai simpan -> teks tampil. Kosong -> kosong; bukan angka (data lama) -> apa adanya; PLAIN tidak diubah. */
fun formatNumberForDisplay(stored: String, format: NumberFormat, currencyCode: String?): String {
    if (stored.isBlank()) return ""
    if (format == NumberFormat.PLAIN) return stored
    val parts = splitStored(stored.trim()) ?: return stored
    val fraction = parts.fraction.trimEnd('0')
    val number = groupThousands(parts.integer.trimStart('0').ifEmpty { "0" }) +
        (if (fraction.isEmpty()) "" else ",$fraction")
    val sign = if (parts.negative) "-" else ""
    val affix = numberAffix(format, currencyCode)
    return when (format) {
        NumberFormat.CURRENCY -> if (affix.prefix.isEmpty()) "$sign$number" else "$sign${affix.prefix} $number"
        NumberFormat.PERCENT -> "$sign$number ${affix.suffix}"
        NumberFormat.PLAIN -> stored
    }
}

/**
 * Teks lengkap (mis. tempelan "Rp 12.000" atau "12,5 %") -> string simpan; null bila bukan angka sah.
 * Aturan: bila ada `,` maka `,` = desimal dan `.` = ribuan; tanpa `,`, `.` dibaca ribuan hanya bila pola `1.234.567`
 * (kelompok tiga digit), selain itu desimal (`12.5`). Kosong -> "". Pecahan maks 4 digit (tolak, bukan bulatkan).
 */
fun parseNumberInput(text: String, format: NumberFormat): String? {
    if (text.isBlank()) return ""
    val cleaned = text.filterNot { it.isWhitespace() || it.isLetter() || it == '%' }
    val negative = cleaned.startsWith("-")
    val body = if (negative) cleaned.substring(1) else cleaned
    if (body.isEmpty() || !body.all { it in '0'..'9' || it == '.' || it == ',' }) return null
    val integerPart: String
    val fractionPart: String
    when {
        ',' in body -> {
            if (body.count { it == ',' } != 1) return null
            val left = body.substringBefore(',')
            if (left.isEmpty() || !(left.all { it in '0'..'9' } || GROUPED_INTEGER.matches(left))) return null
            integerPart = left.replace(".", "")
            fractionPart = body.substringAfter(',')
        }
        GROUPED_INTEGER.matches(body) -> {
            integerPart = body.replace(".", "")
            fractionPart = ""
        }
        body.count { it == '.' } > 1 -> return null
        else -> {
            integerPart = body.substringBefore('.')
            fractionPart = if ('.' in body) body.substringAfter('.') else ""
            if ('.' in body && fractionPart.isEmpty()) return null
        }
    }
    if (integerPart.isEmpty() || !integerPart.all { it in '0'..'9' }) return null
    if (!fractionPart.all { it in '0'..'9' } || fractionPart.length > MAX_FRACTION_DIGITS) return null
    val integer = integerPart.trimStart('0').ifEmpty { "0" }
    val isZero = integer == "0" && fractionPart.all { it == '0' }
    val sign = if (negative && !isZero) "-" else ""
    return sign + integer + (if (fractionPart.isEmpty()) "" else ".$fractionPart")
}

/**
 * Masukan saat mengetik: terima keadaan antara ("", "-", "12.", ",5"); `,` dan `.` sama-sama dianggap desimal,
 * maks 4 digit pecahan. Teks bergrup/berawalan hasil tempel diurai lewat [parseNumberInput]. null = tolak ketikan.
 */
fun normalizeNumberTyping(input: String, format: NumberFormat): String? {
    if (input.isEmpty()) return ""
    if (TYPING.matches(input)) {
        val stored = input.replace(',', '.')
        val fraction = if ('.' in stored) stored.substringAfter('.') else ""
        return if (fraction.length > MAX_FRACTION_DIGITS) null else stored
    }
    return parseNumberInput(input, format)
}

/** Teks tampil sebuah nilai menurut spesifikasi field; NUMBER diformat, DATE withTime diberi spasi (JJ:MM), sisanya apa adanya. */
fun FieldSpec.displayValue(stored: String): String = when (type) {
    FieldType.NUMBER -> formatNumberForDisplay(stored, format, currencyCode)
    FieldType.DATE -> if (withTime) displayIsoDateTime(stored) else stored
    // C7 Track C: resolusi label rujukan ditangani `presentation/relation` (relationDisplay) di
    // call site (tabel/kanban); di sini nilai tersimpan apa adanya (fallback id).
    // C8 Track C: FILE tampil sebagai nama berkasnya saja (segmen terakhir ref) — bukan path `fields/...`.
    // C6: TIME tampil apa adanya — bentuk simpan `JJ:MM` memang bentuk tampilnya (pola DATE tanpa withTime).
    FieldType.TEXT, FieldType.LONG_TEXT, FieldType.TIME, FieldType.ENUM, FieldType.BOOL, FieldType.RELATION -> stored
    // C (TRD-FIELD-003): tampil daftar label dipisah ", " (dari array JSON kanonik); belum ada pilihan -> "—";
    // nilai tak sah (bukan array JSON) ditampilkan apa adanya, tidak disembunyikan (pola DATE).
    FieldType.MULTI_SELECT -> MultiSelectValues.parse(stored)?.joinToString(", ")?.ifEmpty { "-" } ?: stored.ifEmpty { "-" }
    FieldType.FILE -> fileRefDisplayName(stored)
}
