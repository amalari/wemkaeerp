package com.eventverse.app.shared.workqueue

import com.eventverse.app.domain.pipeline.DefectLiability
import com.eventverse.app.domain.workqueue.DefectCode
import com.eventverse.app.domain.workqueue.ReworkTicket
import com.eventverse.app.domain.workqueue.ReworkTicketId
import com.eventverse.app.domain.workqueue.ReworkTicketStatus
import com.eventverse.app.domain.workqueue.WorkCard
import com.eventverse.app.domain.workqueue.WorkCardId
import com.eventverse.app.domain.workqueue.WorkCardStatus
import com.eventverse.app.domain.workqueue.WorkDeposit
import com.eventverse.app.domain.workqueue.WorkDepositId
import com.eventverse.app.domain.workqueue.WorkExecutionMode
import com.eventverse.app.domain.workqueue.WorkQueueBoard
import com.eventverse.app.domain.workqueue.WorkQueueColumn
import com.eventverse.app.domain.workqueue.WorkStationCode
import com.eventverse.app.domain.workqueue.WorkSubjectKind
import com.eventverse.app.domain.workqueue.WorkSubjectRef
import com.eventverse.app.domain.workqueue.WorkTrackingUnit
import com.eventverse.app.shared.common.DateTimeCodec
import com.eventverse.app.shared.json.JsonValue
import com.eventverse.app.shared.json.jsonArrayOf
import com.eventverse.app.shared.json.jsonObjectOf
import com.eventverse.app.shared.json.jsonOf
import kotlinx.datetime.Instant

object WorkQueueCodec {

    private val EPOCH = Instant.fromEpochMilliseconds(0)

    fun encodeCard(card: WorkCard): JsonValue.Obj = jsonObjectOf(
        "id" to jsonOf(card.id.value),
        "tenantId" to jsonOf(card.tenantId),
        "subjectKind" to jsonOf(card.subject.kind.name),
        "subjectId" to jsonOf(card.subject.subjectId),
        "orderNumber" to jsonOf(card.subject.orderNumber),
        "articleName" to jsonOf(card.subject.articleName),
        "stationCode" to jsonOf(card.stationCode.value),
        "sizeLabel" to jsonOf(card.sizeLabel),
        "bundleNo" to (card.bundleNo?.let(::jsonOf) ?: JsonValue.Null),
        "queuedPcs" to jsonOf(card.queuedPcs),
        "wipPcs" to jsonOf(card.wipPcs),
        "scrapPcs" to jsonOf(card.scrapPcs),
        "reworkPcs" to jsonOf(card.reworkPcs),
        "trackingUnit" to jsonOf(card.trackingUnit.name),
        "status" to jsonOf(card.status.name),
        "executionMode" to jsonOf(card.executionMode.name),
        "vendorRef" to (card.vendorRef?.let(::jsonOf) ?: JsonValue.Null),
        "createdAt" to jsonOf(card.createdAt.toString()),
        "completedAt" to (card.completedAt?.let { jsonOf(it.toString()) } ?: JsonValue.Null)
    )

    fun decodeCard(obj: JsonValue.Obj): WorkCard = WorkCard(
        id = WorkCardId(obj.string("id") ?: ""),
        tenantId = obj.string("tenantId") ?: "",
        subject = WorkSubjectRef(
            kind = runCatching {
                WorkSubjectKind.valueOf(obj.string("subjectKind") ?: WorkSubjectKind.BULK_WORK_ORDER.name)
            }.getOrDefault(WorkSubjectKind.BULK_WORK_ORDER),
            subjectId = obj.string("subjectId") ?: "",
            orderNumber = obj.string("orderNumber") ?: "",
            articleName = obj.string("articleName") ?: ""
        ),
        stationCode = WorkStationCode(obj.string("stationCode") ?: ""),
        sizeLabel = obj.string("sizeLabel") ?: "",
        bundleNo = obj.int("bundleNo"),
        queuedPcs = obj.int("queuedPcs") ?: 0,
        wipPcs = obj.int("wipPcs") ?: 0,
        scrapPcs = obj.int("scrapPcs") ?: 0,
        reworkPcs = obj.int("reworkPcs") ?: 0,
        trackingUnit = runCatching {
            WorkTrackingUnit.valueOf(obj.string("trackingUnit") ?: WorkTrackingUnit.BUNDLE.name)
        }.getOrDefault(WorkTrackingUnit.BUNDLE),
        status = runCatching {
            WorkCardStatus.valueOf(obj.string("status") ?: WorkCardStatus.QUEUED.name)
        }.getOrDefault(WorkCardStatus.QUEUED),
        executionMode = runCatching {
            WorkExecutionMode.valueOf(obj.string("executionMode") ?: WorkExecutionMode.IN_HOUSE.name)
        }.getOrDefault(WorkExecutionMode.IN_HOUSE),
        vendorRef = obj.string("vendorRef"),
        createdAt = obj.string("createdAt")?.let { DateTimeCodec.parseInstantOrFallback(it, EPOCH) } ?: EPOCH,
        completedAt = obj.string("completedAt")?.let { DateTimeCodec.parseInstantOrFallback(it, EPOCH) }
    )

