package com.eventverse.app.domain.costing.usecases

import com.eventverse.app.domain.costing.CostingDrift
import com.eventverse.app.domain.costing.CostingSheetId
import com.eventverse.app.domain.costing.CostingSheetRepository
import com.eventverse.app.domain.costing.CostingStrategyResolver
import com.eventverse.app.domain.costing.CostingFormulaInput
import com.eventverse.app.domain.costing.SamMinutesCalculator
import com.eventverse.app.domain.contracts.LaborOperation
import com.eventverse.app.domain.masterdata.MaterialItemRepository
import com.eventverse.app.domain.masterdata.MaterialPriceRepository
import com.eventverse.app.domain.techpack.TechPackId
import com.eventverse.app.domain.techpack.TechPackRepository
import com.eventverse.app.domain.techpack.usecases.PreviewBomMaterialCostUseCase
import com.eventverse.app.domain.tenant.TenantId
import kotlinx.datetime.Clock

/**
 * Mendeteksi apakah lembar HPP yang disetujui sudah tidak akurat akibat perubahan harga material.
 *
 * Langkah:
 * 1. Load lembar + snapshot yang disetujui
 * 2. Hitung ulang HPP dengan harga material terkini
 * 3. Bandingkan `billablePerUnit` — kembalikan [CostingDrift]
 */
class DetectCostingSheetDriftUseCase(
    private val sheetRepository: CostingSheetRepository,
    private val techPackRepository: TechPackRepository,
    private val materialRepository: MaterialItemRepository,
    private val materialPriceRepository: MaterialPriceRepository,
    private val rateCardRepository: com.eventverse.app.domain.costing.CostingRateCardRepository,
    private val strategyResolver: CostingStrategyResolver = CostingStrategyResolver(),
    private val clock: Clock = Clock.System
) {
    suspend operator fun invoke(
        tenantId: TenantId,
        sheetId: CostingSheetId
    ): Result<CostingDrift> = runCatching {
        val sheet = sheetRepository.findById(tenantId, sheetId)
            ?: error("Lembar HPP tidak ditemukan: ${sheetId.value}")

        val snapshot = sheet.approvedSnapshot ?: return@runCatching CostingDrift.None

        val now = clock.now()
        val resolveParamsUseCase = GetEffectiveCostingParametersUseCase(rateCardRepository)
        val params = resolveParamsUseCase(
            tenantId = tenantId,
            behavior = sheet.behavior,
            nodeParams = emptyMap(),
            sheetOverrides = sheet.parameterOverrides,
            pricingAsOf = now
        ).getOrThrow()

        val techPackId = TechPackId(sheet.techPackId)
        val techPack = techPackRepository.findById(tenantId, techPackId)
            ?: error("Tech Pack tidak ditemukan: ${sheet.techPackId}")

        val bomPreview = PreviewBomMaterialCostUseCase(
            techPackRepository = techPackRepository,
            materialRepository = materialRepository,
            priceRepository = materialPriceRepository
        ).invoke(
            tenantId = tenantId,
            techPackId = techPackId,
            orderQuantity = sheet.orderQuantity,
            at = now
        ).getOrThrow()

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

        val currentResult = strategyResolver.resolve(
            costingId = "drift-check-${now.toEpochMilliseconds()}",
            tenantId = tenantId,
            input = CostingFormulaInput(
                techPack = techPack,
                bomCostPreview = bomPreview,
                orderQuantity = sheet.orderQuantity,
                samBreakdown = samBreakdown,
                parameters = params,
                pricingAsOf = now
            ),
            calculatedAt = now
        ).getOrThrow()

        sheet.driftAgainst(currentResult)
    }
}
