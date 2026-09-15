package com.eventverse.app.infrastructure

import com.eventverse.app.domain.common.Money
import com.eventverse.app.domain.common.Ratio
import com.eventverse.app.domain.costing.*
import com.eventverse.app.domain.pipeline.CostingBehavior
import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.infrastructure.tables.*
import com.eventverse.app.shared.contracts.CostingCalculationResultCodec
import com.eventverse.app.shared.costing.CostingRateCardCodec
import com.eventverse.app.shared.costing.CostingSheetCodec
import com.eventverse.app.shared.json.JsonParser
import com.eventverse.app.shared.json.JsonValue
import com.eventverse.app.shared.json.jsonArrayOf
import com.eventverse.app.shared.json.jsonOf
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greaterEq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import org.jetbrains.exposed.sql.transactions.TransactionManager
import kotlin.time.Duration.Companion.days

class PostgresCostingSheetRepository : CostingSheetRepository {

    override suspend fun findById(tenantId: TenantId, id: CostingSheetId): CostingSheet? =
        DatabaseFactory.dbQuery(tenantId) {
            CostingSheetsTable.selectAll()
                .where { (CostingSheetsTable.tenantId eq tenantId.value) and (CostingSheetsTable.id eq id.value) }
                .singleOrNull()
                ?.let(::toCostingSheet)
        }

    override suspend fun findAll(tenantId: TenantId): List<CostingSheet> =
        DatabaseFactory.dbQuery(tenantId) {
            CostingSheetsTable.selectAll()
                .where { CostingSheetsTable.tenantId eq tenantId.value }
                .orderBy(CostingSheetsTable.updatedAt, SortOrder.DESC)
                .map(::toCostingSheet)
        }

    override suspend fun findByTechPack(tenantId: TenantId, techPackId: String): List<CostingSheet> =
        DatabaseFactory.dbQuery(tenantId) {
            CostingSheetsTable.selectAll()
                .where { (CostingSheetsTable.tenantId eq tenantId.value) and (CostingSheetsTable.techPackId eq techPackId) }
                .orderBy(CostingSheetsTable.updatedAt, SortOrder.DESC)
                .map(::toCostingSheet)
        }

    override suspend fun findByStatus(tenantId: TenantId, status: CostingSheetStatus): List<CostingSheet> =
        DatabaseFactory.dbQuery(tenantId) {
            CostingSheetsTable.selectAll()
                .where { (CostingSheetsTable.tenantId eq tenantId.value) and (CostingSheetsTable.status eq status.name) }
                .orderBy(CostingSheetsTable.updatedAt, SortOrder.DESC)
                .map(::toCostingSheet)
        }

    override suspend fun findPendingApprovalOlderThan(
        tenantId: TenantId,
        createdBefore: Instant
    ): List<CostingSheet> =
        DatabaseFactory.dbQuery(tenantId) {
            CostingSheetsTable.selectAll()
                .where {
                    (CostingSheetsTable.tenantId eq tenantId.value) and
                        (CostingSheetsTable.status eq CostingSheetStatus.PENDING_APPROVAL.name) and
                        (CostingSheetsTable.createdAt less createdBefore)
                }
                .orderBy(CostingSheetsTable.createdAt, SortOrder.ASC)
                .map(::toCostingSheet)
        }

