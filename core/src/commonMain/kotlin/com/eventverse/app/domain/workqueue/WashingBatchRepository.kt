package com.eventverse.app.domain.workqueue

/**
 * Repository interface untuk agregat [WashingBatch].
 */
interface WashingBatchRepository {
    suspend fun findById(tenantId: String, batchId: WashingBatchId): WashingBatch?
    suspend fun findByTenant(tenantId: String, limit: Int = 50): List<WashingBatch>
    suspend fun save(batch: WashingBatch)
}
