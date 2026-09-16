package com.eventverse.app.domain.costing

import com.eventverse.app.domain.common.Money
import com.eventverse.app.domain.costing.usecases.EstimateCostingFromAiDesignUseCase
import com.eventverse.app.domain.tenant.TenantId
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Koefisien rumus estimator per tenant (Kontrak 4).
 *
 * Inti yang diuji: dua pabrik dengan arsip **identik** tapi setelan berbeda harus mendapat
 * angka berbeda. Kalau tidak, setelan tenant hanya dekorasi.
 */
class QuickEstimateTuningTest {

    private val tenantId = TenantId("ten-tuning")
    private val now = Clock.System.now()

    private class FakeRepo(private val stored: List<CostingProductBenchmark>) : CostingBenchmarkRepository {
        override suspend fun findById(tenantId: TenantId, id: BenchmarkId) = stored.firstOrNull { it.id == id }
        override suspend fun listAll(tenantId: TenantId, limit: Int) = stored.take(limit)
        override suspend fun findSimilar(tenantId: TenantId, category: KnitCategory, gauge: Int?, limit: Int) =
            stored.filter { it.tenantId == tenantId && it.category == category }.take(limit)
        override suspend fun netWeightSamples(tenantId: TenantId, category: KnitCategory, limit: Int) =
            stored.filter { it.tenantId == tenantId && it.category == category }
                .take(limit).map { it.metrics.netWeightGrams }
        override suspend fun save(benchmark: CostingProductBenchmark) = Unit
        override suspend fun saveBatch(benchmarks: List<CostingProductBenchmark>) = Unit
        override suspend fun findImportedFileNames(tenantId: TenantId) = emptySet<String>()
        override suspend fun count(tenantId: TenantId) = stored.size
    }

    private fun benchmark(
        id: String,
        grams: Double,
        category: KnitCategory = KnitCategory.CARDIGAN,
        yarn: String = "Acrylic 2/32",
        gauge: Int = 7
    ) = CostingProductBenchmark(
            id = BenchmarkId(id),
            tenantId = tenantId,
            styleName = "Artikel $id",
            category = category,
            structure = KnitStructure(yarnType = yarn, gauge = gauge),
            metrics = PhysicalMetrics(netWeightGrams = grams, knittingMinutes = 97),
            pricing = BenchmarkPricing(hppPerUnit = Money(145_000_00)),
            createdAt = now,
            updatedAt = now
        )

    private val rates = QuickEstimateRates(
        yarnPricePerKg = Money(160_000_00),
        laborRatePerKnittingMinute = Money(500_00),
        overheadPerUnit = Money(6_000_00)
    )

    private fun input(quantity: Long = 100L) = QuickEstimateInput(
        orderQuantity = quantity,
        materialCharacter = MaterialCharacter.HANGAT_AKRILIK,
        thickness = KnitThickness.SEDANG,
        silhouette = GarmentSilhouette.CARDIGAN_BUKAAN
    )

    private val archive = listOf(
        benchmark("a", 494.0), benchmark("b", 506.0), benchmark("c", 470.0)
    )

    @Test
    fun yarnWaste_differsPerTenant_soMaterialCostDiffers() = runTest {
        val useCase = EstimateCostingFromAiDesignUseCase(FakeRepo(archive))

        val rapi = useCase(
            tenantId, input(), rates,
            tuning = QuickEstimateTuning(yarnWasteRatio = 0.03)
        ).getOrThrow()
        val rumit = useCase(
            tenantId, input(), rates,
            tuning = QuickEstimateTuning(yarnWasteRatio = 0.12)
        ).getOrThrow()

        val yarnRapi = rapi.breakdown.single { it.label.startsWith("Benang") }.amountPerUnit
        val yarnRumit = rumit.breakdown.single { it.label.startsWith("Benang") }.amountPerUnit

        assertTrue(
            yarnRumit.minorUnits > yarnRapi.minorUnits,
            "Susut 12% (${yarnRumit.minorUnits}) harus lebih mahal dari 3% (${yarnRapi.minorUnits})"
        )
        assertTrue(rapi.breakdown.any { it.explanation.contains("3% susut") })
        assertTrue(rumit.breakdown.any { it.explanation.contains("12% susut") })
    }

