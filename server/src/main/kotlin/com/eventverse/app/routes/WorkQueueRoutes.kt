package com.eventverse.app.routes

import com.eventverse.app.domain.tenant.TenantContext
import com.eventverse.app.domain.workqueue.DefectCode
import com.eventverse.app.domain.workqueue.ReworkTicketId
import com.eventverse.app.domain.workqueue.ReworkTicketRepository
import com.eventverse.app.domain.workqueue.WorkCardId
import com.eventverse.app.domain.workqueue.WorkCardRepository
import com.eventverse.app.domain.workqueue.WorkDepositRepository
import com.eventverse.app.domain.workqueue.WorkExecutionMode
import com.eventverse.app.domain.workqueue.WorkStationCatalog
import com.eventverse.app.domain.workqueue.WorkStationCode
import com.eventverse.app.domain.workqueue.WorkSubjectKind
import com.eventverse.app.domain.workqueue.WorkSubjectRef
import com.eventverse.app.domain.workqueue.usecases.AdvanceReworkTicketCommand
import com.eventverse.app.domain.workqueue.usecases.AdvanceReworkTicketUseCase
import com.eventverse.app.domain.workqueue.usecases.CloseBundlesAtMergeGateCommand
import com.eventverse.app.domain.workqueue.usecases.CloseBundlesAtMergeGateUseCase
import com.eventverse.app.domain.workqueue.usecases.GetWorkQueueBoardQuery
import com.eventverse.app.domain.workqueue.usecases.GetWorkQueueBoardUseCase
import com.eventverse.app.domain.workqueue.usecases.InitializeWorkCardsCommand
import com.eventverse.app.domain.workqueue.usecases.InitializeWorkCardsFromCuttingUseCase
import com.eventverse.app.domain.workqueue.usecases.IssueReworkTicketCommand
import com.eventverse.app.domain.workqueue.usecases.IssueReworkTicketUseCase
import com.eventverse.app.domain.workqueue.usecases.RecordStationOutputCommand
import com.eventverse.app.domain.workqueue.usecases.RecordStationOutputUseCase
import com.eventverse.app.domain.workqueue.usecases.ReworkAction
import com.eventverse.app.plugins.tenantContextOrNull
import com.eventverse.app.shared.json.JsonParser
import com.eventverse.app.shared.json.JsonValue
import com.eventverse.app.shared.json.jsonObjectOf
import com.eventverse.app.shared.json.jsonOf
import com.eventverse.app.shared.workqueue.WorkQueueCodec
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.datetime.Clock

