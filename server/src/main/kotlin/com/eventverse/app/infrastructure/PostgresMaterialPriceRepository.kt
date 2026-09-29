package com.eventverse.app.infrastructure

import com.eventverse.app.domain.common.CurrencyCode
import com.eventverse.app.domain.common.Money
import com.eventverse.app.domain.common.Quantity
import com.eventverse.app.domain.common.UnitOfMeasure
import com.eventverse.app.domain.common.UnitPrice
import com.eventverse.app.domain.masterdata.*
import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.infrastructure.tables.MaterialPricePoliciesTable
import com.eventverse.app.infrastructure.tables.MaterialPricesTable
import com.eventverse.app.shared.json.JsonParser
import com.eventverse.app.shared.json.jsonArrayOf
import com.eventverse.app.shared.json.jsonOf
import kotlinx.datetime.Instant
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.lessEq
import org.jetbrains.exposed.sql.transactions.TransactionManager

class PostgresMaterialPriceRepository : MaterialPriceRepository {

    override suspend fun historyFor(tenantId: TenantId, materialId: MaterialId): MaterialPriceHistory =
        DatabaseFactory.dbQuery(tenantId) {
            val entries = MaterialPricesTable.selectAll()
                .where {
                    (MaterialPricesTable.tenantId eq tenantId.value) and
                        (MaterialPricesTable.materialId eq materialId.value)
                }
                .orderBy(MaterialPricesTable.effectiveFrom, SortOrder.ASC)
                .map(::toPrice)

            MaterialPriceHistory(materialId, entries)
        }

    override suspend fun effectivePriceAt(
        tenantId: TenantId,
        materialId: MaterialId,
        at: Instant,
        source: PriceSource
    ): MaterialPrice? = DatabaseFactory.dbQuery(tenantId) {
        MaterialPricesTable.selectAll()
            .where {
                (MaterialPricesTable.tenantId eq tenantId.value) and
                    (MaterialPricesTable.materialId eq materialId.value) and
                    (MaterialPricesTable.priceSource eq source.name) and
                    (MaterialPricesTable.effectiveFrom lessEq at)
            }
            .orderBy(MaterialPricesTable.effectiveFrom, SortOrder.DESC)
            .limit(1)
            .map(::toPrice)
            .singleOrNull()
    }

    override suspend fun effectivePricesAt(
        tenantId: TenantId,
        materialIds: Collection<MaterialId>,
        at: Instant,
        source: PriceSource
    ): Map<MaterialId, MaterialPrice> = DatabaseFactory.dbQuery(tenantId) {
        if (materialIds.isEmpty()) return@dbQuery emptyMap()

        val idList = materialIds.joinToString(",") { "'${it.value}'" }
        val sql = """
            SELECT DISTINCT ON (material_id)
                id, tenant_id, material_id, amount_minor, currency, per_quantity_micros, per_uom, source, effective_from, note, recorded_by_user_id, recorded_at
            FROM ${MaterialPricesTable.tableName}
            WHERE tenant_id = '${tenantId.value}'
              AND material_id IN ($idList)
              AND source = '${source.name}'
              AND effective_from <= '$at'
            ORDER BY material_id, effective_from DESC
        """.trimIndent()

        val results = mutableMapOf<MaterialId, MaterialPrice>()
        TransactionManager.current().exec(sql) { rs ->
            while (rs.next()) {
                val currencyCode = runCatching { CurrencyCode.valueOf(rs.getString("currency")) }.getOrDefault(CurrencyCode.IDR)
                val uom = UnitOfMeasure.fromCode(rs.getString("per_uom")) ?: UnitOfMeasure.PIECE
                val price = MaterialPrice(
                    id = MaterialPriceId(rs.getString("id")),
                    tenantId = TenantId(rs.getString("tenant_id")),
                    materialId = MaterialId(rs.getString("material_id")),
                    unitPrice = UnitPrice(
                        amount = Money(rs.getLong("amount_minor"), currencyCode),
                        per = Quantity(rs.getLong("per_quantity_micros"), uom)
                    ),
                    source = runCatching { PriceSource.valueOf(rs.getString("source")) }.getOrDefault(PriceSource.STANDARD),
                    effectiveFrom = Instant.parse(rs.getString("effective_from")),
                    note = rs.getString("note") ?: "",
                    recordedByUserId = rs.getString("recorded_by_user_id") ?: "",
                    recordedAt = Instant.parse(rs.getString("recorded_at"))
                )
                results[price.materialId] = price
            }
        }
        results
    }

