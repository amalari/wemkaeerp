package com.eventverse.app.domain.production

import com.eventverse.app.domain.tenant.TenantId

/**
 * Operasi domain SPK massal — bukan DAO generik.
 *
 * `PRODUCTION_MRP` ber-`ScopeCapability.GLOBAL_ONLY`: jadwal mesin dan SPK massal adalah aset
 * bersama pabrik, jadi tidak ada varian query "milik saya sendiri" di sini.
 */
interface BulkWorkOrderRepository {
    suspend fun findById(id: BulkWorkOrderId): BulkWorkOrder?

    suspend fun findAll(tenantId: TenantId, status: BulkProductionStatus? = null): List<BulkWorkOrder>

    /** SPK massal yang lahir dari satu deal — gerbang anti-penerbitan ganda. */
    suspend fun findByDealId(tenantId: TenantId, dealId: String): List<BulkWorkOrder>

    suspend fun save(workOrder: BulkWorkOrder): BulkWorkOrder

    suspend fun nextSpkNumber(tenantId: TenantId): BulkSpkNumber

    suspend fun archive(id: BulkWorkOrderId): Boolean
}
