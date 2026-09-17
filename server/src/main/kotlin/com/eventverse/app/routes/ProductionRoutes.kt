package com.eventverse.app.routes

import com.eventverse.app.domain.deal.DealId
import com.eventverse.app.domain.deal.DealRepository
import com.eventverse.app.domain.pipeline.StockOwnershipSemantics
import com.eventverse.app.domain.production.*
import com.eventverse.app.domain.production.usecases.*
import com.eventverse.app.domain.sampling.SamplingOrderRepository
import com.eventverse.app.domain.tenant.TenantContext
import com.eventverse.app.plugins.tenantContextOrNull
import com.eventverse.app.shared.json.*
import com.eventverse.app.shared.production.BulkWorkOrderCodec
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.datetime.LocalDate

/**
 * Endpoint modul `PRODUCTION_MRP` — Jadwal Mesin & SPK Massal.
 *
 * `PRODUCTION_MRP` ber-`ScopeCapability.GLOBAL_ONLY`, jadi tidak ada penyaringan
 * "data milik saya" di sini: seluruh SPK massal adalah data kolektif pabrik.
 */
fun Route.productionRoutes(
    workOrderRepository: BulkWorkOrderRepository,
    dealRepository: DealRepository,
    samplingOrderRepository: SamplingOrderRepository
) {
    val listUseCase = GetBulkWorkOrderListUseCase(workOrderRepository)
    val detailUseCase = GetBulkWorkOrderDetailUseCase(workOrderRepository)
    val launchUseCase = LaunchBulkWorkOrderFromDealUseCase(
        workOrderRepository = workOrderRepository,
        dealRepository = dealRepository,
        samplingOrderRepository = samplingOrderRepository
    )
    val allocateUseCase = AllocateProductionLineUseCase(workOrderRepository)
    val removeLineUseCase = RemoveProductionLineUseCase(workOrderRepository)
    val progressUseCase = RecordProductionProgressUseCase(workOrderRepository)
    val telemetryUseCase = GetProductionTelemetryUseCase(workOrderRepository)

    route("/api/tenant/production/work-orders") {

        // GET — daftar SPK massal
        get {
            val tenant = call.requireProductionTenant() ?: return@get
            val status = call.request.queryParameters["status"]
                ?.let { runCatching { BulkProductionStatus.valueOf(it) }.getOrNull() }

            listUseCase(tenant.tenantId, status)
                .onSuccess { orders ->
                    call.respondProductionJson(jsonArrayOf(orders.map { BulkWorkOrderCodec.encode(it) }).encode())
                }
                .onFailure { call.respondProductionFailure(HttpStatusCode.InternalServerError, it) }
        }

        // GET — telemetri node untuk kanvas Alur Pabrik (Kontrak 6).
        // Didaftarkan sebelum "/{id}" supaya "telemetry" tidak tertangkap sebagai ID SPK.
        get("/telemetry") {
            val tenant = call.requireProductionTenant() ?: return@get
            telemetryUseCase(tenant.tenantId)
                .onSuccess { telemetry ->
                    call.respondProductionJson(
                        jsonObjectOf(
                            "wipPieces" to jsonOf(telemetry.wipPieces),
                            "cycleTimeHours" to jsonOf(telemetry.cycleTimeHours),
                            "healthStatus" to jsonOf(telemetry.healthStatus.name),
                            "activeWorkOrders" to jsonOf(telemetry.activeWorkOrders),
                            "totalOrderedPcs" to jsonOf(telemetry.totalOrderedPcs),
                            "completedPcs" to jsonOf(telemetry.completedPcs)
                        ).encode()
                    )
                }
                .onFailure { call.respondProductionFailure(HttpStatusCode.InternalServerError, it) }
        }

        // POST /launch-from-deal — tombol [ Luncurkan SPK Massal ] di Tab 2 Deal
        post("/launch-from-deal") {
            val tenant = call.requireProductionTenant() ?: return@post
            val json = JsonParser.parse(call.receiveText()) as? JsonValue.Obj
                ?: return@post call.respond(HttpStatusCode.BadRequest, "Invalid JSON body")

            val dealIdRaw = json.string("dealId")?.takeIf { it.isNotBlank() }
                ?: return@post call.respond(HttpStatusCode.BadRequest, "dealId wajib diisi")

            val command = LaunchBulkWorkOrderCommand(
                tenantId = tenant.tenantId,
                dealId = DealId(dealIdRaw),
                stockOwnership = json.string("stockOwnership")
                    ?.let { runCatching { StockOwnershipSemantics.valueOf(it) }.getOrNull() }
                    ?: StockOwnershipSemantics.OWNED_RAW_MATERIAL,
                notes = json.string("notes") ?: ""
            )

            launchUseCase(command)
                .onSuccess { call.respondProductionJson(BulkWorkOrderCodec.encode(it).encode()) }
                .onFailure { call.respondProductionFailure(HttpStatusCode.BadRequest, it) }
        }

        get("/{id}") {
            val id = call.parameters["id"] ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing ID")
            detailUseCase(BulkWorkOrderId(id))
                .onSuccess { call.respondProductionJson(BulkWorkOrderCodec.encode(it).encode()) }
                .onFailure { call.respondProductionFailure(HttpStatusCode.NotFound, it) }
        }

        // POST /{id}/lines — alokasi lini mesin
        post("/{id}/lines") {
            val id = call.parameters["id"] ?: return@post call.respond(HttpStatusCode.BadRequest, "Missing ID")
            val json = JsonParser.parse(call.receiveText()) as? JsonValue.Obj
                ?: return@post call.respond(HttpStatusCode.BadRequest, "Invalid JSON body")

            val allocation = runCatching {
                MachineLineAllocation(
                    lineName = ProductionLineName(json.string("lineName") ?: ""),
                    machineCount = json.int("machineCount") ?: 0,
                    assignedPcs = json.int("assignedPcs") ?: 0,
                    startDate = json.string("startDate")?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
                    targetFinishDate = json.string("targetFinishDate")?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
                    operatorCount = json.int("operatorCount") ?: 0,
                    notes = json.string("notes") ?: ""
                )
            }.getOrElse { return@post call.respondProductionFailure(HttpStatusCode.BadRequest, it) }

            allocateUseCase(AllocateProductionLineCommand(BulkWorkOrderId(id), allocation))
                .onSuccess { call.respondProductionJson(BulkWorkOrderCodec.encode(it).encode()) }
                .onFailure { call.respondProductionFailure(HttpStatusCode.BadRequest, it) }
        }

        delete("/{id}/lines/{lineName}") {
            val id = call.parameters["id"] ?: return@delete call.respond(HttpStatusCode.BadRequest, "Missing ID")
            val lineName = call.parameters["lineName"]
                ?: return@delete call.respond(HttpStatusCode.BadRequest, "Missing lineName")

            removeLineUseCase(RemoveProductionLineCommand(BulkWorkOrderId(id), ProductionLineName(lineName)))
                .onSuccess { call.respondProductionJson(BulkWorkOrderCodec.encode(it).encode()) }
                .onFailure { call.respondProductionFailure(HttpStatusCode.BadRequest, it) }
        }

        // POST /{id}/progress — setoran hasil lantai produksi (kumulatif per tahap)
        post("/{id}/progress") {
            val id = call.parameters["id"] ?: return@post call.respond(HttpStatusCode.BadRequest, "Missing ID")
            val json = JsonParser.parse(call.receiveText()) as? JsonValue.Obj
                ?: return@post call.respond(HttpStatusCode.BadRequest, "Invalid JSON body")

            val stage = json.string("stage")?.let { runCatching { ProductionStage.valueOf(it) }.getOrNull() }
                ?: return@post call.respond(HttpStatusCode.BadRequest, "Tahap produksi tidak dikenal")

            progressUseCase(
                RecordProductionProgressCommand(
                    workOrderId = BulkWorkOrderId(id),
                    stage = stage,
                    completedPcs = json.int("completedPcs") ?: 0,
                    reworkPcs = json.int("reworkPcs") ?: 0,
                    rejectPcs = json.int("rejectPcs") ?: 0
                )
            )
                .onSuccess { call.respondProductionJson(BulkWorkOrderCodec.encode(it).encode()) }
                .onFailure { call.respondProductionFailure(HttpStatusCode.BadRequest, it) }
        }
    }
}

private suspend fun ApplicationCall.requireProductionTenant(): TenantContext? {
    val tenant = tenantContextOrNull
    if (tenant == null) {
        respond(HttpStatusCode.NotFound, "No tenant context found")
    }
    return tenant
}

private suspend fun ApplicationCall.respondProductionJson(json: String) {
    respondText(text = json, contentType = ContentType.Application.Json)
}

private suspend fun ApplicationCall.respondProductionFailure(status: HttpStatusCode, error: Throwable) {
    respond(status, error.message ?: "Unknown error")
}
