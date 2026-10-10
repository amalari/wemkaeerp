package com.eventverse.app.domain.costing

import com.eventverse.app.domain.common.Money
import com.eventverse.app.domain.common.Ratio

/**
 * Karakter bahan dalam bahasa awam CS, bukan bahasa gudang.
 *
 * CS tidak tahu "Acrylic 2/32 count"; yang ditanyakan klien adalah "yang adem" atau "yang hangat".
 * Enum ini menerjemahkan bahasa itu ke kata kunci benang yang dipakai mencari benchmark, dan ke
 * faktor harga relatif terhadap benang acuan tenant.
 */
enum class MaterialCharacter(
    val displayName: String,
    val yarnKeyword: String,
    /** Pengali harga benang terhadap [QuickEstimateRates.yarnPricePerKg]. */
    val yarnPriceFactor: Double
) {
    ADEM_KATUN("Adem & menyerap keringat (katun)", "cotton", 1.00),
    HANGAT_AKRILIK("Hangat & ringan (akrilik)", "acrylic", 0.75),
    LEMBUT_CAMPURAN("Lembut jatuh (campuran / rayon)", "blend", 1.15),
    PREMIUM_WOL("Premium berat (wol)", "wool", 2.40)
}

/**
 * Ketebalan rajut dalam bahasa awam, dipetakan ke gauge mesin.
 *
 * [weightFactor] adalah pengali gramasi relatif terhadap benchmark ber-gauge sama; dipakai hanya
 * ketika benchmark yang ketemu punya gauge berbeda dari yang diminta.
 */
enum class KnitThickness(
    val displayName: String,
    val gauge: Int,
    val weightFactor: Double
) {
    TIPIS("Tipis & ringan (musim panas)", 12, 0.72),
    SEDANG("Sedang (paling umum)", 7, 1.00),
    TEBAL("Tebal & berat (chunky)", 5, 1.45)
}

/** Model potongan dalam bahasa CS; menentukan kategori pencarian benchmark. */
enum class GarmentSilhouette(val displayName: String, val category: KnitCategory) {
    CARDIGAN_BUKAAN("Cardigan (berkancing depan)", KnitCategory.CARDIGAN),
    PULLOVER_TERTUTUP("Sweater / pullover (tertutup)", KnitCategory.PULLOVER),
    VEST_TANPA_LENGAN("Rompi / vest (tanpa lengan)", KnitCategory.VEST),
    ATASAN_CROP("Atasan pendek / crop", KnitCategory.TOP),
    DRESS_TERUSAN("Dress rajut (terusan)", KnitCategory.DRESS)
}

/**
 * Aksesori jahit yang ditanyakan ke CS. Sengaja hanya dua angka: jumlah kancing dan ada/tidaknya
 * label woven — dua hal itu yang paling sering mengubah harga dan paling mudah dihitung klien
 * dari gambar mockup.
 */
data class TrimSpec(
    val buttonCount: Int = 0,
    val hasWovenLabel: Boolean = true,
    val hasHangtag: Boolean = false
) {
    init {
        require(buttonCount >= 0) { "Jumlah kancing tidak boleh negatif" }
    }
}

/** Lima parameter awam yang diisi CS, plus kuantitas order. */
data class QuickEstimateInput(
    val orderQuantity: Long,
    val materialCharacter: MaterialCharacter,
    val thickness: KnitThickness,
    val silhouette: GarmentSilhouette,
    val trims: TrimSpec = TrimSpec(),
    /** Catatan bebas CS, mis. "ada bordir logo dada kiri". Tidak masuk perhitungan, ikut ke ringkasan. */
    val notes: String = ""
) {
    init {
        require(orderQuantity > 0L) { "Kuantitas order harus lebih besar dari 0" }
    }
}

/**
 * Pembacaan AI atas gambar mockup yang di-upload CS.
 *
 * Semua field opsional dan tidak pernah menimpa isian manual CS — AI di sini berperan sebagai
 * pengisi awal dan pemberi petunjuk, bukan sebagai pengambil keputusan. Kalau model salah baca
 * jumlah kancing, harga penawaran tidak boleh ikut salah tanpa CS menyadarinya.
 */
data class DesignVisionHints(
    val detectedCategory: KnitCategory? = null,
    val detectedButtonCount: Int? = null,
    val detectedFeatures: List<String> = emptyList(),
    val summary: String = "",
    /** 0.0–1.0. Di bawah 0.4 hint tidak ditampilkan sebagai saran. */
    val confidence: Double = 0.0
)

/**
 * Tarif yang dipakai estimator, dirakit oleh lapisan aplikasi dari rate card tenant.
 *
 * Dipisah dari [CostingRateCard] karena Kontrak 4 (module-integration-rules): rumus estimasi
 * tidak boleh mengunci tenant pada satu bentuk rate card. Tenant yang menagih per menit SAM dan
 * tenant yang menagih borongan sama-sama bisa mengisi struktur ini.
 *
 * Semua field nullable berarti "belum dikonfigurasi" — estimator akan turun ke jalur
 * penskalaan HPP historis alih-alih mengarang angka nol.
 */
