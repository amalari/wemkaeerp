package com.eventverse.app.routes

import com.eventverse.app.domain.rbac.AccessLevel
import com.eventverse.app.domain.rbac.BusinessModule
import com.eventverse.app.domain.rbac.ModuleAssignmentRepository
import com.eventverse.app.domain.rbac.RoleRepository
import com.eventverse.app.domain.tenant.TenantContext
import com.eventverse.app.domain.vendor.SubcontractFlowGateway
import com.eventverse.app.domain.vendor.VendorAssignmentId
import com.eventverse.app.domain.vendor.VendorAssignmentRepository
import com.eventverse.app.domain.vendor.VendorId
import com.eventverse.app.domain.vendor.VendorRepository
import com.eventverse.app.domain.vendor.usecases.AssignVendorToProcessCommand
import com.eventverse.app.domain.vendor.usecases.AssignVendorToProcessUseCase
import com.eventverse.app.domain.vendor.usecases.CancelVendorAssignmentCommand
import com.eventverse.app.domain.vendor.usecases.CancelVendorAssignmentUseCase
import com.eventverse.app.domain.vendor.usecases.GetVendorQueueUseCase
import com.eventverse.app.domain.vendor.usecases.RegisterVendorCommand
import com.eventverse.app.domain.vendor.usecases.RegisterVendorUseCase
import com.eventverse.app.domain.vendor.usecases.SetVendorServiceRateCommand
import com.eventverse.app.domain.vendor.usecases.SetVendorServiceRateUseCase
import com.eventverse.app.domain.vendor.usecases.UpdateVendorProfileCommand
import com.eventverse.app.domain.vendor.usecases.UpdateVendorProfileUseCase
import com.eventverse.app.plugins.callerPrincipalOrNull
import com.eventverse.app.plugins.tenantContextOrNull
import com.eventverse.app.shared.common.DateTimeCodec
import com.eventverse.app.shared.json.JsonParser
import com.eventverse.app.shared.json.JsonValue
import com.eventverse.app.shared.vendor.VendorAssignmentCodec
import com.eventverse.app.shared.vendor.VendorCodec
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn

/**
 * REST Kontak Vendor & penugasan vendor (`BusinessModule.VENDOR_CONTACTS`).
 *
 * Membaca (daftar vendor, antrean) cukup `VIEW` — staf sampling perlu tahu vendornya siapa.
 * Semua penulisan butuh `MANAGE`: menambah vendor, mengubah harga, dan menunjuk vendor adalah
 * keputusan admin produksi. Gerbangnya di sini, bukan hanya tombol yang disembunyikan di UI.
 */
