package com.eventverse.app.domain.invoicing.template

/**
 * Lebar maju (*advance width*) setiap glyph untuk keempat font faktur.
 *
 * ## Mengapa tabel ini ada
 *
 * [InvoiceTextLayout] dulu menaksir lebar teks lewat tabel faktor `em` per **kelas karakter**
 * (digit sekian, huruf besar sekian). Taksiran itu meleset ke arah yang berbahaya pada kelas yang
 * paling penting di faktur:
 *
 * | Karakter | Nunito Regular | Taksiran lama | Selisih |
 * |---|---|---|---|
 * | digit `0`–`9` | 0.600 | 0.560 | **−6.7%** |
 * | `W` | 1.101 | 0.900 | **−18.3%** |
 * | `i`, `l` | 0.232 | 0.340 | +46.6% |
 *
 * Meleset ke bawah berarti pemecah baris menyatakan sebuah baris "muat" padahal nyatanya lebih lebar
 * dari kotaknya. Di PDF, penjepit `(widthPt - textWidth).coerceAtLeast(0f)` lalu memaksa hasilnya ke
 * nol — sehingga angka rupiah yang seharusnya rata kanan **diam-diam berubah jadi rata kiri** dan
 * meluber melewati tepi kotak. Kolom uang adalah kolom yang paling banyak digitnya, jadi kelas yang
 * paling salah taksir justru mengenai bagian faktur yang paling tidak boleh salah.
 *
 * ## Dari mana angkanya
 *
 * Dibangkitkan dari berkas TTF yang sama yang dibundel ke server dan ke aplikasi, memakai
 * `PDFont.getStringWidth` — **jalur yang persis dipakai renderer PDF**. Satuannya 1/1000 em, sama
 * seperti PDFBox. [InvoiceFontMetricsDriftTest] di modul `server` membandingkan ulang seluruh isi
 * tabel ini dengan PDFBox pada setiap build; mengganti berkas font akan membuat test itu merah
 * sebelum ada satu pun faktur tercetak salah.
 *
 * Tabel ini di-*check in*, bukan dibangkitkan saat build, karena `core` adalah `commonMain`: kode di
 * sini juga berjalan di JS dan Wasm yang tidak bisa membaca berkas TTF. Menambah codegen lintas lima
 * target untuk 380 angka yang hanya berubah saat font diganti tidak sepadan dengan ongkosnya.
 *
 * ## Yang sengaja tidak dilakukan
 *
 * Kerning (GPOS) **tidak** diperhitungkan, karena `PDFont.getStringWidth` juga tidak
 * memperhitungkannya — tabel ini cocok persis dengan PDF. Skia di kanvas menerapkan kerning, jadi
 * lebar baris di layar bisa meleset pecahan persen. Itu hanya menggeser perataan, tidak pernah
 * mengubah **titik potong baris**, dan titik potong adalah satu-satunya hal yang wajib identik.
 */
object InvoiceFontMetrics {

    /** Satuan tabel: 1/1000 em, sama dengan satuan `PDFont.getStringWidth`. */
    const val UNITS_PER_EM: Int = 1000

    /** Karakter terendah dan tertinggi yang punya entri tabel penuh. */
    private const val ASCII_LOW = 32
    private const val ASCII_HIGH = 126

    private val NUNITO_REGULAR_ASCII = intArrayOf(
        258, 228, 392, 600, 600, 930, 693, 221, 317, 317, 450, 600,  //  !"#$%&'()*+
        228, 424, 228, 283, 600, 600, 600, 600, 600, 600, 600, 600,  // ,-./01234567
        600, 600, 228, 228, 600, 600, 600, 443, 946, 729, 676, 673,  // 89:;<=>?@ABC
        742, 583, 548, 726, 761, 257, 324, 625, 543, 855, 740, 767,  // DEFGHIJKLMNO
        633, 767, 669, 615, 602, 728, 689, 1101, 650, 596, 589, 315,  // PQRSTUVWXYZ[
        283, 315, 600, 500, 356, 530, 583, 463, 583, 532, 333, 586,  // \]^_`abcdefg
        568, 232, 236, 500, 296, 856, 568, 556, 583, 583, 358, 481,  // hijklmnopqrs
        350, 561, 515, 841, 525, 514, 463, 352, 265, 352, 600  // tuvwxyz{|}~
    )