    @Test
    fun smallRunTiers_areTenantSpecific() = runTest {
        val useCase = EstimateCostingFromAiDesignUseCase(FakeRepo(archive))

        val ketat = useCase(
            tenantId, input(quantity = 30L), rates,
            tuning = QuickEstimateTuning(smallRunTiers = listOf(SmallRunTier(50L, 1.50)))
        ).getOrThrow()
        val longgar = useCase(
            tenantId, input(quantity = 30L), rates,
            tuning = QuickEstimateTuning(smallRunTiers = listOf(SmallRunTier(50L, 1.05)))
        ).getOrThrow()

        assertTrue(ketat.hppMid.minorUnits > longgar.hppMid.minorUnits)
    }

    @Test
    fun tenantWithNoSmallRunPenalty_pricesTinyOrdersLikeLargeOnes() = runTest {
        val useCase = EstimateCostingFromAiDesignUseCase(FakeRepo(archive))
        val flat = QuickEstimateTuning(smallRunTiers = listOf(SmallRunTier(1L, 1.0)))

        val tiny = useCase(tenantId, input(quantity = 5L), rates, tuning = flat).getOrThrow()
        val bulk = useCase(tenantId, input(quantity = 5_000L), rates, tuning = flat).getOrThrow()

        assertEquals(bulk.hppMid.minorUnits, tiny.hppMid.minorUnits)
    }

    @Test
    fun spreadWidth_isTenantSpecific() = runTest {
        val useCase = EstimateCostingFromAiDesignUseCase(FakeRepo(archive))

        val sempit = useCase(
            tenantId, input(), rates, tuning = QuickEstimateTuning(spreadHigh = 0.02)
        ).getOrThrow()
        val lebar = useCase(
            tenantId, input(), rates, tuning = QuickEstimateTuning(spreadHigh = 0.40)
        ).getOrThrow()

        val rentangSempit = sempit.hppHigh.minorUnits - sempit.hppLow.minorUnits
        val rentangLebar = lebar.hppHigh.minorUnits - lebar.hppLow.minorUnits
        assertTrue(rentangLebar > rentangSempit * 5, "Sempit=$rentangSempit lebar=$rentangLebar")
    }

    @Test
    fun emptyArchive_usesTenantFallbackWeightsNotSystemTable() = runTest {
        val useCase = EstimateCostingFromAiDesignUseCase(FakeRepo(emptyList()))

        val sistem = useCase(tenantId, input(), rates).getOrThrow()
        val handknit = useCase(
            tenantId, input(), rates,
            tuning = QuickEstimateTuning(
                fallbackWeightGramsByCategory = QuickEstimateTuning.DEFAULT_FALLBACK_WEIGHTS +
                    mapOf(KnitCategory.CARDIGAN to 900.0)
            )
        ).getOrThrow()

        assertEquals(480.0, sistem.estimatedWeightGrams)
        assertEquals(900.0, handknit.estimatedWeightGrams)
        assertNotEquals(sistem.hppMid.minorUnits, handknit.hppMid.minorUnits)
    }

    @Test
    fun noSimilarMatch_butOwnArchiveExists_derivesBaselineFromTenantMedian() = runTest {
        // Arsip vest tenant ini semuanya wol gauge 21; permintaannya akrilik gauge 7.
        // Kategorinya cocok (0.45) tapi gauge dan benangnya tidak, jadi skornya di bawah
        // ambang 0.50 yang dipasang tenant — tidak ada satu pun yang "cukup mirip".
        val vestOnly = listOf(
            benchmark("v1", 900.0, KnitCategory.VEST, yarn = "Wool Blend", gauge = 21),
            benchmark("v2", 920.0, KnitCategory.VEST, yarn = "Wool Blend", gauge = 21),
            benchmark("v3", 910.0, KnitCategory.VEST, yarn = "Wool Blend", gauge = 21)
        )
        val useCase = EstimateCostingFromAiDesignUseCase(FakeRepo(vestOnly))

        val result = useCase(
            tenantId,
            input().copy(silhouette = GarmentSilhouette.VEST_TANPA_LENGAN),
            rates,
            tuning = QuickEstimateTuning(minSimilarity = 0.50)
        ).getOrThrow()

        // Median arsip sendiri (910 g), bukan tabel sistem untuk VEST (300 g).
        assertEquals(910.0, result.estimatedWeightGrams)
        assertTrue(result.warnings.any { it.contains("median") }, "Peringatan: ${result.warnings}")
    }

