package com.eventverse.app.routes

import com.eventverse.app.domain.tenant.TenantContext
import com.eventverse.app.domain.workqueue.WashingBatchId
import com.eventverse.app.domain.workqueue.WashingBatchRepository
import com.eventverse.app.domain.workqueue.WorkCardId
import com.eventverse.app.domain.workqueue.WorkCardRepository
import com.eventverse.app.domain.workqueue.WorkStationCatalog
import com.eventverse.app.domain.workqueue.WorkTrackingUnit
import com.eventverse.app.domain.workqueue.usecases.BundlePhotoItemInput
import com.eventverse.app.domain.workqueue.usecases.CompleteWashingSortCommand
import com.eventverse.app.domain.workqueue.usecases.CompleteWashingSortToLotsUseCase
import com.eventverse.app.domain.workqueue.usecases.CreateWashingBatchCommand
import com.eventverse.app.domain.workqueue.usecases.CreateWashingBatchUseCase
import com.eventverse.app.plugins.tenantContextOrNull
import com.eventverse.app.shared.json.JsonParser
import com.eventverse.app.shared.json.JsonValue
import com.eventverse.app.shared.json.jsonArrayOf
import com.eventverse.app.shared.json.jsonObjectOf
import com.eventverse.app.shared.json.jsonOf
import com.eventverse.app.shared.workqueue.WashingBatchCodec
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

fun Route.washingBatchRoutes(
    cardRepository: WorkCardRepository,
    washingBatchRepository: WashingBatchRepository
) {
    val createBatchUseCase = CreateWashingBatchUseCase(cardRepository, washingBatchRepository)
    val completeSortUseCase = CompleteWashingSortToLotsUseCase(cardRepository, washingBatchRepository)

    route("/api/tenant/work-queue/washing") {
        // GET pending bundles at washing station
        get("/pending-bundles") {
            val tenant = call.requireWashingTenant() ?: return@get
            val cards = cardRepository.findByStation(tenant.tenantId.value, WorkStationCatalog.WASHING.code)
                .filter { it.trackingUnit == WorkTrackingUnit.BUNDLE && !it.isFinished }

            val resp = jsonObjectOf(
                "cards" to jsonArrayOf(cards.map(WorkQueueCodec::encodeCard))
            )
            call.respondWashingJson(resp.encode())
        }

        // GET batches list
        get("/batches") {
            val tenant = call.requireWashingTenant() ?: return@get
            val batches = washingBatchRepository.findByTenant(tenant.tenantId.value)
            val resp = jsonObjectOf(
                "batches" to jsonArrayOf(batches.map(WashingBatchCodec::encodeBatch))
            )
            call.respondWashingJson(resp.encode())
        }

        // POST create batch with mandatory bundle photos
        post("/batches") {
            val tenant = call.requireWashingTenant() ?: return@post
            val body = call.parseWashingBody() ?: return@post call.respondWashingError(
                HttpStatusCode.BadRequest, "Invalid JSON body"
            )

            val rawInputs = body.objectArray("bundles")
            val bundleInputs = rawInputs.map { obj ->
                BundlePhotoItemInput(
                    cardId = WorkCardId(obj.string("cardId") ?: ""),
                    bundlePhotoKey = obj.string("bundlePhotoKey") ?: ""
                )
            }

            val command = CreateWashingBatchCommand(
                tenantId = tenant.tenantId.value,
                batchCode = body.string("batchCode") ?: "WB-${Clock.System.now().toEpochMilliseconds()}",
                machineDrumNo = body.string("machineDrumNo") ?: "",
                washRecipe = body.string("washRecipe") ?: "",
                operatorName = body.string("operatorName") ?: "",
                bundleInputs = bundleInputs,
                notes = body.string("notes") ?: "",
                now = Clock.System.now()
            )

            createBatchUseCase(command)
                .onSuccess { batch ->
                    call.respondWashingJson(WashingBatchCodec.encodeBatch(batch).encode())
                }
                .onFailure { err ->
                    call.respondWashingError(HttpStatusCode.BadRequest, err.message ?: "Failed to create washing batch")
                }
        }

        // POST complete sort and generate downstream steam lot cards per PO & size
        post("/batches/{id}/complete-sort") {
            val tenant = call.requireWashingTenant() ?: return@post
            val batchIdStr = call.parameters["id"] ?: return@post call.respondWashingError(
                HttpStatusCode.BadRequest, "Missing batch id"
            )
            val body = call.parseWashingBody() ?: return@post call.respondWashingError(
                HttpStatusCode.BadRequest, "Invalid JSON body"
            )

            val rawOutputs = body.objectArray("sortOutputs")
            val sortOutputs = rawOutputs.map(WashingBatchCodec::decodeSortOutput)

            val command = CompleteWashingSortCommand(
                tenantId = tenant.tenantId.value,
                batchId = WashingBatchId(batchIdStr),
                sortOutputs = sortOutputs,
                now = Clock.System.now()
            )

            completeSortUseCase(command)
                .onSuccess { createdLotCards ->
                    val resp = jsonObjectOf(
                        "createdLotCardsCount" to jsonOf(createdLotCards.size),
                        "totalPcs" to jsonOf(createdLotCards.sumOf { it.queuedPcs }),
                        "cards" to jsonArrayOf(createdLotCards.map(WorkQueueCodec::encodeCard))
                    )
                    call.respondWashingJson(resp.encode())
                }
                .onFailure { err ->
                    call.respondWashingError(HttpStatusCode.BadRequest, err.message ?: "Failed to complete sorting")
                }
        }
    }
}

private suspend fun ApplicationCall.requireWashingTenant(): TenantContext? {
    val tenant = tenantContextOrNull
    if (tenant == null) {
        respondText("Tenant context missing", status = HttpStatusCode.Unauthorized)
        return null
    }
    return tenant
}

private suspend fun ApplicationCall.parseWashingBody(): JsonValue.Obj? {
    val text = receiveText()
    return JsonParser.parse(text) as? JsonValue.Obj
}

private suspend fun ApplicationCall.respondWashingJson(json: String) {
    respondText(json, contentType = ContentType.Application.Json, status = HttpStatusCode.OK)
}

private suspend fun ApplicationCall.respondWashingError(status: HttpStatusCode, message: String) {
    val err = jsonObjectOf("error" to jsonOf(message))
    respondText(err.encode(), contentType = ContentType.Application.Json, status = status)
}