    private val NUNITO_BOLD_ASCII = intArrayOf(
        271, 248, 448, 600, 600, 945, 726, 243, 358, 358, 453, 600,  //  !"#$%&'()*+
        248, 434, 248, 313, 600, 600, 600, 600, 600, 600, 600, 600,  // ,-./01234567
        600, 600, 248, 248, 600, 600, 600, 459, 950, 744, 688, 680,  // 89:;<=>?@ABC
        762, 597, 562, 736, 773, 282, 354, 665, 562, 868, 748, 785,  // DEFGHIJKLMNO
        652, 785, 686, 631, 621, 738, 713, 1113, 672, 618, 605, 354,  // PQRSTUVWXYZ[
        313, 354, 600, 500, 377, 547, 600, 472, 600, 542, 364, 604,  // \]^_`abcdefg
        585, 255, 259, 536, 319, 877, 585, 576, 600, 600, 392, 488,  // hijklmnopqrs
        384, 579, 527, 853, 546, 526, 474, 391, 288, 391, 600  // tuvwxyz{|}~
    )

    private val FREDOKA_MEDIUM_ASCII = intArrayOf(
        248, 237, 356, 763, 459, 809, 707, 185, 371, 371, 486, 521,  //  !"#$%&'()*+
        225, 418, 214, 508, 580, 388, 587, 580, 547, 509, 531, 543,  // ,-./01234567
        553, 531, 230, 216, 537, 459, 537, 486, 834, 706, 629, 655,  // 89:;<=>?@ABC
        680, 617, 625, 712, 675, 224, 540, 593, 562, 820, 695, 728,  // DEFGHIJKLMNO
        599, 801, 618, 550, 656, 701, 726, 959, 693, 638, 583, 336,  // PQRSTUVWXYZ[
        510, 336, 503, 797, 372, 569, 573, 507, 575, 539, 392, 559,  // \]^_`abcdefg
        545, 233, 238, 497, 291, 814, 560, 559, 563, 560, 415, 459,  // hijklmnopqrs
        394, 551, 551, 755, 509, 543, 536, 368, 210, 368, 554  // tuvwxyz{|}~
    )

    private val FREDOKA_BOLD_ASCII = intArrayOf(
        235, 248, 399, 697, 442, 779, 696, 193, 357, 357, 468, 490,  //  !"#$%&'()*+
        223, 403, 220, 489, 565, 379, 566, 565, 551, 493, 521, 522,  // ,-./01234567
        541, 521, 221, 220, 583, 445, 583, 476, 844, 711, 609, 631,  // 89:;<=>?@ABC
        655, 603, 613, 719, 650, 239, 531, 597, 569, 809, 670, 721,  // DEFGHIJKLMNO
        595, 782, 604, 542, 640, 675, 728, 947, 689, 638, 598, 324,  // PQRSTUVWXYZ[
        491, 324, 489, 768, 448, 557, 554, 503, 554, 535, 419, 546,  // \]^_`abcdefg
        563, 251, 234, 512, 311, 791, 566, 558, 541, 543, 432, 457,  // hijklmnopqrs
        429, 568, 577, 737, 538, 576, 545, 354, 202, 354, 556  // tuvwxyz{|}~
    )

