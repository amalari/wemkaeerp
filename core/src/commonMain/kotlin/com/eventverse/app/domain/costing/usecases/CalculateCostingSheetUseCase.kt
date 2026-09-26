package com.eventverse.app.domain.costing.usecases

import com.eventverse.app.domain.contracts.LaborOperation
import com.eventverse.app.domain.costing.CostingFormulaInput
import com.eventverse.app.domain.costing.CostingRateCardRepository
import com.eventverse.app.domain.costing.CostingSheet
import com.eventverse.app.domain.costing.CostingSheetId
import com.eventverse.app.domain.costing.CostingSheetRepository
import com.eventverse.app.domain.costing.CostingStrategyResolver
import com.eventverse.app.domain.costing.SamMinutesCalculator
import com.eventverse.app.domain.masterdata.MaterialItemRepository
import com.eventverse.app.domain.masterdata.MaterialPriceRepository
import com.eventverse.app.domain.techpack.TechPackId
import com.eventverse.app.domain.techpack.TechPackRepository
import com.eventverse.app.domain.techpack.usecases.PreviewBomMaterialCostUseCase
import com.eventverse.app.domain.tenant.TenantId
import kotlinx.datetime.Clock

/**
 * Menjalankan kalkulasi HPP untuk lembar yang sudah ada.
 *
 * Urutan:
 * 1. Load sheet + tech pack
 * 2. Resolve parameter (default → node → rateCard → override)
 * 3. Hitung biaya BOM via [PreviewBomMaterialCostUseCase]
 * 4. Hitung SAM via [SamMinutesCalculator]
 * 5. Jalankan strategi via [CostingStrategyResolver]
 * 6. Simpan hasil ke sheet (status → CALCULATED)
 */
class CalculateCostingSheetUseCase(
    private val sheetRepository: CostingSheetRepository,
    private val techPackRepository: TechPackRepository,
    private val materialRepository: MaterialItemRepository,
    private val materialPriceRepository: MaterialPriceRepository,
    private val rateCardRepository: CostingRateCardRepository,
    private val strategyResolver: CostingStrategyResolver = CostingStrategyResolver(),
    private val clock: Clock = Clock.System,
    private val idGenerator: () -> String = { "costing-${clock.now().toEpochMilliseconds()}" }
) {
    suspend operator fun invoke(
        tenantId: TenantId,
        sheetId: CostingSheetId,
        nodeParams: Map<String, String> = emptyMap()
    ): Result<CostingSheet> = runCatching {
        val now = clock.now()

        val sheet = sheetRepository.findById(tenantId, sheetId)
            ?: error("Lembar HPP tidak ditemukan: ${sheetId.value}")

        if (!sheet.isEditable) {
            error("Lembar HPP #${sheet.number.value} berstatus '${sheet.status.displayName}' tidak dapat dihitung ulang")
        }

        // Resolve parameter
        val resolveParamsUseCase = GetEffectiveCostingParametersUseCase(rateCardRepository)
        val warnings = mutableListOf<String>()
        val params = resolveParamsUseCase(
            tenantId = tenantId,
            behavior = sheet.behavior,
            nodeParams = nodeParams,
            sheetOverrides = sheet.parameterOverrides,
            pricingAsOf = sheet.pricingAsOf,
            warnings = warnings
        ).getOrThrow()

        // Load tech pack
        val techPackId = TechPackId(sheet.techPackId)
        val techPack = techPackRepository.findById(tenantId, techPackId)
            ?: error("Tech Pack tidak ditemukan: ${sheet.techPackId}")

        // Hitung biaya BOM via use case yang sudah teruji
        val bomPreview = PreviewBomMaterialCostUseCase(
            techPackRepository = techPackRepository,
            materialRepository = materialRepository,
            priceRepository = materialPriceRepository
        ).invoke(
            tenantId = tenantId,
            techPackId = techPackId,
            orderQuantity = sheet.orderQuantity,
            at = sheet.pricingAsOf
        ).getOrThrow()

        // Hitung SAM via SamMinutesCalculator (tidak Ratio.plus)
        val samBreakdown = SamMinutesCalculator.calculate(
            techPack.laborOperations.map {
                LaborOperation(
                    operationId = it.operationId,
                    name = it.name,
                    samMinutes = it.samMinutes,
                    workstation = it.workstation,
                    isSubcontracted = it.isSubcontracted
                )
            }
        )

        val input = CostingFormulaInput(
            techPack = techPack,
            bomCostPreview = bomPreview,
            orderQuantity = sheet.orderQuantity,
            samBreakdown = samBreakdown,
            parameters = params,
            pricingAsOf = sheet.pricingAsOf
        )

        val result = strategyResolver.resolve(
            costingId = idGenerator(),
            tenantId = tenantId,
            input = input,
            calculatedAt = now
        ).getOrThrow()

        val updatedSheet = sheet.applyCalculation(result, now).getOrThrow()
        sheetRepository.save(updatedSheet)
        updatedSheet
    }
}