    override suspend fun save(sheet: CostingSheet) {
        DatabaseFactory.dbQuery(sheet.tenantId) {
            val exists = CostingSheetsTable.selectAll()
                .where { (CostingSheetsTable.tenantId eq sheet.tenantId.value) and (CostingSheetsTable.id eq sheet.id.value) }
                .count() > 0

            val encoded = CostingSheetCodec.encode(sheet)
            val paramOverridesJson = encoded.obj("parameterOverrides")?.encode() ?: "{}"
            val latestResultJson = encoded.obj("latestResult")?.encode()
            val approvedSnapshotJson = encoded.obj("approvedSnapshot")?.encode()

            if (exists) {
                CostingSheetsTable.update({ (CostingSheetsTable.tenantId eq sheet.tenantId.value) and (CostingSheetsTable.id eq sheet.id.value) }) {
                    it[number] = sheet.number.value
                    it[techPackId] = sheet.techPackId
                    it[orderQuantity] = sheet.orderQuantity
                    it[behavior] = sheet.behavior.code
                    it[status] = sheet.status.name
                    it[pricingAsOf] = sheet.pricingAsOf
                    it[parameterOverrides] = paramOverridesJson
                    it[latestResult] = latestResultJson
                    it[approvedSnapshot] = approvedSnapshotJson
                    it[rejectionReason] = sheet.rejectionReason
                    it[notes] = sheet.notes
                    it[linkedSpkNumber] = sheet.linkedSpkNumber
                    it[createdByUserId] = sheet.createdByUserId
                    it[updatedAt] = sheet.updatedAt
                }
            } else {
                CostingSheetsTable.insert {
                    it[id] = sheet.id.value
                    it[tenantId] = sheet.tenantId.value
                    it[number] = sheet.number.value
                    it[techPackId] = sheet.techPackId
                    it[orderQuantity] = sheet.orderQuantity
                    it[behavior] = sheet.behavior.code
                    it[status] = sheet.status.name
                    it[pricingAsOf] = sheet.pricingAsOf
                    it[parameterOverrides] = paramOverridesJson
                    it[latestResult] = latestResultJson
                    it[approvedSnapshot] = approvedSnapshotJson
                    it[rejectionReason] = sheet.rejectionReason
                    it[notes] = sheet.notes
                    it[linkedSpkNumber] = sheet.linkedSpkNumber
                    it[createdByUserId] = sheet.createdByUserId
                    it[createdAt] = sheet.createdAt
                    it[updatedAt] = sheet.updatedAt
                }
            }

            // Sync buckets to costing_sheet_buckets table
            CostingSheetBucketsTable.deleteWhere {
                (CostingSheetBucketsTable.tenantId eq sheet.tenantId.value) and (CostingSheetBucketsTable.sheetId eq sheet.id.value)
            }

            val result = sheet.approvedSnapshot?.result ?: sheet.latestResult
            result?.buckets?.forEach { b ->
                CostingSheetBucketsTable.insert {
                    it[id] = "bkt-${sheet.id.value}-${b.kind.name}-${b.label.hashCode()}"
                    it[tenantId] = sheet.tenantId.value
                    it[sheetId] = sheet.id.value
                    it[kind] = b.kind.name
                    it[label] = b.label
                    it[amountPerUnitMinorUnits] = b.amountPerUnit.minorUnits
                    it[ownership] = b.ownership.code
                    it[isBillable] = b.isBillableToClient
                    it[sourceRefs] = jsonArrayOf(b.sourceRefs.map(::jsonOf)).encode()
                    it[createdAt] = sheet.updatedAt
                }
            }
        }
    }

    override suspend fun countByStatus(tenantId: TenantId, status: CostingSheetStatus): Int =
        DatabaseFactory.dbQuery(tenantId) {
            CostingSheetsTable.selectAll()
                .where { (CostingSheetsTable.tenantId eq tenantId.value) and (CostingSheetsTable.status eq status.name) }
                .count()
                .toInt()
        }

    override suspend fun avgApprovalCycleHours(tenantId: TenantId, withinDays: Int): Double =
        DatabaseFactory.dbQuery(tenantId) {
            val now = Clock.System.now()
            val cutoff = now - withinDays.days

            val rows = CostingSheetsTable.selectAll()
                .where {
                    (CostingSheetsTable.tenantId eq tenantId.value) and
                        (CostingSheetsTable.status eq CostingSheetStatus.APPROVED.name) and
                        (CostingSheetsTable.updatedAt greaterEq cutoff)
                }
                .toList()

            if (rows.isEmpty()) return@dbQuery 0.0

            var totalHours = 0.0
            var count = 0
            for (r in rows) {
                val sheet = toCostingSheet(r)
                val approvedAt = sheet.approvedSnapshot?.approvedAt ?: sheet.updatedAt
                val duration = approvedAt - sheet.createdAt
                totalHours += duration.inWholeMinutes.toDouble() / 60.0
                count++
            }
            if (count == 0) 0.0 else totalHours / count.toDouble()
        }

