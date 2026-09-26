package com.eventverse.app.domain.costing.usecases

import com.eventverse.app.domain.contracts.CostingCalculationResult
import com.eventverse.app.domain.costing.CostingSheetId
import com.eventverse.app.domain.costing.CostingSheetRepository
import com.eventverse.app.domain.tenant.TenantId

/**
 * Mengambil hasil kalkulasi HPP resmi dari lembar HPP untuk dikonsumsi modul hilir
 * (seperti Sales Quotation, Invoicing, SPK).
 *
 * Mengutamakan hasil dari approval snapshot jika sudah disetujui, atau hasil kalkulasi
 * terkini jika sudah berstatus CALCULATED.
 */
class PublishCostingCalculationResultUseCase(
    private val sheetRepository: CostingSheetRepository
) {
    suspend operator fun invoke(
        tenantId: TenantId,
        sheetId: CostingSheetId
    ): Result<CostingCalculationResult> = runCatching {
        val sheet = sheetRepository.findById(tenantId, sheetId)
            ?: error("Lembar HPP tidak ditemukan: ${sheetId.value}")

        sheet.approvedSnapshot?.result
            ?: sheet.latestResult
            ?: error("Lembar HPP #${sheet.number.value} belum memiliki hasil kalkulasi")
    }
}
