package com.eventverse.app.domain.workqueue

/**
 * Repository interface untuk entitas append-only [WorkDeposit].
 */
interface WorkDepositRepository {
    suspend fun findById(id: WorkDepositId): WorkDeposit?
    suspend fun findByCardId(cardId: WorkCardId): List<WorkDeposit>
    suspend fun findByOperator(tenantId: String, operatorId: String): List<WorkDeposit>
    suspend fun save(deposit: WorkDeposit)
}
