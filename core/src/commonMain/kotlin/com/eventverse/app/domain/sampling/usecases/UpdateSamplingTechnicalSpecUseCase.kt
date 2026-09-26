package com.eventverse.app.domain.sampling.usecases

import com.eventverse.app.domain.sampling.*
import kotlinx.datetime.Clock

data class UpdateSamplingTechnicalSpecCommand(
    val orderId: SamplingOrderId,
    val knitSpec: KnitSpec,
    val finishedSizes: List<SizeMeasurement>,
    val rawSizes: List<SizeMeasurement>,
    val machineProgram: MachineProgram,
    val yieldAndTiming: YieldAndTiming
)

class UpdateSamplingTechnicalSpecUseCase(
    private val repository: SamplingOrderRepository
) {
    suspend operator fun invoke(command: UpdateSamplingTechnicalSpecCommand): Result<SamplingOrder> = runCatching {
        val existing = repository.findById(command.orderId)
            ?: error("SPK Sample tidak ditemukan: ${command.orderId.value}")

        val updated = existing.updateTechnicalSpec(
            knitSpec = command.knitSpec,
            finishedSizes = command.finishedSizes,
            rawSizes = command.rawSizes,
            machineProgram = command.machineProgram,
            yieldAndTiming = command.yieldAndTiming,
            updatedAt = Clock.System.now()
        )

        repository.save(updated)
    }
}