    private val EXTRA: Map<InvoiceFont, Map<Char, Int>> = mapOf(
        InvoiceFont.NUNITO_REGULAR to mapOf(
            '\u00A0' to 258,
            '\u00B0' to 371,
            '\u00D7' to 600,
            '\u00A3' to 600,
            '\u20AC' to 600,
            '\u2013' to 500,
            '\u2014' to 1000,
            '\u2018' to 228,
            '\u2019' to 228,
            '\u201C' to 394,
            '\u201D' to 394,
            '\u2026' to 686,
            '\u00E9' to 532,
            '\u00F1' to 568
        ),
        InvoiceFont.NUNITO_BOLD to mapOf(
            '\u00A0' to 271,
            '\u00B0' to 379,
            '\u00D7' to 600,
            '\u00A3' to 600,
            '\u20AC' to 600,
            '\u2013' to 500,
            '\u2014' to 1000,
            '\u2018' to 248,
            '\u2019' to 248,
            '\u201C' to 443,
            '\u201D' to 443,
            '\u2026' to 745,
            '\u00E9' to 542,
            '\u00F1' to 585
        ),
        InvoiceFont.FREDOKA_MEDIUM to mapOf(
            '\u00A0' to 248,
            '\u00B0' to 347,
            '\u00D7' to 464,
            '\u00A3' to 652,
            '\u20AC' to 581,
            '\u2013' to 552,
            '\u2014' to 679,
            '\u2018' to 184,
            '\u2019' to 178,
            '\u201C' to 345,
            '\u201D' to 345,
            '\u2026' to 676,
            '\u00E9' to 539,
            '\u00F1' to 560
        ),
        InvoiceFont.FREDOKA_BOLD to mapOf(
            '\u00A0' to 235,
            '\u00B0' to 379,
            '\u00D7' to 485,
            '\u00A3' to 628,
            '\u20AC' to 569,
            '\u2013' to 542,
            '\u2014' to 665,
            '\u2018' to 220,
            '\u2019' to 220,
            '\u201C' to 448,
            '\u201D' to 448,
            '\u2026' to 803,
            '\u00E9' to 535,
            '\u00F1' to 566
        )
    )

    private fun asciiTable(font: InvoiceFont): IntArray = when (font) {
        InvoiceFont.NUNITO_REGULAR -> NUNITO_REGULAR_ASCII
        InvoiceFont.NUNITO_BOLD -> NUNITO_BOLD_ASCII
        InvoiceFont.FREDOKA_MEDIUM -> FREDOKA_MEDIUM_ASCII
        InvoiceFont.FREDOKA_BOLD -> FREDOKA_BOLD_ASCII
    }

    /**
     * Lebar maju satu karakter dalam 1/1000 em.
     *
     * Karakter di luar ASCII dan di luar [EXTRA] — huruf beraksen, aksara non-Latin — memakai lebar
     * `M`. Itu jelas terlalu lebar untuk kebanyakan glyph, dan itu **disengaja**: taksiran yang
     * kelebaran hanya memecah baris lebih awal dari perlunya, sedangkan taksiran yang kesempitan
     * membuat teks meluber keluar kotak tanpa ada yang melihatnya sampai faktur dicetak. Dari dua
     * arah kesalahan, hanya satu yang merusak dokumen.
     */
    fun advanceUnits(font: InvoiceFont, char: Char): Int {
        val code = char.code
        if (code in ASCII_LOW..ASCII_HIGH) return asciiTable(font)[code - ASCII_LOW]
        EXTRA[font]?.get(char)?.let { return it }
        return asciiTable(font)['M'.code - ASCII_LOW]
    }

    /** Lebar maju satu karakter dalam satuan `em`. */
    fun advanceEm(font: InvoiceFont, char: Char): Double =
        advanceUnits(font, char).toDouble() / UNITS_PER_EM

    /**
     * Lebar sederet karakter dalam satuan `em`.
     *
     * Penjumlahan lurus tanpa kerning — lihat catatan kerning di dokumentasi objek ini.
     */
    fun advanceEm(font: InvoiceFont, text: String): Double =
        text.sumOf { advanceUnits(font, it) }.toDouble() / UNITS_PER_EM
}
