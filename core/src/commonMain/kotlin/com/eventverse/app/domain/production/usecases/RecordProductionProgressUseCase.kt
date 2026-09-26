package com.eventverse.app.domain.production.usecases

import com.eventverse.app.domain.production.BulkWorkOrder
import com.eventverse.app.domain.production.BulkWorkOrderId
import com.eventverse.app.domain.production.BulkWorkOrderRepository
import com.eventverse.app.domain.production.ProductionStage
import kotlinx.datetime.Clock

data class RecordProductionProgressCommand(
    val workOrderId: BulkWorkOrderId,
    val stage: ProductionStage,
    /** Angka **kumulatif** tahap ini, bukan tambahan hari ini. */
    val completedPcs: Int,
    val reworkPcs: Int = 0,
    val rejectPcs: Int = 0
)

/**
 * Mencatat realisasi satu tahap lantai produksi.
 *
 * Nilainya kumulatif dan bukan delta: laporan lantai produksi sering terkirim dua kali atau
 * terlambat, dan angka kumulatif membuat pengiriman ganda tidak menggandakan hasil.
 */
class RecordProductionProgressUseCase(
    private val repository: BulkWorkOrderRepository,
    private val clock: Clock = Clock.System
) {
    suspend operator fun invoke(command: RecordProductionProgressCommand): Result<BulkWorkOrder> = runCatching {
        val order = repository.findById(command.workOrderId)
            ?: error("SPK massal tidak ditemukan: ${command.workOrderId.value}")

        val updated = order.recordStageProgress(
            stage = command.stage,
            completedPcs = command.completedPcs,
            reworkPcs = command.reworkPcs,
            rejectPcs = command.rejectPcs,
            updatedAt = clock.now()
        )
        repository.save(updated)
    }
}
