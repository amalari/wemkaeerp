package com.eventverse.app.domain.workqueue.usecases

import com.eventverse.app.domain.workqueue.WorkCard
import com.eventverse.app.domain.workqueue.WorkCardId
import com.eventverse.app.domain.workqueue.WorkCardRepository
import com.eventverse.app.domain.workqueue.WorkCardStatus
import com.eventverse.app.domain.workqueue.WorkExecutionMode
import com.eventverse.app.domain.workqueue.WorkStationCatalog
import com.eventverse.app.domain.workqueue.WorkStationCode
import com.eventverse.app.domain.workqueue.WorkTrackingUnit
import kotlinx.datetime.Instant

data class CloseBundlesAtMergeGateCommand(
    val tenantId: String,
    val subjectId: String,
    val mergeStationCode: WorkStationCode = WorkStationCatalog.WASHING.code,
    val downstreamStationCode: WorkStationCode = WorkStationCatalog.STEAM.code,
    val now: Instant
)

/**
 * Melebur bendel-bendel terpisah menjadi kartu lot akumulasi PO per ukuran (per size).
 * Terjadi di gerbang cuci/washing saat tali ikat dilepas dan pakaian masuk drum mesin cuci.
 */
class CloseBundlesAtMergeGateUseCase(
    private val cardRepository: WorkCardRepository
) {
    suspend operator fun invoke(command: CloseBundlesAtMergeGateCommand): Result<List<WorkCard>> = runCatching {
        val subjectCards = cardRepository.findBySubject(command.tenantId, command.subjectId)
        val mergeCards = subjectCards.filter { it.stationCode == command.mergeStationCode }

        require(mergeCards.isNotEmpty()) {
            "No work cards found at merge gate station: ${command.mergeStationCode.value}"
        }

        // Invarian: Seluruh bundle yang masuk gerbang peleburan harus sudah tuntas diproses
        val unfinished = mergeCards.filter { it.wipPcs > 0 && it.status != WorkCardStatus.COMPLETED }
        require(unfinished.isEmpty()) {
            "Cannot close merge gate: ${unfinished.size} bundle(s) are still unfinished with pending WIP"
        }

        val updatedMergeCards = mergeCards.map { it.markMerged(command.now) }
        cardRepository.saveAll(updatedMergeCards)

        // Kelompokkan kuantitas selesai per sizeLabel untuk membuat kartu LOT_ACCUMULATION hilir
        val pcsBySize = mergeCards.groupBy { it.sizeLabel }
            .mapValues { (_, cards) -> cards.sumOf { it.completedPcs } }

        val createdLotCards = mutableListOf<WorkCard>()
        val subjectRef = mergeCards.first().subject

        for ((sizeLabel, totalPcs) in pcsBySize) {
            if (totalPcs <= 0) continue

            val lotCardId = WorkCardId("${command.subjectId}-${command.downstreamStationCode.value}-$sizeLabel-lot")
            val lotCard = WorkCard(
                id = lotCardId,
                tenantId = command.tenantId,
                subject = subjectRef,
                stationCode = command.downstreamStationCode,
                sizeLabel = sizeLabel,
                bundleNo = null, // Invarian: LOT_ACCUMULATION tidak membawa bundleNo
                queuedPcs = totalPcs,
                wipPcs = totalPcs,
                scrapPcs = 0,
                reworkPcs = 0,
                trackingUnit = WorkTrackingUnit.LOT_ACCUMULATION,
                status = WorkCardStatus.QUEUED,
                executionMode = WorkExecutionMode.IN_HOUSE,
                createdAt = command.now
            )
            createdLotCards.add(lotCard)
        }

        cardRepository.saveAll(createdLotCards)
        createdLotCards
    }
}
