package com.eventverse.app.domain.costing.usecases

import com.eventverse.app.domain.costing.CostingSheet
import com.eventverse.app.domain.costing.CostingSheetId
import com.eventverse.app.domain.costing.CostingSheetRepository
import com.eventverse.app.domain.tenant.TenantId
import kotlinx.datetime.Clock

/** Mengubah parameterOverrides pada lembar DRAFT/CALCULATED, tanpa menghitung ulang. */
class OverrideCostingParametersUseCase(
    private val sheetRepository: CostingSheetRepository,
    private val clock: Clock = Clock.System
) {
    suspend operator fun invoke(
        tenantId: TenantId,
        sheetId: CostingSheetId,
        overrides: Map<String, String>
    ): Result<CostingSheet> = runCatching {
        val sheet = sheetRepository.findById(tenantId, sheetId)
            ?: error("Lembar HPP tidak ditemukan: ${sheetId.value}")

        if (!sheet.isEditable) {
            error("Lembar HPP #${sheet.number.value} berstatus '${sheet.status.displayName}' tidak dapat diubah")
        }

        val updated = sheet.copy(
            parameterOverrides = overrides,
            updatedAt = clock.now()
        )
        sheetRepository.save(updated)
        updated
    }
}