data class QuickEstimateRates(
    val yarnPricePerKg: Money? = null,
    val laborRatePerKnittingMinute: Money? = null,
    val buttonUnitPrice: Money? = null,
    val labelUnitPrice: Money? = null,
    val hangtagUnitPrice: Money? = null,
    val overheadPerUnit: Money? = null,
    val marginRatio: Ratio? = null
) {
    /** Estimasi bottom-up hanya sah bila dua tarif utama ini terisi. */
    val supportsBottomUp: Boolean
        get() = yarnPricePerKg != null && laborRatePerKnittingMinute != null
}

/** Dari mana angka estimasi berasal — ditampilkan ke CS supaya ia tahu seberapa jauh boleh percaya. */
enum class EstimateBasis(val displayName: String) {
    BOTTOM_UP_WITH_BENCHMARK("Rincian biaya + acuan produk historis"),
    SCALED_FROM_BENCHMARK("Penskalaan dari HPP produk historis termirip"),
    INSUFFICIENT_DATA("Data acuan belum cukup")
}

enum class EstimateConfidence(val displayName: String, val badge: String) {
    HIGH("Akurasi tinggi", "TINGGI"),
    MEDIUM("Perlu konfirmasi produksi", "SEDANG"),
    LOW("Kasar - wajib dicek sebelum dikirim", "RENDAH")
}

/** Satu artikel historis yang dipakai sebagai acuan, beserta skor kemiripannya. */
data class BenchmarkMatch(
    val benchmark: CostingProductBenchmark,
    val similarity: Double
)

/**
 * Hasil estimasi cepat: rentang, bukan angka tunggal.
 *
 * ## Mengapa rentang?
 * Estimasi bottom-up dari gramasi tebakan punya galat nyata. Menyajikan satu angka mengundang
 * CS mengirimnya sebagai harga final ke klien, lalu pabrik terkunci pada angka yang belum pernah
 * diverifikasi produksi. Rentang memaksa percakapan tetap terbuka sampai sampling selesai.
 */
data class QuickQuotationEstimateResult(
    val input: QuickEstimateInput,
    val basis: EstimateBasis,
    val confidence: EstimateConfidence,
    val estimatedWeightGrams: Double,
    val estimatedKnittingMinutes: Int,
    val hppLow: Money,
    val hppHigh: Money,
    val suggestedPriceLow: Money,
    val suggestedPriceHigh: Money,
    val appliedMargin: Ratio,
    val breakdown: List<EstimateLine> = emptyList(),
    val matches: List<BenchmarkMatch> = emptyList(),
    val visionHints: DesignVisionHints? = null,
    val warnings: List<String> = emptyList()
) {
    val hppMid: Money get() = Money((hppLow.minorUnits + hppHigh.minorUnits) / 2, hppLow.currency)

    /**
     * Ringkasan siap tempel ke WhatsApp.
     *
     * Ditaruh di domain, bukan di UI, karena teks ini adalah **komunikasi komersial**: kalimat
     * "estimasi awal, final setelah sampling" adalah pagar yang melindungi pabrik dan harus ikut
     * ke mana pun hasil ini dipakai — web, mobile, atau integrasi bot.
     */
    fun toWhatsAppSummary(currencyFormatter: (Money) -> String): String = buildString {
        appendLine("*Estimasi Harga Produksi Rajut*")
        appendLine()
        appendLine("Model      : ${input.silhouette.displayName}")
        appendLine("Bahan      : ${input.materialCharacter.displayName}")
        appendLine("Ketebalan  : ${input.thickness.displayName}")
        if (input.trims.buttonCount > 0) appendLine("Kancing    : ${input.trims.buttonCount} pcs")
        appendLine("Jumlah     : ${input.orderQuantity} pcs")
        appendLine()
        appendLine("Perkiraan berat : ${estimatedWeightGrams.toInt()} gram/pcs")
        appendLine(
            "*Harga per pcs  : ${currencyFormatter(suggestedPriceLow)} - ${currencyFormatter(suggestedPriceHigh)}*"
        )
        appendLine()
        if (input.notes.isNotBlank()) {
            appendLine("Catatan: ${input.notes}")
            appendLine()
        }
        append(
            "_Angka di atas adalah estimasi awal berbasis arsip produksi kami. " +
                "Harga final ditetapkan setelah sampel disetujui._"
        )
    }
}

/** Satu baris rincian biaya estimasi — dipakai UI untuk menunjukkan asal angkanya. */
data class EstimateLine(
    val label: String,
    val amountPerUnit: Money,
    val explanation: String = ""
)
