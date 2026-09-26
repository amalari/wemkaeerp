package com.eventverse.app.infrastructure

import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.domain.workqueue.WorkCardId
import com.eventverse.app.domain.workqueue.WorkDeposit
import com.eventverse.app.domain.workqueue.WorkDepositId
import com.eventverse.app.domain.workqueue.WorkDepositRepository
import com.eventverse.app.infrastructure.tables.WorkCardsTable
import com.eventverse.app.infrastructure.tables.WorkDepositsTable
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll

class PostgresWorkDepositRepository : WorkDepositRepository {

    override suspend fun findById(id: WorkDepositId): WorkDeposit? =
        DatabaseFactory.dbQuery(null) {
            WorkDepositsTable.selectAll()
                .where { WorkDepositsTable.id eq id.value }
                .singleOrNull()
                ?.let(::hydrate)
        }

    override suspend fun findByCardId(cardId: WorkCardId): List<WorkDeposit> =
        DatabaseFactory.dbQuery(null) {
            WorkDepositsTable.selectAll()
                .where { WorkDepositsTable.workCardId eq cardId.value }
                .toList()
                .map(::hydrate)
        }

    override suspend fun findByOperator(tenantId: String, operatorId: String): List<WorkDeposit> =
        DatabaseFactory.dbQuery(TenantId(tenantId)) {
            WorkDepositsTable.selectAll()
                .where {
                    (WorkDepositsTable.tenantId eq tenantId) and
                    (WorkDepositsTable.operatorId eq operatorId)
                }
                .toList()
                .map(::hydrate)
        }

    override suspend fun save(deposit: WorkDeposit) {
        // Retrieve tenantId from the parent work card
        val cardTenantId = DatabaseFactory.dbQuery(null) {
            WorkCardsTable.selectAll()
                .where { WorkCardsTable.id eq deposit.cardId.value }
                .singleOrNull()
                ?.get(WorkCardsTable.tenantId)
        } ?: error("WorkCard ${deposit.cardId.value} not found when saving WorkDeposit")

        DatabaseFactory.dbQuery(TenantId(cardTenantId)) {
            WorkDepositsTable.insert {
                it[id] = deposit.id.value
                it[tenantId] = cardTenantId
                it[workCardId] = deposit.cardId.value
                it[operatorId] = deposit.operatorId
                it[operatorName] = deposit.operatorName
                it[qtyPcs] = deposit.qtyPcs
                it[tariffSnapshotIdr] = deposit.tariffSnapshotIdr
                it[isReworkDeposit] = deposit.isReworkDeposit
                it[notes] = deposit.notes
                it[verifiedPhotoKey] = deposit.verifiedPhotoKey
                it[submittedAt] = deposit.submittedAt
            }
        }
    }

    private fun hydrate(row: ResultRow): WorkDeposit =
        WorkDeposit(
            id = WorkDepositId(row[WorkDepositsTable.id]),
            cardId = WorkCardId(row[WorkDepositsTable.workCardId]),
            operatorId = row[WorkDepositsTable.operatorId],
            operatorName = row[WorkDepositsTable.operatorName],
            qtyPcs = row[WorkDepositsTable.qtyPcs],
            tariffSnapshotIdr = row[WorkDepositsTable.tariffSnapshotIdr],
            isReworkDeposit = row[WorkDepositsTable.isReworkDeposit],
            notes = row[WorkDepositsTable.notes],
            verifiedPhotoKey = row[WorkDepositsTable.verifiedPhotoKey],
            submittedAt = row[WorkDepositsTable.submittedAt]
        )
}