fun Route.vendorRoutes(
    vendorRepository: VendorRepository,
    assignmentRepository: VendorAssignmentRepository,
    flowGateway: SubcontractFlowGateway,
    roleRepository: RoleRepository,
    moduleAssignmentRepository: ModuleAssignmentRepository
) {
    val register = RegisterVendorUseCase(vendorRepository)
    val updateProfile = UpdateVendorProfileUseCase(vendorRepository)
    val setRate = SetVendorServiceRateUseCase(vendorRepository)
    val assign = AssignVendorToProcessUseCase(vendorRepository, assignmentRepository, flowGateway)
    val cancel = CancelVendorAssignmentUseCase(assignmentRepository, flowGateway)
    val queue = GetVendorQueueUseCase(assignmentRepository, flowGateway)

    suspend fun ApplicationCall.authorized(required: AccessLevel): TenantContext? {
        val tenant = tenantContextOrNull ?: run {
            respond(HttpStatusCode.NotFound, "No tenant context found")
            return null
        }
        val module = BusinessModule.VENDOR_CONTACTS
        val decision = moduleDecision(module, tenant, roleRepository, moduleAssignmentRepository)
        return tenant.takeIf { requireModuleAccess(module, decision, required) }
    }

    route("/api/tenant/vendors") {
        get {
            val tenant = call.authorized(AccessLevel.VIEW) ?: return@get
            val includeInactive = call.request.queryParameters["includeInactive"] == "true"
            call.respondVendorJson(VendorCodec.encodeVendors(vendorRepository.listContacts(tenant.tenantId, includeInactive)))
        }

        post {
            val tenant = call.authorized(AccessLevel.MANAGE) ?: return@post
            val body = call.receiveObject() ?: return@post
            register(
                RegisterVendorCommand(
                    tenantId = tenant.tenantId,
                    name = body.string("name") ?: "",
                    phone = body.string("phone") ?: "",
                    address = body.string("address") ?: "",
                    notes = body.string("notes") ?: ""
                )
            ).onSuccess { call.respondVendorJson(VendorCodec.encodeVendor(it).encode(), HttpStatusCode.Created) }
                .onFailure { call.respondVendorFailure(it) }
        }

        put("/{id}") {
            val tenant = call.authorized(AccessLevel.MANAGE) ?: return@put
            val vendorId = call.vendorIdParam() ?: return@put
            val body = call.receiveObject() ?: return@put
            updateProfile(
                UpdateVendorProfileCommand(
                    tenantId = tenant.tenantId,
                    vendorId = vendorId,
                    name = body.string("name") ?: "",
                    phone = body.string("phone") ?: "",
                    address = body.string("address") ?: "",
                    notes = body.string("notes") ?: "",
                    isActive = body.boolean("isActive")
                )
            ).onSuccess { call.respondVendorJson(VendorCodec.encodeVendor(it).encode()) }
                .onFailure { call.respondVendorFailure(it) }
        }

        post("/{id}/rates") {
            val tenant = call.authorized(AccessLevel.MANAGE) ?: return@post
            val vendorId = call.vendorIdParam() ?: return@post
            val body = call.receiveObject() ?: return@post
            runCatching { VendorCodec.decodeRate(body) }
                .mapCatching { rate -> setRate(SetVendorServiceRateCommand(tenant.tenantId, vendorId, rate)).getOrThrow() }
                .onSuccess { call.respondVendorJson(VendorCodec.encodeVendor(it).encode()) }
                .onFailure { call.respondVendorFailure(it) }
        }

        get("/{id}/assignments") {
            val tenant = call.authorized(AccessLevel.VIEW) ?: return@get
            val vendorId = call.vendorIdParam() ?: return@get
            call.respondVendorJson(VendorAssignmentCodec.encodeAssignments(assignmentRepository.findByVendor(tenant.tenantId, vendorId)))
        }
    }

    // Antrean "Menunggu Vendor": proses Vendor Luar di SPK yang masih berjalan.
    get("/api/tenant/vendor-queue") {
        val tenant = call.authorized(AccessLevel.VIEW) ?: return@get
        queue(tenant.tenantId)
            .onSuccess { call.respondVendorJson(VendorAssignmentCodec.encodeQueue(it)) }
            .onFailure { call.respondVendorFailure(it) }
    }

    route("/api/tenant/vendor-assignments") {
        post {
            val tenant = call.authorized(AccessLevel.MANAGE) ?: return@post
            val body = call.receiveObject() ?: return@post
            runCatching {
                AssignVendorToProcessCommand(
                    tenantId = tenant.tenantId,
                    subjectId = body.string("subjectId") ?: "",
                    processCode = body.string("processCode") ?: "",
                    vendorId = VendorId(body.string("vendorId") ?: ""),
                    pricePerUnitIdr = body.long("pricePerUnitIdr"),
                    unit = VendorCodec.decodeUnit(body.string("unit")),
                    unitsPerPiece = body.int("unitsPerPiece") ?: 1,
                    quantityPcs = body.int("quantityPcs"),
                    expectedReturnAt = DateTimeCodec.parseLocalDateOrNull(body.string("expectedReturnAt")),
                    notes = body.string("notes") ?: "",
                    assignedByUserId = call.callerPrincipalOrNull?.userId ?: "",
                    today = Clock.System.todayIn(TimeZone.currentSystemDefault())
                )
            }.mapCatching { assign(it).getOrThrow() }
                .onSuccess { call.respondVendorJson(VendorAssignmentCodec.encodeAssignment(it).encode(), HttpStatusCode.Created) }
                .onFailure { call.respondVendorFailure(it) }
        }

        post("/{id}/cancel") {
            val tenant = call.authorized(AccessLevel.MANAGE) ?: return@post
            val id = call.parameters["id"]?.let { runCatching { VendorAssignmentId(it) }.getOrNull() }
                ?: return@post call.respond(HttpStatusCode.BadRequest, "Missing assignment id")
            cancel(CancelVendorAssignmentCommand(tenant.tenantId, id))
                .onSuccess { call.respondVendorJson(VendorAssignmentCodec.encodeAssignment(it).encode()) }
                .onFailure { call.respondVendorFailure(it) }
        }
    }
}

private suspend fun ApplicationCall.receiveObject(): JsonValue.Obj? {
    val body = runCatching { JsonParser.parse(receiveText()) }.getOrNull() as? JsonValue.Obj
    if (body == null) respond(HttpStatusCode.BadRequest, "Invalid JSON body")
    return body
}

private suspend fun ApplicationCall.vendorIdParam(): VendorId? {
    val id = parameters["id"]?.let { runCatching { VendorId(it) }.getOrNull() }
    if (id == null) respond(HttpStatusCode.BadRequest, "Missing vendor id")
    return id
}

private suspend fun ApplicationCall.respondVendorJson(json: String, status: HttpStatusCode = HttpStatusCode.OK) {
    respondText(text = json, contentType = ContentType.Application.Json, status = status)
}

/** Pelanggaran aturan domain (`require`/`check`/`error`) adalah 422, bukan 500. */
private suspend fun ApplicationCall.respondVendorFailure(error: Throwable) {
    val status = when (error) {
        is IllegalArgumentException, is IllegalStateException -> HttpStatusCode.UnprocessableEntity
        else -> HttpStatusCode.InternalServerError
    }
    respond(status, error.message ?: "Unknown error")
}
