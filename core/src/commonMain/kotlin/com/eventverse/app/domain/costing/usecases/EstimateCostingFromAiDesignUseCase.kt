package com.eventverse.app.domain.costing.usecases

import com.eventverse.app.domain.common.Money
import com.eventverse.app.domain.common.Ratio
import com.eventverse.app.domain.costing.*
import com.eventverse.app.domain.tenant.TenantId
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * Estimasi harga instan dari 5 parameter awam CS + (opsional) pembacaan AI atas gambar mockup.
 *
 * ## Alur berpikirnya
 * ```
 * 5 parameter CS ─┬─▶ cari benchmark termirip ─▶ gramasi & menit rajut acuan
 *                 │                                     │
 *   hint AI ──────┘                                     ▼
 *                                    tarif tenant ─▶ rincian biaya ─▶ rentang HPP ─▶ harga jual
 * ```
 *
 * ## Mengapa benchmark dulu, baru rumus?
 * Variabel paling tidak pasti dalam HPP rajut adalah **gramasi** — bukan harga benang, yang
 * sudah diketahui pasti. Menebak gramasi dari rumus geometri baju selalu meleset; membacanya
 * dari artikel serupa yang benar-benar pernah diproduksi jauh lebih dekat ke kenyataan.
 *
 * ## Mengapa tidak menolak saat tarif kosong?
 * Tenant yang baru mengimpor arsip Excel belum tentu sudah mengisi rate card. Selama ada
 * benchmark, HPP historisnya bisa diskalakan menurut rasio gramasi — kasar, tapi jujur, dan
 * [EstimateBasis] memberi tahu CS bahwa angka itu hasil penskalaan.
 */
