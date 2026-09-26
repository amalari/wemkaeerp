package com.eventverse.app.domain.costing

import com.eventverse.app.domain.common.Ratio

/**
 * Satu tingkat penalti run kecil: order di bawah [maxQuantityExclusive] dikali [factor]
 * pada biaya non-material.
 */
data class SmallRunTier(val maxQuantityExclusive: Long, val factor: Double) {
    init {
        require(maxQuantityExclusive > 0L) { "Batas tier run kecil harus lebih besar dari 0" }
        require(factor >= 1.0) { "Faktor run kecil tidak boleh di bawah 1.0 (itu diskon, bukan penalti)" }
    }
}

/**
 * Koefisien rumus estimator yang boleh berbeda per tenant.
 *
 * ## Kenapa ini ada
 * Sebelumnya seluruh angka di sini adalah `const val` di dalam
 * [com.eventverse.app.domain.costing.usecases.EstimateCostingFromAiDesignUseCase] — melanggar
 * Kontrak 4 (`module-integration-rules`): rumus costing tidak boleh mengunci tenant pada satu
 * set koefisien. Dampaknya konkret, bukan teoretis:
 *
 * - Susut benang 8% masuk akal untuk rajut jacquard rumit, tapi terlalu besar untuk makloon
 *   rapi yang benangnya 3% — tenant kedua ditagih susut yang tidak pernah terjadi.
 * - Penalti run kecil 1,35× wajar untuk pabrik dengan sedikit mesin besar, tapi menghukum
 *   pabrik yang justru punya banyak mesin kecil dan tidak repot ganti setelan.
 * - Gramasi baku cardigan 480 g adalah angka rajut mesin; pabrik handknit chunky jauh di atasnya.
 *
 * ## Bagaimana tenant mengubahnya
 * Lewat `customFormulaParameters` pada node `COSTING_HPP` di pipeline tenant — mekanisme yang
 * sama yang sudah dipakai [CostingParameterCodec], jadi tidak ada tabel atau migrasi baru.
 * Lihat [QuickEstimateTuningCodec] untuk daftar kuncinya.
 *
 * ## Catatan tentang [fallbackWeightGramsByCategory]
 * Tabel ini hanya dipakai saat arsip tenant belum bisa menjawab. Estimator selalu mencoba
 * menurunkan gramasi baku dari arsip tenant itu sendiri lebih dulu, sehingga tenant yang
 * sudah mengimpor Excel-nya praktis tidak pernah menyentuh angka di sini.
 */
data class QuickEstimateTuning(
    /** Susut benang: sisa cone, perca linking, sampel ukuran. */
    val yarnWasteRatio: Double = 0.08,

    /** Produktivitas mesin rajut saat arsip belum punya menit riil (gram per menit). */
    val defaultGramsPerMinute: Double = 5.0,

    val smallRunTiers: List<SmallRunTier> = listOf(
        SmallRunTier(24L, 1.35),
        SmallRunTier(50L, 1.20),
        SmallRunTier(100L, 1.10)
    ),

    /** Dipakai hanya kalau rate card tenant tidak menyebut margin. */
    val defaultMargin: Ratio = Ratio.percent(20.0),

    /** Lebar rentang HPP per tingkat keyakinan. */
    val spreadHigh: Double = 0.10,
    val spreadMedium: Double = 0.18,
    val spreadLow: Double = 0.30,

    /** Bobot skor kemiripan; totalnya tidak wajib 1.0 tapi rasionya yang menentukan. */
    val categoryWeight: Double = 0.45,
    val gaugeWeight: Double = 0.30,
    val yarnWeight: Double = 0.25,

    val minSimilarity: Double = 0.35,
    val maxMatches: Int = 5,
    val candidateLimit: Int = 25,

    /** Di bawah ini, pembacaan AI atas gambar tidak dipakai sebagai saran. */
    val visionTrustThreshold: Double = 0.40,

    /** Minimal artikel sejenis di arsip sebelum gramasi bakunya boleh diturunkan dari arsip. */
    val archiveFallbackMinSamples: Int = 3,

    val fallbackWeightGramsByCategory: Map<KnitCategory, Double> = DEFAULT_FALLBACK_WEIGHTS
) {
    init {
        require(yarnWasteRatio >= 0.0 && yarnWasteRatio < 1.0) {
            "Susut benang harus di rentang 0%-100%, bukan ${yarnWasteRatio * 100}%"
        }
        require(defaultGramsPerMinute > 0.0) { "Produktivitas mesin harus lebih besar dari 0 gram/menit" }
        require(smallRunTiers.isNotEmpty()) { "Tier run kecil tidak boleh kosong; pakai satu tier faktor 1.0" }
        require(maxMatches in 1..50) { "maxMatches di luar rentang wajar: $maxMatches" }
        require(candidateLimit >= maxMatches) { "candidateLimit harus >= maxMatches" }
        require(archiveFallbackMinSamples >= 1) { "archiveFallbackMinSamples minimal 1" }
        listOf(spreadHigh, spreadMedium, spreadLow).forEach {
            require(it > 0.0 && it < 1.0) { "Lebar rentang harus di antara 0% dan 100%, bukan ${it * 100}%" }
        }
    }

    /** Tier yang berlaku untuk [quantity]; 1.0 kalau ordernya di atas seluruh tier. */
    fun smallRunFactorFor(quantity: Long): Double =
        smallRunTiers.sortedBy { it.maxQuantityExclusive }
            .firstOrNull { quantity < it.maxQuantityExclusive }
            ?.factor
            ?: 1.0

    /** Kuantitas di bawah tier terbesar dianggap run kecil — dasar peringatan ke CS. */
    val smallRunThreshold: Long
        get() = smallRunTiers.maxOf { it.maxQuantityExclusive }

    fun spreadFor(confidence: EstimateConfidence): Double = when (confidence) {
        EstimateConfidence.HIGH -> spreadHigh
        EstimateConfidence.MEDIUM -> spreadMedium
        EstimateConfidence.LOW -> spreadLow
    }

    fun fallbackWeightFor(category: KnitCategory): Double =
        fallbackWeightGramsByCategory[category]
            ?: DEFAULT_FALLBACK_WEIGHTS[category]
            ?: DEFAULT_FALLBACK_WEIGHTS.getValue(KnitCategory.OTHER)

    companion object {
        /**
         * Titik tengah kelas produk rajut dewasa ukuran M pada gauge 7.
         *
         * Perannya semata jaring pengaman hari pertama: tenant yang arsipnya masih kosong tetap
         * dapat angka, bukan error. Begitu arsipnya terisi, estimator memakai median miliknya sendiri.
         */
        val DEFAULT_FALLBACK_WEIGHTS: Map<KnitCategory, Double> = mapOf(
            KnitCategory.CARDIGAN to 480.0,
            KnitCategory.PULLOVER to 450.0,
            KnitCategory.VEST to 300.0,
            KnitCategory.DRESS to 620.0,
            KnitCategory.TOP to 280.0,
            KnitCategory.SCARF_ACCESSORY to 150.0,
            KnitCategory.OTHER to 400.0
        )

        /** Setelan bawaan sistem — dipakai saat tenant belum menyetel apa pun. */
        val SYSTEM_DEFAULT = QuickEstimateTuning()
    }
}
