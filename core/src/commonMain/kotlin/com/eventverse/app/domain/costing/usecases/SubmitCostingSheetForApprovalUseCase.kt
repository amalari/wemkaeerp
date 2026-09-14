package com.eventverse.app.domain.costing.usecases

import com.eventverse.app.domain.costing.CostingSheet
import com.eventverse.app.domain.costing.CostingSheetId
import com.eventverse.app.domain.costing.CostingSheetRepository
import com.eventverse.app.domain.costing.CostingSheetStatus
import com.eventverse.app.domain.tenant.TenantId
import kotlinx.datetime.Clock

/** Mengubah status ke PENDING_APPROVAL setelah validasi parameter kritis. */
class SubmitCostingSheetForApprovalUseCase(
    private val sheetRepository: CostingSheetRepository,
    private val rateCardRepository: com.eventverse.app.domain.costing.CostingRateCardRepository,
    private val clock: Clock = Clock.System
) {
    suspend operator fun invoke(
        tenantId: TenantId,
        sheetId: CostingSheetId,
        submittedByUserId: String
    ): Result<CostingSheet> = runCatching {
        val sheet = sheetRepository.findById(tenantId, sheetId)
            ?: error("Lembar HPP tidak ditemukan: ${sheetId.value}")

        // Cek parameter kritis — tolak jika masih default
        val resolveUseCase = GetEffectiveCostingParametersUseCase(rateCardRepository)
        val params = resolveUseCase(
            tenantId = tenantId,
            behavior = sheet.behavior,
            sheetOverrides = sheet.parameterOverrides,
            pricingAsOf = sheet.pricingAsOf
        ).getOrThrow()

        val criticalKeys = com.eventverse.app.domain.costing.ResolvedCostingParameters
            .criticalProvenanceKeys(sheet.behavior)

        val missingCritical = criticalKeys.filter { key ->
            params.provenance[key] == com.eventverse.app.domain.costing.CostingParameterSource.HARDCODED_DEFAULT
        }.toSet()

        val submitted = sheet.submitForApproval(
            criticalParamsMissing = missingCritical,
            now = clock.now()
        ).getOrThrow()

        sheetRepository.save(submitted)
        submitted
    }
}