    @Test
    fun ownArchiveBelowMinSamples_fallsBackToTheSystemTable() = runTest {
        val useCase = EstimateCostingFromAiDesignUseCase(
            FakeRepo(listOf(benchmark("v1", 900.0, KnitCategory.VEST, yarn = "Wool Blend", gauge = 21)))
        )

        val result = useCase(
            tenantId,
            input().copy(silhouette = GarmentSilhouette.VEST_TANPA_LENGAN),
            rates,
            tuning = QuickEstimateTuning(minSimilarity = 0.50, archiveFallbackMinSamples = 3)
        ).getOrThrow()

        assertEquals(300.0, result.estimatedWeightGrams, "Satu sampel tidak cukup untuk jadi median")
    }

    @Test
    fun codec_readsTenantNodeParameters_andRecordsProvenance() {
        val resolved = QuickEstimateTuningCodec.resolve(
            mapOf(
                QuickEstimateTuningCodec.KEY_YARN_WASTE_PERCENT to "3",
                QuickEstimateTuningCodec.KEY_SMALL_RUN_TIERS to "20:1.5,40:1.25",
                QuickEstimateTuningCodec.KEY_FALLBACK_GRAMS to "CARDIGAN:900,VEST:520"
            )
        )

        assertEquals(0.03, resolved.tuning.yarnWasteRatio)
        assertEquals(listOf(SmallRunTier(20L, 1.5), SmallRunTier(40L, 1.25)), resolved.tuning.smallRunTiers)
        assertEquals(900.0, resolved.tuning.fallbackWeightFor(KnitCategory.CARDIGAN))
        assertEquals(520.0, resolved.tuning.fallbackWeightFor(KnitCategory.VEST))
        // Kategori yang tidak disebut tenant tetap memakai nilai sistem.
        assertEquals(450.0, resolved.tuning.fallbackWeightFor(KnitCategory.PULLOVER))

        assertEquals(
            CostingParameterSource.PIPELINE_NODE,
            resolved.provenance[QuickEstimateTuningCodec.KEY_YARN_WASTE_PERCENT]
        )
        assertEquals(
            CostingParameterSource.HARDCODED_DEFAULT,
            resolved.provenance[QuickEstimateTuningCodec.KEY_SPREAD_HIGH_PERCENT]
        )
        assertTrue(QuickEstimateTuningCodec.KEY_SPREAD_HIGH_PERCENT in resolved.keysOnSystemDefault)
    }

    @Test
    fun codec_withUnreadableValue_warnsAndKeepsSystemDefault() {
        val resolved = QuickEstimateTuningCodec.resolve(
            mapOf(
                QuickEstimateTuningCodec.KEY_YARN_WASTE_PERCENT to "delapan persen",
                QuickEstimateTuningCodec.KEY_SMALL_RUN_TIERS to "tanpa-titik-dua"
            )
        )

        assertEquals(0.08, resolved.tuning.yarnWasteRatio, "Salah ketik harus jatuh ke default")
        assertEquals(QuickEstimateTuning.SYSTEM_DEFAULT.smallRunTiers, resolved.tuning.smallRunTiers)
        assertTrue(resolved.warnings.size >= 2, "Peringatan: ${resolved.warnings}")
    }

    @Test
    fun codec_withInconsistentCombination_returnsSystemDefaultInsteadOfThrowing() {
        // candidateLimit < maxMatches melanggar invariant VO; estimator tidak boleh mati karenanya.
        val resolved = QuickEstimateTuningCodec.resolveSafely(
            mapOf(
                QuickEstimateTuningCodec.KEY_MAX_MATCHES to "40",
                QuickEstimateTuningCodec.KEY_CANDIDATE_LIMIT to "5"
            )
        )

        assertEquals(QuickEstimateTuning.SYSTEM_DEFAULT, resolved.tuning)
        assertTrue(resolved.warnings.any { it.contains("tidak konsisten") }, "Peringatan: ${resolved.warnings}")
    }
}
