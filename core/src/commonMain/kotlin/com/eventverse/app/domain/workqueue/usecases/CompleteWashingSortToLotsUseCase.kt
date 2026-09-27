package com.eventverse.app.domain.workqueue.usecases

import com.eventverse.app.domain.workqueue.WashingBatchId
import com.eventverse.app.domain.workqueue.WashingBatchRepository
import com.eventverse.app.domain.workqueue.WashingSortOutput
import com.eventverse.app.domain.workqueue.WorkCard
import com.eventverse.app.domain.workqueue.WorkCardId
import com.eventverse.app.domain.workqueue.WorkCardRepository
import com.eventverse.app.domain.workqueue.WorkCardStatus
import com.eventverse.app.domain.workqueue.WorkExecutionMode
import com.eventverse.app.domain.workqueue.WorkStationCatalog
import com.eventverse.app.domain.workqueue.WorkStationCode
import com.eventverse.app.domain.workqueue.WorkSubjectKind
import com.eventverse.app.domain.workqueue.WorkSubjectRef
import com.eventverse.app.domain.workqueue.WorkTrackingUnit
import kotlinx.datetime.Instant

data class CompleteWashingSortCommand(
    val tenantId: String,
    val batchId: WashingBatchId,
    val sortOutputs: List<WashingSortOutput>,
    val downstreamStationCode: WorkStationCode = WorkStationCatalog.STEAM.code,
    val now: Instant
)

/**
 * UseCase untuk menyelesaikan sortir meja pasca-dryer dan menerbitkan Kartu Lot Setrika (STEAM)
 * yang dipisah secara ketat per PO (subjectId) dan per Ukuran (sizeLabel).
 */
class CompleteWashingSortToLotsUseCase(
    private val cardRepository: WorkCardRepository,
    private val washingBatchRepository: WashingBatchRepository
) {
    suspend operator fun invoke(command: CompleteWashingSortCommand): Result<List<WorkCard>> = runCatching {
        val batch = washingBatchRepository.findById(command.tenantId, command.batchId)
            ?: error("WashingBatch tidak ditemukan: ${command.batchId.value}")

        require(command.sortOutputs.isNotEmpty()) {
            "Hasil sortir meja pasca-dryer tidak boleh kosong"
        }

        val updatedBatch = batch.completeSorting(command.sortOutputs, command.now)

        // 1. Ambil seluruh bundle kartu asal dan tandai sebagai MERGED
        val mergedCards = mutableListOf<WorkCard>()
        for (item in batch.items) {
            val card = cardRepository.findById(item.workCardId) ?: continue
            mergedCards.add(card.markMerged(command.now).copy(wipPcs = 0))
        }
        if (mergedCards.isNotEmpty()) {
            cardRepository.saveAll(mergedCards)
        }

        // 2. Petakan subject metadata dari item batch
        val subjectMap = batch.items.associateBy(
            keySelector = { it.subjectId },
            valueTransform = { item ->
                WorkSubjectRef(
                    kind = WorkSubjectKind.BULK_WORK_ORDER,
                    subjectId = item.subjectId,
                    orderNumber = item.orderNumber,
                    articleName = item.articleName
                )
            }
        )

        // 3. Terbitkan Kartu Lot Hilir (Setrika Uap / STEAM) per PO & per Ukuran
        val createdLotCards = mutableListOf<WorkCard>()
        for (output in command.sortOutputs) {
            if (output.outputPcs <= 0) continue

            val subjectRef = subjectMap[output.subjectId] ?: WorkSubjectRef(
                kind = WorkSubjectKind.BULK_WORK_ORDER,
                subjectId = output.subjectId,
                orderNumber = output.orderNumber,
                articleName = "Garment Lot"
            )

            val lotCardId = WorkCardId("${output.subjectId}-${command.downstreamStationCode.value}-${output.sizeLabel}-lot")
            val lotCard = WorkCard(
                id = lotCardId,
                tenantId = command.tenantId,
                subject = subjectRef,
                stationCode = command.downstreamStationCode,
                sizeLabel = output.sizeLabel,
                bundleNo = null, // Invarian: LOT_ACCUMULATION tidak membawa bundleNo
                queuedPcs = output.outputPcs,
                wipPcs = output.outputPcs,
                scrapPcs = output.scrapPcs,
                reworkPcs = output.defectPcs,
                trackingUnit = WorkTrackingUnit.LOT_ACCUMULATION,
                status = WorkCardStatus.QUEUED,
                executionMode = WorkExecutionMode.IN_HOUSE,
                createdAt = command.now
            )
            createdLotCards.add(lotCard)
        }

        if (createdLotCards.isNotEmpty()) {
            cardRepository.saveAll(createdLotCards)
        }
        washingBatchRepository.save(updatedBatch)

        createdLotCards
    }
}
