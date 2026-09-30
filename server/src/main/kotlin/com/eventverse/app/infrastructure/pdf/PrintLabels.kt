package com.eventverse.app.infrastructure.pdf

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
 * Lokal `id-ID` dipakai untuk nama bulan ("30 Sep 2026"), bukan untuk pemisah angka: angka pada
 * dokumen diformat oleh `IdrFormat` di domain, tanpa API locale.
 */
object PrintLabels {

    private const val PATTERN = "dd MMM yyyy HH:mm"

    /** `2026-09-30T17:21:00Z` di zona server (+07:00) → `"01 Okt 2026 00:21 +07:00"`. */
    fun of(zoned: java.time.ZonedDateTime): String =
        "${zoned.format(java.time.format.DateTimeFormatter.ofPattern(PATTERN, java.util.Locale("id", "ID")))} ${zoned.offset.id}"

    /** Waktu sekarang di zona server. */
    fun now(): String = of(java.time.ZonedDateTime.now())

    /** Epoch millis → label; `null` tetap `null` supaya pemanggil tidak perlu bercabang. */
    fun ofEpochMillis(epochMillis: Long?): String? = epochMillis?.let {
        of(java.time.ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(it), java.time.ZoneId.systemDefault()))
    }
}
