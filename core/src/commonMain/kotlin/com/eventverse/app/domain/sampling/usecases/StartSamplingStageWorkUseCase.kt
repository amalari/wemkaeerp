package com.eventverse.app.domain.sampling.usecases

import com.eventverse.app.domain.sampling.SamplingOrder
import com.eventverse.app.domain.sampling.SamplingOrderId
import com.eventverse.app.domain.sampling.SamplingOrderRepository
import com.eventverse.app.domain.sampling.startStageWork
import kotlinx.datetime.Instant

data class StartSamplingStageWorkCommand(
    val orderId: SamplingOrderId,
    val operatorName: String,
    val actorEmail: String,
    val now: Instant
)

/** Operator mengambil SPK dari antrian mejanya: Antrian → Sedang Dikerjakan. */
class StartSamplingStageWorkUseCase(
    private val repository: SamplingOrderRepository
) {
    suspend operator fun invoke(command: StartSamplingStageWorkCommand): Result<SamplingOrder> = runCatching {
        val order = repository.findById(command.orderId)
            ?: error("SPK Sample tidak ditemukan: ${command.orderId.value}")
        repository.save(order.startStageWork(command.operatorName, command.actorEmail, command.now))
    }
}
