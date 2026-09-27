package com.eventverse.app.domain.sampling.usecases

import com.eventverse.app.domain.process.TenantOptionalProcess
import com.eventverse.app.domain.sampling.SamplingOrder
import com.eventverse.app.domain.sampling.SamplingOrderRepository
import com.eventverse.app.domain.sampling.SamplingPipelineStage
import com.eventverse.app.domain.sampling.storage.SampleStorageRecord
import com.eventverse.app.domain.sampling.storage.SampleStorageRecordRepository
import com.eventverse.app.domain.sampling.storage.StorageCustodian
import com.eventverse.app.domain.sampling.storage.dealStorageReadiness
import kotlinx.datetime.Instant

data class ReleaseSampleFromStorageCommand(
    val order: SamplingOrder,
    /** PIC kirim — diambil server dari JWT, bukan dari body. */
    val pic: StorageCustodian,
    val actorRole: String = "",
    /** Wajib bila SPK lain dalam deal yang sama belum tersimpan. */
    val partialReason: String? = null,
    val stages: List<SamplingPipelineStage> = SamplingPipelineStage.entries,
    val processes: List<TenantOptionalProcess> = emptyList(),
    val now: Instant
)

data class ReleasedSample(val order: SamplingOrder, val record: SampleStorageRecord)

/**
 * Melepas barang dari penyimpanan ke pengiriman buyer.
 *
 * Default-nya menunggu seluruh SPK aktif dalam satu deal tersimpan, karena buyer menerima
 * satu kiriman untuk satu PO. Kirim parsial tetap boleh — hanya dari penyimpanan, dan
 * alasannya ikut tercatat di jejak audit tahap sehingga bisa ditanyakan balik nanti.
 */
class ReleaseSampleFromStorageUseCase(
    private val orderRepository: SamplingOrderRepository,
    private val storageRepository: SampleStorageRecordRepository,
    private val advanceStage: AdvanceSamplingStageUseCase
) {
    suspend operator fun invoke(command: ReleaseSampleFromStorageCommand): Result<ReleasedSample> = runCatching {
        val order = command.order
        require(order.pipelineStage == SamplingPipelineStage.STORAGE_HOLDING) {
            "Pengiriman hanya dari penyimpanan — ${order.spkNumber.value} masih di ${order.pipelineStage.displayName}"
        }
        val record = requireNotNull(storageRepository.findLatestByOrderId(order.tenantId, order.id)) {
            "Catatan penyimpanan ${order.spkNumber.value} tidak ditemukan — simpan ulang barangnya dulu"
        }

        val partialReason = command.partialReason?.trim()?.takeIf { it.isNotEmpty() }
        requireDealComplete(order, partialReason)

        val advanced = advanceStage(
            AdvanceSamplingStageCommand(
                order = order,
                target = SamplingPipelineStage.IN_DELIVERY,
                stages = command.stages,
                processes = command.processes,
                actorEmail = command.pic.email,
                actorRole = partialReason
                    ?.let { "${command.actorRole} (kirim parsial: $it)" }
                    ?: command.actorRole,
                custodyRecorded = true,
                now = command.now
            )
        ).getOrThrow()

        val released = record.release(command.pic, command.now, partialReason)
        val saved = orderRepository.save(advanced)
        ReleasedSample(order = saved, record = storageRepository.save(released))
    }

    private suspend fun requireDealComplete(order: SamplingOrder, partialReason: String?) {
        if (partialReason != null) return
        val dealId = order.dealId?.takeIf { it.isNotBlank() } ?: return
        val readiness = dealStorageReadiness(orderRepository.findByDealId(order.tenantId, dealId))
        if (readiness.isComplete) return
        throw IllegalArgumentException(
            "${readiness.pending.size} dari ${readiness.total} SPK deal ini belum masuk penyimpanan: " +
                readiness.pending.joinToString { it.spkNumber.value } +
                ". Tunggu lengkap, atau kirim parsial dengan alasan."
        )
    }
}
