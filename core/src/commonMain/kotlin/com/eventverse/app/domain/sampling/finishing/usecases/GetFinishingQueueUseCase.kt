package com.eventverse.app.domain.sampling.finishing.usecases

import com.eventverse.app.domain.sampling.FinishingPath
import com.eventverse.app.domain.sampling.SamplingOrder
import com.eventverse.app.domain.sampling.SamplingOrderRepository
import com.eventverse.app.domain.sampling.SamplingStatus
import com.eventverse.app.domain.sampling.finishing.FinishingDeposit
import com.eventverse.app.domain.sampling.finishing.FinishingDepositRepository
import com.eventverse.app.domain.sampling.finishing.FinishingProgress
import com.eventverse.app.domain.sampling.finishing.progressAgainst
import com.eventverse.app.domain.tenant.TenantId

/** Satu baris antrean kerja operator finishing: SPK-nya, akumulasinya, dan riwayat setorannya. */
data class FinishingTask(
    val order: SamplingOrder,
    val progress: FinishingProgress,
    val deposits: List<FinishingDeposit>
) {
    val isComplete: Boolean get() = progress.isComplete
}

/**
 * Antrean tugas finishing internal.
 *
 * Yang masuk antrean hanya SPK berjalur [FinishingPath.INTERNAL] — SPK yang dilempar ke vendor
 * makloon dipantau admin di tab vendor, bukan disetor operator meja. Tugas yang sudah tuntas
 * tetap dikembalikan (ditandai [FinishingTask.isComplete]) supaya operator bisa melihat hasil
 * kerjanya hari itu, bukan langsung hilang dari layar begitu sisa mencapai nol.
 */
class GetFinishingQueueUseCase(
    private val samplingOrderRepository: SamplingOrderRepository,
    private val depositRepository: FinishingDepositRepository
) {
    suspend operator fun invoke(tenantId: TenantId): Result<List<FinishingTask>> = runCatching {
        val deposits = depositRepository.findAll(tenantId).groupBy { it.samplingOrderId }

        samplingOrderRepository.findAll(tenantId)
            .filter { it.finishingPath == FinishingPath.INTERNAL }
            .filter { it.status != SamplingStatus.CANCELLED }
            .map { order ->
                val own = deposits[order.id].orEmpty().sortedBy { it.createdAt }
                FinishingTask(
                    order = order,
                    progress = own.progressAgainst(finishingTargetPcs(order)),
                    deposits = own
                )
            }
            // Yang belum tuntas naik ke atas; sisanya urut SPK terbaru.
            .sortedWith(compareBy({ it.isComplete }, { -it.order.updatedAt.toEpochMilliseconds() }))
    }
}
