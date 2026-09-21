package com.eventverse.app.infrastructure

import com.eventverse.app.domain.pipeline.DefectLiability
import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.domain.workqueue.DefectCode
import com.eventverse.app.domain.workqueue.ReworkTicket
import com.eventverse.app.domain.workqueue.ReworkTicketId
import com.eventverse.app.domain.workqueue.ReworkTicketRepository
import com.eventverse.app.domain.workqueue.ReworkTicketStatus
import com.eventverse.app.domain.workqueue.WorkCardId
import com.eventverse.app.domain.workqueue.WorkStationCode
import com.eventverse.app.domain.workqueue.WorkSubjectKind
import com.eventverse.app.domain.workqueue.WorkSubjectRef
import com.eventverse.app.infrastructure.tables.ReworkTicketsTable
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update

class PostgresReworkTicketRepository : ReworkTicketRepository {

    override suspend fun findById(id: ReworkTicketId): ReworkTicket? =
        DatabaseFactory.dbQuery(null) {
            ReworkTicketsTable.selectAll()
                .where { ReworkTicketsTable.id eq id.value }
                .singleOrNull()
                ?.let(::hydrate)
        }

    override suspend fun findBySubject(tenantId: String, subjectId: String): List<ReworkTicket> =
        DatabaseFactory.dbQuery(TenantId(tenantId)) {
            ReworkTicketsTable.selectAll()
                .where {
                    (ReworkTicketsTable.tenantId eq tenantId) and
                    (ReworkTicketsTable.subjectId eq subjectId)
                }
                .toList()
                .map(::hydrate)
        }

    override suspend fun findByTargetStation(tenantId: String, stationCode: WorkStationCode): List<ReworkTicket> =
        DatabaseFactory.dbQuery(TenantId(tenantId)) {
            ReworkTicketsTable.selectAll()
                .where {
                    (ReworkTicketsTable.tenantId eq tenantId) and
                    (ReworkTicketsTable.targetStationCode eq stationCode.value)
                }
                .toList()
                .map(::hydrate)
        }

    override suspend fun save(ticket: ReworkTicket) {
        DatabaseFactory.dbQuery(TenantId(ticket.tenantId)) {
            val existing = ReworkTicketsTable.selectAll()
                .where { ReworkTicketsTable.id eq ticket.id.value }
                .singleOrNull()

            if (existing == null) {
                ReworkTicketsTable.insert {
                    it[id] = ticket.id.value
                    it[tenantId] = ticket.tenantId
                    it[workCardId] = ticket.cardId.value
                    it[subjectKind] = ticket.subject.kind.name
                    it[subjectId] = ticket.subject.subjectId
                    it[orderNumber] = ticket.subject.orderNumber
                    it[articleName] = ticket.subject.articleName
                    it[defectCode] = ticket.defectCode.value
                    it[defectDisplayName] = ticket.defectDisplayName
                    it[liability] = ticket.liability.name
                    it[qtyPcs] = ticket.qtyPcs
                    it[sizeLabel] = ticket.sizeLabel
                    it[targetStationCode] = ticket.targetStationCode.value
                    it[responsibleOperatorId] = ticket.responsibleOperatorId
                    it[assignedRepairOperatorId] = ticket.assignedRepairOperatorId
                    it[status] = ticket.status.name
                    it[qcNotes] = ticket.qcNotes
                    it[repairNotes] = ticket.repairNotes
                    it[scrapReason] = ticket.scrapReason
                    it[issuedAt] = ticket.issuedAt
                    it[inRepairAt] = ticket.inRepairAt
                    it[readyForRecheckAt] = ticket.readyForRecheckAt
                    it[closedAt] = ticket.closedAt
                }
            } else {
                ReworkTicketsTable.update({ ReworkTicketsTable.id eq ticket.id.value }) {
                    it[assignedRepairOperatorId] = ticket.assignedRepairOperatorId
                    it[status] = ticket.status.name
                    it[repairNotes] = ticket.repairNotes
                    it[scrapReason] = ticket.scrapReason
                    it[inRepairAt] = ticket.inRepairAt
                    it[readyForRecheckAt] = ticket.readyForRecheckAt
                    it[closedAt] = ticket.closedAt
                }
            }
        }
    }

    private fun hydrate(row: ResultRow): ReworkTicket =
        ReworkTicket(
            id = ReworkTicketId(row[ReworkTicketsTable.id]),
            cardId = WorkCardId(row[ReworkTicketsTable.workCardId]),
            tenantId = row[ReworkTicketsTable.tenantId],
            subject = WorkSubjectRef(
                kind = WorkSubjectKind.valueOf(row[ReworkTicketsTable.subjectKind]),
                subjectId = row[ReworkTicketsTable.subjectId],
                orderNumber = row[ReworkTicketsTable.orderNumber],
                articleName = row[ReworkTicketsTable.articleName]
            ),
            defectCode = DefectCode(row[ReworkTicketsTable.defectCode]),
            defectDisplayName = row[ReworkTicketsTable.defectDisplayName],
            liability = DefectLiability.valueOf(row[ReworkTicketsTable.liability]),
            qtyPcs = row[ReworkTicketsTable.qtyPcs],
            sizeLabel = row[ReworkTicketsTable.sizeLabel],
            targetStationCode = WorkStationCode(row[ReworkTicketsTable.targetStationCode]),
            responsibleOperatorId = row[ReworkTicketsTable.responsibleOperatorId],
            assignedRepairOperatorId = row[ReworkTicketsTable.assignedRepairOperatorId],
            status = ReworkTicketStatus.valueOf(row[ReworkTicketsTable.status]),
            qcNotes = row[ReworkTicketsTable.qcNotes],
            repairNotes = row[ReworkTicketsTable.repairNotes],
            scrapReason = row[ReworkTicketsTable.scrapReason],
            issuedAt = row[ReworkTicketsTable.issuedAt],
            inRepairAt = row[ReworkTicketsTable.inRepairAt],
            readyForRecheckAt = row[ReworkTicketsTable.readyForRecheckAt],
            closedAt = row[ReworkTicketsTable.closedAt]
        )
}
