package com.eventverse.app.domain.costing

import com.eventverse.app.domain.common.Money
import com.eventverse.app.domain.common.Ratio
import com.eventverse.app.domain.tenant.TenantId
import kotlinx.datetime.Instant
import kotlin.jvm.JvmInline

@JvmInline
value class BenchmarkId(val value: String) {
    init {
        require(value.isNotBlank()) { "BenchmarkId tidak boleh kosong" }
    }
}

/**
 * Siluet produk rajut — dipakai sebagai sumbu utama pencarian kemiripan.
 *
 * Nilainya sengaja kasar (bukan "cardigan crop lengan balon"): makin halus kategorinya,
 * makin sering pencarian kemiripan pulang dengan tangan kosong. Detail halus hidup di
 * [CostingProductBenchmark.features] yang bebas-bentuk.
 */
enum class KnitCategory(val displayName: String, val aliases: List<String>) {
    CARDIGAN("Cardigan", listOf("cardigan", "kardigan", "cardi")),
    PULLOVER("Pullover / Sweater", listOf("pullover", "sweater", "sweter", "jumper")),
    VEST("Vest / Rompi", listOf("vest", "rompi")),
    DRESS("Dress Rajut", listOf("dress", "terusan")),
    TOP("Atasan / Blouse Rajut", listOf("top", "blouse", "atasan", "crop")),
    SCARF_ACCESSORY("Syal & Aksesori", listOf("scarf", "syal", "beanie", "topi", "kupluk")),
    OTHER("Lainnya", emptyList());

    companion object {
        /** Mencocokkan nama artikel bebas dari Excel lama ke kategori terdekat. */
        fun fromFreeText(text: String?): KnitCategory {
            if (text.isNullOrBlank()) return OTHER
            val lowered = text.lowercase()
            return entries.firstOrNull { category ->
                category.aliases.any { alias -> lowered.contains(alias) }
            } ?: OTHER
        }
    }
}

/**
 * Struktur teknis rajutan: jenis rajut, benang, dan gauge mesin.
 *
 * ## Mengapa gauge disimpan sebagai angka, bukan enum?
 * Gauge (jarum per inci) berkorelasi terbalik dengan gramasi: mesin 5G menghasilkan kain jauh
 * lebih tebal daripada 12G untuk siluet yang sama. Menyimpannya sebagai angka memungkinkan
 * estimator menskalakan gramasi secara kontinu, bukan melompat antar-kelas.
 */
data class KnitStructure(
    /** Mis. "Jacquard", "Rib 2x2", "Plain Jersey". Kosong bila tidak tercatat di Excel lama. */
    val knitType: String = "",
    /** Mis. "Acrylic 2/32", "Cotton Combed 30s", "Wool Blend". */
    val yarnType: String = "",
    /** Jarum per inci. null = tidak tercatat. */
    val gauge: Int? = null
) {
    init {
        gauge?.let { require(it in 1..21) { "Gauge mesin rajut di luar rentang wajar (1-21): $it" } }
    }
}

/**
 * Metrik fisik yang benar-benar terukur di lantai produksi — bukan asumsi.
 *
 * Inilah yang membuat arsip 100 Excel lama berharga: gramasi dan menit mesin riil adalah
 * dua angka yang paling sering ditebak salah saat CS menghitung penawaran manual.
 */
data class PhysicalMetrics(
    /** Berat bersih kain jadi per pcs, dalam gram. */
    val netWeightGrams: Double,
    /** Menit mesin rajut per pcs. null = tidak tercatat di sheet asal. */
    val knittingMinutes: Int? = null,
    val buttonCount: Int = 0
) {
    init {
        require(netWeightGrams > 0.0) { "Berat bersih harus lebih besar dari 0 gram" }
        require(netWeightGrams < 10_000.0) { "Berat bersih $netWeightGrams gram tidak masuk akal untuk satu pcs" }
        knittingMinutes?.let { require(it >= 0) { "Menit rajut tidak boleh negatif" } }
        require(buttonCount >= 0) { "Jumlah kancing tidak boleh negatif" }
    }
}

/**
 * Satu baris rincian biaya sebagaimana tertulis di lembar Excel asal.
 *
 * ## Mengapa rincian ini ikut disimpan, bukan cuma totalnya?
 * Rencana awal menyalin rincian ini ke `costing_sheet_buckets` lewat lembar HPP sintetis.
 * Itu ditolak: lembar HPP punya siklus DRAFT→APPROVED, dan 100 lembar historis palsu akan
 * masuk ke antrean persetujuan serta merusak telemetri `pendingSheetCount`. Rincian tetap
 * dipertahankan, tapi sebagai bagian dari arsip — bukan sebagai dokumen transaksional.
 */
data class BenchmarkCostLine(
    val label: String,
    val amountPerUnit: Money
)

