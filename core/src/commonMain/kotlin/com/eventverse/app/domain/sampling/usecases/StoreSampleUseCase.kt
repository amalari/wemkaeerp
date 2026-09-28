package com.eventverse.app.domain.sampling.usecases

import com.eventverse.app.domain.process.TenantOptionalProcess
import com.eventverse.app.domain.sampling.ExitStages
import com.eventverse.app.domain.sampling.SamplingOrder
import com.eventverse.app.domain.sampling.SamplingOrderRepository
import com.eventverse.app.domain.sampling.SamplingRoute
import com.eventverse.app.domain.sampling.currentStage
import com.eventverse.app.domain.sampling.packingStage
import com.eventverse.app.domain.sampling.storage.SampleStorageRecord
import com.eventverse.app.domain.sampling.storage.SampleStorageRecordId
import com.eventverse.app.domain.sampling.storage.SampleStorageRecordRepository
import com.eventverse.app.domain.sampling.storage.StorageCustodian
import com.eventverse.app.domain.sampling.storage.StorageLocationLabel
import com.eventverse.app.domain.stageflow.StageCode
import kotlinx.datetime.Instant

data class StoreSampleCommand(
    val order: SamplingOrder,
    val location: StorageLocationLabel,
    val qtyPcs: Int,
    /** Penerima simpan — diambil server dari JWT, bukan dari body. */
    val custodian: StorageCustodian,
    val actorRole: String = "",
    val stages: List<StageCode> = SamplingRoute.DEFAULT_FRAME,
    val processes: List<TenantOptionalProcess> = emptyList(),
    val now: Instant
)

data class StoredSample(val order: SamplingOrder, val record: SampleStorageRecord)

/**
 * Menerima barang dari pengemasan ke penyimpanan (rak packing maupun gudang).
 *
 * Perpindahan tahapnya didelegasikan ke [AdvanceSamplingStageUseCase], supaya bila tenant
 * memetakan penyimpanan ke gedung lain, Surat Jalan internal tetap wajib diterima dulu —
 * aturan itu tidak ditulis dua kali.
 */
class StoreSampleUseCase(
    private val orderRepository: SamplingOrderRepository,
    private val storageRepository: SampleStorageRecordRepository,
    private val advanceStage: AdvanceSamplingStageUseCase
) {
    suspend operator fun invoke(command: StoreSampleCommand): Result<StoredSample> = runCatching {
        val order = command.order
        require(order.stageCode == order.packingStage) {
            "Hanya barang yang selesai dikemas yang bisa disimpan — ${order.spkNumber.value} masih di ${order.currentStage.displayName}"
        }
        val advanced = advanceStage(
            AdvanceSamplingStageCommand(
                order = order,
                target = ExitStages.STORAGE,
                stages = command.stages,
                processes = command.processes,
                actorEmail = command.custodian.email,
                actorRole = command.actorRole,
                custodyRecorded = true,
                now = command.now
            )
        ).getOrThrow()

        val record = SampleStorageRecord(
            id = SampleStorageRecordId("sto-${order.id.value.takeLast(40)}-${command.now.toEpochMilliseconds()}"),
            tenantId = order.tenantId,
            orderId = order.id,
            dealId = order.dealId,
            location = command.location,
            qtyPcs = command.qtyPcs,
            storedBy = command.custodian,
            storedAt = command.now
        )
        val saved = orderRepository.save(advanced)
        StoredSample(order = saved, record = storageRepository.save(record))
    }
}
