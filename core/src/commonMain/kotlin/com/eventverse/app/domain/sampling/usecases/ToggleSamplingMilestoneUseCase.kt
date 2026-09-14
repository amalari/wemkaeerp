package com.eventverse.app.domain.sampling.usecases

import com.eventverse.app.domain.sampling.MilestoneStep
import com.eventverse.app.domain.sampling.SamplingOrder
import com.eventverse.app.domain.sampling.SamplingOrderId
import com.eventverse.app.domain.sampling.SamplingOrderRepository
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate

data class ToggleSamplingMilestoneCommand(
    val orderId: SamplingOrderId,
    val step: MilestoneStep,
    val isCompleted: Boolean,
    val completedAt: LocalDate? = null,
    val notes: String? = null
)

class ToggleSamplingMilestoneUseCase(
    private val repository: SamplingOrderRepository
) {
    suspend operator fun invoke(command: ToggleSamplingMilestoneCommand): Result<SamplingOrder> = runCatching {
        val existing = repository.findById(command.orderId)
            ?: error("SPK Sample tidak ditemukan: ${command.orderId.value}")

        val updated = existing.toggleMilestone(
            step = command.step,
            isCompleted = command.isCompleted,
            completedAt = command.completedAt,
            milestoneNotes = command.notes,
            updatedAt = Clock.System.now()
        )

        repository.save(updated)
    }
}
