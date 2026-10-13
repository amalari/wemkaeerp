package com.eventverse.app.infrastructure.pdf

import com.eventverse.app.domain.i18n.LanguageCode

/**
 * Label waktu untuk dokumen cetak.
 *
 * Offset zona ikut dicetak karena dokumen berpindah tangan: "14:05" tanpa zona membuat dua orang di
 * dua pulau memperdebatkan jam berapa dokumen itu dibuat. Yang tercetak adalah **offset** zona server
 * (`+07:00`), bukan singkatan ramah-manusia seperti "WIB": singkatan itu tidak mengenal DST dan tidak
 * bisa diturunkan dari `ZoneOffset` dengan benar untuk semua zona.
 *
 * Formatnya diangkat ke satu tempat begitu dokumen platform kedua memerlukannya — dua dokumen yang
 * mencetak jam dengan format berbeda terbaca sebagai dua dokumen yang dibuat oleh dua sistem berbeda.
 *
 * Nama bulan mengikuti [LanguageCode] (PLAN-i18n Fase 1, B4): `id-ID` → "30 Sep 2026",
 * `en` → "30 Sep 2026" (nama bulan Inggris). Pemisah angka tetap diformat `IdrFormat` di
 * domain, tanpa API locale.
 */
object PrintLabels {

    private const val PATTERN = "dd MMM yyyy HH:mm"

    private val ID_LOCALE = java.util.Locale("id", "ID")

    /** `2026-09-30T17:21:00Z` di zona server (+07:00) → `"01 Okt 2026 00:21 +07:00"`. */
    fun of(zoned: java.time.ZonedDateTime, language: LanguageCode = LanguageCode.ID): String =
        "${zoned.format(java.time.format.DateTimeFormatter.ofPattern(PATTERN, localeFor(language)))} ${zoned.offset.id}"

    /** Waktu sekarang di zona server. */
    fun now(language: LanguageCode = LanguageCode.ID): String = of(java.time.ZonedDateTime.now(), language)

    /** Epoch millis → label; `null` tetap `null` supaya pemanggil tidak perlu bercabang. */
    fun ofEpochMillis(epochMillis: Long?, language: LanguageCode = LanguageCode.ID): String? = epochMillis?.let {
        of(java.time.ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(it), java.time.ZoneId.systemDefault()), language)
    }

    private fun localeFor(language: LanguageCode): java.util.Locale =
        if (language == LanguageCode.EN) java.util.Locale.ENGLISH else ID_LOCALE
}
