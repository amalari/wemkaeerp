package com.eventverse.app.domain.costing

import com.eventverse.app.domain.tenant.TenantId
import kotlinx.datetime.Instant

interface CostingSheetRepository {
    suspend fun findById(tenantId: TenantId, id: CostingSheetId): CostingSheet?
    suspend fun findByTechPack(tenantId: TenantId, techPackId: String): List<CostingSheet>
    suspend fun findByStatus(tenantId: TenantId, status: CostingSheetStatus): List<CostingSheet>
    /**
     * Lembar yang sudah APPROVED dan dibuat sebelum [createdBefore] — untuk deteksi overdue approval.
     */
    suspend fun findPendingApprovalOlderThan(tenantId: TenantId, createdBefore: Instant): List<CostingSheet>
    suspend fun save(sheet: CostingSheet)
    suspend fun countByStatus(tenantId: TenantId, status: CostingSheetStatus): Int
    /**
     * Rata-rata jam dari pembuatan (DRAFT) ke persetujuan (APPROVED) dalam [withinDays] hari terakhir.
     * Mengembalikan 0.0 jika tidak ada data.
     */
    suspend fun avgApprovalCycleHours(tenantId: TenantId, withinDays: Int = 30): Double
    suspend fun nextSheetNumber(tenantId: TenantId): CostingNumber
}
