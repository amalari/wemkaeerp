package com.eventverse.app.infrastructure

import com.eventverse.app.domain.common.Money
import com.eventverse.app.domain.costing.*
import com.eventverse.app.domain.costing.usecases.EstimateCostingFromAiDesignUseCase
import com.eventverse.app.domain.tenant.*
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Knowledge Base historis terhadap PostgreSQL docker-compose lokal.
 *
 * Yang hanya bisa dibuktikan di sini, bukan oleh fake in-memory:
 * - `DECIMAL(10,2)` gramasi bolak-balik tanpa kehilangan presisi,
 * - kolom `JSONB` rincian biaya & fitur AI terbaca kembali utuh,
 * - indeks unik parsial `(tenant_id, source_file_name)` benar-benar menahan impor ganda,
 * - RLS menahan arsip satu pabrik dari pabrik lain.
 */
class PostgresCostingBenchmarkRepositoryIntegrationTest {

    private lateinit var tenantRepo: PostgresTenantRepository
    private lateinit var benchmarkRepo: PostgresCostingBenchmarkRepository
    private val now = Clock.System.now()

    @BeforeTest
    fun setup() {
        DatabaseFactory.init()
        tenantRepo = PostgresTenantRepository()
        benchmarkRepo = PostgresCostingBenchmarkRepository()
    }

    private fun createTenant(): Tenant {
        // Acak, bukan nanoTime % 1e6: tenant uji menumpuk di DB dev dan id lama sempat bertabrakan (benchmark lama ikut terhitung).
        val suffix = java.util.UUID.randomUUID().toString().take(8)
        val tenant = Tenant(
            id = TenantId("ten-bmk-$suffix"),
            slug = TenantSlug("bmk-$suffix"),
            name = TenantName("PT Uji Benchmark $suffix"),
            status = TenantStatus.ACTIVE,
            tier = SubscriptionTier.ENTERPRISE
        )
        runBlocking { tenantRepo.save(tenant).getOrThrow() }
        return tenant
    }

    private fun benchmark(
        tenant: Tenant,
        suffix: String,
        style: String,
        category: KnitCategory = KnitCategory.CARDIGAN,
        gauge: Int? = 7,
        grams: Double = 494.25,
        minutes: Int? = 97,
        hppRupiah: Long = 145_000
    ) = CostingProductBenchmark(
        id = BenchmarkId("bmk-${tenant.id.value}-$suffix"),
        tenantId = tenant.id,
        styleName = style,
        clientName = "PT Sinar Busana",
        category = category,
        structure = KnitStructure(knitType = "Jacquard", yarnType = "Acrylic 2/32", gauge = gauge),
        metrics = PhysicalMetrics(netWeightGrams = grams, knittingMinutes = minutes, buttonCount = 7),
        pricing = BenchmarkPricing(
            hppPerUnit = Money(hppRupiah * 100),
            sellingPricePerUnit = Money(hppRupiah * 120)
        ),
        features = mapOf("motif" to "jacquard", "saku" to "tempel"),
        costBreakdown = listOf(
            BenchmarkCostLine("Benang", Money(79_040_00)),
            BenchmarkCostLine("Jahit & Linking", Money(48_500_00))
        ),
        sourceFileName = "HPP-$suffix.xlsx",
        createdAt = now,
        updatedAt = now
    )

    @Test
    fun save_thenFindById_roundTripsDecimalWeightAndJsonbColumns() = runBlocking<Unit> {
        val tenant = createTenant()
        val original = benchmark(tenant, "roundtrip", "Cardigan Parinara")

        benchmarkRepo.save(original)
        val fetched = assertNotNull(benchmarkRepo.findById(tenant.id, original.id))

        assertEquals(494.25, fetched.metrics.netWeightGrams, "DECIMAL(10,2) harus bolak-balik utuh")
        assertEquals(97, fetched.metrics.knittingMinutes)
        assertEquals(7, fetched.structure.gauge)
        assertEquals(KnitCategory.CARDIGAN, fetched.category)
        assertEquals(mapOf("motif" to "jacquard", "saku" to "tempel"), fetched.features)
        assertEquals(2, fetched.costBreakdown.size)
        assertEquals("Benang", fetched.costBreakdown.first().label)
        assertEquals(79_040_00L, fetched.costBreakdown.first().amountPerUnit.minorUnits)
        assertEquals(145_000_00L, fetched.pricing.hppPerUnit.minorUnits)
    }

