package com.eventverse.app.services

import com.eventverse.app.domain.costing.BenchmarkCostLine
import com.eventverse.app.domain.common.Money
import com.eventverse.app.domain.costing.usecases.ParsedHistoricalCosting
import kotlin.math.roundToLong

/**
 * Menerjemahkan satu berkas Excel HPP lama menjadi [ParsedHistoricalCosting].
 *
 * Ada dua implementasi dan keduanya wajib bisa berdiri sendiri:
 * - [GeminiCostingParserService] — luwes terhadap variasi tata letak, butuh `GEMINI_API_KEY`.
 * - [HeuristicCostingParser] — berbasis label, jalan tanpa jaringan dan tanpa biaya.
 *
 * Heuristik bukan sekadar cadangan darurat: ia yang membuat impor tetap bisa dijalankan dan
 * diuji di mesin developer tanpa kunci API, dan ia yang menjadi jaring pengaman ketika kuota
 * AI habis di tengah batch 100 berkas.
 */
interface HistoricalCostingParser {
    suspend fun parse(extract: WorkbookExtract, mockupImageUrl: String?): ParsedHistoricalCosting
}

/**
 * Parser berbasis pencarian label pada teks sheet yang sudah diratakan.
 *
 * Strateginya: untuk tiap konsep (gramasi, menit, HPP) cari baris yang memuat salah satu
 * kata kuncinya, lalu ambil angka pertama **di sebelah kanan** kata kunci itu. Aturan "di
 * sebelah kanan" penting — lembar HPP hampir selalu berbentuk label-di-kiri, nilai-di-kanan,
 * dan tanpa aturan itu nomor baris di kolom A akan terbaca sebagai nilainya.
 */
class HeuristicCostingParser : HistoricalCostingParser {

    override suspend fun parse(
        extract: WorkbookExtract,
        mockupImageUrl: String?
    ): ParsedHistoricalCosting {
        val lines = extract.gridText.lines()

        return ParsedHistoricalCosting(
            sourceFileName = extract.fileName,
            styleName = findText(lines, STYLE_LABELS)
                // Nama berkas adalah petunjuk terakhir yang selalu ada: "HPP CARDIGAN PARINARA.xlsx".
                ?: extract.fileName.substringBeforeLast('.').replace('_', ' ').trim(),
            clientName = findText(lines, CLIENT_LABELS),
            categoryText = findText(lines, CATEGORY_LABELS),
            knitType = findText(lines, KNIT_TYPE_LABELS),
            yarnType = findText(lines, YARN_LABELS),
            gauge = findNumber(lines, GAUGE_LABELS)?.roundToLong()?.toInt(),
            netWeightGrams = findNumber(lines, WEIGHT_LABELS),
            knittingMinutes = findNumber(lines, MINUTE_LABELS)?.roundToLong()?.toInt(),
            buttonCount = findNumber(lines, BUTTON_LABELS)?.roundToLong()?.toInt(),
            hppPerUnitMinor = findNumber(lines, HPP_LABELS)?.toMinorUnits(),
            sellingPricePerUnitMinor = findNumber(lines, SELLING_LABELS)?.toMinorUnits(),
            mockupImageUrl = mockupImageUrl,
            features = emptyMap(),
            costBreakdown = findCostBreakdown(lines)
        )
    }

    private fun findText(lines: List<String>, labels: List<String>): String? {
        lines.forEach { line ->
            val cells = line.split('\t')
            cells.forEachIndexed { index, cell ->
                if (labels.any { cell.contains(it, ignoreCase = true) }) {
                    cells.drop(index + 1)
                        .firstOrNull { it.isNotBlank() && it.toDoubleOrNull() == null }
                        ?.let { return it.trim() }
                }
            }
        }
        return null
    }

    private fun findNumber(lines: List<String>, labels: List<String>): Double? {
        lines.forEach { line ->
            val cells = line.split('\t')
            cells.forEachIndexed { index, cell ->
                if (labels.any { cell.contains(it, ignoreCase = true) }) {
                    cells.drop(index + 1)
                        .firstNotNullOfOrNull { it.parseIndonesianNumber() }
                        ?.let { return it }
                }
            }
        }
        return null
    }

    /**
     * Baris rincian biaya: sel kiri berupa teks, sel kanan berupa rupiah.
     *
     * Tiga saringan, masing-masing menahan kesalahan yang berbeda:
     * 1. **Baris total dibuang** supaya rinciannya tidak dihitung dua kali saat ditampilkan.
     * 2. **Baris spesifikasi dibuang.** Ini bukan teoretis: "MENIT RAJUT 97" lolos filter kata
     *    kunci lewat kata "rajut" dan tersimpan sebagai biaya Rp 97; "KANCING 7" jadi Rp 7.
     *    Angka receh itu tidak pernah terlihat salah sekilas, dan diam-diam muncul di kolom
     *    rincian Knowledge Base.
     * 3. **Nominal di bawah [MIN_COST_LINE_RUPIAH] dibuang** sebagai jaring terakhir: tidak ada
     *    komponen biaya produksi garmen yang berharga di bawah seratus rupiah per pcs.
     */
    private fun findCostBreakdown(lines: List<String>): List<BenchmarkCostLine> =
        lines.mapNotNull { line ->
            val cells = line.split('\t').map(String::trim).filter(String::isNotBlank)
            if (cells.size < 2) return@mapNotNull null
            val label = cells.first()
            if (label.toDoubleOrNull() != null) return@mapNotNull null
            if (TOTAL_LABELS.any { label.contains(it, ignoreCase = true) }) return@mapNotNull null
            if (SPEC_LABELS.any { label.contains(it, ignoreCase = true) }) return@mapNotNull null
            if (!COST_LINE_LABELS.any { label.contains(it, ignoreCase = true) }) return@mapNotNull null

            val amount = cells.drop(1).firstNotNullOfOrNull { it.parseIndonesianNumber() }
                ?: return@mapNotNull null
            if (amount < MIN_COST_LINE_RUPIAH) return@mapNotNull null

            BenchmarkCostLine(label = label, amountPerUnit = Money(amount.toMinorUnits()))
        }

