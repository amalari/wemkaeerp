package com.eventverse.app.domain.costing.usecases

import com.eventverse.app.domain.costing.CostingNumber
import com.eventverse.app.domain.costing.CostingSheet
import com.eventverse.app.domain.costing.CostingSheetId
import com.eventverse.app.domain.costing.CostingSheetRepository
import com.eventverse.app.domain.pipeline.CostingBehavior
import com.eventverse.app.domain.tenant.TenantId
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

data class CreateCostingSheetCommand(
    val tenantId: TenantId,
    val techPackId: String,
    val orderQuantity: Long,
    val behavior: CostingBehavior,
    val pricingAsOf: Instant,
    val parameterOverrides: Map<String, String> = emptyMap(),
    val linkedSpkNumber: String? = null,
    val notes: String = "",
    val createdByUserId: String? = null
)

class CreateCostingSheetDraftUseCase(
    private val sheetRepository: CostingSheetRepository,
    private val clock: Clock = Clock.System,
    private val idGenerator: () -> String = { "sheet-${clock.now().toEpochMilliseconds()}" }
) {
    suspend operator fun invoke(command: CreateCostingSheetCommand): Result<CostingSheet> = runCatching {
        require(command.orderQuantity > 0L) { "Kuantitas order harus lebih besar dari 0" }

        val now = clock.now()
        val number = sheetRepository.nextSheetNumber(command.tenantId)

        val sheet = CostingSheet(
            id = CostingSheetId(idGenerator()),
            tenantId = command.tenantId,
            number = number,
            techPackId = command.techPackId,
            orderQuantity = command.orderQuantity,
            behavior = command.behavior,
            pricingAsOf = command.pricingAsOf,
            parameterOverrides = command.parameterOverrides,
            linkedSpkNumber = command.linkedSpkNumber,
            notes = command.notes,
            createdByUserId = command.createdByUserId,
            createdAt = now,
            updatedAt = now
        )
        sheetRepository.save(sheet)
        sheet
    }
}
