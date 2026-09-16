package com.eventverse.app.domain.costing

import com.eventverse.app.domain.costing.usecases.ImportHistoricalCostingUseCase
import com.eventverse.app.domain.costing.usecases.ParsedHistoricalCosting
import com.eventverse.app.domain.tenant.TenantId
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ImportHistoricalCostingTest {

    private val tenantId = TenantId("ten-import")

    private fun repository() = object : CostingBenchmarkRepository {
        val stored = mutableListOf<CostingProductBenchmark>()

        override suspend fun findById(tenantId: TenantId, id: BenchmarkId) =
            stored.firstOrNull { it.id == id }

        override suspend fun listAll(tenantId: TenantId, limit: Int) = stored.take(limit)

        override suspend fun findSimilar(
            tenantId: TenantId,
            category: KnitCategory,
            gauge: Int?,
            limit: Int
        ) = stored.filter { it.category == category }.take(limit)

        override suspend fun netWeightSamples(
            tenantId: TenantId,
            category: KnitCategory,
            limit: Int
        ) = stored.filter { it.category == category }.take(limit).map { it.metrics.netWeightGrams }

        override suspend fun save(benchmark: CostingProductBenchmark) {
            stored += benchmark
        }

        override suspend fun saveBatch(benchmarks: List<CostingProductBenchmark>) {
            stored += benchmarks
        }

        override suspend fun findImportedFileNames(tenantId: TenantId) =
            stored.mapNotNull { it.sourceFileName.takeIf(String::isNotBlank) }.toSet()

        override suspend fun count(tenantId: TenantId) = stored.size
    }

    private fun useCaseOver(repo: CostingBenchmarkRepository): ImportHistoricalCostingUseCase {
        var counter = 0
        return ImportHistoricalCostingUseCase(repo, idGenerator = { "bmk-${++counter}" })
    }

    private fun record(
        file: String,
        style: String? = "Cardigan Parinara",
        grams: Double? = 494.0,
        hpp: Long? = 145_000_00
    ) = ParsedHistoricalCosting(
        sourceFileName = file,
        styleName = style,
        netWeightGrams = grams,
        knittingMinutes = 97,
        hppPerUnitMinor = hpp,
        gauge = 7
    )

    @Test
    fun import_validRecord_storesBenchmarkWithArchiveMetrics() = runTest {
        val repo = repository()
        val report = useCaseOver(repo)(tenantId, listOf(record("HPP-PARINARA.xlsx"))).getOrThrow()

        assertEquals(1, report.importedCount)
        assertEquals(0, report.skippedCount)

        val saved = report.imported.single()
        assertEquals(494.0, saved.metrics.netWeightGrams)
        assertEquals(97, saved.metrics.knittingMinutes)
        assertEquals(KnitCategory.CARDIGAN, saved.category)
        assertEquals("HPP-PARINARA.xlsx", saved.sourceFileName)
    }

    @Test
    fun import_categoryInferredFromStyleNameWhenNotStated() = runTest {
        val repo = repository()
        val report = useCaseOver(repo)(
            tenantId,
            listOf(record("a.xlsx", style = "SWEATER RAJUT ANAK"))
        ).getOrThrow()

        assertEquals(KnitCategory.PULLOVER, report.imported.single().category)
    }

    @Test
    fun import_recordMissingWeight_isSkippedWithoutFailingTheBatch() = runTest {
        val repo = repository()
        val report = useCaseOver(repo)(
            tenantId,
            listOf(
                record("good.xlsx"),
                record("no-weight.xlsx", grams = null),
                record("no-hpp.xlsx", hpp = null)
            )
        ).getOrThrow()

        assertEquals(1, report.importedCount)
        assertEquals(2, report.skippedCount)
        assertTrue(report.skipped.any { it.sourceFileName == "no-weight.xlsx" })
        assertTrue(report.skipped.any { it.reason.contains("HPP", ignoreCase = true) })
    }

    @Test
    fun import_rerunOnSameFolder_doesNotDuplicateArchive() = runTest {
        val repo = repository()
        val useCase = useCaseOver(repo)
        val records = listOf(record("HPP-A.xlsx"), record("HPP-B.xlsx"))

        val first = useCase(tenantId, records).getOrThrow()
        val second = useCase(tenantId, records).getOrThrow()

        assertEquals(2, first.importedCount)
        assertEquals(0, second.importedCount)
        assertEquals(2, second.skippedCount)
        assertEquals(2, repo.count(tenantId))
    }

    @Test
    fun import_withReimportRequested_ignoresTheDuplicateGuard() = runTest {
        val repo = repository()
        val useCase = useCaseOver(repo)
        val records = listOf(record("HPP-A.xlsx"))

        useCase(tenantId, records).getOrThrow()
        val second = useCase(tenantId, records, skipAlreadyImported = false).getOrThrow()

        assertEquals(1, second.importedCount)
    }

    @Test
    fun import_implausibleWeight_isRejectedByDomainInvariant() = runTest {
        val repo = repository()
        val report = useCaseOver(repo)(
            tenantId,
            listOf(record("halusinasi.xlsx", grams = 40_000.0))
        ).getOrThrow()

        assertEquals(0, report.importedCount)
        assertEquals(1, report.skippedCount)
    }
}