    override suspend fun append(price: MaterialPrice): MaterialPrice =
        DatabaseFactory.dbQuery(price.tenantId) {
            MaterialPricesTable.insert {
                it[id] = price.id.value
                it[tenantId] = price.tenantId.value
                it[materialId] = price.materialId.value
                it[amountMinor] = price.unitPrice.amount.minorUnits
                it[currency] = price.unitPrice.amount.currency.code
                it[perQuantityMicros] = price.unitPrice.per.micros
                it[perUom] = price.unitPrice.per.uom.code
                it[priceSource] = price.source.name
                it[effectiveFrom] = price.effectiveFrom
                it[note] = price.note
                it[recordedByUserId] = price.recordedByUserId.ifBlank { null }
                it[recordedAt] = price.recordedAt
            }
            price
        }

    override suspend fun policyFor(tenantId: TenantId): TenantPricePolicy =
        DatabaseFactory.dbQuery(tenantId) {
            val row = MaterialPricePoliciesTable.selectAll()
                .where { MaterialPricePoliciesTable.tenantId eq tenantId.value }
                .singleOrNull()
                ?: return@dbQuery TenantPricePolicy(tenantId)

            val rawPref = row[MaterialPricePoliciesTable.preferenceOrder]
            val prefOrder = runCatching {
                JsonParser.parseArray(rawPref).mapNotNull {
                    val code = (it as? com.eventverse.app.shared.json.JsonValue.Str)?.value ?: return@mapNotNull null
                    runCatching { PriceSource.valueOf(code) }.getOrNull()
                }
            }.getOrDefault(listOf(PriceSource.STANDARD)).ifEmpty { listOf(PriceSource.STANDARD) }

            TenantPricePolicy(
                tenantId = tenantId,
                preferenceOrder = prefOrder,
                fallbackToStandard = row[MaterialPricePoliciesTable.fallbackToStandard]
            )
        }

    override suspend fun savePolicy(policy: TenantPricePolicy): TenantPricePolicy =
        DatabaseFactory.dbQuery(policy.tenantId) {
            val prefJson = jsonArrayOf(policy.preferenceOrder.map { jsonOf(it.name) }).encode()
            val existing = MaterialPricePoliciesTable.selectAll()
                .where { MaterialPricePoliciesTable.tenantId eq policy.tenantId.value }
                .singleOrNull()

            val now = kotlinx.datetime.Clock.System.now()
            if (existing != null) {
                MaterialPricePoliciesTable.update({ MaterialPricePoliciesTable.tenantId eq policy.tenantId.value }) {
                    it[preferenceOrder] = prefJson
                    it[fallbackToStandard] = policy.fallbackToStandard
                    it[updatedAt] = now
                }
            } else {
                MaterialPricePoliciesTable.insert {
                    it[tenantId] = policy.tenantId.value
                    it[preferenceOrder] = prefJson
                    it[fallbackToStandard] = policy.fallbackToStandard
                    it[updatedAt] = now
                }
            }
            policy
        }

    private fun toPrice(row: ResultRow): MaterialPrice {
        val currencyCode = runCatching {
            CurrencyCode.valueOf(row[MaterialPricesTable.currency])
        }.getOrDefault(CurrencyCode.IDR)

        val uom = UnitOfMeasure.fromCode(row[MaterialPricesTable.perUom]) ?: UnitOfMeasure.PIECE
        val source = runCatching {
            PriceSource.valueOf(row[MaterialPricesTable.priceSource])
        }.getOrDefault(PriceSource.STANDARD)

        return MaterialPrice(
            id = MaterialPriceId(row[MaterialPricesTable.id]),
            tenantId = TenantId(row[MaterialPricesTable.tenantId]),
            materialId = MaterialId(row[MaterialPricesTable.materialId]),
            unitPrice = UnitPrice(
                amount = Money(row[MaterialPricesTable.amountMinor], currencyCode),
                per = Quantity(row[MaterialPricesTable.perQuantityMicros], uom)
            ),
            source = source,
            effectiveFrom = row[MaterialPricesTable.effectiveFrom],
            note = row[MaterialPricesTable.note],
            recordedByUserId = row[MaterialPricesTable.recordedByUserId] ?: "",
            recordedAt = row[MaterialPricesTable.recordedAt]
        )
    }
}
