package com.eventverse.app.domain.workqueue.usecases

import com.eventverse.app.domain.workqueue.WorkCard
import com.eventverse.app.domain.workqueue.WorkCardId
import com.eventverse.app.domain.workqueue.WorkCardRepository
import com.eventverse.app.domain.workqueue.WorkCardStatus
import com.eventverse.app.domain.workqueue.WorkExecutionMode
import com.eventverse.app.domain.workqueue.WorkStationCatalog
import com.eventverse.app.domain.workqueue.WorkStationCode
import com.eventverse.app.domain.workqueue.WorkSubjectRef
import com.eventverse.app.domain.workqueue.WorkTrackingUnit
import kotlinx.datetime.Instant

data class InitializeWorkCardsCommand(
    val tenantId: String,
    val subject: WorkSubjectRef,
    val quantitiesBySize: Map<String, Int>,
    val bundleCapacity: Int = 20,
    val initialStationCode: WorkStationCode = WorkStationCatalog.CUTTING.code,
    val executionMode: WorkExecutionMode = WorkExecutionMode.IN_HOUSE,
    val vendorRef: String? = null,
    val now: Instant
)

/**
 * Menginisialisasi tumpukan kartu bundle pertama kali (dari Meja Potong atau Turun Mesin Rajut).
 * Memecah total potong per ukuran pakaian menjadi bundel-bundel standar (misal 20–24 pcs).
 */
class InitializeWorkCardsFromCuttingUseCase(
    private val cardRepository: WorkCardRepository
) {
    suspend operator fun invoke(command: InitializeWorkCardsCommand): Result<List<WorkCard>> = runCatching {
        require(command.quantitiesBySize.isNotEmpty()) { "quantitiesBySize cannot be empty" }
        require(command.bundleCapacity > 0) { "bundleCapacity must be positive, was ${command.bundleCapacity}" }

        val createdCards = mutableListOf<WorkCard>()
        var globalBundleCounter = 1

        for ((sizeLabel, totalQty) in command.quantitiesBySize) {
            if (totalQty <= 0) continue

            var remaining = totalQty
            while (remaining > 0) {
                val bundleQty = remaining.coerceAtMost(command.bundleCapacity)
                val cardId = WorkCardId("${command.subject.subjectId}-${command.initialStationCode.value}-$sizeLabel-b$globalBundleCounter")

                val card = WorkCard(
                    id = cardId,
                    tenantId = command.tenantId,
                    subject = command.subject,
                    stationCode = command.initialStationCode,
                    sizeLabel = sizeLabel,
                    bundleNo = globalBundleCounter,
                    queuedPcs = bundleQty,
                    wipPcs = bundleQty,
                    scrapPcs = 0,
                    reworkPcs = 0,
                    trackingUnit = WorkTrackingUnit.BUNDLE,
                    status = WorkCardStatus.QUEUED,
                    executionMode = command.executionMode,
                    vendorRef = command.vendorRef,
                    createdAt = command.now
                )

                createdCards.add(card)
                globalBundleCounter++
                remaining -= bundleQty
            }
        }

        cardRepository.saveAll(createdCards)
        createdCards
    }
}
