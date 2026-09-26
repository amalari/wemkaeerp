package com.eventverse.app.domain.workqueue.usecases

import com.eventverse.app.domain.workqueue.WorkCard
import com.eventverse.app.domain.workqueue.WorkCardId
import com.eventverse.app.domain.workqueue.WorkCardRepository
import com.eventverse.app.domain.workqueue.WorkDeposit
import com.eventverse.app.domain.workqueue.WorkDepositId
import com.eventverse.app.domain.workqueue.WorkDepositRepository
import com.eventverse.app.domain.workqueue.WorkStationCatalog
import com.eventverse.app.domain.workqueue.WorkStationSpec
import kotlinx.datetime.Instant

data class RecordStationOutputCommand(
    val cardId: WorkCardId,
    val operatorId: String,
    val operatorName: String,
    val completedQty: Int,
    val isReworkDeposit: Boolean = false,
    val notes: String = "",
    val verifiedPhotoKey: String? = null,
    val now: Instant,
    val customStations: List<WorkStationSpec> = emptyList()
)

/**
 * Mencatat setoran output selesai oleh operator di satu stasiun.
 * Merekam jejak upah borongan dan memajukan status kartu antrean.
 */
class RecordStationOutputUseCase(
    private val cardRepository: WorkCardRepository,
    private val depositRepository: WorkDepositRepository
) {
    suspend operator fun invoke(command: RecordStationOutputCommand): Result<Pair<WorkCard, WorkDeposit>> = runCatching {
        val card = cardRepository.findById(command.cardId)
            ?: error("WorkCard not found with id: ${command.cardId.value}")

        val stationSpec = WorkStationCatalog.resolve(card.stationCode, command.customStations)
        val tariffSnapshot = stationSpec?.piecerateTariffIdr ?: 0L

        val depositId = WorkDepositId("dep-${command.cardId.value}-${command.now.toEpochMilliseconds()}")
        val deposit = WorkDeposit(
            id = depositId,
            cardId = card.id,
            operatorId = command.operatorId,
            operatorName = command.operatorName,
            qtyPcs = command.completedQty,
            tariffSnapshotIdr = tariffSnapshot,
            isReworkDeposit = command.isReworkDeposit,
            notes = command.notes,
            verifiedPhotoKey = command.verifiedPhotoKey,
            submittedAt = command.now
        )

        val updatedCard = card.recordOutput(command.completedQty, command.now)

        depositRepository.save(deposit)
        cardRepository.save(updatedCard)

        Pair(updatedCard, deposit)
    }
}
