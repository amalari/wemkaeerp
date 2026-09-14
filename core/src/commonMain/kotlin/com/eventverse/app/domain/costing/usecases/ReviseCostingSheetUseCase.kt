package com.eventverse.app.domain.costing.usecases

import com.eventverse.app.domain.costing.CostingSheet
import com.eventverse.app.domain.costing.CostingSheetId
import com.eventverse.app.domain.costing.CostingSheetRepository
import com.eventverse.app.domain.costing.CostingSheetStatus
import com.eventverse.app.domain.tenant.TenantId
import kotlinx.datetime.Clock

/**
 * Membuat revisi baru dari lembar APPROVED/REJECTED.
 * Lembar lama di-supersede, revisi baru dimulai dari DRAFT.
 */
class ReviseCostingSheetUseCase(
    private val sheetRepository: CostingSheetRepository,
    private val createDraftUseCase: CreateCostingSheetDraftUseCase,
    private val clock: Clock = Clock.System
) {
    suspend operator fun invoke(
        tenantId: TenantId,
        sheetId: CostingSheetId,
        revisedByUserId: String
    ): Result<CostingSheet> = runCatching {
        val now = clock.now()

        val original = sheetRepository.findById(tenantId, sheetId)
            ?: error("Lembar HPP tidak ditemukan: ${sheetId.value}")

        // Supersede lembar lama
        val superseded = original.supersede(now).getOrThrow()
        sheetRepository.save(superseded)

        // Buat revisi baru dengan parameter yang sama
        createDraftUseCase(
            CreateCostingSheetCommand(
                tenantId = tenantId,
                techPackId = original.techPackId,
                orderQuantity = original.orderQuantity,
                behavior = original.behavior,
                pricingAsOf = now,   // Revisi selalu menggunakan harga hari ini
                parameterOverrides = original.parameterOverrides,
                linkedSpkNumber = original.linkedSpkNumber,
                notes = "Revisi dari #${original.number.value}",
                createdByUserId = revisedByUserId
            )
        ).getOrThrow()
    }
}
