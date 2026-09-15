package com.eventverse.app.domain.invoicing.template

/**
 * Satu warna yang boleh dipakai pada elemen template faktur.
 *
 * @param label Nama peran yang dibaca pengguna di panel properti ("Teks Utama", bukan "Slate-800").
 * @param hex Nilai ARGB 0xAARRGGBB, sama bentuknya dengan `TextStyleSpec.colorHex`.
 */
data class PrintColorToken(val label: String, val hex: Long)

/**
 * Palet warna dokumen faktur.
 *
 * ## Kenapa di domain, bukan di theme aplikasi
 *
 * Warna di sini **bukan keputusan desain UI** — ia adalah bagian dari data template yang disimpan
 * tenant (`TemplateElement.style.colorHex`), sama seperti `node.stage.colorHex` pada Factory Flow.
 * Menaruhnya di `WeMadeColors` akan membuat palet dokumen ikut berubah begitu theme aplikasi
 * dirapikan, dan faktur yang sudah tersimpan akan berubah warna tanpa ada yang memintanya.
 *
 * ## Kenapa daftar tertutup, bukan pemilih warna bebas
 *
 * Faktur adalah dokumen legal yang dicetak dan diarsipkan. Pemilih warna bebas membuka pintu ke teks
 * kuning di atas kertas putih yang tidak terbaca saat difotokopi. Daftar pendek yang semuanya sudah
 * lolos kontras membuat kesalahan itu tidak mungkin terjadi.
 *
 * Nilai hex di sini adalah satu-satunya tempat angka warna dokumen boleh ditulis; komponen UI
 * membacanya sebagai data (`Color(InvoicePrintPalette.Body.hex)`), bukan menulis literalnya sendiri.
 */
object InvoicePrintPalette {

    /** Teks isi utama dokumen. */
    val Body = PrintColorToken("Teks Utama", 0xFF1E293BL)

    /** Teks sekunder: alamat, kontak, keterangan. */
    val Muted = PrintColorToken("Teks Sekunder", 0xFF64748BL)

    /** Aksen brand untuk judul dan nominal penting. */
    val Primary = PrintColorToken("Aksen Biru", 0xFF2563EBL)

    /** Aksen brand kedua, untuk penanda jenis dokumen. */
    val Brand = PrintColorToken("Aksen Oranye", 0xFFEA580CL)

    /** Peringatan: jatuh tempo, sisa tagihan. */
    val Danger = PrintColorToken("Peringatan", 0xFFB91C1CL)

    /** Konfirmasi: lunas, disetujui. */
    val Success = PrintColorToken("Konfirmasi", 0xFF15803DL)

    /** Warna yang ditawarkan panel properti, terurut sesuai kepentingannya. */
    val ALL: List<PrintColorToken> = listOf(Body, Muted, Primary, Brand, Danger, Success)

    /** Mencari token berdasarkan nilainya; null bila warna di luar palet (template lama). */
    fun tokenFor(hex: Long): PrintColorToken? = ALL.firstOrNull { it.hex == hex }
}
