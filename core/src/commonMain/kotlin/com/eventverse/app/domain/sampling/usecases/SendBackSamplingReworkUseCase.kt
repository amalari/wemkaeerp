package com.eventverse.app.domain.sampling.usecases

import com.eventverse.app.domain.pipeline.DefectLiability
import com.eventverse.app.domain.sampling.SamplingOrder
import com.eventverse.app.domain.sampling.SamplingOrderId
import com.eventverse.app.domain.sampling.SamplingOrderRepository
import com.eventverse.app.domain.sampling.sendBackForRework
import com.eventverse.app.domain.stageflow.StageCode
import kotlinx.datetime.Instant

data class SendBackSamplingReworkCommand(
    val orderId: SamplingOrderId,
    val target: StageCode,
    val reason: String,
    val liability: DefectLiability,
    val actorEmail: String,
    val actorRole: String,
    val now: Instant
)

/** Mengirim SPK mundur ke meja penyebab cacat, dengan alasan dan penanggung tercatat. */
class SendBackSamplingReworkUseCase(
    private val repository: SamplingOrderRepository
) {
    suspend operator fun invoke(command: SendBackSamplingReworkCommand): Result<SamplingOrder> = runCatching {
        val order = repository.findById(command.orderId)
            ?: error("SPK Sample tidak ditemukan: ${command.orderId.value}")
        repository.save(
            order.sendBackForRework(
                target = command.target,
                reason = command.reason,
                liability = command.liability,
                actorEmail = command.actorEmail,
                actorRole = command.actorRole,
                now = command.now
            )
        )
    }
}