    private companion object {
        val STYLE_LABELS = listOf("nama artikel", "artikel", "style", "nama model", "model", "item")
        val CLIENT_LABELS = listOf("buyer", "customer", "klien", "pelanggan", "brand")
        val CATEGORY_LABELS = listOf("kategori", "category", "jenis produk")
        val KNIT_TYPE_LABELS = listOf("jenis rajut", "knit type", "struktur", "motif")
        val YARN_LABELS = listOf("benang", "yarn", "bahan")
        val GAUGE_LABELS = listOf("gauge", "gg", "jarum")
        val WEIGHT_LABELS = listOf("gramasi", "berat bersih", "berat netto", "netto", "berat", "weight")
        val MINUTE_LABELS = listOf("menit rajut", "waktu rajut", "menit mesin", "knitting", "menit")
        val BUTTON_LABELS = listOf("kancing", "button")
        val HPP_LABELS = listOf("hpp", "harga pokok", "total biaya", "total cost")
        val SELLING_LABELS = listOf("harga jual", "selling price", "harga penawaran")
        val TOTAL_LABELS = listOf("total", "hpp", "harga jual", "grand")

        /** Baris yang nilainya spesifikasi teknis, bukan rupiah — lihat [findCostBreakdown]. */
        val SPEC_LABELS = listOf(
            "menit", "waktu", "gramasi", "berat", "netto", "gauge", "jarum", "gg",
            "jumlah kancing", "qty", "kuantitas", "ukuran", "size"
        )

        const val MIN_COST_LINE_RUPIAH = 100.0
        val COST_LINE_LABELS = listOf(
            "benang", "yarn", "rajut", "jahit", "linking", "obras", "kancing", "label",
            "hangtag", "packing", "cuci", "setrika", "overhead", "listrik", "bordir", "sablon"
        )
    }
}

/**
 * Membaca angka bergaya Indonesia: `Rp 45.000,50` maupun gaya Inggris `45,000.50`.
 *
 * ## Kenapa ini tidak sesederhana `toDoubleOrNull()`
 * `Rp 45.000` di lembar HPP Indonesia berarti empat puluh lima ribu rupiah. `toDouble()`
 * membacanya sebagai 45,0 — HPP tercatat seperseribu nilainya, dan kesalahan itu tidak pernah
 * terlihat sampai ada yang membandingkan penawaran dengan arsipnya.
 *
 * ## Aturannya
 * 1. Kalau kedua pemisah muncul, yang **terakhir** adalah desimalnya (`45.000,50` vs `45,000.50`).
 * 2. Kalau hanya satu jenis pemisah yang muncul, ia dianggap **pemisah ribuan** bila setiap
 *    kelompok setelahnya tepat tiga digit (`45.000`, `1.234.567`) — dan desimal bila tidak
 *    (`45.5`, `0,75`).
 *
 * Aturan 2 masih ambigu untuk `1.500` (seribu lima ratus vs satu koma lima). Ambiguitas itu
 * diputus ke arah ribuan karena sumber datanya adalah lembar HPP rupiah buatan Indonesia,
 * di mana pecahan tiga desimal praktis tidak pernah muncul.
 */
internal fun String.parseIndonesianNumber(): Double? {
    val cleaned = trim()
        .removePrefix("Rp").removePrefix("RP").removePrefix("rp")
        .filterNot { it.isWhitespace() }
    if (cleaned.isBlank()) return null
    if (cleaned.none { it.isDigit() }) return null
    if (cleaned.any { !it.isDigit() && it != '.' && it != ',' && it != '-' }) return null

    val hasComma = cleaned.contains(',')
    val hasDot = cleaned.contains('.')

    val normalised = when {
        hasComma && hasDot ->
            if (cleaned.lastIndexOf(',') > cleaned.lastIndexOf('.')) {
                cleaned.replace(".", "").replace(',', '.')
            } else {
                cleaned.replace(",", "")
            }

        hasComma ->
            if (cleaned.looksLikeGroupedThousands(',')) cleaned.replace(",", "")
            else cleaned.replace(',', '.')

        hasDot ->
            if (cleaned.looksLikeGroupedThousands('.')) cleaned.replace(".", "")
            else cleaned

        else -> cleaned
    }
    return normalised.toDoubleOrNull()
}

/** `45.000` dan `1.234.567` benar; `45.5` dan `1.2345` tidak. */
private fun String.looksLikeGroupedThousands(separator: Char): Boolean {
    val groups = split(separator)
    if (groups.size < 2) return false
    // Kelompok pertama 1-3 digit ("45.000"), sisanya wajib tepat 3 digit.
    if (groups.first().trimStart('-').length !in 1..3) return false
    return groups.drop(1).all { it.length == 3 && it.all(Char::isDigit) }
}

/** Rupiah utuh → minor units (sen), sejalan dengan [com.eventverse.app.domain.common.CurrencyCode.IDR]. */
internal fun Double.toMinorUnits(): Long = (this * 100.0).roundToLong()
