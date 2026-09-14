package com.eventverse.app.domain.costing.usecases

import com.eventverse.app.domain.costing.CostingSheet
import com.eventverse.app.domain.costing.CostingSheetId
import com.eventverse.app.domain.costing.CostingSheetRepository
import com.eventverse.app.domain.costing.CostingSheetStatus
import com.eventverse.app.domain.tenant.TenantId

class GetCostingSheetListUseCase(
    private val sheetRepository: CostingSheetRepository
) {
    suspend fun listByTechPack(tenantId: TenantId, techPackId: String): Result<List<CostingSheet>> =
        runCatching { sheetRepository.findByTechPack(tenantId, techPackId) }

    suspend fun listByStatus(tenantId: TenantId, status: CostingSheetStatus): Result<List<CostingSheet>> =
        runCatching { sheetRepository.findByStatus(tenantId, status) }

    suspend fun getById(tenantId: TenantId, sheetId: CostingSheetId): Result<CostingSheet> = runCatching {
        sheetRepository.findById(tenantId, sheetId)
            ?: error("Lembar HPP tidak ditemukan: ${sheetId.value}")
    }
}
