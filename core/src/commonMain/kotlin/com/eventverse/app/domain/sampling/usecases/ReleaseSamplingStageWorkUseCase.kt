package com.eventverse.app.domain.sampling.usecases

import com.eventverse.app.domain.sampling.SamplingOrder
import com.eventverse.app.domain.sampling.SamplingOrderId
import com.eventverse.app.domain.sampling.SamplingOrderRepository
import com.eventverse.app.domain.sampling.releaseStageWork
import kotlinx.datetime.Instant

data class ReleaseSamplingStageWorkCommand(
    val orderId: SamplingOrderId,
    val actorEmail: String,
    val now: Instant
)

/** Operator mengembalikan SPK ke antrian mejanya: Sedang Dikerjakan → Antrian. */
class ReleaseSamplingStageWorkUseCase(
    private val repository: SamplingOrderRepository
) {
    suspend operator fun invoke(command: ReleaseSamplingStageWorkCommand): Result<SamplingOrder> = runCatching {
        val order = repository.findById(command.orderId)
            ?: error("SPK Sample tidak ditemukan: ${command.orderId.value}")
        repository.save(order.releaseStageWork(command.now, command.actorEmail))
    }
}
