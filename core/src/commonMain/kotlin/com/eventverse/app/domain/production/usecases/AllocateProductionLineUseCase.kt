package com.eventverse.app.domain.production.usecases

import com.eventverse.app.domain.production.BulkWorkOrder
import com.eventverse.app.domain.production.BulkWorkOrderId
import com.eventverse.app.domain.production.BulkWorkOrderRepository
import com.eventverse.app.domain.production.MachineLineAllocation
import com.eventverse.app.domain.production.ProductionLineName
import kotlinx.datetime.Clock

data class AllocateProductionLineCommand(
    val workOrderId: BulkWorkOrderId,
    val allocation: MachineLineAllocation
)

/** Menempatkan sebagian beban SPK ke satu lini mesin. Alokasi ulang lini yang sama menimpa, bukan menumpuk. */
class AllocateProductionLineUseCase(
    private val repository: BulkWorkOrderRepository,
    private val clock: Clock = Clock.System
) {
    suspend operator fun invoke(command: AllocateProductionLineCommand): Result<BulkWorkOrder> = runCatching {
        val order = repository.findById(command.workOrderId)
            ?: error("SPK massal tidak ditemukan: ${command.workOrderId.value}")
        repository.save(order.allocateLine(command.allocation, clock.now()))
    }
}

data class RemoveProductionLineCommand(
    val workOrderId: BulkWorkOrderId,
    val lineName: ProductionLineName
)

class RemoveProductionLineUseCase(
    private val repository: BulkWorkOrderRepository,
    private val clock: Clock = Clock.System
) {
    suspend operator fun invoke(command: RemoveProductionLineCommand): Result<BulkWorkOrder> = runCatching {
        val order = repository.findById(command.workOrderId)
            ?: error("SPK massal tidak ditemukan: ${command.workOrderId.value}")
        repository.save(order.removeLineAllocation(command.lineName, clock.now()))
    }
}
