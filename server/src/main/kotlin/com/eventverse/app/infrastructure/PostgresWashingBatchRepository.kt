package com.eventverse.app.infrastructure

import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.domain.workqueue.WashingBatch
import com.eventverse.app.domain.workqueue.WashingBatchId
import com.eventverse.app.domain.workqueue.WashingBatchItem
import com.eventverse.app.domain.workqueue.WashingBatchRepository
import com.eventverse.app.domain.workqueue.WashingBatchStatus
import com.eventverse.app.domain.workqueue.WashingSortOutput
import com.eventverse.app.domain.workqueue.WorkCardId
import com.eventverse.app.infrastructure.tables.WashingBatchItemsTable
import com.eventverse.app.infrastructure.tables.WashingBatchSortOutputsTable
import com.eventverse.app.infrastructure.tables.WashingBatchesTable
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq

/**
 * Implementasi Postgres Exposed untuk agregat [WashingBatch]. Cermin V70.
 *
 * Wajib lewat [DatabaseFactory.dbQuery] dengan tenant: transaksi mentah memakai pool tenant **tanpa**
 * `app.current_tenant_id`, sehingga di bawah RLS baca kosong diam-diam dan tulis ditolak.
 */
class PostgresWashingBatchRepository : WashingBatchRepository {

    override suspend fun findById(tenantId: String, batchId: WashingBatchId): WashingBatch? =
        DatabaseFactory.dbQuery(TenantId(tenantId)) {
            val batchRow = WashingBatchesTable.selectAll()
                .where { (WashingBatchesTable.tenantId eq tenantId) and (WashingBatchesTable.id eq batchId.value) }
                .singleOrNull() ?: return@dbQuery null

            val itemRows = WashingBatchItemsTable.selectAll()
                .where { (WashingBatchItemsTable.tenantId eq tenantId) and (WashingBatchItemsTable.batchId eq batchId.value) }
                .toList()

            val sortRows = WashingBatchSortOutputsTable.selectAll()
                .where { (WashingBatchSortOutputsTable.tenantId eq tenantId) and (WashingBatchSortOutputsTable.batchId eq batchId.value) }
                .toList()

            mapBatch(batchRow, itemRows, sortRows)
        }

    override suspend fun findByTenant(tenantId: String, limit: Int): List<WashingBatch> =
        DatabaseFactory.dbQuery(TenantId(tenantId)) {
            val batchRows = WashingBatchesTable.selectAll()
                .where { WashingBatchesTable.tenantId eq tenantId }
                .orderBy(WashingBatchesTable.createdAt, SortOrder.DESC)
                .limit(limit)
                .toList()

            if (batchRows.isEmpty()) return@dbQuery emptyList()

            val batchIds = batchRows.map { it[WashingBatchesTable.id] }

            val itemRows = WashingBatchItemsTable.selectAll()
                .where { (WashingBatchItemsTable.tenantId eq tenantId) and (WashingBatchItemsTable.batchId inList batchIds) }
                .groupBy { it[WashingBatchItemsTable.batchId] }

            val sortRows = WashingBatchSortOutputsTable.selectAll()
                .where { (WashingBatchSortOutputsTable.tenantId eq tenantId) and (WashingBatchSortOutputsTable.batchId inList batchIds) }
                .groupBy { it[WashingBatchSortOutputsTable.batchId] }

            batchRows.map { bRow ->
                val id = bRow[WashingBatchesTable.id]
                mapBatch(bRow, itemRows[id] ?: emptyList(), sortRows[id] ?: emptyList())
            }
        }

