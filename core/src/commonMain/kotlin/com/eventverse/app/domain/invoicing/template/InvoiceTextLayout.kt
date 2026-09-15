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
 * Karena itu **keputusan pemotongan baris dilakukan satu kali, di sini**. Komprominya: lebar teks
 * diperkirakan dengan tabel faktor `em` per kelas karakter, bukan metrik font sungguhan. Itu
 * disengaja dan aman, karena:
 *
 * 1. Kanvas dan PDFBox **memakai daftar baris hasil fungsi ini apa adanya** (bukan membiarkan
 *    masing-masing mesin wrap sendiri), jadi titik potongnya identik secara konstruksi.
 * 2. Metrik asli tetap dipakai mesin gambar untuk hal yang tidak mengubah struktur: perataan
 *    (`align`) dan penempatan glyph.
 * 3. Konsekuensi satu-satunya adalah sisa ruang di kanan baris bisa berbeda beberapa persen —
 *    kosmetik, bukan struktural.
 *
 * Alternatif "tiap mesin wrap sendiri dengan metrik aslinya" justru menciptakan bug yang lebih sulit
 * dilihat: 3 baris di layar, 4 baris di kertas, tanpa satu pun tes yang bisa menangkapnya.
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

    /** Lebar spasi dalam satuan `em`. */
    private const val SPACE_EM = 0.27

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

        val maxEm = maxEmFor(widthMm10, style)
        val lines = mutableListOf<String>()
        normalized.split('\n').forEach { segment -> lines += wrapSegment(segment, maxEm) }
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

    /** Lebar teks dalam Mm10 menurut tabel perkiraan yang sama dengan pemecah baris. */
    fun estimateWidthMm10(text: String, style: TextStyleSpec): Int =
        (text.sumOf { advanceEm(it) } * style.fontSizePt.coerceAtLeast(1) * MM10_PER_PT)
            .roundToInt()

    private fun maxEmFor(widthMm10: Int, style: TextStyleSpec): Double {
        val usableMm10 = widthMm10.coerceAtLeast(1)
        return (usableMm10 / (style.fontSizePt.coerceAtLeast(1) * MM10_PER_PT))
            .coerceAtLeast(MIN_LINE_EM)
    }

    private fun wrapSegment(segment: String, maxEm: Double): List<String> {
        if (segment.isBlank()) return listOf("")

        val lines = mutableListOf<String>()
        var current = StringBuilder()
        var currentEm = 0.0

        segment.trim().split(' ').filter { it.isNotEmpty() }.forEach { word ->
            var remaining = word
            while (remaining.isNotEmpty()) {
                val spaceEm = if (current.isEmpty()) 0.0 else SPACE_EM
                val remainingEm = remaining.sumOf { advanceEm(it) }

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
                        val chEm = advanceEm(ch)
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

    /**
     * Perkiraan lebar satu karakter dalam satuan `em`.
     *
     * Nilainya dikelompokkan per kelas karakter, bukan per glyph: tabel per glyph untuk Nunito akan
     * berarti memelihara ratusan angka yang harus ikut berubah setiap kali font diganti. Yang
     * dibutuhkan pemecah baris hanyalah urutan relatif lebar — `m` lebih lebar dari `i`, digit di
     * antaranya — dan itu sudah dipenuhi pengelompokan ini.
     */
    private fun advanceEm(char: Char): Double = when {
        char == ' ' -> SPACE_EM
        char == '\t' -> SPACE_EM * 4
        char == '\n' -> SPACE_EM
        char in ".,:;'’`!|" -> 0.28
        char in "ijltfrI()[]{}/\\-" -> 0.34
        char.isDigit() -> 0.56
        char in "MWmw@%&" -> 0.90
        char.isUpperCase() -> 0.68
        else -> 0.52
    }
}