class EstimateCostingFromAiDesignUseCase(
    private val benchmarkRepository: CostingBenchmarkRepository
) {

    suspend operator fun invoke(
        tenantId: TenantId,
        input: QuickEstimateInput,
        rates: QuickEstimateRates,
        visionHints: DesignVisionHints? = null,
        /**
         * Koefisien rumus milik tenant ini. Default-nya setelan sistem, sehingga tenant yang
         * belum menyetel apa pun tetap dapat angka yang masuk akal.
         */
        tuning: QuickEstimateTuning = QuickEstimateTuning.SYSTEM_DEFAULT
    ): Result<QuickQuotationEstimateResult> = runCatching {
        val warnings = mutableListOf<String>()

        // Pilihan CS selalu menang atas pembacaan AI; ketidaksesuaian hanya dilaporkan.
        val category = input.silhouette.category
        visionHints
            ?.takeIf { it.confidence >= tuning.visionTrustThreshold }
            ?.detectedCategory
            ?.takeIf { it != category }
            ?.let { detected ->
                warnings += "AI membaca gambar sebagai '${detected.displayName}', " +
                    "berbeda dari pilihan CS '${category.displayName}'. " +
                    "Perhitungan memakai pilihan CS."
            }

        val requestedGauge = input.thickness.gauge
        val yarnKeyword = input.materialCharacter.yarnKeyword

        val matches = benchmarkRepository
            .findSimilar(tenantId, category, requestedGauge, tuning.candidateLimit)
            .map { BenchmarkMatch(it, it.similarityTo(category, requestedGauge, yarnKeyword, tuning)) }
            .filter { it.similarity >= tuning.minSimilarity }
            .sortedByDescending { it.similarity }
            .take(tuning.maxMatches)

        val estimatedWeight = estimateWeightGrams(tenantId, matches, input, tuning, warnings)
        val estimatedMinutes = estimateKnittingMinutes(matches, estimatedWeight, tuning)
        val confidence = resolveConfidence(matches, tuning)

        val basis: EstimateBasis
        val breakdown: List<EstimateLine>
        val hppMid: Money
        when {
            rates.supportsBottomUp -> {
                basis = EstimateBasis.BOTTOM_UP_WITH_BENCHMARK
                breakdown = buildBottomUpBreakdown(input, rates, estimatedWeight, estimatedMinutes, tuning)
                hppMid = breakdown.sumPerUnit()
            }

            matches.isNotEmpty() -> {
                basis = EstimateBasis.SCALED_FROM_BENCHMARK
                breakdown = emptyList()
                hppMid = scaleBenchmarkHpp(matches.first(), estimatedWeight)
                warnings += "Rate card tenant belum lengkap (harga benang / tarif rajut). " +
                    "Angka diskalakan dari HPP artikel '${matches.first().benchmark.styleName}'."
            }

            else -> error(
                "Estimasi tidak dapat dihitung: arsip benchmark kosong dan rate card tenant " +
                    "belum mengisi harga benang serta tarif rajut per menit."
            )
        }

        require(hppMid.minorUnits > 0L) {
            "Estimasi HPP menghasilkan angka nol - periksa isian rate card tenant."
        }

        val spread = tuning.spreadFor(confidence)
        val hppLow = hppMid.scaleBy(1.0 - spread)
        val hppHigh = hppMid.scaleBy(1.0 + spread)

        val margin = rates.marginRatio ?: tuning.defaultMargin
        val priceLow = hppLow + hppLow.times(margin)
        val priceHigh = hppHigh + hppHigh.times(margin)

        if (input.orderQuantity < tuning.smallRunThreshold) {
            warnings += "Kuantitas ${input.orderQuantity} pcs tergolong run kecil; " +
                "biaya setting mesin dan overhead per pcs sudah dinaikkan sesuai tier."
        }

        QuickQuotationEstimateResult(
            input = input,
            basis = basis,
            confidence = confidence,
            estimatedWeightGrams = estimatedWeight,
            estimatedKnittingMinutes = estimatedMinutes,
            hppLow = hppLow,
            hppHigh = hppHigh,
            suggestedPriceLow = priceLow,
            suggestedPriceHigh = priceHigh,
            appliedMargin = margin,
            breakdown = breakdown,
            matches = matches,
            visionHints = visionHints,
            warnings = warnings
        )
    }

    // ── Gramasi ──────────────────────────────────────────────────────────────────────────

    /**
     * Rata-rata tertimbang gramasi benchmark, dinormalisasi ke gauge yang diminta.
     *
     * Normalisasi gauge penting: cardigan 12G seberat 300g yang diminta ulang sebagai 5G tidak
     * akan tetap 300g — kainnya jauh lebih tebal. [KnitThickness.weightFactor] membawa gramasi
     * benchmark ke skala yang diminta sebelum dirata-ratakan.
     */
    private suspend fun estimateWeightGrams(
        tenantId: TenantId,
        matches: List<BenchmarkMatch>,
        input: QuickEstimateInput,
        tuning: QuickEstimateTuning,
        warnings: MutableList<String>
    ): Double {
        if (matches.isNotEmpty()) return weightedArchiveWeight(matches, input)

        // Tidak ada artikel yang cukup mirip — tapi arsip tenant belum tentu kosong. Median
        // kategori yang sama milik pabrik ini sendiri jauh lebih relevan daripada tabel baku
        // sistem, yang dikalibrasi untuk rajut mesin ukuran M dan salah untuk handknit chunky.
        val ownSamples = benchmarkRepository.netWeightSamples(tenantId, input.silhouette.category)
        if (ownSamples.size >= tuning.archiveFallbackMinSamples) {
            val median = ownSamples.median()
            warnings += "Tidak ada artikel yang cukup mirip; gramasi baku diambil dari median " +
                "${ownSamples.size} artikel '${input.silhouette.category.displayName}' milik pabrik ini " +
                "(${median.roundToInt()} g). Rentang harga dilebarkan."
            return normaliseThickness(median, benchGauge = null, input = input)
        }

        warnings += "Belum ada artikel historis yang mirip di arsip. " +
            "Estimasi memakai gramasi baku sistem dan rentangnya dilebarkan."
        return tuning.fallbackWeightFor(input.silhouette.category) * input.thickness.weightFactor
    }

    /**
     * Rata-rata tertimbang gramasi benchmark, dinormalisasi ke gauge yang diminta.
     *
     * Normalisasi gauge penting: cardigan 12G seberat 300g yang diminta ulang sebagai 5G tidak
     * akan tetap 300g — kainnya jauh lebih tebal. [KnitThickness.weightFactor] membawa gramasi
     * benchmark ke skala yang diminta sebelum dirata-ratakan.
     */
    private fun weightedArchiveWeight(matches: List<BenchmarkMatch>, input: QuickEstimateInput): Double {
        var weightedSum = 0.0
        var weightTotal = 0.0
        matches.forEach { match ->
            val normalised = normaliseThickness(
                grams = match.benchmark.metrics.netWeightGrams,
                benchGauge = match.benchmark.structure.gauge,
                input = input
            )
            weightedSum += normalised * match.similarity
            weightTotal += match.similarity
        }
        val base = if (weightTotal > 0.0) weightedSum / weightTotal else 0.0
        return (base * 100.0).roundToInt() / 100.0
    }

    private fun normaliseThickness(grams: Double, benchGauge: Int?, input: QuickEstimateInput): Double =
        grams * (input.thickness.weightFactor / factorForGauge(benchGauge))

    // ── Menit rajut ──────────────────────────────────────────────────────────────────────

    /**
     * Menit mesin diturunkan dari **produktivitas** (gram per menit) benchmark, bukan dari
     * menitnya langsung. Dua artikel dengan gramasi berbeda tapi mesin dan gauge sama punya
     * gram/menit yang mirip; menitnya tidak.
     */
    private fun estimateKnittingMinutes(
        matches: List<BenchmarkMatch>,
        weightGrams: Double,
        tuning: QuickEstimateTuning
    ): Int {
        val productivities = matches.mapNotNull { match ->
            match.benchmark.gramsPerKnittingMinute?.let { it to match.similarity }
        }
        val gramsPerMinute = if (productivities.isEmpty()) {
            tuning.defaultGramsPerMinute
        } else {
            val sum = productivities.sumOf { (value, weight) -> value * weight }
            val total = productivities.sumOf { (_, weight) -> weight }
            if (total > 0.0) sum / total else tuning.defaultGramsPerMinute
        }
        return (weightGrams / gramsPerMinute).roundToInt().coerceAtLeast(1)
    }

    // ── Biaya ────────────────────────────────────────────────────────────────────────────

    private fun buildBottomUpBreakdown(
        input: QuickEstimateInput,
        rates: QuickEstimateRates,
        weightGrams: Double,
        minutes: Int,
        tuning: QuickEstimateTuning
    ): List<EstimateLine> {
        val lines = mutableListOf<EstimateLine>()
        val smallRunFactor = tuning.smallRunFactorFor(input.orderQuantity)

        rates.yarnPricePerKg?.let { pricePerKg ->
            val effectivePerKg = pricePerKg.scaleBy(input.materialCharacter.yarnPriceFactor)
            // Susut benang: sisa gulungan dan perca rajut yang tidak pernah jadi baju.
            val grossGrams = weightGrams * (1.0 + tuning.yarnWasteRatio)
            lines += EstimateLine(
                label = "Benang (${input.materialCharacter.displayName})",
                amountPerUnit = effectivePerKg.scaleBy(grossGrams / 1000.0),
                explanation = "${weightGrams.roundToInt()} g netto + ${(tuning.yarnWasteRatio * 100).roundToInt()}% susut"
            )
        }

        rates.laborRatePerKnittingMinute?.let { rate ->
            lines += EstimateLine(
                label = "Rajut & jahit",
                amountPerUnit = rate.times(minutes.toLong()).scaleBy(smallRunFactor),
                explanation = "$minutes menit mesin" +
                    if (smallRunFactor > 1.0) " × tier run kecil ${formatFactor(smallRunFactor)}" else ""
            )
        }

        if (input.trims.buttonCount > 0) {
            rates.buttonUnitPrice?.let { price ->
                lines += EstimateLine(
                    label = "Kancing",
                    amountPerUnit = price.times(input.trims.buttonCount.toLong()),
                    explanation = "${input.trims.buttonCount} pcs"
                )
            }
        }

        if (input.trims.hasWovenLabel) {
            rates.labelUnitPrice?.let { lines += EstimateLine("Label woven", it) }
        }
        if (input.trims.hasHangtag) {
            rates.hangtagUnitPrice?.let { lines += EstimateLine("Hangtag", it) }
        }

        rates.overheadPerUnit?.let { overhead ->
            lines += EstimateLine(
                label = "Overhead pabrik",
                amountPerUnit = overhead.scaleBy(smallRunFactor),
                explanation = if (smallRunFactor > 1.0) "tier run kecil ${formatFactor(smallRunFactor)}" else ""
            )
        }

        return lines
    }

    /** HPP historis diskalakan menurut rasio gramasi — benang mendominasi HPP rajut. */
    private fun scaleBenchmarkHpp(match: BenchmarkMatch, estimatedWeight: Double): Money {
        val ratio = estimatedWeight / match.benchmark.metrics.netWeightGrams
        return match.benchmark.pricing.hppPerUnit.scaleBy(ratio)
    }

    private fun resolveConfidence(
        matches: List<BenchmarkMatch>,
        tuning: QuickEstimateTuning
    ): EstimateConfidence {
        val top = matches.firstOrNull()?.similarity ?: 0.0
        // Ambang "cukup mirip" ikut bergerak bersama bobot kemiripan tenant: kalau tenant
        // mengecilkan bobot benang, skor tertingginya juga turun, dan ambang tetap 0.70 akan
        // membuat setiap estimasi turun ke MEDIUM selamanya.
        val highThreshold = (tuning.categoryWeight + tuning.gaugeWeight + tuning.yarnWeight) * 0.70
        return when {
            matches.size >= 3 && top >= highThreshold -> EstimateConfidence.HIGH
            matches.isNotEmpty() -> EstimateConfidence.MEDIUM
            else -> EstimateConfidence.LOW
        }
    }

    private fun factorForGauge(gauge: Int?): Double {
        if (gauge == null) return 1.0
        return KnitThickness.entries.minByOrNull { abs(it.gauge - gauge) }?.weightFactor ?: 1.0
    }

    private fun formatFactor(factor: Double): String = "${(factor * 100).roundToInt()}%"

}

/** Median, bukan rata-rata: satu artikel salah input (40.000 g) tidak menggeser hasilnya. */
private fun List<Double>.median(): Double {
    val sorted = sorted()
    val mid = sorted.size / 2
    return if (sorted.size % 2 == 0) (sorted[mid - 1] + sorted[mid]) / 2.0 else sorted[mid]
}

private fun List<EstimateLine>.sumPerUnit(): Money =
    fold(Money(0L)) { acc, line -> Money(acc.minorUnits + line.amountPerUnit.minorUnits, line.amountPerUnit.currency) }

private fun Money.scaleBy(factor: Double): Money =
    Money((minorUnits.toDouble() * factor).roundToLong(), currency)
