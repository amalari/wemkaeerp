package com.eventverse.app.domain.sampling.usecases

import com.eventverse.app.domain.sampling.SamplingOrder
import com.eventverse.app.domain.sampling.SamplingOrderId
import com.eventverse.app.domain.sampling.SamplingOrderRepository
import kotlinx.datetime.Clock

data class ApproveSamplingOrderCommand(
    val orderId: SamplingOrderId,
    val isApproved: Boolean, // true: ACC_APPROVED, false: REVISION
    val notes: String = ""
)

class ApproveSamplingOrderUseCase(
    private val repository: SamplingOrderRepository
) {
    suspend operator fun invoke(command: ApproveSamplingOrderCommand): Result<SamplingOrder> = runCatching {
        val existing = repository.findById(command.orderId)
            ?: error("SPK Sample tidak ditemukan: ${command.orderId.value}")

        val now = Clock.System.now()
        val updated = if (command.isApproved) {
            existing.approveAcc(notes = command.notes, updatedAt = now)
        } else {
            existing.requestRevision(notes = command.notes, updatedAt = now)
        }

        repository.save(updated)
    }
}
