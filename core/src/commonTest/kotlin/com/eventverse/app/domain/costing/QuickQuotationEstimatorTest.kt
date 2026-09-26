package com.eventverse.app.domain.costing

import com.eventverse.app.domain.common.Money
import com.eventverse.app.domain.common.Ratio
import com.eventverse.app.domain.costing.usecases.EstimateCostingFromAiDesignUseCase
import com.eventverse.app.domain.tenant.TenantId
import kotlinx.datetime.Clock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlinx.coroutines.test.runTest

private class FakeBenchmarkRepository(
    private val stored: MutableList<CostingProductBenchmark> = mutableListOf()
) : CostingBenchmarkRepository {
    override suspend fun findById(tenantId: TenantId, id: BenchmarkId) =
        stored.firstOrNull { it.tenantId == tenantId && it.id == id }

    override suspend fun listAll(tenantId: TenantId, limit: Int) =
        stored.filter { it.tenantId == tenantId }.take(limit)

    override suspend fun findSimilar(
        tenantId: TenantId,
        category: KnitCategory,
        gauge: Int?,
        limit: Int
    ): List<CostingProductBenchmark> {
        val scoped = stored.filter { it.tenantId == tenantId }
        return scoped.filter { it.category == category }.ifEmpty { scoped }.take(limit)
    }

    override suspend fun netWeightSamples(
        tenantId: TenantId,
        category: KnitCategory,
        limit: Int
    ): List<Double> = stored
        .filter { it.tenantId == tenantId && it.category == category }
        .take(limit)
        .map { it.metrics.netWeightGrams }

    override suspend fun save(benchmark: CostingProductBenchmark) {
        stored.removeAll { it.id == benchmark.id }
        stored += benchmark
    }

    override suspend fun saveBatch(benchmarks: List<CostingProductBenchmark>) {
        benchmarks.forEach { save(it) }
    }

    override suspend fun findImportedFileNames(tenantId: TenantId) =
        stored.filter { it.tenantId == tenantId }.mapNotNull { it.sourceFileName.takeIf(String::isNotBlank) }.toSet()

    override suspend fun count(tenantId: TenantId) = stored.count { it.tenantId == tenantId }
}

class QuickQuotationEstimatorTest {

    private val tenantId = TenantId("ten-estimator")
    private val now = Clock.System.now()

    private fun benchmark(
        id: String,
        style: String,
        category: KnitCategory,
        gauge: Int?,
        grams: Double,
        minutes: Int?,
        hppRupiah: Long,
        yarn: String = "Acrylic 2/32"
    ) = CostingProductBenchmark(
        id = BenchmarkId(id),
        tenantId = tenantId,
        styleName = style,
        category = category,
        structure = KnitStructure(yarnType = yarn, gauge = gauge),
        metrics = PhysicalMetrics(netWeightGrams = grams, knittingMinutes = minutes),
        pricing = BenchmarkPricing(hppPerUnit = Money(hppRupiah * 100)),
        sourceFileName = "$id.xlsx",
        createdAt = now,
        updatedAt = now
    )

    private val cardiganArchive = listOf(
        benchmark("bmk-1", "Cardigan Parinara", KnitCategory.CARDIGAN, 7, 494.0, 97, 145_000),
        benchmark("bmk-2", "Cardigan Alcarina", KnitCategory.CARDIGAN, 7, 506.0, 101, 150_000),
        benchmark("bmk-3", "Cardigan Basic", KnitCategory.CARDIGAN, 7, 470.0, 94, 138_000)
    )

    private val fullRates = QuickEstimateRates(
        yarnPricePerKg = Money(160_000 * 100),
        laborRatePerKnittingMinute = Money(500 * 100),
        buttonUnitPrice = Money(350 * 100),
        labelUnitPrice = Money(900 * 100),
        overheadPerUnit = Money(6_000 * 100),
        marginRatio = Ratio.percent(20.0)
    )

    private fun input(
        quantity: Long = 100L,
        thickness: KnitThickness = KnitThickness.SEDANG,
        buttons: Int = 7
    ) = QuickEstimateInput(
        orderQuantity = quantity,
        materialCharacter = MaterialCharacter.HANGAT_AKRILIK,
        thickness = thickness,
        silhouette = GarmentSilhouette.CARDIGAN_BUKAAN,
        trims = TrimSpec(buttonCount = buttons)
    )

    @Test
    fun estimate_withThreeCloseBenchmarks_reportsHighConfidenceAndArchiveWeight() = runTest {
        val useCase = EstimateCostingFromAiDesignUseCase(FakeBenchmarkRepository(cardiganArchive.toMutableList()))

        val result = useCase(tenantId, input(), fullRates).getOrThrow()

        assertEquals(EstimateConfidence.HIGH, result.confidence)
        assertEquals(EstimateBasis.BOTTOM_UP_WITH_BENCHMARK, result.basis)
        // Rata-rata arsip 470/494/506 g pada gauge yang sama — estimasi harus mendarat di antaranya.
        assertTrue(
            result.estimatedWeightGrams in 470.0..510.0,
            "Gramasi estimasi ${result.estimatedWeightGrams} keluar dari rentang arsip"
        )
        assertTrue(result.hppLow < result.hppHigh)
        assertTrue(result.suggestedPriceLow > result.hppLow, "Harga jual harus di atas HPP")
    }

