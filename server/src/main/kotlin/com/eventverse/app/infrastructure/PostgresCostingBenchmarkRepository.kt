package com.eventverse.app.infrastructure

import com.eventverse.app.domain.common.Money
import com.eventverse.app.domain.costing.*
import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.infrastructure.tables.CostingProductBenchmarksTable
import com.eventverse.app.shared.common.MeasureCodec
import com.eventverse.app.shared.json.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.abs

class PostgresCostingBenchmarkRepository : CostingBenchmarkRepository {

    override suspend fun findById(tenantId: TenantId, id: BenchmarkId): CostingProductBenchmark? =
        DatabaseFactory.dbQuery(tenantId) {
            CostingProductBenchmarksTable.selectAll()
                .where {
                    (CostingProductBenchmarksTable.tenantId eq tenantId.value) and
                        (CostingProductBenchmarksTable.id eq id.value)
                }
                .singleOrNull()
                ?.let(::toBenchmark)
        }

    override suspend fun listAll(tenantId: TenantId, limit: Int): List<CostingProductBenchmark> =
        DatabaseFactory.dbQuery(tenantId) {
            CostingProductBenchmarksTable.selectAll()
                .where { CostingProductBenchmarksTable.tenantId eq tenantId.value }
                .orderBy(CostingProductBenchmarksTable.createdAt, SortOrder.DESC)
                .limit(limit)
                .map(::toBenchmark)
        }

    /**
     * Penyempitan kandidat dilakukan di SQL hanya sampai kategori; pemberian skor akhir
     * (gauge, benang) tetap di domain supaya aturannya bisa diuji tanpa database.
     *
     * Kalau kategori yang diminta tidak menghasilkan apa-apa, pencarian melebar ke seluruh arsip
     * tenant — lebih baik memberi CS pembanding lintas-kategori dengan skor rendah daripada
     * memulangkan daftar kosong dan memaksa estimator jatuh ke gramasi baku.
     */
    override suspend fun findSimilar(
        tenantId: TenantId,
        category: KnitCategory,
        gauge: Int?,
        limit: Int
    ): List<CostingProductBenchmark> = DatabaseFactory.dbQuery(tenantId) {
        fun query(restrictCategory: Boolean) = CostingProductBenchmarksTable.selectAll()
            .where {
                val base = CostingProductBenchmarksTable.tenantId eq tenantId.value
                if (restrictCategory) {
                    base and (CostingProductBenchmarksTable.category eq category.name)
                } else {
                    base
                }
            }
            .orderBy(CostingProductBenchmarksTable.createdAt, SortOrder.DESC)
            .limit(limit * 2)
            .map(::toBenchmark)

        val inCategory = query(restrictCategory = true)
        val pool = inCategory.ifEmpty { query(restrictCategory = false) }

        // Gauge terdekat dulu: itu penentu gramasi paling kuat setelah kategori.
        pool.sortedBy { candidate ->
            val candidateGauge = candidate.structure.gauge
            when {
                gauge == null || candidateGauge == null -> 99
                else -> abs(candidateGauge - gauge)
            }
        }.take(limit)
    }

    override suspend fun netWeightSamples(
        tenantId: TenantId,
        category: KnitCategory,
        limit: Int
    ): List<Double> = DatabaseFactory.dbQuery(tenantId) {
        CostingProductBenchmarksTable
            .select(CostingProductBenchmarksTable.netWeightGrams)
            .where {
                (CostingProductBenchmarksTable.tenantId eq tenantId.value) and
                    (CostingProductBenchmarksTable.category eq category.name)
            }
            .orderBy(CostingProductBenchmarksTable.createdAt, SortOrder.DESC)
            .limit(limit)
            .map { it[CostingProductBenchmarksTable.netWeightGrams].toDouble() }
    }

    override suspend fun save(benchmark: CostingProductBenchmark) {
        DatabaseFactory.dbQuery(benchmark.tenantId) { upsert(benchmark) }
    }

    override suspend fun saveBatch(benchmarks: List<CostingProductBenchmark>) {
        if (benchmarks.isEmpty()) return
        val tenantId = benchmarks.first().tenantId
        require(benchmarks.all { it.tenantId == tenantId }) {
            "saveBatch tidak boleh mencampur tenant dalam satu transaksi"
        }
        DatabaseFactory.dbQuery(tenantId) {
            benchmarks.forEach { upsert(it) }
        }
    }

    override suspend fun findImportedFileNames(tenantId: TenantId): Set<String> =
        DatabaseFactory.dbQuery(tenantId) {
            CostingProductBenchmarksTable
                .select(CostingProductBenchmarksTable.sourceFileName)
                .where { CostingProductBenchmarksTable.tenantId eq tenantId.value }
                .mapNotNull { it[CostingProductBenchmarksTable.sourceFileName].takeIf(String::isNotBlank) }
                .toSet()
        }

    override suspend fun count(tenantId: TenantId): Int =
        DatabaseFactory.dbQuery(tenantId) {
            CostingProductBenchmarksTable.selectAll()
                .where { CostingProductBenchmarksTable.tenantId eq tenantId.value }
                .count()
                .toInt()
        }

