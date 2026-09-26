package com.eventverse.app.domain.workqueue.usecases

import com.eventverse.app.domain.workqueue.ReworkTicketRepository
import com.eventverse.app.domain.workqueue.WorkCardRepository
import com.eventverse.app.domain.workqueue.WorkQueueBoard
import com.eventverse.app.domain.workqueue.WorkStationCode
import com.eventverse.app.domain.workqueue.WorkStationSpec

data class WorkStationTelemetry(
    val stationCode: WorkStationCode,
    val displayName: String,
    val queuedPcs: Int,
    val wipPcs: Int,
    val completedPcs: Int,
    val activeTicketsCount: Int,
    val healthStatus: String
)

data class WorkQueueNodeTelemetry(
    val subjectId: String,
    val totalWipPcs: Int,
    val totalInRepairPcs: Int,
    val totalScrapPcs: Int,
    val totalCompletedPcs: Int,
    val balanceDeltaPcs: Int,
    val isBalanced: Boolean,
    val stations: List<WorkStationTelemetry>
)

data class GetWorkQueueTelemetryQuery(
    val tenantId: String,
    val subjectId: String,
    val orderedPcs: Int,
    val customStations: List<WorkStationSpec> = emptyList()
)

/**
 * Menyediakan telemetri kesehatan lini produksi untuk kanvas Factory Flow dan Control Tower.
 */
class GetWorkQueueTelemetryUseCase(
    private val getBoardUseCase: GetWorkQueueBoardUseCase
) {
    suspend operator fun invoke(query: GetWorkQueueTelemetryQuery): Result<WorkQueueNodeTelemetry> = runCatching {
        val boardResult = getBoardUseCase(
            GetWorkQueueBoardQuery(
                tenantId = query.tenantId,
                subjectId = query.subjectId,
                orderedPcs = query.orderedPcs,
                customStations = query.customStations
            )
        ).getOrThrow()

        val stationTelemetries = boardResult.columns.map { column ->
            val health = when {
                column.activeTickets.size >= 5 || column.totalWipPcs > (query.orderedPcs * 0.5) -> "BOTTLENECK"
                column.activeTickets.isNotEmpty() || column.totalWipPcs > 0 -> "WARNING"
                else -> "HEALTHY"
            }

            WorkStationTelemetry(
                stationCode = column.station.code,
                displayName = column.station.displayName,
                queuedPcs = column.totalQueuedPcs,
                wipPcs = column.totalWipPcs,
                completedPcs = column.totalCompletedPcs,
                activeTicketsCount = column.activeTickets.size,
                healthStatus = health
            )
        }

        WorkQueueNodeTelemetry(
            subjectId = query.subjectId,
            totalWipPcs = boardResult.balance.wipPcs,
            totalInRepairPcs = boardResult.balance.inRepairPcs,
            totalScrapPcs = boardResult.balance.scrapPcs,
            totalCompletedPcs = boardResult.balance.finishedPcs,
            balanceDeltaPcs = boardResult.balance.reconciliationDeltaPcs,
            isBalanced = boardResult.balance.isBalanced,
            stations = stationTelemetries
        )
    }
}
