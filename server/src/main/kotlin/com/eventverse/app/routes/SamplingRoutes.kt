package com.eventverse.app.routes

import com.eventverse.app.domain.sampling.*
import com.eventverse.app.domain.sampling.usecases.*
import com.eventverse.app.domain.tenant.TenantContext
import com.eventverse.app.plugins.tenantContextOrNull
import com.eventverse.app.shared.json.*
import com.eventverse.app.shared.sampling.SamplingOrderCodec
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

fun Route.samplingRoutes(
    repository: SamplingOrderRepository
) {
    val listOrdersUseCase = GetSamplingOrderListUseCase(repository)
    val getDetailUseCase = GetSamplingOrderDetailUseCase(repository)
    val createOrderUseCase = CreateSamplingOrderUseCase(repository)
    val updateTechSpecUseCase = UpdateSamplingTechnicalSpecUseCase(repository)
    val toggleMilestoneUseCase = ToggleSamplingMilestoneUseCase(repository)
    val approveOrderUseCase = ApproveSamplingOrderUseCase(repository)

    route("/api/tenant/sampling/orders") {

        // GET /api/tenant/sampling/orders (List)
        get {
            val tenant = call.requireTenant() ?: return@get
            val statusParam = call.request.queryParameters["status"]?.let {
                runCatching { SamplingStatus.valueOf(it) }.getOrNull()
            }

            listOrdersUseCase(tenant.tenantId, statusParam)
                .onSuccess { orders ->
                    val jsonArray = jsonArrayOf(orders.map { SamplingOrderCodec.encode(it) })
                    call.respondJson(jsonArray.encode())
                }
                .onFailure { call.respondFailure(HttpStatusCode.InternalServerError, it) }
        }

        // POST /api/tenant/sampling/orders (Create Draft / Standalone / from CRM)
        post {
            val tenant = call.requireTenant() ?: return@post
            val body = call.receiveText()
            val json = JsonParser.parse(body) as? JsonValue.Obj
                ?: return@post call.respond(HttpStatusCode.BadRequest, "Invalid JSON body")

            val clientName = json.string("clientName") ?: ""
            val styleName = json.string("styleName") ?: ""
            val sizeMode = json.string("sizeMode")?.let {
                runCatching { SizeMode.valueOf(it) }.getOrNull()
            } ?: SizeMode.ALL_SIZE

            val deadlineProgram = json.string("deadlineProgram")?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            val deadlineFinishing = json.string("deadlineFinishing")?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            val deadlineDelivery = json.string("deadlineDelivery")?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            val leadId = json.string("leadId")
            val notes = json.string("notes") ?: ""
            val useFactoryPreset = json.boolean("useFactoryAllSizePreset") ?: true

            val command = CreateSamplingOrderCommand(
                tenantId = tenant.tenantId,
                clientName = clientName,
                styleName = styleName,
                sizeMode = sizeMode,
                deadlineProgram = deadlineProgram,
                deadlineFinishing = deadlineFinishing,
                deadlineDelivery = deadlineDelivery,
                leadId = leadId,
                notes = notes,
                useFactoryAllSizePreset = useFactoryPreset
            )

            createOrderUseCase(command)
                .onSuccess { order ->
                    call.respondJson(SamplingOrderCodec.encode(order).encode())
                }
                .onFailure { call.respondFailure(HttpStatusCode.BadRequest, it) }
        }

        // GET /api/tenant/sampling/orders/{id} (Detail)
        get("/{id}") {
            val idParam = call.parameters["id"] ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing ID")
            getDetailUseCase(SamplingOrderId(idParam))
                .onSuccess { order ->
                    call.respondJson(SamplingOrderCodec.encode(order).encode())
                }
                .onFailure { call.respondFailure(HttpStatusCode.NotFound, it) }
        }

        // PUT /api/tenant/sampling/orders/{id}/technical-spec (Update Tech Spec)
        put("/{id}/technical-spec") {
            val idParam = call.parameters["id"] ?: return@put call.respond(HttpStatusCode.BadRequest, "Missing ID")
            val body = call.receiveText()
            val json = JsonParser.parse(body) as? JsonValue.Obj
                ?: return@put call.respond(HttpStatusCode.BadRequest, "Invalid JSON body")

            val decodedOrder = SamplingOrderCodec.decode(json)
            val command = UpdateSamplingTechnicalSpecCommand(
                orderId = SamplingOrderId(idParam),
                knitSpec = decodedOrder.knitSpec,
                finishedSizes = decodedOrder.finishedSizeCharts,
                rawSizes = decodedOrder.rawKnitSizeCharts,
                machineProgram = decodedOrder.machineProgram,
                yieldAndTiming = decodedOrder.yieldAndTiming
            )

            updateTechSpecUseCase(command)
                .onSuccess { order ->
                    call.respondJson(SamplingOrderCodec.encode(order).encode())
                }
                .onFailure { call.respondFailure(HttpStatusCode.BadRequest, it) }
        }

        // PATCH /api/tenant/sampling/orders/{id}/milestones/{step} (Toggle Milestone)
        patch("/{id}/milestones/{step}") {
            val idParam = call.parameters["id"] ?: return@patch call.respond(HttpStatusCode.BadRequest, "Missing ID")
            val stepParam = call.parameters["step"] ?: return@patch call.respond(HttpStatusCode.BadRequest, "Missing step")
            val step = runCatching { MilestoneStep.valueOf(stepParam) }.getOrNull()
                ?: return@patch call.respond(HttpStatusCode.BadRequest, "Invalid milestone step: $stepParam")

            val body = call.receiveText()
            val json = JsonParser.parse(body) as? JsonValue.Obj
            val isCompleted = json?.boolean("isCompleted") ?: true
            val completedAt = json?.string("completedAt")?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            val notes = json?.string("notes")

            val command = ToggleSamplingMilestoneCommand(
                orderId = SamplingOrderId(idParam),
                step = step,
                isCompleted = isCompleted,
                completedAt = completedAt,
                notes = notes
            )

            toggleMilestoneUseCase(command)
                .onSuccess { order ->
                    call.respondJson(SamplingOrderCodec.encode(order).encode())
                }
                .onFailure { call.respondFailure(HttpStatusCode.BadRequest, it) }
        }

        // POST /api/tenant/sampling/orders/{id}/approve (ACC Produksi / Revisi)
        post("/{id}/approve") {
            val idParam = call.parameters["id"] ?: return@post call.respond(HttpStatusCode.BadRequest, "Missing ID")
            val body = call.receiveText()
            val json = JsonParser.parse(body) as? JsonValue.Obj
            val isApproved = json?.boolean("isApproved") ?: true
            val accNotes = json?.string("accNotes") ?: ""

            val command = ApproveSamplingOrderCommand(
                orderId = SamplingOrderId(idParam),
                isApproved = isApproved,
                notes = accNotes
            )

            approveOrderUseCase(command)
                .onSuccess { order ->
                    call.respondJson(SamplingOrderCodec.encode(order).encode())
                }
                .onFailure { call.respondFailure(HttpStatusCode.BadRequest, it) }
        }

        // PUT /api/tenant/sampling/orders/{id} (Full update)
        put("/{id}") {
            val idParam = call.parameters["id"] ?: return@put call.respond(HttpStatusCode.BadRequest, "Missing ID")
            val body = call.receiveText()
            val json = JsonParser.parse(body) as? JsonValue.Obj
                ?: return@put call.respond(HttpStatusCode.BadRequest, "Invalid JSON body")

            val decodedOrder = SamplingOrderCodec.decode(json)
            val updated = repository.save(decodedOrder.copy(id = SamplingOrderId(idParam), updatedAt = Clock.System.now()))
            call.respondJson(SamplingOrderCodec.encode(updated).encode())
        }

        // POST /api/tenant/sampling/orders/{id}/stage (Advance / Change stage)
        post("/{id}/stage") {
            val idParam = call.parameters["id"] ?: return@post call.respond(HttpStatusCode.BadRequest, "Missing ID")
            val body = call.receiveText()
            val json = JsonParser.parse(body) as? JsonValue.Obj
            val stageName = json?.string("targetStage") ?: return@post call.respond(HttpStatusCode.BadRequest, "Missing targetStage")
            val targetStage = runCatching { SamplingPipelineStage.valueOf(stageName) }.getOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest, "Invalid stage: $stageName")

            val order = repository.findById(SamplingOrderId(idParam))
                ?: return@post call.respond(HttpStatusCode.NotFound, "Sampling order not found")
            val updated = repository.save(order.advancePipelineStage(targetStage, Clock.System.now()))
            call.respondJson(SamplingOrderCodec.encode(updated).encode())
        }

        // POST /api/tenant/sampling/orders/{id}/finishing/deposits (Finishing Setoran Pcs & Kg)
        post("/{id}/finishing/deposits") {
            val idParam = call.parameters["id"] ?: return@post call.respond(HttpStatusCode.BadRequest, "Missing ID")
            val body = call.receiveText()
            val json = JsonParser.parse(body) as? JsonValue.Obj
                ?: return@post call.respond(HttpStatusCode.BadRequest, "Invalid JSON body")

            val order = repository.findById(SamplingOrderId(idParam))
                ?: return@post call.respond(HttpStatusCode.NotFound, "Sampling order not found")

            val deposit = FinishingDeposit(
                id = "dep_${order.id.value}_${Clock.System.now().toEpochMilliseconds()}",
                samplingOrderId = order.id.value,
                depositDate = json.string("depositDate")?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                    ?: Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date,
                qtyPcs = json.int("qtyPcs") ?: 1,
                weightKg = json.double("weightKg") ?: 0.0,
                scalePhotoKey = json.string("scalePhotoKey"),
                garmentPhotoKey = json.string("garmentPhotoKey"),
                operatorName = json.string("operatorName") ?: "",
                notes = json.string("notes") ?: "",
                createdAt = Clock.System.now()
            )

            val updated = repository.save(order.addFinishingDeposit(deposit, Clock.System.now()))
            call.respondJson(SamplingOrderCodec.encode(updated).encode())
        }

        // POST /api/tenant/sampling/orders/{id}/finishing/vendor (Assign to Makloon Vendor)
        post("/{id}/finishing/vendor") {
            val idParam = call.parameters["id"] ?: return@post call.respond(HttpStatusCode.BadRequest, "Missing ID")
            val body = call.receiveText()
            val json = JsonParser.parse(body) as? JsonValue.Obj
                ?: return@post call.respond(HttpStatusCode.BadRequest, "Invalid JSON body")

            val order = repository.findById(SamplingOrderId(idParam))
                ?: return@post call.respond(HttpStatusCode.NotFound, "Sampling order not found")

            val info = MakloonVendorInfo(
                vendorName = json.string("vendorName") ?: "",
                vendorPhone = json.string("vendorPhone") ?: "",
                sentAt = json.string("sentAt")?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
                expectedReturnAt = json.string("expectedReturnAt")?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
                costPerPcsIdr = json.long("costPerPcsIdr") ?: 0L,
                notes = json.string("notes") ?: ""
            )

            val updated = repository.save(order.assignMakloonVendor(info, Clock.System.now()))
            call.respondJson(SamplingOrderCodec.encode(updated).encode())
        }

        // POST /api/tenant/sampling/orders/{id}/finishing/vendor-receive (Receive from Makloon Vendor)
        post("/{id}/finishing/vendor-receive") {
            val idParam = call.parameters["id"] ?: return@post call.respond(HttpStatusCode.BadRequest, "Missing ID")
            val body = call.receiveText()
            val json = JsonParser.parse(body) as? JsonValue.Obj
            val returnedAt = json?.string("returnedAt")?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                ?: Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date

            val order = repository.findById(SamplingOrderId(idParam))
                ?: return@post call.respond(HttpStatusCode.NotFound, "Sampling order not found")

            val updated = repository.save(order.recordVendorReturn(returnedAt, Clock.System.now()))
            call.respondJson(SamplingOrderCodec.encode(updated).encode())
        }

        // POST /api/tenant/sampling/orders/{id}/qc/inspect (Submit QC Report)
        post("/{id}/qc/inspect") {
            val idParam = call.parameters["id"] ?: return@post call.respond(HttpStatusCode.BadRequest, "Missing ID")
            val body = call.receiveText()
            val json = JsonParser.parse(body) as? JsonValue.Obj
                ?: return@post call.respond(HttpStatusCode.BadRequest, "Invalid JSON body")

            val order = repository.findById(SamplingOrderId(idParam))
                ?: return@post call.respond(HttpStatusCode.NotFound, "Sampling order not found")

            val pomList = json.objectArray("pomMeasurements").map {
                QcPomMeasurement(
                    pomName = it.string("pomName") ?: "",
                    targetCm = it.double("targetCm") ?: 0.0,
                    actualCm = it.double("actualCm") ?: 0.0,
                    toleranceCm = it.double("toleranceCm") ?: 1.0
                )
            }
            val defects = json.stringArray("defectsFound")
            val qcResult = json.string("qcResult")?.let { runCatching { QcInspectionResult.valueOf(it) }.getOrNull() } ?: QcInspectionResult.PASSED

            val report = QcInspectionReport(
                id = "qc_${order.id.value}_${Clock.System.now().toEpochMilliseconds()}",
                samplingOrderId = order.id.value,
                inspectorName = json.string("inspectorName") ?: "",
                inspectedAt = Clock.System.now(),
                pomMeasurements = pomList,
                defectsFound = defects,
                qcResult = qcResult,
                qcNotes = json.string("qcNotes") ?: "",
                verifiedPhotoFrontKey = json.string("verifiedPhotoFrontKey"),
                verifiedPhotoBackKey = json.string("verifiedPhotoBackKey")
            )

            val updated = repository.save(order.completeQcInspection(report, Clock.System.now()))
            call.respondJson(SamplingOrderCodec.encode(updated).encode())
        }

        // POST /api/tenant/sampling/orders/{id}/revision (Request Revision)
        post("/{id}/revision") {
            val idParam = call.parameters["id"] ?: return@post call.respond(HttpStatusCode.BadRequest, "Missing ID")
            val body = call.receiveText()
            val json = JsonParser.parse(body) as? JsonValue.Obj
            val notes = json?.string("notes") ?: ""

            val order = repository.findById(SamplingOrderId(idParam))
                ?: return@post call.respond(HttpStatusCode.NotFound, "Sampling order not found")

            val updated = repository.save(order.requestRevision(notes, Clock.System.now()))
            call.respondJson(SamplingOrderCodec.encode(updated).encode())
        }
    }
}

private suspend fun ApplicationCall.requireTenant(): TenantContext? {
    val tenant = tenantContextOrNull
    if (tenant == null) {
        respond(HttpStatusCode.NotFound, "No tenant context found")
    }
    return tenant
}

private suspend fun ApplicationCall.respondJson(json: String) {
    respondText(text = json, contentType = ContentType.Application.Json)
}

private suspend fun ApplicationCall.respondFailure(status: HttpStatusCode, error: Throwable) {
    respond(status, error.message ?: "Unknown error")
}