    override suspend fun nextSheetNumber(tenantId: TenantId): CostingNumber =
        DatabaseFactory.dbQuery(tenantId) {
            val total = CostingSheetsTable.selectAll()
                .where { CostingSheetsTable.tenantId eq tenantId.value }
                .count()
            val seq = total + 1
            CostingNumber("HPP-${seq.toString().padStart(4, '0')}")
        }

    private fun toCostingSheet(row: ResultRow): CostingSheet {
        val paramOverridesObj = row[CostingSheetsTable.parameterOverrides].let { raw ->
            runCatching { JsonParser.parse(raw) as? JsonValue.Obj }.getOrNull() ?: JsonValue.Obj(emptyMap())
        }
        val latestResultObj = row[CostingSheetsTable.latestResult]?.let { raw ->
            runCatching { JsonParser.parse(raw) as? JsonValue.Obj }.getOrNull()
        }
        val approvedSnapshotObj = row[CostingSheetsTable.approvedSnapshot]?.let { raw ->
            runCatching { JsonParser.parse(raw) as? JsonValue.Obj }.getOrNull()
        }

        val syntheticJson = com.eventverse.app.shared.json.jsonObjectOf(
            "id" to jsonOf(row[CostingSheetsTable.id]),
            "tenantId" to jsonOf(row[CostingSheetsTable.tenantId]),
            "number" to jsonOf(row[CostingSheetsTable.number]),
            "techPackId" to jsonOf(row[CostingSheetsTable.techPackId]),
            "orderQuantity" to jsonOf(row[CostingSheetsTable.orderQuantity]),
            "behavior" to jsonOf(row[CostingSheetsTable.behavior]),
            "status" to jsonOf(row[CostingSheetsTable.status]),
            "pricingAsOf" to jsonOf(row[CostingSheetsTable.pricingAsOf].toString()),
            "parameterOverrides" to paramOverridesObj,
            "latestResult" to (latestResultObj ?: JsonValue.Null),
            "approvedSnapshot" to (approvedSnapshotObj ?: JsonValue.Null),
            "rejectionReason" to (row[CostingSheetsTable.rejectionReason]?.let { jsonOf(it) } ?: JsonValue.Null),
            "notes" to jsonOf(row[CostingSheetsTable.notes]),
            "linkedSpkNumber" to (row[CostingSheetsTable.linkedSpkNumber]?.let { jsonOf(it) } ?: JsonValue.Null),
            "createdByUserId" to (row[CostingSheetsTable.createdByUserId]?.let { jsonOf(it) } ?: JsonValue.Null),
            "createdAt" to jsonOf(row[CostingSheetsTable.createdAt].toString()),
            "updatedAt" to jsonOf(row[CostingSheetsTable.updatedAt].toString())
        )
        return CostingSheetCodec.decode(syntheticJson)
    }
}

class PostgresCostingRateCardRepository : CostingRateCardRepository {

    override suspend fun findById(tenantId: TenantId, id: CostingRateCardId): CostingRateCard? =
        DatabaseFactory.dbQuery(tenantId) {
            CostingRateCardsTable.selectAll()
                .where { (CostingRateCardsTable.tenantId eq tenantId.value) and (CostingRateCardsTable.id eq id.value) }
                .singleOrNull()
                ?.let(::toRateCard)
        }

    override suspend fun findActive(tenantId: TenantId, behavior: CostingBehavior): CostingRateCard? =
        DatabaseFactory.dbQuery(tenantId) {
            CostingRateCardsTable.selectAll()
                .where {
                    (CostingRateCardsTable.tenantId eq tenantId.value) and
                        (CostingRateCardsTable.behavior eq behavior.code) and
                        (CostingRateCardsTable.effectiveTo.isNull())
                }
                .orderBy(CostingRateCardsTable.effectiveFrom, SortOrder.DESC)
                .limit(1)
                .singleOrNull()
                ?.let(::toRateCard)
        }