    fun encodeDeposit(dep: WorkDeposit): JsonValue.Obj = jsonObjectOf(
        "id" to jsonOf(dep.id.value),
        "cardId" to jsonOf(dep.cardId.value),
        "operatorId" to jsonOf(dep.operatorId),
        "operatorName" to jsonOf(dep.operatorName),
        "qtyPcs" to jsonOf(dep.qtyPcs),
        "tariffSnapshotIdr" to jsonOf(dep.tariffSnapshotIdr),
        "isReworkDeposit" to jsonOf(dep.isReworkDeposit),
        "earnedPayIdr" to jsonOf(dep.earnedPayIdr),
        "notes" to jsonOf(dep.notes),
        "verifiedPhotoKey" to (dep.verifiedPhotoKey?.let(::jsonOf) ?: JsonValue.Null),
        "submittedAt" to jsonOf(dep.submittedAt.toString())
    )

    fun decodeDeposit(obj: JsonValue.Obj): WorkDeposit = WorkDeposit(
        id = WorkDepositId(obj.string("id") ?: ""),
        cardId = WorkCardId(obj.string("cardId") ?: ""),
        operatorId = obj.string("operatorId") ?: "",
        operatorName = obj.string("operatorName") ?: "",
        qtyPcs = obj.int("qtyPcs") ?: 0,
        tariffSnapshotIdr = obj.long("tariffSnapshotIdr") ?: 0L,
        isReworkDeposit = obj.boolean("isReworkDeposit") ?: false,
        notes = obj.string("notes") ?: "",
        verifiedPhotoKey = obj.string("verifiedPhotoKey"),
        submittedAt = obj.string("submittedAt")?.let { DateTimeCodec.parseInstantOrFallback(it, EPOCH) } ?: EPOCH
    )

    fun encodeReworkTicket(t: ReworkTicket): JsonValue.Obj = jsonObjectOf(
        "id" to jsonOf(t.id.value),
        "cardId" to jsonOf(t.cardId.value),
        "tenantId" to jsonOf(t.tenantId),
        "defectCode" to jsonOf(t.defectCode.value),
        "defectDisplayName" to jsonOf(t.defectDisplayName),
        "liability" to jsonOf(t.liability.name),
        "qtyPcs" to jsonOf(t.qtyPcs),
        "sizeLabel" to jsonOf(t.sizeLabel),
        "targetStationCode" to jsonOf(t.targetStationCode.value),
        "responsibleOperatorId" to (t.responsibleOperatorId?.let(::jsonOf) ?: JsonValue.Null),
        "assignedRepairOperatorId" to (t.assignedRepairOperatorId?.let(::jsonOf) ?: JsonValue.Null),
        "status" to jsonOf(t.status.name),
        "qcNotes" to jsonOf(t.qcNotes),
        "repairNotes" to jsonOf(t.repairNotes),
        "scrapReason" to (t.scrapReason?.let(::jsonOf) ?: JsonValue.Null),
        "issuedAt" to jsonOf(t.issuedAt.toString()),
        "inRepairAt" to (t.inRepairAt?.let { jsonOf(it.toString()) } ?: JsonValue.Null),
        "readyForRecheckAt" to (t.readyForRecheckAt?.let { jsonOf(it.toString()) } ?: JsonValue.Null),
        "closedAt" to (t.closedAt?.let { jsonOf(it.toString()) } ?: JsonValue.Null)
    )

    fun encodeColumn(col: WorkQueueColumn): JsonValue.Obj = jsonObjectOf(
        "stationCode" to jsonOf(col.station.code.value),
        "stationName" to jsonOf(col.station.displayName),
        "totalQueuedPcs" to jsonOf(col.totalQueuedPcs),
        "totalWipPcs" to jsonOf(col.totalWipPcs),
        "totalCompletedPcs" to jsonOf(col.totalCompletedPcs),
        "cards" to jsonArrayOf(col.cards.map(::encodeCard)),
        "activeTickets" to jsonArrayOf(col.activeTickets.map(::encodeReworkTicket)),
        "isClear" to jsonOf(col.isClear)
    )

    fun encodeBoard(board: WorkQueueBoard): JsonValue.Obj = jsonObjectOf(
        "subjectKind" to jsonOf(board.subject.kind.name),
        "subjectId" to jsonOf(board.subject.subjectId),
        "orderNumber" to jsonOf(board.subject.orderNumber),
        "articleName" to jsonOf(board.subject.articleName),
        "columns" to jsonArrayOf(board.columns.map(::encodeColumn)),
        "orderedPcs" to jsonOf(board.balance.orderedPcs),
        "wipPcs" to jsonOf(board.balance.wipPcs),
        "inRepairPcs" to jsonOf(board.balance.inRepairPcs),
        "scrapPcs" to jsonOf(board.balance.scrapPcs),
        "finishedPcs" to jsonOf(board.balance.finishedPcs),
        "isBalanced" to jsonOf(board.balance.isBalanced)
    )
}
