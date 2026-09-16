package com.eventverse.app.routes

import com.eventverse.app.domain.crm.ContactRepository
import com.eventverse.app.domain.deal.DealId
import com.eventverse.app.domain.deal.DealRepository
import com.eventverse.app.domain.deal.PoOrigin
import com.eventverse.app.domain.deal.PurchaseOrderId
import com.eventverse.app.domain.deal.storage.PoFileStorage
import com.eventverse.app.domain.deal.usecases.AttachPurchaseOrderUseCase
import com.eventverse.app.domain.deal.usecases.GetDealDetailUseCase
import com.eventverse.app.domain.deal.usecases.ListDealsUseCase
import com.eventverse.app.domain.deal.usecases.UpdateDealStageUseCase
import com.eventverse.app.domain.orgchart.EmployeeRepository
import com.eventverse.app.domain.rbac.AccessLevel
import com.eventverse.app.domain.rbac.BusinessModule
import com.eventverse.app.domain.rbac.ModuleAssignmentRepository
import com.eventverse.app.domain.rbac.RoleRepository
import com.eventverse.app.plugins.callerPrincipalOrNull
import com.eventverse.app.plugins.tenantContextOrNull
import com.eventverse.app.shared.deal.DealCodec
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * REST surface for Deals (`BusinessModule.CRM_SALES` — deals are the transaction side of the
 * same module, so authority reuses [crmDecision] instead of introducing a new BusinessModule).
 *
 * Enforcement points per route, matching [CrmRoutes]: tenant (RLS) -> level
 * (`requireCrmAccess`) -> read scope (pushed into `findActive` as SQL) -> write scope
 * (`requireReachableOwner` on the deal's owner) -> per-row lookup always tenant-scoped.
 *
 * Upload design note: the upload endpoint takes metadata as query parameters and the file
 * bytes as the raw request body (single PUT), not multipart — one request, no extra plugin,
 * and the client needs nothing beyond `setBody(bytes)`.
 */
fun Route.dealRoutes(
    dealRepository: DealRepository,
    contactRepository: ContactRepository,
    employeeRepository: EmployeeRepository,
    roleRepository: RoleRepository,
    moduleAssignmentRepository: ModuleAssignmentRepository,
    poFileStorage: PoFileStorage? = null,
) {
    val listDealsUseCase = ListDealsUseCase(dealRepository)
    val getDealDetailUseCase = GetDealDetailUseCase(dealRepository, contactRepository)
    val updateDealStageUseCase = UpdateDealStageUseCase(dealRepository)
    val attachPurchaseOrderUseCase = AttachPurchaseOrderUseCase(dealRepository, poFileStorage)

    route("/api/tenant/deals") {

        get {
            val tenant = call.requireTenant() ?: return@get
            val decision = call.crmDecision(tenant, roleRepository, moduleAssignmentRepository)
            if (!call.requireCrmAccess(decision, AccessLevel.VIEW)) return@get

            val scope = decision.config.sanitizeFor(BusinessModule.CRM_SALES).scope
            val principal = call.callerPrincipalOrNull
            val viewerEmployeeId = principal?.email
                ?.let { email -> employeeRepository.findByEmail(tenant.tenantId, email) }
                ?.id
            val employees = employeeRepository.findAllByTenant(tenant.tenantId)

            listDealsUseCase(tenant.tenantId, scope, employees, viewerEmployeeId, principal?.departmentId)
                .onSuccess { deals -> call.respondJson(DealCodec.encodeDeals(deals)) }
                .onFailure { call.respondFailure(HttpStatusCode.InternalServerError, it) }
        }

        // ROUTES_DETAIL
        route("/{id}") {

            get {
                val tenant = call.requireTenant() ?: return@get
                val decision = call.crmDecision(tenant, roleRepository, moduleAssignmentRepository)
                if (!call.requireCrmAccess(decision, AccessLevel.VIEW)) return@get

                val deal = dealRepository.findById(tenant.tenantId, DealId(call.parameters["id"] ?: ""))
                if (deal == null) {
                    call.respond(HttpStatusCode.NotFound, "Deal not found")
                    return@get
                }
                val reach = call.crmOwnerReach(tenant, decision, employeeRepository)
                if (!call.requireReachableOwner(reach, deal.ownerEmployeeId)) return@get

                getDealDetailUseCase(tenant.tenantId, deal.id)
                    .onSuccess { detail ->
                        val payload = com.eventverse.app.shared.json.jsonObjectOf(
                            "deal" to DealCodec.encodeDeal(detail.deal),
                            "contact" to (detail.contact?.let { DealCodec.encodeContact(it) }
                                ?: com.eventverse.app.shared.json.JsonValue.Null),
                            "purchaseOrders" to com.eventverse.app.shared.json.jsonArrayOf(
                                detail.purchaseOrders.map { DealCodec.encodePurchaseOrder(it) }
                            )
                        )
                        call.respondJson(payload.encode())
                    }
                    .onFailure { call.respondFailure(HttpStatusCode.InternalServerError, it) }
            }

            post("/stage") {
                val tenant = call.requireTenant() ?: return@post
                val decision = call.crmDecision(tenant, roleRepository, moduleAssignmentRepository)
                if (!call.requireCrmAccess(decision, AccessLevel.OPERATE)) return@post

                val dealId = DealId(call.parameters["id"] ?: "")
                val existing = dealRepository.findById(tenant.tenantId, dealId)
                if (existing == null) {
                    call.respond(HttpStatusCode.NotFound, "Deal not found")
                    return@post
                }
                val reach = call.crmOwnerReach(tenant, decision, employeeRepository)
                if (!call.requireReachableOwner(reach, existing.ownerEmployeeId)) return@post

                val newStage = DealCodec.decodeStageRequest(call.receiveText())
                if (newStage == null) {
                    call.respond(HttpStatusCode.BadRequest, "Invalid or missing 'stage'")
                    return@post
                }

                updateDealStageUseCase(tenant.tenantId, dealId, newStage)
                    .onSuccess { deal -> call.respondJson(DealCodec.encodeDeal(deal).encode()) }
                    .onFailure { call.respondFailure(HttpStatusCode.BadRequest, it) }
            }
        }

        // ROUTES_PO
        route("/{id}") {

            post("/purchase-orders") {
                val tenant = call.requireTenant() ?: return@post
                val decision = call.crmDecision(tenant, roleRepository, moduleAssignmentRepository)
                if (!call.requireCrmAccess(decision, AccessLevel.OPERATE)) return@post

                val dealId = DealId(call.parameters["id"] ?: "")
                val existing = dealRepository.findById(tenant.tenantId, dealId)
                if (existing == null) {
                    call.respond(HttpStatusCode.NotFound, "Deal not found")
                    return@post
                }
                val reach = call.crmOwnerReach(tenant, decision, employeeRepository)
                if (!call.requireReachableOwner(reach, existing.ownerEmployeeId)) return@post

                val req = DealCodec.decodeAttachManualPoRequest(call.receiveText())
                attachPurchaseOrderUseCase(
                    AttachPurchaseOrderUseCase.Command(
                        tenantId = tenant.tenantId,
                        dealId = dealId,
                        poNumber = req.poNumber,
                        poDate = req.poDate,
                        origin = PoOrigin.MANUAL,
                        lines = req.lines,
                        notes = req.notes,
                        recordedBy = call.callerPrincipalOrNull?.userId ?: "system",
                        newId = { newId("po") }
                    )
                ).onSuccess { po ->
                    call.respondText(
                        text = DealCodec.encodePurchaseOrder(po).encode(),
                        status = HttpStatusCode.Created,
                        contentType = ContentType.Application.Json
                    )
                }.onFailure { call.respondFailure(HttpStatusCode.BadRequest, it) }
            }

            post("/purchase-orders/upload") {
                val tenant = call.requireTenant() ?: return@post
                val decision = call.crmDecision(tenant, roleRepository, moduleAssignmentRepository)
                if (!call.requireCrmAccess(decision, AccessLevel.OPERATE)) return@post

                if (poFileStorage?.isConfigured != true) {
                    call.respond(
                        HttpStatusCode.ServiceUnavailable,
                        "Object storage belum dikonfigurasi (S3_ENDPOINT/S3_ACCESS_KEY/S3_SECRET_KEY/S3_BUCKET_PO)."
                    )
                    return@post
                }

                val dealId = DealId(call.parameters["id"] ?: "")
                val existing = dealRepository.findById(tenant.tenantId, dealId)
                if (existing == null) {
                    call.respond(HttpStatusCode.NotFound, "Deal not found")
                    return@post
                }
                val reach = call.crmOwnerReach(tenant, decision, employeeRepository)
                if (!call.requireReachableOwner(reach, existing.ownerEmployeeId)) return@post

                val query = call.request.queryParameters
                val fileName = query["fileName"]?.takeIf { it.isNotBlank() }
                val mimeType = query["mimeType"]?.takeIf { it.isNotBlank() } ?: "application/octet-stream"
                val poNumber = query["poNumber"]?.takeIf { it.isNotBlank() }
                if (fileName == null || poNumber == null) {
                    call.respond(HttpStatusCode.BadRequest, "Query 'fileName' dan 'poNumber' wajib diisi")
                    return@post
                }
                if (mimeType !in ALLOWED_PO_MIME_TYPES) {
                    call.respond(HttpStatusCode.UnsupportedMediaType, "Tipe berkas $mimeType tidak diizinkan")
                    return@post
                }

                val bytes = call.receive<ByteArray>()
                if (bytes.isEmpty()) {
                    call.respond(HttpStatusCode.BadRequest, "Body berkas kosong")
                    return@post
                }
                if (bytes.size > MAX_PO_FILE_BYTES) {
                    call.respond(HttpStatusCode.PayloadTooLarge, "Ukuran berkas melebihi 10 MB")
                    return@post
                }

                attachPurchaseOrderUseCase(
                    AttachPurchaseOrderUseCase.Command(
                        tenantId = tenant.tenantId,
                        dealId = dealId,
                        poNumber = poNumber,
                        poDate = com.eventverse.app.shared.common.DateTimeCodec
                            .parseLocalDateOrNull(query["poDate"])
                            ?: kotlinx.datetime.Clock.System.now()
                                .toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault()).date,
                        origin = PoOrigin.UPLOADED,
                        notes = query["notes"] ?: "",
                        recordedBy = call.callerPrincipalOrNull?.userId ?: "system",
                        fileBytes = bytes,
                        fileName = fileName,
                        mimeType = mimeType,
                        newId = { newId("po") }
                    )
                ).onSuccess { po ->
                    call.respondText(
                        text = DealCodec.encodePurchaseOrder(po).encode(),
                        status = HttpStatusCode.Created,
                        contentType = ContentType.Application.Json
                    )
                }.onFailure { call.respondFailure(HttpStatusCode.BadRequest, it) }
            }

            get("/purchase-orders/{poId}/download") {
                val tenant = call.requireTenant() ?: return@get
                val decision = call.crmDecision(tenant, roleRepository, moduleAssignmentRepository)
                if (!call.requireCrmAccess(decision, AccessLevel.VIEW)) return@get

                val dealId = DealId(call.parameters["id"] ?: "")
                val deal = dealRepository.findById(tenant.tenantId, dealId)
                if (deal == null) {
                    call.respond(HttpStatusCode.NotFound, "Deal not found")
                    return@get
                }
                val reach = call.crmOwnerReach(tenant, decision, employeeRepository)
                if (!call.requireReachableOwner(reach, deal.ownerEmployeeId)) return@get

                val po = dealRepository.findPurchaseOrderById(
                    tenant.tenantId,
                    PurchaseOrderId(call.parameters["poId"] ?: "")
                )
                val storageKey = po?.storageKey
                if (storageKey == null) {
                    call.respond(HttpStatusCode.NotFound, "PO tidak ditemukan atau bukan PO upload")
                    return@get
                }
                val storage = poFileStorage
                if (storage?.isConfigured != true) {
                    call.respond(HttpStatusCode.ServiceUnavailable, "Object storage belum dikonfigurasi")
                    return@get
                }
                storage.downloadUrl(storageKey)
                    .onSuccess { url ->
                        call.respondText(
                            text = com.eventverse.app.shared.json.jsonObjectOf(
                                "url" to com.eventverse.app.shared.json.jsonOf(url)
                            ).encode(),
                            contentType = ContentType.Application.Json
                        )
                    }
                    .onFailure { call.respondFailure(HttpStatusCode.InternalServerError, it) }
            }
        }
    }
    route("/api/tenant/crm/contacts") {

        // Customer master listing. Same authority surface as deals: CRM_SALES VIEW.
        get {
            val tenant = call.requireTenant() ?: return@get
            val decision = call.crmDecision(tenant, roleRepository, moduleAssignmentRepository)
            if (!call.requireCrmAccess(decision, AccessLevel.VIEW)) return@get

            val contacts = contactRepository.findActive(tenant.tenantId)
            call.respondJson(DealCodec.encodeContacts(contacts))
        }
    }
}

private const val MAX_PO_FILE_BYTES = 10 * 1024 * 1024

private val ALLOWED_PO_MIME_TYPES = setOf(
    "application/pdf",
    "image/png",
    "image/jpeg",
    "image/webp"
)

/** ID generator with the same shape the CRM routes use, prefixed by resource. */
internal fun newId(prefix: String): String =
    "$prefix-${Instant.fromEpochMilliseconds(System.currentTimeMillis()).toEpochMilliseconds()}-" +
        (100..999).random()

private suspend fun ApplicationCall.requireTenant(): com.eventverse.app.domain.tenant.TenantContext? {
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
