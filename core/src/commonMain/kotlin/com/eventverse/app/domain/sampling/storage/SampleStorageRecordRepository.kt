package com.eventverse.app.domain.sampling.storage

import com.eventverse.app.domain.sampling.SamplingOrderId
import com.eventverse.app.domain.tenant.TenantId

interface SampleStorageRecordRepository {
    /** Catatan kustodi terakhir SPK ini — SPK yang direvisi bisa masuk penyimpanan lagi. */
    suspend fun findLatestByOrderId(tenantId: TenantId, orderId: SamplingOrderId): SampleStorageRecord?
    suspend fun findByDealId(tenantId: TenantId, dealId: String): List<SampleStorageRecord>
    suspend fun save(record: SampleStorageRecord): SampleStorageRecord
}
