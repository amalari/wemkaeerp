package com.eventverse.app.infrastructure

import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.domain.workqueue.WorkCard
import com.eventverse.app.domain.workqueue.WorkCardId
import com.eventverse.app.domain.workqueue.WorkCardRepository
import com.eventverse.app.domain.workqueue.WorkCardStatus
import com.eventverse.app.domain.workqueue.WorkExecutionMode
import com.eventverse.app.domain.workqueue.WorkStationCode
import com.eventverse.app.domain.workqueue.WorkSubjectKind
import com.eventverse.app.domain.workqueue.WorkSubjectRef
import com.eventverse.app.domain.workqueue.WorkTrackingUnit
import com.eventverse.app.infrastructure.tables.WorkCardsTable
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update

class PostgresWorkCardRepository : WorkCardRepository {

    override suspend fun findById(id: WorkCardId): WorkCard? =
        DatabaseFactory.dbQuery(null) {
            WorkCardsTable.selectAll()
                .where { WorkCardsTable.id eq id.value }
                .singleOrNull()
                ?.let(::hydrate)
        }

    override suspend fun findBySubject(tenantId: String, subjectId: String): List<WorkCard> =
        DatabaseFactory.dbQuery(TenantId(tenantId)) {
            WorkCardsTable.selectAll()
                .where {
                    (WorkCardsTable.tenantId eq tenantId) and
                    (WorkCardsTable.subjectId eq subjectId)
                }
                .toList()
                .map(::hydrate)
        }

    override suspend fun findByStation(tenantId: String, stationCode: WorkStationCode): List<WorkCard> =
        DatabaseFactory.dbQuery(TenantId(tenantId)) {
            WorkCardsTable.selectAll()
                .where {
                    (WorkCardsTable.tenantId eq tenantId) and
                    (WorkCardsTable.stationCode eq stationCode.value)
                }
                .toList()
                .map(::hydrate)
        }

    override suspend fun save(card: WorkCard) {
        DatabaseFactory.dbQuery(TenantId(card.tenantId)) {
            val existing = WorkCardsTable.selectAll()
                .where { WorkCardsTable.id eq card.id.value }
                .singleOrNull()

            if (existing == null) {
                WorkCardsTable.insert {
                    it[id] = card.id.value
                    it[tenantId] = card.tenantId
                    it[subjectKind] = card.subject.kind.name
                    it[subjectId] = card.subject.subjectId
                    it[orderNumber] = card.subject.orderNumber
                    it[articleName] = card.subject.articleName
                    it[stationCode] = card.stationCode.value
                    it[sizeLabel] = card.sizeLabel
                    it[bundleNo] = card.bundleNo
                    it[queuedPcs] = card.queuedPcs
                    it[wipPcs] = card.wipPcs
                    it[scrapPcs] = card.scrapPcs
                    it[reworkPcs] = card.reworkPcs
                    it[trackingUnit] = card.trackingUnit.name
                    it[status] = card.status.name
                    it[executionMode] = card.executionMode.name
                    it[vendorRef] = card.vendorRef
                    it[createdAt] = card.createdAt
                    it[completedAt] = card.completedAt
                }
            } else {
                WorkCardsTable.update({ WorkCardsTable.id eq card.id.value }) {
                    it[queuedPcs] = card.queuedPcs
                    it[wipPcs] = card.wipPcs
                    it[scrapPcs] = card.scrapPcs
                    it[reworkPcs] = card.reworkPcs
                    it[status] = card.status.name
                    it[executionMode] = card.executionMode.name
                    it[vendorRef] = card.vendorRef
                    it[completedAt] = card.completedAt
                }
            }
        }
    }

    override suspend fun saveAll(cards: List<WorkCard>) {
        if (cards.isEmpty()) return
        cards.forEach { save(it) }
    }

    private fun hydrate(row: ResultRow): WorkCard =
        WorkCard(
            id = WorkCardId(row[WorkCardsTable.id]),
            tenantId = row[WorkCardsTable.tenantId],
            subject = WorkSubjectRef(
                kind = WorkSubjectKind.valueOf(row[WorkCardsTable.subjectKind]),
                subjectId = row[WorkCardsTable.subjectId],
                orderNumber = row[WorkCardsTable.orderNumber],
                articleName = row[WorkCardsTable.articleName]
            ),
            stationCode = WorkStationCode(row[WorkCardsTable.stationCode]),
            sizeLabel = row[WorkCardsTable.sizeLabel],
            bundleNo = row[WorkCardsTable.bundleNo],
            queuedPcs = row[WorkCardsTable.queuedPcs],
            wipPcs = row[WorkCardsTable.wipPcs],
            scrapPcs = row[WorkCardsTable.scrapPcs],
            reworkPcs = row[WorkCardsTable.reworkPcs],
            trackingUnit = WorkTrackingUnit.valueOf(row[WorkCardsTable.trackingUnit]),
            status = WorkCardStatus.valueOf(row[WorkCardsTable.status]),
            executionMode = WorkExecutionMode.valueOf(row[WorkCardsTable.executionMode]),
            vendorRef = row[WorkCardsTable.vendorRef],
            createdAt = row[WorkCardsTable.createdAt],
            completedAt = row[WorkCardsTable.completedAt]
        )
}
