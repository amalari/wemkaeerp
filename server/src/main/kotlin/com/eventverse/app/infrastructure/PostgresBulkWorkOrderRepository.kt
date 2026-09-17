package com.eventverse.app.infrastructure

import com.eventverse.app.domain.pipeline.StockOwnershipSemantics
import com.eventverse.app.domain.production.*
import com.eventverse.app.domain.sampling.SamplingOrderId
import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.infrastructure.tables.BulkWorkOrdersTable
import com.eventverse.app.shared.production.BulkWorkOrderCodec
import kotlinx.datetime.Clock
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.isNull
import org.jetbrains.exposed.sql.statements.UpdateBuilder

class PostgresBulkWorkOrderRepository : BulkWorkOrderRepository {

    override suspend fun findById(id: BulkWorkOrderId): BulkWorkOrder? =
        DatabaseFactory.dbQuery {
            BulkWorkOrdersTable.selectAll()
                .where { (BulkWorkOrdersTable.id eq id.value) and (BulkWorkOrdersTable.archivedAt.isNull()) }
                .singleOrNull()
                ?.let(::toBulkWorkOrder)
        }

    override suspend fun findAll(tenantId: TenantId, status: BulkProductionStatus?): List<BulkWorkOrder> =
        DatabaseFactory.dbQuery(tenantId) {
            BulkWorkOrdersTable.selectAll()
                .where {
                    val base = (BulkWorkOrdersTable.tenantId eq tenantId.value) and
                        (BulkWorkOrdersTable.archivedAt.isNull())
                    if (status != null) base and (BulkWorkOrdersTable.status eq status.name) else base
                }
                .orderBy(BulkWorkOrdersTable.updatedAt, SortOrder.DESC)
                .map(::toBulkWorkOrder)
        }

    override suspend fun findByDealId(tenantId: TenantId, dealId: String): List<BulkWorkOrder> =
        DatabaseFactory.dbQuery(tenantId) {
            BulkWorkOrdersTable.selectAll()
                .where {
                    (BulkWorkOrdersTable.tenantId eq tenantId.value) and
                        (BulkWorkOrdersTable.dealId eq dealId) and
                        (BulkWorkOrdersTable.archivedAt.isNull())
                }
                .orderBy(BulkWorkOrdersTable.createdAt, SortOrder.ASC)
                .map(::toBulkWorkOrder)
        }

    override suspend fun save(workOrder: BulkWorkOrder): BulkWorkOrder =
        DatabaseFactory.dbQuery(workOrder.tenantId) {
            val exists = BulkWorkOrdersTable.selectAll()
                .where { BulkWorkOrdersTable.id eq workOrder.id.value }
                .singleOrNull() != null

            if (exists) {
                BulkWorkOrdersTable.update({ BulkWorkOrdersTable.id eq workOrder.id.value }) {
                    it.applyMutableColumns(workOrder)
                    it[archivedAt] = workOrder.archivedAt
                }
            } else {
                BulkWorkOrdersTable.insert {
                    it[id] = workOrder.id.value
                    it[tenantId] = workOrder.tenantId.value
                    it[spkNumber] = workOrder.spkNumber.value
                    it[dealId] = workOrder.dealId
                    it[goldenSampleOrderId] = workOrder.goldenSampleOrderId?.value
                    it[createdAt] = workOrder.createdAt
                    it.applyMutableColumns(workOrder)
                }
            }

            workOrder
        }

    /**
     * Nomor SPK massal berikutnya.
     *
     * Dihitung dari nomor tertinggi yang pernah dipakai tenant ini, bukan dari `count()`:
     * SPK yang diarsipkan tetap memegang nomornya, dan menghitung baris aktif akan
     * menerbitkan ulang nomor yang sudah ada begitu satu SPK diarsipkan.
     */
    override suspend fun nextSpkNumber(tenantId: TenantId): BulkSpkNumber =
        DatabaseFactory.dbQuery(tenantId) {
            val highest = BulkWorkOrdersTable.selectAll()
                .where { BulkWorkOrdersTable.tenantId eq tenantId.value }
                .mapNotNull { row ->
                    row[BulkWorkOrdersTable.spkNumber]
                        .removePrefix(SPK_PREFIX)
                        .toIntOrNull()
                }
                .maxOrNull() ?: 0

            BulkSpkNumber("$SPK_PREFIX${(highest + 1).toString().padStart(4, '0')}")
        }

