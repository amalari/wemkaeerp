package com.eventverse.app.domain.workqueue

/**
 * Repository interface untuk agregat [ReworkTicket].
 */
interface ReworkTicketRepository {
    suspend fun findById(id: ReworkTicketId): ReworkTicket?
    suspend fun findBySubject(tenantId: String, subjectId: String): List<ReworkTicket>
    suspend fun findByTargetStation(tenantId: String, stationCode: WorkStationCode): List<ReworkTicket>
    suspend fun save(ticket: ReworkTicket)
}