/** Angka finansial historis dari lembar HPP asal. */
data class BenchmarkPricing(
    val hppPerUnit: Money,
    val sellingPricePerUnit: Money? = null
) {
    /** Margin implisit = (jual − hpp) / hpp. null bila harga jual tidak tercatat atau HPP nol. */
    val impliedMargin: Ratio?
        get() {
            val selling = sellingPricePerUnit ?: return null
            if (hppPerUnit.minorUnits <= 0L) return null
            return Ratio(selling.minorUnits - hppPerUnit.minorUnits, hppPerUnit.minorUnits)
        }
}

/**
 * Satu artikel produk masa lalu yang sudah diproduksi dan diketahui angka riilnya —
 * unit dasar Knowledge Base modul `COSTING_HPP`.
 *
 * ## Mengapa entity terpisah dari [CostingSheet]?
 * Lembar HPP adalah dokumen transaksional dengan siklus approval; benchmark adalah fakta
 * historis yang sudah selesai. Memaksa arsip Excel lama masuk ke siklus DRAFT → APPROVED
 * berarti 100 baris palsu di antrean persetujuan. Benchmark menunjuk balik ke lembar asalnya
 * lewat [sourceSheetId] ketika impor memang membuat lembar resmi.
 *
 * ## Mengapa [features] bebas-bentuk?
 * Parser AI mengembalikan atribut yang berbeda-beda per file (ada yang mencatat "saku tempel",
 * ada yang "bordir dada"). Mengunci atribut itu ke kolom akan membuang informasi yang justru
 * membedakan dua artikel dengan gramasi sama.
 */
data class CostingProductBenchmark(
    val id: BenchmarkId,
    val tenantId: TenantId,
    val styleName: String,
    val clientName: String = "",
    val category: KnitCategory = KnitCategory.OTHER,
    val structure: KnitStructure = KnitStructure(),
    val metrics: PhysicalMetrics,
    val pricing: BenchmarkPricing,
    val mockupImageUrl: String? = null,
    val features: Map<String, String> = emptyMap(),
    /** Rincian biaya per pcs dari sheet asal, apa adanya. Kosong bila parser tidak menemukannya. */
    val costBreakdown: List<BenchmarkCostLine> = emptyList(),
    /** Lembar HPP resmi yang dibuat dari file Excel ini, bila ada. */
    val sourceSheetId: CostingSheetId? = null,
    /** Nama berkas Excel asal — supaya hasil impor bisa ditelusuri balik ke sumbernya. */
    val sourceFileName: String = "",
    val createdAt: Instant,
    val updatedAt: Instant
) {
    init {
        require(styleName.isNotBlank()) { "Nama artikel benchmark tidak boleh kosong" }
    }

    /** Gram benang per menit mesin — indikator produktivitas yang sebanding lintas artikel. */
    val gramsPerKnittingMinute: Double?
        get() = metrics.knittingMinutes
            ?.takeIf { it > 0 }
            ?.let { metrics.netWeightGrams / it.toDouble() }

    /**
     * Skor kemiripan 0.0–1.0 terhadap sebuah permintaan estimasi.
     *
     * Bobotnya default-nya berat di kategori: dua artikel dengan gramasi mirip tapi siluet
     * berbeda (vest vs dress) punya struktur biaya jahit yang sama sekali lain, sementara dua
     * cardigan dengan gauge berbeda masih saling menjelaskan.
     *
     * Bobot diterima sebagai parameter, bukan konstanta, karena tenant yang variasi benangnya
     * cuma satu-dua jenis ingin [QuickEstimateTuning.yarnWeight] kecil — kalau tidak, seluruh
     * arsipnya mendapat skor benang penuh dan bobot itu berhenti membedakan apa pun.
     */
    fun similarityTo(
        category: KnitCategory,
        gauge: Int?,
        yarnKeyword: String?,
        tuning: QuickEstimateTuning = QuickEstimateTuning.SYSTEM_DEFAULT
    ): Double {
        var score = 0.0

        score += when {
            this.category == category -> tuning.categoryWeight
            // Salah satu sisi tak terkategori: masih mungkin relevan, tapi jangan diperlakukan
            // seolah cocok — sepertiga bobot cukup untuk menjaganya tetap sebagai kandidat.
            this.category == KnitCategory.OTHER || category == KnitCategory.OTHER ->
                tuning.categoryWeight / 3.0
            else -> 0.0
        }

        val ownGauge = structure.gauge
        score += when {
            ownGauge == null || gauge == null -> tuning.gaugeWeight / 3.0
            ownGauge == gauge -> tuning.gaugeWeight
            else -> (tuning.gaugeWeight - (kotlin.math.abs(ownGauge - gauge) * tuning.gaugeWeight / 5.0))
                .coerceAtLeast(0.0)
        }

        score += when {
            yarnKeyword.isNullOrBlank() -> tuning.yarnWeight / 2.5
            structure.yarnType.contains(yarnKeyword, ignoreCase = true) -> tuning.yarnWeight
            yarnKeyword.contains(structure.yarnType, ignoreCase = true) &&
                structure.yarnType.isNotBlank() -> tuning.yarnWeight * 0.8
            else -> 0.0
        }

        return score.coerceIn(0.0, 1.0)
    }
}
