package com.eventverse.app.domain.workqueue.usecases

import com.eventverse.app.domain.workqueue.WashingBatch
import com.eventverse.app.domain.workqueue.WashingBatchId
import com.eventverse.app.domain.workqueue.WashingBatchItem
import com.eventverse.app.domain.workqueue.WashingBatchRepository
import com.eventverse.app.domain.workqueue.WashingBatchStatus
import com.eventverse.app.domain.workqueue.WorkCardId
import com.eventverse.app.domain.workqueue.WorkCardRepository
import com.eventverse.app.domain.workqueue.WorkCardStatus
import com.eventverse.app.domain.workqueue.WorkStationCatalog
import com.eventverse.app.domain.workqueue.WorkTrackingUnit
import kotlinx.datetime.Instant

data class BundlePhotoItemInput(
    val cardId: WorkCardId,
    val bundlePhotoKey: String
)

data class CreateWashingBatchCommand(
    val tenantId: String,
    val batchCode: String,
    val machineDrumNo: String,
    val washRecipe: String,
    val operatorName: String,
    val bundleInputs: List<BundlePhotoItemInput>,
    val notes: String = "",
    val now: Instant
)

/**
 * UseCase untuk membuat sesi drum cuci masal baru (Washing Batch).
 * Menegakkan syarat bahwa setiap bundle yang masuk ke mesin cuci wajib menyertakan foto bukti fisik.
 */
class CreateWashingBatchUseCase(
    private val cardRepository: WorkCardRepository,
    private val washingBatchRepository: WashingBatchRepository
) {
    suspend operator fun invoke(command: CreateWashingBatchCommand): Result<WashingBatch> = runCatching {
        require(command.tenantId.isNotBlank()) { "tenantId cannot be blank" }
        require(command.batchCode.isNotBlank()) { "batchCode cannot be blank" }
        require(command.bundleInputs.isNotEmpty()) { "Batch cuci harus berisi minimal satu bundle" }

        val batchId = WashingBatchId("wb-${command.now.toEpochMilliseconds()}-${(100..999).random()}")
        val batchItems = mutableListOf<WashingBatchItem>()
        val updatedCards = mutableListOf<com.eventverse.app.domain.workqueue.WorkCard>()

        for (input in command.bundleInputs) {
            require(input.bundlePhotoKey.isNotBlank()) {
                "Bundle ${input.cardId.value} wajib menyertakan foto bukti fisik sebelum masuk cuci"
            }
            val card = cardRepository.findById(input.cardId)
                ?: error("WorkCard tidak ditemukan: ${input.cardId.value}")

            require(card.tenantId == command.tenantId) {
                "WorkCard ${input.cardId.value} bukan milik tenant ${command.tenantId}"
            }
            require(card.trackingUnit == WorkTrackingUnit.BUNDLE) {
                "Hanya unit BUNDLE yang dapat dilebur di mesin cuci, ditemukan: ${card.trackingUnit}"
            }
            require(card.stationCode == WorkStationCatalog.WASHING.code) {
                "WorkCard harus berada di stasiun WASHING, saat ini di: ${card.stationCode.value}"
            }
            require(card.wipPcs > 0 || card.status == WorkCardStatus.QUEUED) {
                "WorkCard ${input.cardId.value} sudah tidak memiliki WIP aktif"
            }

            val item = WashingBatchItem(
                id = "${batchId.value}-${card.id.value}",
                workCardId = card.id,
                subjectId = card.subject.subjectId,
                orderNumber = card.subject.orderNumber,
                articleName = card.subject.articleName,
                bundleNo = card.bundleNo ?: 1,
                sizeLabel = card.sizeLabel,
                inputPcs = card.wipPcs,
                bundlePhotoKey = input.bundlePhotoKey,
                createdAt = command.now
            )
            batchItems.add(item)
            updatedCards.add(card.copy(status = WorkCardStatus.IN_PROGRESS))
        }

        val batch = WashingBatch(
            id = batchId,
            tenantId = command.tenantId,
            batchCode = command.batchCode,
            machineDrumNo = command.machineDrumNo,
            washRecipe = command.washRecipe,
            operatorName = command.operatorName,
            items = batchItems,
            status = WashingBatchStatus.IN_WASHER,
            notes = command.notes,
            createdAt = command.now
        )

        cardRepository.saveAll(updatedCards)
        washingBatchRepository.save(batch)
        batch
    }
}
