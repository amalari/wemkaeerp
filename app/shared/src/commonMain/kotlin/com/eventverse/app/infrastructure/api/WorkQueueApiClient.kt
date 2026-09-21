package com.eventverse.app.infrastructure.api

import com.eventverse.app.domain.pipeline.DefectLiability
import com.eventverse.app.domain.workqueue.DefectCode
import com.eventverse.app.domain.workqueue.ReworkTicket
import com.eventverse.app.domain.workqueue.ReworkTicketId
import com.eventverse.app.domain.workqueue.ReworkTicketStatus
import com.eventverse.app.domain.workqueue.WorkCard
import com.eventverse.app.domain.workqueue.WorkCardId
import com.eventverse.app.domain.workqueue.WorkDeposit
import com.eventverse.app.domain.workqueue.WorkQueueBalance
import com.eventverse.app.domain.workqueue.WorkQueueBoard
import com.eventverse.app.domain.workqueue.WorkQueueColumn
import com.eventverse.app.domain.workqueue.WorkStationCatalog
import com.eventverse.app.domain.workqueue.WorkStationCode
import com.eventverse.app.domain.workqueue.WorkSubjectKind
import com.eventverse.app.domain.workqueue.WorkSubjectRef
import com.eventverse.app.shared.json.JsonParser
import com.eventverse.app.shared.json.JsonValue
import com.eventverse.app.shared.json.jsonArrayOf
import com.eventverse.app.shared.json.jsonObjectOf
import com.eventverse.app.shared.json.jsonOf
import com.eventverse.app.shared.workqueue.WorkQueueCodec
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.datetime.Clock

interface WorkQueueRemoteDataSource {
    suspend fun fetchBoard(subjectId: String, orderedPcs: Int): Result<WorkQueueBoard>
    suspend fun initializeCards(
        subject: WorkSubjectRef,
        quantitiesBySize: Map<String, Int>,
        bundleCapacity: Int = 20,
        initialStationCode: WorkStationCode = WorkStationCatalog.CUTTING.code
    ): Result<Int>
    suspend fun recordOutput(
        cardId: WorkCardId,
        operatorId: String,
        operatorName: String,
        completedQty: Int,
        isReworkDeposit: Boolean = false,
        notes: String = ""
    ): Result<Pair<WorkCard, WorkDeposit>>
    suspend fun issueRework(
        cardId: WorkCardId,
        defectCode: DefectCode,
        qtyPcs: Int,
        responsibleOperatorId: String? = null,
        qcNotes: String = ""
    ): Result<ReworkTicket>
    suspend fun advanceRework(
        ticketId: String,
        action: String,
        repairOperatorId: String? = null,
        repairNotes: String = "",
        scrapReason: String? = null
    ): Result<ReworkTicket>
    suspend fun closeMergeGate(
        subjectId: String,
        mergeStationCode: WorkStationCode = WorkStationCatalog.WASHING.code,
        downstreamStationCode: WorkStationCode = WorkStationCatalog.STEAM.code
    ): Result<Int>
}

