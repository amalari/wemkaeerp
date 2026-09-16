package com.eventverse.app.domain.invoicing.template

/**
 * Berkas font yang benar-benar dibundel untuk dokumen faktur.
 *
 * Enum ini menamai **file**, bukan keluarga font, karena hanya bobot inilah yang ada di
 * `server/src/main/resources/fonts/` dan `app/shared/src/commonMain/composeResources/font/`.
 * Menamai keluarga ("Nunito") akan menyembunyikan kenyataan bahwa Nunito Italic tidak dibundel,
 * dan membuat `TextStyleSpec.isItalic` terlihat seolah berfungsi.
 */
enum class InvoiceFont(val resourceName: String) {
    NUNITO_REGULAR("nunito_regular"),
    NUNITO_BOLD("nunito_bold"),
    FREDOKA_MEDIUM("fredoka_medium"),
    FREDOKA_BOLD("fredoka_bold")
}

/**
 * Menentukan font mana yang dipakai untuk sebuah gaya teks.
 *
 * ## Mengapa aturan ini harus berada di domain
 *
 * Aturannya dulu hidup sebagai fungsi privat `pickFont` di dalam renderer PDF server. Akibatnya
 * kanvas Compose tidak mengetahuinya sama sekali: judul faktur 20pt digambar Fredoka di kertas,
 * tetapi di layar digambar dengan font bawaan platform — `Text` di kanvas tidak pernah menyebut
 * `fontFamily`, sehingga jatuh ke `LocalTextStyle.current` yang di Material 3 bernilai
 * `TextStyle.Default`.
 *
 * Selisih itu juga menular ke pemecahan baris. Begitu [InvoiceTextLayout] memakai lebar glyph yang
 * sebenarnya, ia harus tahu glyph font **yang mana** — dan satu-satunya jawaban yang benar adalah
 * jawaban yang sama dengan yang dipakai mesin gambar. Karena itu ketiganya (pemecah baris, kanvas,
 * PDF) sekarang bertanya ke fungsi ini.
 *
 * Ambang 14pt memisahkan "judul" dari "isi". Ini memang kasar — [TextStyleSpec] belum punya medan
 * keluarga font sendiri — tetapi sengaja dipertahankan apa adanya dari perilaku lama supaya
 * pemindahan aturan ini tidak mengubah satu pun faktur yang sudah terbit.
 */
object InvoiceFontResolver {

    /** Ukuran (pt) minimal agar sebuah teks diperlakukan sebagai judul dan memakai Fredoka. */
    const val HEADING_THRESHOLD_PT: Int = 14

    fun resolve(style: TextStyleSpec): InvoiceFont = when {
        style.fontSizePt >= HEADING_THRESHOLD_PT && style.isBold -> InvoiceFont.FREDOKA_BOLD
        style.fontSizePt >= HEADING_THRESHOLD_PT -> InvoiceFont.FREDOKA_MEDIUM
        style.isBold -> InvoiceFont.NUNITO_BOLD
        else -> InvoiceFont.NUNITO_REGULAR
    }
}
