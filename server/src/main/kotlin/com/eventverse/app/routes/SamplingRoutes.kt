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
import kotlinx.datetime.LocalDate

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