    override suspend fun save(batch: WashingBatch): Unit =
        DatabaseFactory.dbQuery(TenantId(batch.tenantId)) {
            val existing = WashingBatchesTable.selectAll()
                .where { WashingBatchesTable.id eq batch.id.value }
                .singleOrNull()

            if (existing == null) {
                WashingBatchesTable.insert {
                    it[id] = batch.id.value
                    it[tenantId] = batch.tenantId
                    it[batchCode] = batch.batchCode
                    it[machineDrumNo] = batch.machineDrumNo
                    it[washRecipe] = batch.washRecipe
                    it[operatorName] = batch.operatorName
                    it[totalBundles] = batch.totalBundles
                    it[totalInputPcs] = batch.totalInputPcs
                    it[totalOutputPcs] = batch.totalOutputPcs
                    it[missingPcs] = batch.missingPcs
                    it[status] = batch.status.name
                    it[notes] = batch.notes
                    it[createdAt] = batch.createdAt
                    it[completedAt] = batch.completedAt
                }

                for (item in batch.items) {
                    WashingBatchItemsTable.insert {
                        it[id] = item.id
                        it[tenantId] = batch.tenantId
                        it[batchId] = batch.id.value
                        it[workCardId] = item.workCardId.value
                        it[subjectId] = item.subjectId
                        it[orderNumber] = item.orderNumber
                        it[articleName] = item.articleName
                        it[bundleNo] = item.bundleNo
                        it[sizeLabel] = item.sizeLabel
                        it[inputPcs] = item.inputPcs
                        it[bundlePhotoKey] = item.bundlePhotoKey
                        it[createdAt] = item.createdAt
                    }
                }
            } else {
                WashingBatchesTable.update({ WashingBatchesTable.id eq batch.id.value }) {
                    it[machineDrumNo] = batch.machineDrumNo
                    it[washRecipe] = batch.washRecipe
                    it[operatorName] = batch.operatorName
                    it[totalBundles] = batch.totalBundles
                    it[totalInputPcs] = batch.totalInputPcs
                    it[totalOutputPcs] = batch.totalOutputPcs
                    it[missingPcs] = batch.missingPcs
                    it[status] = batch.status.name
                    it[notes] = batch.notes
                    it[completedAt] = batch.completedAt
                }
            }

            if (batch.sortOutputs.isNotEmpty()) {
                WashingBatchSortOutputsTable.deleteWhere {
                    (WashingBatchSortOutputsTable.tenantId eq batch.tenantId) and
                    (WashingBatchSortOutputsTable.batchId eq batch.id.value)
                }
                for (out in batch.sortOutputs) {
                    WashingBatchSortOutputsTable.insert {
                        it[id] = "${batch.id.value}-${out.subjectId}-${out.sizeLabel}"
                        it[tenantId] = batch.tenantId
                        it[batchId] = batch.id.value
                        it[subjectId] = out.subjectId
                        it[orderNumber] = out.orderNumber
                        it[sizeLabel] = out.sizeLabel
                        it[outputPcs] = out.outputPcs
                        it[scrapPcs] = out.scrapPcs
                        it[defectPcs] = out.defectPcs
                        it[notes] = out.notes
                        it[createdAt] = batch.completedAt ?: batch.createdAt
                    }
                }
            }
        }

    private fun mapBatch(
        row: ResultRow,
        itemRows: List<ResultRow>,
        sortRows: List<ResultRow>
    ): WashingBatch {
        val items = itemRows.map { iRow ->
            WashingBatchItem(
                id = iRow[WashingBatchItemsTable.id],
                workCardId = WorkCardId(iRow[WashingBatchItemsTable.workCardId]),
                subjectId = iRow[WashingBatchItemsTable.subjectId],
                orderNumber = iRow[WashingBatchItemsTable.orderNumber],
                articleName = iRow[WashingBatchItemsTable.articleName],
                bundleNo = iRow[WashingBatchItemsTable.bundleNo],
                sizeLabel = iRow[WashingBatchItemsTable.sizeLabel],
                inputPcs = iRow[WashingBatchItemsTable.inputPcs],
                bundlePhotoKey = iRow[WashingBatchItemsTable.bundlePhotoKey],
                createdAt = iRow[WashingBatchItemsTable.createdAt]
            )
        }

        val sorts = sortRows.map { sRow ->
            WashingSortOutput(
                subjectId = sRow[WashingBatchSortOutputsTable.subjectId],
                orderNumber = sRow[WashingBatchSortOutputsTable.orderNumber],
                sizeLabel = sRow[WashingBatchSortOutputsTable.sizeLabel],
                outputPcs = sRow[WashingBatchSortOutputsTable.outputPcs],
                scrapPcs = sRow[WashingBatchSortOutputsTable.scrapPcs],
                defectPcs = sRow[WashingBatchSortOutputsTable.defectPcs],
                notes = sRow[WashingBatchSortOutputsTable.notes]
            )
        }

        return WashingBatch(
            id = WashingBatchId(row[WashingBatchesTable.id]),
            tenantId = row[WashingBatchesTable.tenantId],
            batchCode = row[WashingBatchesTable.batchCode],
            machineDrumNo = row[WashingBatchesTable.machineDrumNo],
            washRecipe = row[WashingBatchesTable.washRecipe],
            operatorName = row[WashingBatchesTable.operatorName],
            items = items,
            sortOutputs = sorts,
            totalOutputPcs = row[WashingBatchesTable.totalOutputPcs],
            missingPcs = row[WashingBatchesTable.missingPcs],
            status = WashingBatchStatus.valueOf(row[WashingBatchesTable.status]),
            notes = row[WashingBatchesTable.notes],
            createdAt = row[WashingBatchesTable.createdAt],
            completedAt = row[WashingBatchesTable.completedAt]
        )
    }
}