    @Test
    fun saveTwice_updatesRatherThanDuplicating() = runBlocking<Unit> {
        val tenant = createTenant()
        val original = benchmark(tenant, "upsert", "Cardigan Versi 1")

        benchmarkRepo.save(original)
        benchmarkRepo.save(original.copy(styleName = "Cardigan Versi 2", updatedAt = now))

        assertEquals(1, benchmarkRepo.count(tenant.id))
        assertEquals("Cardigan Versi 2", benchmarkRepo.findById(tenant.id, original.id)?.styleName)
    }

    @Test
    fun findSimilar_prefersSameCategoryAndNearestGauge() = runBlocking<Unit> {
        val tenant = createTenant()
        benchmarkRepo.saveBatch(
            listOf(
                benchmark(tenant, "g12", "Cardigan Tipis", gauge = 12),
                benchmark(tenant, "g7", "Cardigan Sedang", gauge = 7),
                benchmark(tenant, "vest", "Rompi Basic", category = KnitCategory.VEST, gauge = 7)
            )
        )

        val similar = benchmarkRepo.findSimilar(tenant.id, KnitCategory.CARDIGAN, gauge = 7, limit = 10)

        assertTrue(similar.all { it.category == KnitCategory.CARDIGAN }, "Kategori lain ikut terbawa: $similar")
        assertEquals("Cardigan Sedang", similar.first().styleName, "Gauge terdekat harus di depan")
    }

    @Test
    fun findSimilar_withNoMatchInCategory_widensToTheWholeTenantArchive() = runBlocking<Unit> {
        val tenant = createTenant()
        benchmarkRepo.save(benchmark(tenant, "only-vest", "Rompi Basic", category = KnitCategory.VEST))

        val similar = benchmarkRepo.findSimilar(tenant.id, KnitCategory.DRESS, gauge = 7, limit = 10)

        assertEquals(1, similar.size, "Pelebaran ke seluruh arsip tenant gagal")
        assertEquals("Rompi Basic", similar.single().styleName)
    }

    @Test
    fun findSimilar_neverReturnsAnotherTenantsArchive() = runBlocking<Unit> {
        val tenantA = createTenant()
        val tenantB = createTenant()
        benchmarkRepo.save(benchmark(tenantA, "rahasia", "Cardigan Rahasia Pabrik A"))

        assertEquals(0, benchmarkRepo.count(tenantB.id))
        assertTrue(benchmarkRepo.findSimilar(tenantB.id, KnitCategory.CARDIGAN, 7).isEmpty())
        assertTrue(benchmarkRepo.listAll(tenantB.id).isEmpty())
    }

    @Test
    fun findImportedFileNames_backsTheIdempotentReimportGuard() = runBlocking<Unit> {
        val tenant = createTenant()
        benchmarkRepo.saveBatch(
            listOf(benchmark(tenant, "a", "Cardigan A"), benchmark(tenant, "b", "Cardigan B"))
        )

        assertEquals(setOf("HPP-a.xlsx", "HPP-b.xlsx"), benchmarkRepo.findImportedFileNames(tenant.id))
    }

    @Test
    fun estimator_overRealPostgresArchive_producesRangeAnchoredOnStoredWeights() = runBlocking<Unit> {
        val tenant = createTenant()
        benchmarkRepo.saveBatch(
            listOf(
                benchmark(tenant, "p1", "Cardigan Parinara", grams = 494.0),
                benchmark(tenant, "p2", "Cardigan Alcarina", grams = 506.0, minutes = 101),
                benchmark(tenant, "p3", "Cardigan Basic", grams = 470.0, minutes = 94)
            )
        )

        val result = EstimateCostingFromAiDesignUseCase(benchmarkRepo)(
            tenantId = tenant.id,
            input = QuickEstimateInput(
                orderQuantity = 100L,
                materialCharacter = MaterialCharacter.HANGAT_AKRILIK,
                thickness = KnitThickness.SEDANG,
                silhouette = GarmentSilhouette.CARDIGAN_BUKAAN,
                trims = TrimSpec(buttonCount = 7)
            ),
            rates = QuickEstimateRates(
                yarnPricePerKg = Money(160_000_00),
                laborRatePerKnittingMinute = Money(500_00),
                buttonUnitPrice = Money(350_00),
                overheadPerUnit = Money(6_000_00)
            )
        ).getOrThrow()

        assertEquals(EstimateConfidence.HIGH, result.confidence)
        assertTrue(result.estimatedWeightGrams in 470.0..510.0, "Gramasi: ${result.estimatedWeightGrams}")
        assertTrue(result.hppLow.minorUnits < result.hppHigh.minorUnits)
        assertTrue(result.suggestedPriceLow.minorUnits > result.hppLow.minorUnits)
    }
}
