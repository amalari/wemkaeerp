package com.eventverse.app.domain.sampling.qc

import com.eventverse.app.domain.sampling.SamplingOrderId
import com.eventverse.app.domain.tenant.TenantId

interface QcInspectionRepository {
    suspend fun findBySamplingOrder(tenantId: TenantId, samplingOrderId: SamplingOrderId): List<QcInspection>

    /** Seluruh lembar inspeksi tenant — `QUALITY_CONTROL` bersifat GLOBAL_ONLY (data kolektif pabrik). */
    suspend fun findAll(tenantId: TenantId): List<QcInspection>

    suspend fun save(inspection: QcInspection): QcInspection

    suspend fun nextId(tenantId: TenantId): QcInspectionId
}
