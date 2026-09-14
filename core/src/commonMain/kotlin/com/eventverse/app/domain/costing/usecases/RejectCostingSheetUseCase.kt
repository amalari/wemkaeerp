package com.eventverse.app.domain.costing.usecases

import com.eventverse.app.domain.costing.CostingSheet
import com.eventverse.app.domain.costing.CostingSheetId
import com.eventverse.app.domain.costing.CostingSheetRepository
import com.eventverse.app.domain.tenant.TenantId
import kotlinx.datetime.Clock

class RejectCostingSheetUseCase(
    private val sheetRepository: CostingSheetRepository,
    private val clock: Clock = Clock.System
) {
    suspend operator fun invoke(
        tenantId: TenantId,
        sheetId: CostingSheetId,
        rejectedByUserId: String,
        reason: String
    ): Result<CostingSheet> = runCatching {
        require(reason.isNotBlank()) { "Alasan penolakan tidak boleh kosong" }

        val sheet = sheetRepository.findById(tenantId, sheetId)
            ?: error("Lembar HPP tidak ditemukan: ${sheetId.value}")

        val rejected = sheet.reject(reason, clock.now()).getOrThrow()
        sheetRepository.save(rejected)
        rejected
    }
}
