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
import com.eventverse.app.domain.deal.storage.PoFileStorage as PoStorage
import com.eventverse.app.domain.sampling.SamplingOrder
import com.eventverse.app.domain.sampling.SamplingOrderId
import com.eventverse.app.domain.sampling.SamplingOrderRepository
import com.eventverse.app.domain.sampling.SizeChartRow
import com.eventverse.app.domain.sampling.usecases.AttachSamplingMockupCommand
import com.eventverse.app.domain.sampling.usecases.AttachSamplingMockupUseCase
import com.eventverse.app.domain.sampling.usecases.ApproveSamplingFromDealCommand
import com.eventverse.app.domain.sampling.usecases.ApproveSamplingFromDealUseCase
import com.eventverse.app.domain.sampling.usecases.CreateSamplingOrderFromDealUseCase
import com.eventverse.app.domain.sampling.usecases.SamplingFromDealCommand
import com.eventverse.app.plugins.callerPrincipalOrNull
import com.eventverse.app.plugins.tenantContextOrNull
import com.eventverse.app.shared.deal.DealCodec
import com.eventverse.app.shared.sampling.SamplingOrderCodec
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
    samplingOrderRepository: SamplingOrderRepository? = null,
) {
    val listDealsUseCase = ListDealsUseCase(dealRepository)
    val getDealDetailUseCase = GetDealDetailUseCase(dealRepository, contactRepository)
    val updateDealStageUseCase = UpdateDealStageUseCase(dealRepository)
    val attachPurchaseOrderUseCase = AttachPurchaseOrderUseCase(dealRepository, poFileStorage)
    val createSamplingFromDealUseCase = samplingOrderRepository?.let { CreateSamplingOrderFromDealUseCase(it) }
    val approveSamplingFromDealUseCase = samplingOrderRepository?.let { ApproveSamplingFromDealUseCase(it) }
    val attachSamplingMockupUseCase = samplingOrderRepository?.let { AttachSamplingMockupUseCase(it) }

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

        // ROUTES_SAMPLING — lembar sampling per desain menempel pada deal (Golden Sample Lock).
        // Authority sama dengan deal lainnya: CRM_SALES (VIEW untuk baca, OPERATE untuk tulis).
        get("/{id}/sampling-orders") {
            val tenant = call.requireTenant() ?: return@get
            val decision = call.crmDecision(tenant, roleRepository, moduleAssignmentRepository)
            if (!call.requireCrmAccess(decision, AccessLevel.VIEW)) return@get
            val repo = samplingOrderRepository
            if (repo == null) {
                call.respond(HttpStatusCode.ServiceUnavailable, "Modul sampling tidak tersedia")
                return@get
            }

            val dealId = DealId(call.parameters["id"] ?: "")
            val deal = dealRepository.findById(tenant.tenantId, dealId)
            if (deal == null) {
                call.respond(HttpStatusCode.NotFound, "Deal not found")
                return@get
            }

            val orders = repo.findByDealId(tenant.tenantId, dealId.value)
            // DB menyimpan OBJECT KEY, bukan presigned URL (yang kedaluwarsa). URL segar
            // dibuat di sini supaya foto desain selalu bisa ditampilkan klien.
            val resolved = orders.map { withResolvedMockups(it, poFileStorage) }
            call.respondJson(
                com.eventverse.app.shared.json.jsonArrayOf(resolved.map { SamplingOrderCodec.encode(it) }).encode()
            )
        }

        post("/{id}/sampling-orders") {
            val tenant = call.requireTenant() ?: return@post
            val decision = call.crmDecision(tenant, roleRepository, moduleAssignmentRepository)
            if (!call.requireCrmAccess(decision, AccessLevel.OPERATE)) return@post
            val useCase = createSamplingFromDealUseCase
            if (useCase == null) {
                call.respond(HttpStatusCode.ServiceUnavailable, "Modul sampling tidak tersedia")
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

            val json = com.eventverse.app.shared.json.JsonParser.parseObject(call.receiveText())
            // POST = khusus membuat lembar sampling BARU. Pembaruan memakai PUT
            // /{id}/sampling-orders/{samplingId} — id lewat path, bukan body, supaya create
            // dan update tidak lagi berbagi satu pintu dan saling menimpa satu sama lain.
            if (!json.string("samplingOrderId").isNullOrBlank()) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    "samplingOrderId tidak boleh dikirim lewat POST. Gunakan PUT /{id}/sampling-orders/{samplingId} untuk memperbarui lembar sampling."
                )
                return@post
            }
            val postSizeMatrix = json.objectArray("sizeMatrix").map { rowObj ->
                val valuesMap = mutableMapOf<String, String>()
                rowObj.obj("values")?.entries?.forEach { (k, v) ->
                    when (v) {
                        is com.eventverse.app.shared.json.JsonValue.Str -> valuesMap[k] = v.value
                        is com.eventverse.app.shared.json.JsonValue.Num -> valuesMap[k] = v.raw
                        else -> Unit
                    }
                }
                SizeChartRow(
                    id = rowObj.string("id") ?: "",
                    pomName = rowObj.string("pomName") ?: "",
                    values = valuesMap
                )
            }.takeIf { it.isNotEmpty() }

            val clientNameResolved = contactRepository.findById(tenant.tenantId, existing.contactId)?.displayName ?: existing.contactId.value

            val command = SamplingFromDealCommand(
                tenantId = tenant.tenantId,
                dealId = dealId.value,
                clientName = clientNameResolved,
                styleName = json.string("styleName") ?: "",
                samplingOrderId = null,
                sampleQuantity = json.int("sampleQuantity") ?: 2,
                courierTracking = json.string("courierTracking"),
                samplingFeeIdr = json.long("samplingFeeIdr") ?: 0L,
                notes = json.string("notes") ?: "",
                sizeMatrix = postSizeMatrix,
                deadlineDelivery = com.eventverse.app.shared.common.DateTimeCodec.parseLocalDateOrNull(json.string("deadlineDelivery"))
            )

            useCase(command)
                .onSuccess { order ->
                    val resolved = withResolvedMockups(order, poFileStorage)
                    call.respondJson(SamplingOrderCodec.encode(resolved).encode())
                }
                .onFailure { call.respondFailure(HttpStatusCode.BadRequest, it) }
        }

        // PUT /{id}/sampling-orders/{samplingId} — khusus pembaruan lembar sampling yang sudah
        // ada (rename, quantity, resi kurir, biaya, catatan). ID resmi sumber kebenaran ada di
        // path, sehingga tidak mungkin payload update yang salah id membuat lembar baru.
        put("/{id}/sampling-orders/{samplingId}") {
            val tenant = call.requireTenant() ?: return@put
            val decision = call.crmDecision(tenant, roleRepository, moduleAssignmentRepository)
            if (!call.requireCrmAccess(decision, AccessLevel.OPERATE)) return@put
            val useCase = createSamplingFromDealUseCase
            if (useCase == null) {
                call.respond(HttpStatusCode.ServiceUnavailable, "Modul sampling tidak tersedia")
                return@put
            }

            val dealId = DealId(call.parameters["id"] ?: "")
            val existing = dealRepository.findById(tenant.tenantId, dealId)
            if (existing == null) {
                call.respond(HttpStatusCode.NotFound, "Deal not found")
                return@put
            }
            val reach = call.crmOwnerReach(tenant, decision, employeeRepository)
            if (!call.requireReachableOwner(reach, existing.ownerEmployeeId)) return@put

            val json = com.eventverse.app.shared.json.JsonParser.parseObject(call.receiveText())
            val putSizeMatrix = json.objectArray("sizeMatrix").map { rowObj ->
                val valuesMap = mutableMapOf<String, String>()
                rowObj.obj("values")?.entries?.forEach { (k, v) ->
                    when (v) {
                        is com.eventverse.app.shared.json.JsonValue.Str -> valuesMap[k] = v.value
                        is com.eventverse.app.shared.json.JsonValue.Num -> valuesMap[k] = v.raw
                        else -> Unit
                    }
                }
                SizeChartRow(
                    id = rowObj.string("id") ?: "",
                    pomName = rowObj.string("pomName") ?: "",
                    values = valuesMap
                )
            }.takeIf { it.isNotEmpty() }

            val clientNameResolved = contactRepository.findById(tenant.tenantId, existing.contactId)?.displayName ?: existing.contactId.value

            val command = SamplingFromDealCommand(
                tenantId = tenant.tenantId,
                dealId = dealId.value,
                clientName = clientNameResolved,
                styleName = json.string("styleName") ?: "",
                samplingOrderId = call.parameters["samplingId"] ?: "",
                sampleQuantity = json.int("sampleQuantity") ?: 2,
                courierTracking = json.string("courierTracking"),
                samplingFeeIdr = json.long("samplingFeeIdr") ?: 0L,
                notes = json.string("notes") ?: "",
                sizeMatrix = putSizeMatrix,
                deadlineDelivery = com.eventverse.app.shared.common.DateTimeCodec.parseLocalDateOrNull(json.string("deadlineDelivery"))
            )

            useCase(command)
                .onSuccess { order ->
                    val resolved = withResolvedMockups(order, poFileStorage)
                    call.respondJson(SamplingOrderCodec.encode(resolved).encode())
                }
                .onFailure { call.respondFailure(HttpStatusCode.BadRequest, it) }
        }

        post("/{id}/sampling-orders/{samplingId}/acc") {
            val tenant = call.requireTenant() ?: return@post
            val decision = call.crmDecision(tenant, roleRepository, moduleAssignmentRepository)
            if (!call.requireCrmAccess(decision, AccessLevel.OPERATE)) return@post
            val useCase = approveSamplingFromDealUseCase
            if (useCase == null) {
                call.respond(HttpStatusCode.ServiceUnavailable, "Modul sampling tidak tersedia")
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

            val json = com.eventverse.app.shared.json.JsonParser.parseObject(call.receiveText())
            val command = ApproveSamplingFromDealCommand(
                tenantId = tenant.tenantId,
                dealId = dealId.value,
                samplingOrderId = call.parameters["samplingId"] ?: "",
                isApproved = json.boolean("isApproved") ?: true,
                notes = json.string("notes") ?: ""
            )

            useCase(command)
                .onSuccess { orders ->
                    call.respondJson(
                        com.eventverse.app.shared.json.jsonArrayOf(orders.map { SamplingOrderCodec.encode(it) }).encode()
                    )
                }
                .onFailure { call.respondFailure(HttpStatusCode.BadRequest, it) }
        }

        // POST /{id}/sampling-orders/{samplingId}/mockup — unggah foto mockup desain.
        // Kontrak sama dengan upload PO: metadata via query, bytes sebagai raw body.
        post("/{id}/sampling-orders/{samplingId}/mockup") {
            val tenant = call.requireTenant() ?: return@post
            val decision = call.crmDecision(tenant, roleRepository, moduleAssignmentRepository)
            if (!call.requireCrmAccess(decision, AccessLevel.OPERATE)) return@post

            val useCase = attachSamplingMockupUseCase
            if (useCase == null) {
                call.respond(HttpStatusCode.ServiceUnavailable, "Modul sampling tidak tersedia")
                return@post
            }
            // Objek storage opsional: kalau belum dikonfigurasi (dev tanpa MinIO), foto tetap
            // bisa masuk sebagai data URL kecil supaya tombol Upload tidak menemui jalan buntu.
            val storage = poFileStorage?.takeIf { it.isConfigured }

            val dealId = DealId(call.parameters["id"] ?: "")
            val existing = dealRepository.findById(tenant.tenantId, dealId)
            if (existing == null) {
                call.respond(HttpStatusCode.NotFound, "Deal not found")
                return@post
            }
            val reach = call.crmOwnerReach(tenant, decision, employeeRepository)
            if (!call.requireReachableOwner(reach, existing.ownerEmployeeId)) return@post

            val samplingId = call.parameters["samplingId"] ?: ""
            if (samplingId.isBlank()) {
                call.respond(HttpStatusCode.BadRequest, "Missing samplingOrderId")
                return@post
            }

            val query = call.request.queryParameters
            val slot = query["slot"]?.takeIf { it == "back" } ?: "front"
            val fileName = query["fileName"]?.takeIf { it.isNotBlank() }
            val mimeType = query["mimeType"]?.takeIf { it.isNotBlank() } ?: "application/octet-stream"
            if (fileName == null) {
                call.respond(HttpStatusCode.BadRequest, "Query 'fileName' wajib diisi")
                return@post
            }
            if (mimeType !in ALLOWED_MOCKUP_MIME_TYPES) {
                call.respond(HttpStatusCode.UnsupportedMediaType, "Tipe gambar $mimeType tidak diizinkan")
                return@post
            }

            val bytes = call.receive<ByteArray>()
            if (bytes.isEmpty()) {
                call.respond(HttpStatusCode.BadRequest, "Body berkas kosong")
                return@post
            }
            val inline = storage == null
            val limit = if (inline) MAX_INLINE_MOCKUP_BYTES else MAX_MOCKUP_BYTES
            if (bytes.size > limit) {
                val limitMb = limit / (1024 * 1024)
                call.respond(
                    HttpStatusCode.PayloadTooLarge,
                    if (inline) {
                        "Ukuran foto melebihi $limitMb MB. Konfigurasikan S3/MinIO " +
                            "(S3_ENDPOINT/S3_ACCESS_KEY/S3_SECRET_KEY) untuk foto berukuran besar."
                    } else {
                        "Ukuran foto melebihi $limitMb MB"
                    }
                )
                return@post
            }

            val safeName = fileName.replace(Regex("[^A-Za-z0-9._-]"), "_")
            val reference: String
            if (inline) {
                reference = "data:$mimeType;base64," + java.util.Base64.getEncoder().encodeToString(bytes)
            } else {
                val key = "sampling-mockups/${tenant.tenantId.value}/$samplingId/" +
                    "${Instant.fromEpochMilliseconds(System.currentTimeMillis()).toEpochMilliseconds()}-$safeName"
                val stored = storage.put(key, bytes, mimeType)
                if (stored.isFailure) {
                    call.respondFailure(HttpStatusCode.InternalServerError, stored.exceptionOrNull()!!)
                    return@post
                }
                reference = key
            }

            useCase(
                AttachSamplingMockupCommand(
                    tenantId = tenant.tenantId,
                    samplingOrderId = SamplingOrderId(samplingId),
                    storageKey = reference,
                    slot = slot
                )
            ).onSuccess { order ->
                call.respondJson(
                    SamplingOrderCodec.encode(withResolvedMockups(order, storage)).encode()
                )
            }.onFailure { call.respondFailure(HttpStatusCode.BadRequest, it) }
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

/** Foto mockup desain: hanya raster web yang aman ditampilkan di kanvas. */
private val ALLOWED_MOCKUP_MIME_TYPES = setOf(
    "image/png",
    "image/jpeg",
    "image/webp"
)

private const val MAX_MOCKUP_BYTES = 5 * 1024 * 1024

/**
 * Batas foto yang disimpan INLINE (base64) saat object storage belum dikonfigurasi.
 * Sengaja jauh di bawah batas storage: nilai ini ikut ditulis ke kolom jsonb, jadi
 * 2 MB adalah kompromi paling jauh yang masih sehat untuk lingkungan pengembangan.
 */
private const val MAX_INLINE_MOCKUP_BYTES = 2 * 1024 * 1024

/**
 * Mengganti key object storage di `knitSpec.mockupImageUrls` dengan presigned URL segar.
 * Entri yang sudah berupa URL absolut dibiarkan; entri yang gagal di-resolve dibuang supaya
 * UI tidak menerima tautan mati.
 */
private suspend fun withResolvedMockups(
    order: SamplingOrder,
    storage: PoStorage?
): SamplingOrder {
    val entries = order.knitSpec.mockupImageUrls
    if (entries.isEmpty()) return order
    val resolved = entries.mapNotNull { entry ->
        val prefix = when {
            entry.startsWith("front:") -> "front:"
            entry.startsWith("back:") -> "back:"
            else -> ""
        }
        val key = entry.removePrefix(prefix)
        val url = when {
            key.startsWith("http") -> key
            key.startsWith("data:") -> key
            storage?.isConfigured == true -> storage.downloadUrl(key).getOrNull()
            else -> null
        }
        url?.let { prefix + it }
    }
    return order.copy(knitSpec = order.knitSpec.copy(mockupImageUrls = resolved))
}

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