    private fun upsert(benchmark: CostingProductBenchmark) {
        val exists = CostingProductBenchmarksTable.selectAll()
            .where {
                (CostingProductBenchmarksTable.tenantId eq benchmark.tenantId.value) and
                    (CostingProductBenchmarksTable.id eq benchmark.id.value)
            }
            .count() > 0

        val featuresJsonText = jsonStringMapOf(benchmark.features).encode()
        val breakdownJsonText = jsonArrayOf(
            benchmark.costBreakdown.map { line ->
                jsonObjectOf(
                    "label" to jsonOf(line.label),
                    "amountPerUnit" to MeasureCodec.encodeMoney(line.amountPerUnit)
                )
            }
        ).encode()
        val weight = BigDecimal(benchmark.metrics.netWeightGrams).setScale(2, RoundingMode.HALF_UP)

        if (exists) {
            CostingProductBenchmarksTable.update({
                (CostingProductBenchmarksTable.tenantId eq benchmark.tenantId.value) and
                    (CostingProductBenchmarksTable.id eq benchmark.id.value)
            }) {
                it[styleName] = benchmark.styleName
                it[clientName] = benchmark.clientName
                it[category] = benchmark.category.name
                it[knitType] = benchmark.structure.knitType
                it[yarnType] = benchmark.structure.yarnType
                it[gauge] = benchmark.structure.gauge
                it[netWeightGrams] = weight
                it[knittingMinutes] = benchmark.metrics.knittingMinutes
                it[buttonCount] = benchmark.metrics.buttonCount
                it[mockupImageUrl] = benchmark.mockupImageUrl
                it[featuresJson] = featuresJsonText
                it[costBreakdownJson] = breakdownJsonText
                it[hppPerUnitMinor] = benchmark.pricing.hppPerUnit.minorUnits
                it[sellingPriceMinor] = benchmark.pricing.sellingPricePerUnit?.minorUnits
                it[sourceSheetId] = benchmark.sourceSheetId?.value
                it[sourceFileName] = benchmark.sourceFileName
                it[updatedAt] = benchmark.updatedAt
            }
        } else {
            CostingProductBenchmarksTable.insert {
                it[id] = benchmark.id.value
                it[tenantId] = benchmark.tenantId.value
                it[styleName] = benchmark.styleName
                it[clientName] = benchmark.clientName
                it[category] = benchmark.category.name
                it[knitType] = benchmark.structure.knitType
                it[yarnType] = benchmark.structure.yarnType
                it[gauge] = benchmark.structure.gauge
                it[netWeightGrams] = weight
                it[knittingMinutes] = benchmark.metrics.knittingMinutes
                it[buttonCount] = benchmark.metrics.buttonCount
                it[mockupImageUrl] = benchmark.mockupImageUrl
                it[featuresJson] = featuresJsonText
                it[costBreakdownJson] = breakdownJsonText
                it[hppPerUnitMinor] = benchmark.pricing.hppPerUnit.minorUnits
                it[sellingPriceMinor] = benchmark.pricing.sellingPricePerUnit?.minorUnits
                it[sourceSheetId] = benchmark.sourceSheetId?.value
                it[sourceFileName] = benchmark.sourceFileName
                it[createdAt] = benchmark.createdAt
                it[updatedAt] = benchmark.updatedAt
            }
        }
    }

    private fun toBenchmark(row: ResultRow): CostingProductBenchmark {
        val features = runCatching {
            (JsonParser.parse(row[CostingProductBenchmarksTable.featuresJson]) as? JsonValue.Obj)
                ?.entries
                ?.mapNotNull { (k, v) -> (v as? JsonValue.Str)?.let { k to it.value } }
                ?.toMap()
        }.getOrNull() ?: emptyMap()

        val breakdown = runCatching {
            (JsonParser.parse(row[CostingProductBenchmarksTable.costBreakdownJson]) as? JsonValue.Arr)
                ?.items
                ?.filterIsInstance<JsonValue.Obj>()
                ?.map { line ->
                    BenchmarkCostLine(
                        label = line.string("label") ?: "",
                        amountPerUnit = MeasureCodec.decodeMoney(line.obj("amountPerUnit"))
                    )
                }
        }.getOrNull() ?: emptyList()

        return CostingProductBenchmark(
            id = BenchmarkId(row[CostingProductBenchmarksTable.id]),
            tenantId = TenantId(row[CostingProductBenchmarksTable.tenantId]),
            styleName = row[CostingProductBenchmarksTable.styleName],
            clientName = row[CostingProductBenchmarksTable.clientName],
            category = KnitCategory.entries
                .firstOrNull { it.name == row[CostingProductBenchmarksTable.category] }
                ?: KnitCategory.OTHER,
            structure = KnitStructure(
                knitType = row[CostingProductBenchmarksTable.knitType],
                yarnType = row[CostingProductBenchmarksTable.yarnType],
                gauge = row[CostingProductBenchmarksTable.gauge]
            ),
            metrics = PhysicalMetrics(
                netWeightGrams = row[CostingProductBenchmarksTable.netWeightGrams].toDouble(),
                knittingMinutes = row[CostingProductBenchmarksTable.knittingMinutes],
                buttonCount = row[CostingProductBenchmarksTable.buttonCount]
            ),
            pricing = BenchmarkPricing(
                hppPerUnit = Money(row[CostingProductBenchmarksTable.hppPerUnitMinor]),
                sellingPricePerUnit = row[CostingProductBenchmarksTable.sellingPriceMinor]?.let { Money(it) }
            ),
            mockupImageUrl = row[CostingProductBenchmarksTable.mockupImageUrl],
            features = features,
            costBreakdown = breakdown,
            sourceSheetId = row[CostingProductBenchmarksTable.sourceSheetId]?.let { CostingSheetId(it) },
            sourceFileName = row[CostingProductBenchmarksTable.sourceFileName],
            createdAt = row[CostingProductBenchmarksTable.createdAt],
            updatedAt = row[CostingProductBenchmarksTable.updatedAt]
        )
    }
}