fun Route.workQueueRoutes(
    cardRepository: WorkCardRepository,
    depositRepository: WorkDepositRepository,
    ticketRepository: ReworkTicketRepository
) {
    val boardUseCase = GetWorkQueueBoardUseCase(cardRepository, ticketRepository)
    val initCardsUseCase = InitializeWorkCardsFromCuttingUseCase(cardRepository)
    val recordOutputUseCase = RecordStationOutputUseCase(cardRepository, depositRepository)
    val issueTicketUseCase = IssueReworkTicketUseCase(cardRepository, ticketRepository)
    val advanceTicketUseCase = AdvanceReworkTicketUseCase(ticketRepository, cardRepository)
    val mergeGateUseCase = CloseBundlesAtMergeGateUseCase(cardRepository)

    route("/api/tenant/work-queue") {

        // GET — Papan Kanban Antrean Stasiun Kerja & Rekonsiliasi Keseimbangan
        get("/board") {
            val tenant = call.requireWorkQueueTenant() ?: return@get
            val subjectId = call.request.queryParameters["subjectId"] ?: return@get call.respondWorkQueueError(
                HttpStatusCode.BadRequest, "Missing query param subjectId"
            )
            val orderedPcs = call.request.queryParameters["orderedPcs"]?.toIntOrNull() ?: 0

            val query = GetWorkQueueBoardQuery(
                tenantId = tenant.tenantId.value,
                subjectId = subjectId,
                orderedPcs = orderedPcs
            )

            boardUseCase(query)
                .onSuccess { board ->
                    call.respondWorkQueueJson(WorkQueueCodec.encodeBoard(board).encode())
                }
                .onFailure { err ->
                    call.respondWorkQueueError(HttpStatusCode.InternalServerError, err.message ?: "Failed to get board")
                }
        }

        // POST — Inisialisasi kartu bundle dari hasil meja potong
        post("/cards/initialize") {
            val tenant = call.requireWorkQueueTenant() ?: return@post
            val body = call.parseBody() ?: return@post call.respondWorkQueueError(
                HttpStatusCode.BadRequest, "Invalid JSON body"
            )

            val now = Clock.System.now()
            val quantitiesBySize = mutableMapOf<String, Int>()
            body.objectArray("quantitiesBySize").forEach { obj ->
                val size = obj.string("sizeLabel") ?: ""
                val qty = obj.int("qtyPcs") ?: 0
                if (size.isNotBlank() && qty > 0) {
                    quantitiesBySize[size] = qty
                }
            }

            val command = InitializeWorkCardsCommand(
                tenantId = tenant.tenantId.value,
                subject = WorkSubjectRef(
                    kind = WorkSubjectKind.valueOf(body.string("subjectKind") ?: WorkSubjectKind.BULK_WORK_ORDER.name),
                    subjectId = body.string("subjectId") ?: "",
                    orderNumber = body.string("orderNumber") ?: "",
                    articleName = body.string("articleName") ?: ""
                ),
                quantitiesBySize = quantitiesBySize,
                bundleCapacity = body.int("bundleCapacity") ?: 20,
                initialStationCode = WorkStationCode(body.string("initialStationCode") ?: WorkStationCatalog.CUTTING.code.value),
                executionMode = runCatching {
                    WorkExecutionMode.valueOf(body.string("executionMode") ?: WorkExecutionMode.IN_HOUSE.name)
                }.getOrDefault(WorkExecutionMode.IN_HOUSE),
                vendorRef = body.string("vendorRef"),
                now = now
            )

            initCardsUseCase(command)
                .onSuccess { cards ->
                    val resp = jsonObjectOf(
                        "createdCount" to jsonOf(cards.size),
                        "totalPcs" to jsonOf(cards.sumOf { it.queuedPcs })
                    )
                    call.respondWorkQueueJson(resp.encode())
                }
                .onFailure { err ->
                    call.respondWorkQueueError(HttpStatusCode.BadRequest, err.message ?: "Failed to initialize cards")
                }
        }

        // POST — Catat setoran output hasil kerja operator di stasiun
        post("/cards/output") {
            val tenant = call.requireWorkQueueTenant() ?: return@post
            val body = call.parseBody() ?: return@post call.respondWorkQueueError(
                HttpStatusCode.BadRequest, "Invalid JSON body"
            )

            val now = Clock.System.now()
            val command = RecordStationOutputCommand(
                cardId = WorkCardId(body.string("cardId") ?: ""),
                operatorId = body.string("operatorId") ?: "",
                operatorName = body.string("operatorName") ?: "",
                completedQty = body.int("completedQty") ?: 0,
                isReworkDeposit = body.boolean("isReworkDeposit") ?: false,
                notes = body.string("notes") ?: "",
                verifiedPhotoKey = body.string("verifiedPhotoKey"),
                now = now
            )

            recordOutputUseCase(command)
                .onSuccess { (card, deposit) ->
                    val resp = jsonObjectOf(
                        "card" to WorkQueueCodec.encodeCard(card),
                        "deposit" to WorkQueueCodec.encodeDeposit(deposit)
                    )
                    call.respondWorkQueueJson(resp.encode())
                }
                .onFailure { err ->
                    call.respondWorkQueueError(HttpStatusCode.BadRequest, err.message ?: "Failed to record output")
                }
        }

        // POST — Terbitkan tiket perbaikan cacat (Rework Ticket) dari QC
        post("/rework/issue") {
            val tenant = call.requireWorkQueueTenant() ?: return@post
            val body = call.parseBody() ?: return@post call.respondWorkQueueError(
                HttpStatusCode.BadRequest, "Invalid JSON body"
            )

            val now = Clock.System.now()
            val command = IssueReworkTicketCommand(
                cardId = WorkCardId(body.string("cardId") ?: ""),
                defectCode = DefectCode(body.string("defectCode") ?: ""),
                qtyPcs = body.int("qtyPcs") ?: 0,
                responsibleOperatorId = body.string("responsibleOperatorId"),
                qcNotes = body.string("qcNotes") ?: "",
                now = now
            )

            issueTicketUseCase(command)
                .onSuccess { ticket ->
                    call.respondWorkQueueJson(WorkQueueCodec.encodeReworkTicket(ticket).encode())
                }
                .onFailure { err ->
                    call.respondWorkQueueError(HttpStatusCode.BadRequest, err.message ?: "Failed to issue rework ticket")
                }
        }

        // POST — Jalankan transisi status tiket perbaikan cacat
        post("/rework/{id}/advance") {
            val tenant = call.requireWorkQueueTenant() ?: return@post
            val idParam = call.parameters["id"] ?: return@post call.respondWorkQueueError(
                HttpStatusCode.BadRequest, "Missing rework ticket id"
            )
            val body = call.parseBody() ?: return@post call.respondWorkQueueError(
                HttpStatusCode.BadRequest, "Invalid JSON body"
            )

            val now = Clock.System.now()
            val actionType = body.string("action") ?: "START_REPAIR"
            val action: ReworkAction = when (actionType) {
                "START_REPAIR" -> ReworkAction.StartRepair(body.string("repairOperatorId") ?: "")
                "READY_FOR_RECHECK" -> ReworkAction.MarkReadyForRecheck(body.string("repairNotes") ?: "")
                "CLOSE_PASSED" -> ReworkAction.CloseAsPassed(body.string("qcNotes") ?: "")
                "CLOSE_SCRAP" -> ReworkAction.CloseAsScrap(body.string("scrapReason") ?: "Scrapped at QC")
                else -> return@post call.respondWorkQueueError(HttpStatusCode.BadRequest, "Unknown action: $actionType")
            }

            val command = AdvanceReworkTicketCommand(
                ticketId = ReworkTicketId(idParam),
                action = action,
                now = now
            )

            advanceTicketUseCase(command)
                .onSuccess { ticket ->
                    call.respondWorkQueueJson(WorkQueueCodec.encodeReworkTicket(ticket).encode())
                }
                .onFailure { err ->
                    call.respondWorkQueueError(HttpStatusCode.BadRequest, err.message ?: "Failed to advance rework ticket")
                }
        }

        // POST — Gerbang Peleburan Bundle ke Lot (Washing Gate)
        post("/merge-gate/close") {
            val tenant = call.requireWorkQueueTenant() ?: return@post
            val body = call.parseBody() ?: return@post call.respondWorkQueueError(
                HttpStatusCode.BadRequest, "Invalid JSON body"
            )

            val now = Clock.System.now()
            val command = CloseBundlesAtMergeGateCommand(
                tenantId = tenant.tenantId.value,
                subjectId = body.string("subjectId") ?: "",
                mergeStationCode = WorkStationCode(body.string("mergeStationCode") ?: WorkStationCatalog.WASHING.code.value),
                downstreamStationCode = WorkStationCode(body.string("downstreamStationCode") ?: WorkStationCatalog.STEAM.code.value),
                now = now
            )

            mergeGateUseCase(command)
                .onSuccess { lotCards ->
                    val resp = jsonObjectOf(
                        "createdLotCardsCount" to jsonOf(lotCards.size),
                        "totalPcs" to jsonOf(lotCards.sumOf { it.queuedPcs })
                    )
                    call.respondWorkQueueJson(resp.encode())
                }
                .onFailure { err ->
                    call.respondWorkQueueError(HttpStatusCode.BadRequest, err.message ?: "Failed to close bundles at merge gate")
                }
        }
    }
}

private suspend fun ApplicationCall.requireWorkQueueTenant(): TenantContext? {
    val tenant = tenantContextOrNull
    if (tenant == null) {
        respondText(
            text = jsonObjectOf("error" to jsonOf("No tenant context found")).encode(),
            status = HttpStatusCode.NotFound,
            contentType = ContentType.Application.Json
        )
    }
    return tenant
}

private suspend fun ApplicationCall.parseBody(): JsonValue.Obj? =
    runCatching { JsonParser.parse(receiveText()) as? JsonValue.Obj }.getOrNull()

private suspend fun ApplicationCall.respondWorkQueueJson(json: String) =
    respondText(text = json, contentType = ContentType.Application.Json)

private suspend fun ApplicationCall.respondWorkQueueError(status: HttpStatusCode, message: String) =
    respondText(
        text = jsonObjectOf("error" to jsonOf(message)).encode(),
        status = status,
        contentType = ContentType.Application.Json
    )