    override suspend fun findEffectiveAt(
        tenantId: TenantId,
        behavior: CostingBehavior,
        at: Instant
    ): CostingRateCard? =
        DatabaseFactory.dbQuery(tenantId) {
            CostingRateCardsTable.selectAll()
                .where {
                    (CostingRateCardsTable.tenantId eq tenantId.value) and
                        (CostingRateCardsTable.behavior eq behavior.code) and
                        (CostingRateCardsTable.effectiveFrom lessEq at) and
                        ((CostingRateCardsTable.effectiveTo.isNull()) or (CostingRateCardsTable.effectiveTo greater at))
                }
                .orderBy(CostingRateCardsTable.effectiveFrom, SortOrder.DESC)
                .limit(1)
                .singleOrNull()
                ?.let(::toRateCard)
        }

    override suspend fun findAll(tenantId: TenantId, behavior: CostingBehavior): List<CostingRateCard> =
        DatabaseFactory.dbQuery(tenantId) {
            CostingRateCardsTable.selectAll()
                .where {
                    (CostingRateCardsTable.tenantId eq tenantId.value) and
                        (CostingRateCardsTable.behavior eq behavior.code)
                }
                .orderBy(CostingRateCardsTable.effectiveFrom, SortOrder.DESC)
                .map(::toRateCard)
        }

    override suspend fun save(rateCard: CostingRateCard) {
        DatabaseFactory.dbQuery(rateCard.tenantId) {
            val exists = CostingRateCardsTable.selectAll()
                .where { (CostingRateCardsTable.tenantId eq rateCard.tenantId.value) and (CostingRateCardsTable.id eq rateCard.id.value) }
                .count() > 0

            if (exists) {
                CostingRateCardsTable.update({
                    (CostingRateCardsTable.tenantId eq rateCard.tenantId.value) and (CostingRateCardsTable.id eq rateCard.id.value)
                }) {
                    it[behavior] = rateCard.behavior.code
                    it[version] = rateCard.version
                    it[effectiveFrom] = rateCard.effectiveFrom
                    it[effectiveTo] = rateCard.effectiveTo
                    it[description] = rateCard.description
                    it[laborRateMinorUnits] = rateCard.laborRatePerSamMinute?.minorUnits
                    it[subcontractRateMinorUnits] = rateCard.subcontractRatePerSamMinute?.minorUnits
                    it[serviceFeeMinorUnits] = rateCard.serviceFeePerUnit?.minorUnits
                    it[overheadMinorUnits] = rateCard.overheadPerUnit?.minorUnits
                    it[packingUnitMinorUnits] = rateCard.packingCostPerUnit?.minorUnits
                    it[packingOrderMinorUnits] = rateCard.packingCostPerOrder?.minorUnits
                    it[marginRatioMicros] = rateCard.marginRatio?.applyTo(1_000_000L)
                    it[retailMarkupMicros] = rateCard.retailMarkupRatio?.applyTo(1_000_000L)
                    it[marketplaceFeeMicros] = rateCard.marketplaceFeeRatio?.applyTo(1_000_000L)
                    it[fabricWasteMicros] = rateCard.fabricWastageToleranceRatio?.applyTo(1_000_000L)
                    it[includeFabricCost] = rateCard.includeFabricCost
                    it[seededFromNodeId] = rateCard.seededFromNodeId
                    it[createdByUserId] = rateCard.createdByUserId
                    it[updatedAt] = rateCard.updatedAt
                }
            } else {
                CostingRateCardsTable.insert {
                    it[id] = rateCard.id.value
                    it[tenantId] = rateCard.tenantId.value
                    it[behavior] = rateCard.behavior.code
                    it[version] = rateCard.version
                    it[effectiveFrom] = rateCard.effectiveFrom
                    it[effectiveTo] = rateCard.effectiveTo
                    it[description] = rateCard.description
                    it[laborRateMinorUnits] = rateCard.laborRatePerSamMinute?.minorUnits
                    it[subcontractRateMinorUnits] = rateCard.subcontractRatePerSamMinute?.minorUnits
                    it[serviceFeeMinorUnits] = rateCard.serviceFeePerUnit?.minorUnits
                    it[overheadMinorUnits] = rateCard.overheadPerUnit?.minorUnits
                    it[packingUnitMinorUnits] = rateCard.packingCostPerUnit?.minorUnits
                    it[packingOrderMinorUnits] = rateCard.packingCostPerOrder?.minorUnits
                    it[marginRatioMicros] = rateCard.marginRatio?.applyTo(1_000_000L)
                    it[retailMarkupMicros] = rateCard.retailMarkupRatio?.applyTo(1_000_000L)
                    it[marketplaceFeeMicros] = rateCard.marketplaceFeeRatio?.applyTo(1_000_000L)
                    it[fabricWasteMicros] = rateCard.fabricWastageToleranceRatio?.applyTo(1_000_000L)
                    it[includeFabricCost] = rateCard.includeFabricCost
                    it[seededFromNodeId] = rateCard.seededFromNodeId
                    it[createdByUserId] = rateCard.createdByUserId
                    it[createdAt] = rateCard.createdAt
                    it[updatedAt] = rateCard.updatedAt
                }
            }
        }
    }

