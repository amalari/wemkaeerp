package com.eventverse.app.infrastructure

import com.eventverse.app.domain.costing.*
import com.eventverse.app.domain.pipeline.CostingBehavior
import com.eventverse.app.domain.tenant.TenantId
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.time.Duration.Companion.days

class InMemoryCostingSheetRepository : CostingSheetRepository {
    private val sheets = mutableMapOf<Pair<String, String>, CostingSheet>()
    private var sequenceCounter = 0L

    override suspend fun findById(tenantId: TenantId, id: CostingSheetId): CostingSheet? =
        sheets[tenantId.value to id.value]

    override suspend fun findAll(tenantId: TenantId): List<CostingSheet> =
        sheets.values
            .filter { it.tenantId == tenantId }
            .sortedByDescending { it.updatedAt }

    override suspend fun findByTechPack(tenantId: TenantId, techPackId: String): List<CostingSheet> =
        sheets.values
            .filter { it.tenantId == tenantId && it.techPackId == techPackId }
            .sortedByDescending { it.updatedAt }

    override suspend fun findByStatus(tenantId: TenantId, status: CostingSheetStatus): List<CostingSheet> =
        sheets.values
            .filter { it.tenantId == tenantId && it.status == status }
            .sortedByDescending { it.updatedAt }

    override suspend fun findPendingApprovalOlderThan(
        tenantId: TenantId,
        createdBefore: Instant
    ): List<CostingSheet> =
        sheets.values
            .filter { it.tenantId == tenantId && it.status == CostingSheetStatus.PENDING_APPROVAL && it.createdAt < createdBefore }
            .sortedBy { it.createdAt }

    override suspend fun save(sheet: CostingSheet) {
        sheets[sheet.tenantId.value to sheet.id.value] = sheet
    }

    override suspend fun countByStatus(tenantId: TenantId, status: CostingSheetStatus): Int =
        sheets.values.count { it.tenantId == tenantId && it.status == status }

    override suspend fun avgApprovalCycleHours(tenantId: TenantId, withinDays: Int): Double {
        val now = Clock.System.now()
        val cutoff = now - withinDays.days
        val approved = sheets.values.filter {
            val snapshot = it.approvedSnapshot
            it.tenantId == tenantId &&
                it.status == CostingSheetStatus.APPROVED &&
                snapshot != null &&
                snapshot.approvedAt >= cutoff
        }
        if (approved.isEmpty()) return 0.0

        val totalHours = approved.sumOf {
            val snapshot = it.approvedSnapshot!!
            val duration = snapshot.approvedAt - it.createdAt
            duration.inWholeMinutes.toDouble() / 60.0
        }
        return totalHours / approved.size.toDouble()
    }

    override suspend fun nextSheetNumber(tenantId: TenantId): CostingNumber {
        sequenceCounter++
        val formatted = sequenceCounter.toString().padStart(4, '0')
        return CostingNumber("HPP-$formatted")
    }
}

class InMemoryCostingRateCardRepository : CostingRateCardRepository {
    private val rateCards = mutableMapOf<Pair<String, String>, CostingRateCard>()

    override suspend fun findById(tenantId: TenantId, id: CostingRateCardId): CostingRateCard? =
        rateCards[tenantId.value to id.value]

    override suspend fun findActive(tenantId: TenantId, behavior: CostingBehavior): CostingRateCard? =
        rateCards.values
            .filter { it.tenantId == tenantId && it.behavior == behavior && it.isActive }
            .maxByOrNull { it.effectiveFrom }

    override suspend fun findEffectiveAt(
        tenantId: TenantId,
        behavior: CostingBehavior,
        at: Instant
    ): CostingRateCard? =
        rateCards.values
            .filter {
                val to = it.effectiveTo
                it.tenantId == tenantId &&
                    it.behavior == behavior &&
                    it.effectiveFrom <= at &&
                    (to == null || at < to)
            }
            .maxByOrNull { it.effectiveFrom }

    override suspend fun findAll(tenantId: TenantId, behavior: CostingBehavior): List<CostingRateCard> =
        rateCards.values
            .filter { it.tenantId == tenantId && it.behavior == behavior }
            .sortedByDescending { it.effectiveFrom }

    override suspend fun save(rateCard: CostingRateCard) {
        rateCards[rateCard.tenantId.value to rateCard.id.value] = rateCard
    }
}

/**
 * Arsip benchmark di memori — dipakai test rute dan mode demo tanpa PostgreSQL.
 *
 * Aturan penyempitan kandidatnya sengaja dibuat setara dengan
 * [PostgresCostingBenchmarkRepository.findSimilar]: sempitkan ke kategori, lebarkan ke seluruh
 * arsip bila kosong, urutkan gauge terdekat. Kalau keduanya berbeda, test rute akan lulus dengan
 * perilaku yang tidak pernah terjadi di produksi.
 */
class InMemoryCostingBenchmarkRepository : CostingBenchmarkRepository {
    private val benchmarks = mutableMapOf<Pair<String, String>, CostingProductBenchmark>()

    override suspend fun findById(tenantId: TenantId, id: BenchmarkId): CostingProductBenchmark? =
        benchmarks[tenantId.value to id.value]

    override suspend fun listAll(tenantId: TenantId, limit: Int): List<CostingProductBenchmark> =
        benchmarks.values
            .filter { it.tenantId == tenantId }
            .sortedByDescending { it.createdAt }
            .take(limit)

    override suspend fun findSimilar(
        tenantId: TenantId,
        category: KnitCategory,
        gauge: Int?,
        limit: Int
    ): List<CostingProductBenchmark> {
        val tenantScoped = benchmarks.values.filter { it.tenantId == tenantId }
        val pool = tenantScoped.filter { it.category == category }.ifEmpty { tenantScoped }
        return pool
            .sortedBy { candidate ->
                val candidateGauge = candidate.structure.gauge
                if (gauge == null || candidateGauge == null) 99 else kotlin.math.abs(candidateGauge - gauge)
            }
            .take(limit)
    }

    override suspend fun netWeightSamples(
        tenantId: TenantId,
        category: KnitCategory,
        limit: Int
    ): List<Double> = benchmarks.values
        .filter { it.tenantId == tenantId && it.category == category }
        .sortedByDescending { it.createdAt }
        .take(limit)
        .map { it.metrics.netWeightGrams }

    override suspend fun save(benchmark: CostingProductBenchmark) {
        benchmarks[benchmark.tenantId.value to benchmark.id.value] = benchmark
    }

    override suspend fun saveBatch(benchmarks: List<CostingProductBenchmark>) {
        benchmarks.forEach { save(it) }
    }

    override suspend fun findImportedFileNames(tenantId: TenantId): Set<String> =
        benchmarks.values
            .filter { it.tenantId == tenantId }
            .mapNotNull { it.sourceFileName.takeIf(String::isNotBlank) }
            .toSet()

    override suspend fun count(tenantId: TenantId): Int =
        benchmarks.values.count { it.tenantId == tenantId }
}