    @Test
    fun estimate_withThickerKnitRequested_scalesWeightUpFromArchive() = runTest {
        val useCase = EstimateCostingFromAiDesignUseCase(FakeBenchmarkRepository(cardiganArchive.toMutableList()))

        val medium = useCase(tenantId, input(thickness = KnitThickness.SEDANG), fullRates).getOrThrow()
        val thick = useCase(tenantId, input(thickness = KnitThickness.TEBAL), fullRates).getOrThrow()

        assertTrue(
            thick.estimatedWeightGrams > medium.estimatedWeightGrams,
            "Rajut tebal (${thick.estimatedWeightGrams} g) harus lebih berat dari sedang (${medium.estimatedWeightGrams} g)"
        )
    }

    @Test
    fun estimate_withEmptyArchive_fallsBackToDefaultWeightAndLowConfidence() = runTest {
        val useCase = EstimateCostingFromAiDesignUseCase(FakeBenchmarkRepository())

        val result = useCase(tenantId, input(), fullRates).getOrThrow()

        assertEquals(EstimateConfidence.LOW, result.confidence)
        assertTrue(result.matches.isEmpty())
        assertTrue(result.warnings.any { it.contains("arsip", ignoreCase = true) })
        assertTrue(result.estimatedWeightGrams > 0.0)
    }

    @Test
    fun estimate_withoutRateCard_scalesHistoricalHppInstead() = runTest {
        val useCase = EstimateCostingFromAiDesignUseCase(FakeBenchmarkRepository(cardiganArchive.toMutableList()))

        val result = useCase(tenantId, input(), QuickEstimateRates()).getOrThrow()

        assertEquals(EstimateBasis.SCALED_FROM_BENCHMARK, result.basis)
        assertTrue(result.breakdown.isEmpty(), "Jalur penskalaan tidak punya rincian biaya")
        assertTrue(result.hppLow.minorUnits > 0L)
        assertTrue(result.warnings.any { it.contains("Rate card", ignoreCase = true) })
    }

    @Test
    fun estimate_withoutRatesAndWithoutArchive_failsWithActionableMessage() = runTest {
        val useCase = EstimateCostingFromAiDesignUseCase(FakeBenchmarkRepository())

        val result = useCase(tenantId, input(), QuickEstimateRates())

        assertTrue(result.isFailure)
        val message = result.exceptionOrNull()?.message.orEmpty()
        assertTrue(message.contains("benchmark") && message.contains("rate card"), "Pesan: $message")
    }

    @Test
    fun estimate_withSmallRun_costsMorePerPieceThanLargeRun() = runTest {
        val useCase = EstimateCostingFromAiDesignUseCase(FakeBenchmarkRepository(cardiganArchive.toMutableList()))

        val small = useCase(tenantId, input(quantity = 12L), fullRates).getOrThrow()
        val large = useCase(tenantId, input(quantity = 500L), fullRates).getOrThrow()

        assertTrue(
            small.hppMid.minorUnits > large.hppMid.minorUnits,
            "Run 12 pcs (${small.hppMid.minorUnits}) harus lebih mahal per pcs daripada 500 pcs (${large.hppMid.minorUnits})"
        )
        assertTrue(small.warnings.any { it.contains("run kecil", ignoreCase = true) })
    }

    @Test
    fun estimate_visionHintDisagreeingWithCsChoice_warnsButKeepsCsChoice() = runTest {
        val useCase = EstimateCostingFromAiDesignUseCase(FakeBenchmarkRepository(cardiganArchive.toMutableList()))

        val result = useCase(
            tenantId = tenantId,
            input = input(),
            rates = fullRates,
            visionHints = DesignVisionHints(detectedCategory = KnitCategory.VEST, confidence = 0.9)
        ).getOrThrow()

        assertTrue(result.warnings.any { it.contains("AI membaca gambar") })
        // Pilihan CS (cardigan) tetap dipakai, jadi acuannya tetap arsip cardigan.
        assertTrue(result.matches.all { it.benchmark.category == KnitCategory.CARDIGAN })
    }

    @Test
    fun whatsAppSummary_statesRangeAndNeverPresentsASingleFinalPrice() = runTest {
        val useCase = EstimateCostingFromAiDesignUseCase(FakeBenchmarkRepository(cardiganArchive.toMutableList()))
        val result = useCase(tenantId, input(), fullRates).getOrThrow()

        val summary = result.toWhatsAppSummary { money -> "Rp ${money.minorUnits / 100}" }

        assertTrue(summary.contains("–"), "Ringkasan harus memuat rentang harga")
        assertTrue(summary.contains("estimasi awal"), "Ringkasan wajib memuat pagar komersial")
        assertTrue(summary.contains("100 pcs"))
        assertFalse(summary.contains("null"))
    }
}