class WorkQueueApiClient(
    private val httpClient: HttpClient = HttpClient(),
    private val baseUrl: String = "",
    private val tokenProvider: SessionTokenProvider = StoredSessionTokenProvider
) : WorkQueueRemoteDataSource {

    private val tenantSlug: String
        get() = StoredTenantSlugProvider.currentTenantSlug() ?: "demo-tenant"

    override suspend fun fetchBoard(subjectId: String, orderedPcs: Int): Result<WorkQueueBoard> = runCatching {
        val response = httpClient.get("$baseUrl/api/tenant/work-queue/board?subjectId=$subjectId&orderedPcs=$orderedPcs") {
            tenantRequest(tenantSlug, tokenProvider)
        }
        val text = response.bodyAsText()
        if (!response.status.isSuccess()) error("HTTP ${response.status.value}: $text")
        val obj = JsonParser.parse(text) as? JsonValue.Obj ?: error("Invalid JSON response")

        val columns = obj.objectArray("columns").map { col ->
            val cards = col.objectArray("cards").map(WorkQueueCodec::decodeCard)
            val stationCode = WorkStationCode(col.string("stationCode") ?: "")
            val spec = WorkStationCatalog.resolve(stationCode) ?: error("Unknown station: ${stationCode.value}")
            val tickets = col.objectArray("activeTickets").map { t ->
                ReworkTicket(
                    id = ReworkTicketId(t.string("id") ?: ""),
                    cardId = WorkCardId(t.string("cardId") ?: ""),
                    tenantId = t.string("tenantId") ?: "",
                    subject = WorkSubjectRef(
                        kind = WorkSubjectKind.BULK_WORK_ORDER,
                        subjectId = subjectId,
                        orderNumber = obj.string("orderNumber") ?: "",
                        articleName = obj.string("articleName") ?: ""
                    ),
                    defectCode = DefectCode(t.string("defectCode") ?: ""),
                    defectDisplayName = t.string("defectDisplayName") ?: "",
                    liability = runCatching { DefectLiability.valueOf(t.string("liability") ?: DefectLiability.FACTORY_WORKMANSHIP.name) }.getOrDefault(DefectLiability.FACTORY_WORKMANSHIP),
                    qtyPcs = t.int("qtyPcs") ?: 0,
                    sizeLabel = t.string("sizeLabel") ?: "",
                    targetStationCode = stationCode,
                    responsibleOperatorId = t.string("responsibleOperatorId"),
                    assignedRepairOperatorId = t.string("assignedRepairOperatorId"),
                    status = runCatching { ReworkTicketStatus.valueOf(t.string("status") ?: ReworkTicketStatus.REWORK_ISSUED.name) }.getOrDefault(ReworkTicketStatus.REWORK_ISSUED),
                    qcNotes = t.string("qcNotes") ?: "",
                    issuedAt = Clock.System.now()
                )
            }
            WorkQueueColumn(
                station = spec,
                cards = cards,
                totalQueuedPcs = col.int("totalQueuedPcs") ?: 0,
                totalWipPcs = col.int("totalWipPcs") ?: 0,
                totalCompletedPcs = col.int("totalCompletedPcs") ?: 0,
                activeTickets = tickets
            )
        }

        val allCards = columns.flatMap { it.cards }
        val allTickets = columns.flatMap { it.activeTickets }
        val balance = WorkQueueBalance.calculate(orderedPcs, allCards, allTickets)

        WorkQueueBoard(
            subject = WorkSubjectRef(
                kind = runCatching { WorkSubjectKind.valueOf(obj.string("subjectKind") ?: "BULK_WORK_ORDER") }.getOrDefault(WorkSubjectKind.BULK_WORK_ORDER),
                subjectId = obj.string("subjectId") ?: subjectId,
                orderNumber = obj.string("orderNumber") ?: "",
                articleName = obj.string("articleName") ?: ""
            ),
            columns = columns,
            balance = balance
        )
    }

    override suspend fun initializeCards(
        subject: WorkSubjectRef,
        quantitiesBySize: Map<String, Int>,
        bundleCapacity: Int,
        initialStationCode: WorkStationCode
    ): Result<Int> = runCatching {
        val sizeList = quantitiesBySize.map { (size, qty) ->
            jsonObjectOf("sizeLabel" to jsonOf(size), "qtyPcs" to jsonOf(qty))
        }
        val body = jsonObjectOf(
            "subjectKind" to jsonOf(subject.kind.name),
            "subjectId" to jsonOf(subject.subjectId),
            "orderNumber" to jsonOf(subject.orderNumber),
            "articleName" to jsonOf(subject.articleName),
            "quantitiesBySize" to jsonArrayOf(sizeList),
            "bundleCapacity" to jsonOf(bundleCapacity),
            "initialStationCode" to jsonOf(initialStationCode.value)
        )
        val response = httpClient.post("$baseUrl/api/tenant/work-queue/cards/initialize") {
            contentType(ContentType.Application.Json)
            tenantRequest(tenantSlug, tokenProvider)
            setBody(body.encode())
        }
        val text = response.bodyAsText()
        if (!response.status.isSuccess()) error("HTTP ${response.status.value}: $text")
        val obj = JsonParser.parse(text) as? JsonValue.Obj ?: error("Invalid response")
        obj.int("createdCount") ?: 0
    }

    override suspend fun recordOutput(
        cardId: WorkCardId,
        operatorId: String,
        operatorName: String,
        completedQty: Int,
        isReworkDeposit: Boolean,
        notes: String
    ): Result<Pair<WorkCard, WorkDeposit>> = runCatching {
        val body = jsonObjectOf(
            "cardId" to jsonOf(cardId.value),
            "operatorId" to jsonOf(operatorId),
            "operatorName" to jsonOf(operatorName),
            "completedQty" to jsonOf(completedQty),
            "isReworkDeposit" to jsonOf(isReworkDeposit),
            "notes" to jsonOf(notes)
        )
        val response = httpClient.post("$baseUrl/api/tenant/work-queue/cards/output") {
            contentType(ContentType.Application.Json)
            tenantRequest(tenantSlug, tokenProvider)
            setBody(body.encode())
        }
        val text = response.bodyAsText()
        if (!response.status.isSuccess()) error("HTTP ${response.status.value}: $text")
        val obj = JsonParser.parse(text) as? JsonValue.Obj ?: error("Invalid response")
        val cardObj = obj.obj("card") ?: error("Missing card")
        val depositObj = obj.obj("deposit") ?: error("Missing deposit")
        Pair(WorkQueueCodec.decodeCard(cardObj), WorkQueueCodec.decodeDeposit(depositObj))
    }

    override suspend fun issueRework(
        cardId: WorkCardId,
        defectCode: DefectCode,
        qtyPcs: Int,
        responsibleOperatorId: String?,
        qcNotes: String
    ): Result<ReworkTicket> = runCatching {
        val body = jsonObjectOf(
            "cardId" to jsonOf(cardId.value),
            "defectCode" to jsonOf(defectCode.value),
            "qtyPcs" to jsonOf(qtyPcs),
            "responsibleOperatorId" to (responsibleOperatorId?.let(::jsonOf) ?: JsonValue.Null),
            "qcNotes" to jsonOf(qcNotes)
        )
        val response = httpClient.post("$baseUrl/api/tenant/work-queue/rework/issue") {
            contentType(ContentType.Application.Json)
            tenantRequest(tenantSlug, tokenProvider)
            setBody(body.encode())
        }
        val text = response.bodyAsText()
        if (!response.status.isSuccess()) error("HTTP ${response.status.value}: $text")
        val obj = JsonParser.parse(text) as? JsonValue.Obj ?: error("Invalid response")
        ReworkTicket(
            id = ReworkTicketId(obj.string("id") ?: ""),
            cardId = cardId,
            tenantId = obj.string("tenantId") ?: "",
            subject = WorkSubjectRef(
                kind = WorkSubjectKind.BULK_WORK_ORDER,
                subjectId = "",
                orderNumber = "",
                articleName = ""
            ),
            defectCode = defectCode,
            defectDisplayName = obj.string("defectDisplayName") ?: "",
            liability = DefectLiability.FACTORY_WORKMANSHIP,
            qtyPcs = qtyPcs,
            sizeLabel = obj.string("sizeLabel") ?: "",
            targetStationCode = WorkStationCode(obj.string("targetStationCode") ?: ""),
            status = ReworkTicketStatus.REWORK_ISSUED,
            qcNotes = qcNotes,
            issuedAt = Clock.System.now()
        )
    }

    override suspend fun advanceRework(
        ticketId: String,
        action: String,
        repairOperatorId: String?,
        repairNotes: String,
        scrapReason: String?
    ): Result<ReworkTicket> = runCatching {
        val body = jsonObjectOf(
            "action" to jsonOf(action),
            "repairOperatorId" to (repairOperatorId?.let(::jsonOf) ?: JsonValue.Null),
            "repairNotes" to jsonOf(repairNotes),
            "scrapReason" to (scrapReason?.let(::jsonOf) ?: JsonValue.Null)
        )
        val response = httpClient.post("$baseUrl/api/tenant/work-queue/rework/$ticketId/advance") {
            contentType(ContentType.Application.Json)
            tenantRequest(tenantSlug, tokenProvider)
            setBody(body.encode())
        }
        val text = response.bodyAsText()
        if (!response.status.isSuccess()) error("HTTP ${response.status.value}: $text")
        val obj = JsonParser.parse(text) as? JsonValue.Obj ?: error("Invalid response")
        ReworkTicket(
            id = ReworkTicketId(obj.string("id") ?: ""),
            cardId = WorkCardId(obj.string("cardId") ?: ""),
            tenantId = obj.string("tenantId") ?: "",
            subject = WorkSubjectRef(
                kind = WorkSubjectKind.BULK_WORK_ORDER,
                subjectId = "",
                orderNumber = "",
                articleName = ""
            ),
            defectCode = DefectCode(obj.string("defectCode") ?: ""),
            defectDisplayName = obj.string("defectDisplayName") ?: "",
            liability = DefectLiability.FACTORY_WORKMANSHIP,
            qtyPcs = obj.int("qtyPcs") ?: 0,
            sizeLabel = obj.string("sizeLabel") ?: "",
            targetStationCode = WorkStationCode(obj.string("targetStationCode") ?: ""),
            status = runCatching { ReworkTicketStatus.valueOf(obj.string("status") ?: ReworkTicketStatus.REWORK_ISSUED.name) }.getOrDefault(ReworkTicketStatus.REWORK_ISSUED),
            qcNotes = obj.string("qcNotes") ?: "",
            repairNotes = obj.string("repairNotes") ?: "",
            scrapReason = obj.string("scrapReason"),
            issuedAt = Clock.System.now()
        )
    }

    override suspend fun closeMergeGate(
        subjectId: String,
        mergeStationCode: WorkStationCode,
        downstreamStationCode: WorkStationCode
    ): Result<Int> = runCatching {
        val body = jsonObjectOf(
            "subjectId" to jsonOf(subjectId),
            "mergeStationCode" to jsonOf(mergeStationCode.value),
            "downstreamStationCode" to jsonOf(downstreamStationCode.value)
        )
        val response = httpClient.post("$baseUrl/api/tenant/work-queue/merge-gate/close") {
            contentType(ContentType.Application.Json)
            tenantRequest(tenantSlug, tokenProvider)
            setBody(body.encode())
        }
        val text = response.bodyAsText()
        if (!response.status.isSuccess()) error("HTTP ${response.status.value}: $text")
        val obj = JsonParser.parse(text) as? JsonValue.Obj ?: error("Invalid response")
        obj.int("createdLotCardsCount") ?: 0
    }
}