    override suspend fun archive(id: BulkWorkOrderId): Boolean =
        DatabaseFactory.dbQuery {
            BulkWorkOrdersTable.update({ BulkWorkOrdersTable.id eq id.value }) {
                it[archivedAt] = Clock.System.now()
            } > 0
        }

    /** Kolom yang boleh berubah sesudah SPK lahir — dipakai bersama oleh insert dan update. */
    private fun UpdateBuilder<*>.applyMutableColumns(order: BulkWorkOrder) {
        this[BulkWorkOrdersTable.clientName] = order.clientName
        this[BulkWorkOrdersTable.styleName] = order.styleName
        this[BulkWorkOrdersTable.status] = order.status.name
        this[BulkWorkOrdersTable.stockOwnership] = order.stockOwnership.name
        this[BulkWorkOrdersTable.sizeBreakdown] = BulkWorkOrderCodec.encodeSizeBreakdown(order.sizeBreakdown)
        this[BulkWorkOrdersTable.lineAllocations] = BulkWorkOrderCodec.encodeAllocations(order.lineAllocations)
        this[BulkWorkOrdersTable.stageProgress] = BulkWorkOrderCodec.encodeStageProgress(order.stageProgress)
        this[BulkWorkOrdersTable.targetOutputPerDay] = order.targetOutputPerDay
        this[BulkWorkOrdersTable.plannedStartDate] = order.plannedStartDate
        this[BulkWorkOrdersTable.plannedFinishDate] = order.plannedFinishDate
        this[BulkWorkOrdersTable.notes] = order.notes
        this[BulkWorkOrdersTable.updatedAt] = order.updatedAt
        this[BulkWorkOrdersTable.releasedAt] = order.releasedAt
    }

    private fun toBulkWorkOrder(row: ResultRow): BulkWorkOrder = BulkWorkOrder(
        id = BulkWorkOrderId(row[BulkWorkOrdersTable.id]),
        tenantId = TenantId(row[BulkWorkOrdersTable.tenantId]),
        spkNumber = BulkSpkNumber(row[BulkWorkOrdersTable.spkNumber]),
        clientName = row[BulkWorkOrdersTable.clientName],
        styleName = row[BulkWorkOrdersTable.styleName],
        status = runCatching { BulkProductionStatus.valueOf(row[BulkWorkOrdersTable.status]) }
            .getOrDefault(BulkProductionStatus.DRAFT),
        dealId = row[BulkWorkOrdersTable.dealId],
        goldenSampleOrderId = row[BulkWorkOrdersTable.goldenSampleOrderId]
            ?.takeIf { it.isNotBlank() }
            ?.let { SamplingOrderId(it) },
        stockOwnership = runCatching { StockOwnershipSemantics.valueOf(row[BulkWorkOrdersTable.stockOwnership]) }
            .getOrDefault(StockOwnershipSemantics.OWNED_RAW_MATERIAL),
        sizeBreakdown = BulkWorkOrderCodec.decodeSizeBreakdown(row[BulkWorkOrdersTable.sizeBreakdown]),
        lineAllocations = BulkWorkOrderCodec.decodeAllocations(row[BulkWorkOrdersTable.lineAllocations]),
        stageProgress = BulkWorkOrderCodec.decodeStageProgress(row[BulkWorkOrdersTable.stageProgress]),
        targetOutputPerDay = row[BulkWorkOrdersTable.targetOutputPerDay],
        plannedStartDate = row[BulkWorkOrdersTable.plannedStartDate],
        plannedFinishDate = row[BulkWorkOrdersTable.plannedFinishDate],
        notes = row[BulkWorkOrdersTable.notes],
        createdAt = row[BulkWorkOrdersTable.createdAt],
        updatedAt = row[BulkWorkOrdersTable.updatedAt],
        releasedAt = row[BulkWorkOrdersTable.releasedAt],
        archivedAt = row[BulkWorkOrdersTable.archivedAt]
    )

    private companion object {
        const val SPK_PREFIX = "SPK-MSL-"
    }
}
