package com.eventverse.app.domain.costing.usecases

import com.eventverse.app.domain.costing.CostingSheet
import com.eventverse.app.domain.costing.CostingSheetId
import com.eventverse.app.domain.costing.CostingSheetRepository
import com.eventverse.app.domain.tenant.TenantId
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

/**
 * Menghitung ulang lembar HPP dengan pricingAsOf baru (default: sekarang).
 * Hanya dapat dijalankan pada lembar yang masih berstatus editable (DRAFT / CALCULATED / REJECTED).
 */
class RepriceCostingSheetUseCase(
    private val sheetRepository: CostingSheetRepository,
    private val calculateCostingSheetUseCase: CalculateCostingSheetUseCase,
    private val clock: Clock = Clock.System
) {
    suspend operator fun invoke(
        tenantId: TenantId,
        sheetId: CostingSheetId,
        pricingAsOf: Instant = clock.now(),
        nodeParams: Map<String, String> = emptyMap()
    ): Result<CostingSheet> = runCatching {
        val sheet = sheetRepository.findById(tenantId, sheetId)
            ?: error("Lembar HPP tidak ditemukan: ${sheetId.value}")

        if (!sheet.isEditable) {
            error("Lembar HPP #${sheet.number.value} berstatus '${sheet.status.displayName}' tidak dapat di-reprice")
        }

        val updatedSheet = sheet.copy(
            pricingAsOf = pricingAsOf,
            updatedAt = clock.now()
        )
        sheetRepository.save(updatedSheet)

        calculateCostingSheetUseCase(
            tenantId = tenantId,
            sheetId = sheetId,
            nodeParams = nodeParams
        ).getOrThrow()
    }
}