    private fun toRateCard(row: ResultRow): CostingRateCard =
        CostingRateCard(
            id = CostingRateCardId(row[CostingRateCardsTable.id]),
            tenantId = TenantId(row[CostingRateCardsTable.tenantId]),
            behavior = CostingBehavior.entries.firstOrNull {
                it.code == row[CostingRateCardsTable.behavior] || it.name.equals(row[CostingRateCardsTable.behavior], ignoreCase = true)
            } ?: CostingBehavior.FULL_PACKAGE_COGS,
            version = row[CostingRateCardsTable.version],
            effectiveFrom = row[CostingRateCardsTable.effectiveFrom],
            effectiveTo = row[CostingRateCardsTable.effectiveTo],
            description = row[CostingRateCardsTable.description],
            laborRatePerSamMinute = row[CostingRateCardsTable.laborRateMinorUnits]?.let { Money(it) },
            subcontractRatePerSamMinute = row[CostingRateCardsTable.subcontractRateMinorUnits]?.let { Money(it) },
            serviceFeePerUnit = row[CostingRateCardsTable.serviceFeeMinorUnits]?.let { Money(it) },
            overheadPerUnit = row[CostingRateCardsTable.overheadMinorUnits]?.let { Money(it) },
            packingCostPerUnit = row[CostingRateCardsTable.packingUnitMinorUnits]?.let { Money(it) },
            packingCostPerOrder = row[CostingRateCardsTable.packingOrderMinorUnits]?.let { Money(it) },
            marginRatio = row[CostingRateCardsTable.marginRatioMicros]?.let { Ratio.of(it, 1_000_000L) },
            retailMarkupRatio = row[CostingRateCardsTable.retailMarkupMicros]?.let { Ratio.of(it, 1_000_000L) },
            marketplaceFeeRatio = row[CostingRateCardsTable.marketplaceFeeMicros]?.let { Ratio.of(it, 1_000_000L) },
            fabricWastageToleranceRatio = row[CostingRateCardsTable.fabricWasteMicros]?.let { Ratio.of(it, 1_000_000L) },
            includeFabricCost = row[CostingRateCardsTable.includeFabricCost],
            seededFromNodeId = row[CostingRateCardsTable.seededFromNodeId],
            createdByUserId = row[CostingRateCardsTable.createdByUserId],
            createdAt = row[CostingRateCardsTable.createdAt],
            updatedAt = row[CostingRateCardsTable.updatedAt]
        )
}
