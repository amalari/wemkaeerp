package com.eventverse.app.domain.invoicing.template

import kotlin.math.roundToInt

/**
 * Pemecah baris teks faktur — murni, deterministik, dan bebas platform.
 *
 * ## Mengapa ada di lapisan domain, bukan di UI
 *
 * Desainer faktur punya dua mesin gambar: kanvas Compose (Wasm/Desktop/Android/iOS) dan renderer
 * PDFBox di server. Keduanya menggambar elemen yang sama dari koordinat yang sama. Kalau masing-
 * masing memakai mesin pengukurnya sendiri untuk memutuskan di mana baris dipotong, dokumen yang
 * dirancang di kanvas tidak akan sama dengan hasil cetaknya — dan perbedaannya baru terlihat setelah
 * faktur dikirim ke klien, bukan saat mendesain.
 *
 * Karena itu **keputusan pemotongan baris dilakukan satu kali, di sini**, dan kanvas maupun PDFBox
 * memakai daftar baris hasilnya apa adanya. Titik potongnya identik secara konstruksi, bukan karena
 * kebetulan dua mesin pengukur sepakat.
 *
 * Alternatif "tiap mesin wrap sendiri dengan metrik aslinya" menciptakan bug yang sulit dilihat:
 * 3 baris di layar, 4 baris di kertas, tanpa satu pun tes yang bisa menangkapnya.
 *
 * ## Lebar glyph berasal dari fontnya, bukan dari taksiran
 *
 * Versi awal objek ini menaksir lebar dengan tabel faktor `em` per kelas karakter. Taksiran itu
 * meleset hingga −18% ke arah yang salah (lihat [InvoiceFontMetrics]), sehingga baris yang dinyatakan
 * "muat" ternyata lebih lebar dari kotaknya. Sekarang lebarnya dibaca dari [InvoiceFontMetrics] —
 * angka yang dibangkitkan dari berkas TTF yang sama yang dipakai PDFBox menggambar.
 *
 * Font mana yang berlaku ditentukan [InvoiceFontResolver] dari [TextStyleSpec], memakai aturan yang
 * sama persis dengan yang dipakai renderer PDF memilih `PDFont`-nya.
 */
object InvoiceTextLayout {

    /** 1 pt = 25.4/72 mm = 2540/720 satuan 1/10 mm (Mm10). */
    const val MM10_PER_PT: Double = 2540.0 / 720.0

    /**
     * Tinggi satu baris = fontSize × 1.35.
     *
     * Dipakai bersama sebagai tinggi kotak teks yang diturunkan. Tanpa rasio tetap, tinggi elemen
     * di kanvas akan bergantung pada `lineHeight` gaya bawaan Compose di masing-masing platform.
     */
    const val LINE_HEIGHT_RATIO: Double = 1.35

    /** Batas bawah lebar baris agar pemecahan selalu punya ruang minimal satu karakter. */
    private const val MIN_LINE_EM = 0.5

    /**
     * Memecah [text] menjadi baris-baris yang muat pada lebar [widthMm10].
     *
     * Perilaku yang dijamin:
     * - `\n` eksplisit selalu memulai baris baru (baris kosong ikut dipertahankan);
     * - kata yang lebih panjang dari satu baris dipecah per karakter, bukan dibiarkan meluber
     *   (nomor faktur seperti `INV/2026/03/0001` adalah kasus nyata);
     * - teks kosong menghasilkan **satu** baris kosong, supaya tinggi kotaknya tidak nol dan
     *   elemen tetap bisa dipilih di kanvas.
     */
    fun wrap(text: String, widthMm10: Int, style: TextStyleSpec): List<String> {
        val normalized = text.replace("\r\n", "\n").replace('\r', '\n')
        if (normalized.isEmpty()) return listOf("")

        val font = InvoiceFontResolver.resolve(style)
        val maxEm = maxEmFor(widthMm10, style)
        val lines = mutableListOf<String>()
        normalized.split('\n').forEach { segment -> lines += wrapSegment(segment, font, maxEm) }
        return lines.ifEmpty { listOf("") }
    }

    /** Tinggi kotak teks yang diturunkan dari jumlah baris hasil [wrap]. */
    fun measureHeightMm10(text: String, widthMm10: Int, style: TextStyleSpec): Int =
        wrap(text, widthMm10, style).size * lineHeightMm10(style)

    /** Tinggi satu baris dalam Mm10. */
    fun lineHeightMm10(style: TextStyleSpec): Int =
        (style.fontSizePt.coerceAtLeast(1) * LINE_HEIGHT_RATIO * MM10_PER_PT)
            .roundToInt()
            .coerceAtLeast(1)

    /** Lebar teks dalam Mm10 menurut metrik font yang sama dengan pemecah baris. */
    fun measureWidthMm10(text: String, style: TextStyleSpec): Int =
        (InvoiceFontMetrics.advanceEm(InvoiceFontResolver.resolve(style), text) *
            style.fontSizePt.coerceAtLeast(1) * MM10_PER_PT)
            .roundToInt()

    private fun maxEmFor(widthMm10: Int, style: TextStyleSpec): Double {
        val usableMm10 = widthMm10.coerceAtLeast(1)
        return (usableMm10 / (style.fontSizePt.coerceAtLeast(1) * MM10_PER_PT))
            .coerceAtLeast(MIN_LINE_EM)
    }

    private fun wrapSegment(segment: String, font: InvoiceFont, maxEm: Double): List<String> {
        if (segment.isBlank()) return listOf("")

        val lines = mutableListOf<String>()
        var current = StringBuilder()
        var currentEm = 0.0
        val spaceWidthEm = InvoiceFontMetrics.advanceEm(font, ' ')

        segment.trim().split(' ').filter { it.isNotEmpty() }.forEach { word ->
            var remaining = word
            while (remaining.isNotEmpty()) {
                val spaceEm = if (current.isEmpty()) 0.0 else spaceWidthEm
                val remainingEm = InvoiceFontMetrics.advanceEm(font, remaining)

                if (currentEm + spaceEm + remainingEm <= maxEm) {
                    if (spaceEm > 0.0) current.append(' ')
                    current.append(remaining)
                    currentEm += spaceEm + remainingEm
                    remaining = ""
                    continue
                }

                // Kata ini tidak muat. Kalau baris sekarang sudah berisi, tutup dulu barisnya;
                // kalau baris masih kosong, kata itu sendiri yang harus dipotong per karakter.
                if (current.isEmpty()) {
                    val head = StringBuilder()
                    var headEm = 0.0
                    for (ch in remaining) {
                        val chEm = InvoiceFontMetrics.advanceEm(font, ch)
                        if (headEm + chEm > maxEm) break
                        head.append(ch)
                        headEm += chEm
                    }
                    // Jaminan kemajuan: minimal satu karakter per baris, walau tipenya sangat besar.
                    if (head.isEmpty()) head.append(remaining.first())
                    lines += head.toString()
                    remaining = remaining.substring(head.length)
                } else {
                    lines += current.toString()
                    current = StringBuilder()
                    currentEm = 0.0
                }
            }
        }

        if (current.isNotEmpty() || lines.isEmpty()) lines += current.toString()
        return lines
    }

}
