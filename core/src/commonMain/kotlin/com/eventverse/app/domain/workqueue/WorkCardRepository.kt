package com.eventverse.app.domain.workqueue

/**
 * Repository interface untuk agregat [WorkCard].
 */
interface WorkCardRepository {
    suspend fun findById(id: WorkCardId): WorkCard?
    suspend fun findBySubject(tenantId: String, subjectId: String): List<WorkCard>
    suspend fun findByStation(tenantId: String, stationCode: WorkStationCode): List<WorkCard>
    suspend fun save(card: WorkCard)
    suspend fun saveAll(cards: List<WorkCard>)
}
