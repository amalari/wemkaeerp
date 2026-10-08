package com.eventverse.app.routes

import com.eventverse.app.domain.rbac.isScopeSupported

import com.eventverse.app.domain.rbac.isFoundation

import com.eventverse.app.domain.rbac.isOperational

import com.eventverse.app.domain.rbac.isGovernance

import com.eventverse.app.domain.rbac.isHierarchical

import com.eventverse.app.domain.rbac.isGlobalOnly


import com.eventverse.app.domain.rbac.supportedScopes

import com.eventverse.app.domain.rbac.kind

import com.eventverse.app.domain.rbac.scopeCapability

import com.eventverse.app.domain.rbac.iconKey

import com.eventverse.app.domain.rbac.description

import com.eventverse.app.domain.rbac.displayName

import com.eventverse.app.domain.rbac.name

import com.eventverse.app.domain.rbac.code

import com.eventverse.app.domain.pack.GarmentModules

import com.eventverse.app.domain.auth.Permission
import com.eventverse.app.domain.fulfillment.HandoverProof
import com.eventverse.app.domain.fulfillment.FulfillmentRouteConfig
import com.eventverse.app.domain.fulfillment.FulfillmentRouteConfigRepository
import com.eventverse.app.domain.fulfillment.HandoverMode
import com.eventverse.app.domain.fulfillment.HandoverRouteCode
import com.eventverse.app.domain.fulfillment.HandoverRouteRepository
import com.eventverse.app.domain.fulfillment.InternalTransferRepository
import com.eventverse.app.domain.fulfillment.SackTransferId
import com.eventverse.app.domain.fulfillment.SackTransferStatus
import com.eventverse.app.domain.fulfillment.TenantHandoverRoutes
import com.eventverse.app.domain.fulfillment.WeightKg
import com.eventverse.app.domain.fulfillment.usecases.ApproveTransferUseCase
import com.eventverse.app.domain.fulfillment.usecases.ListTransfersUseCase
import com.eventverse.app.domain.fulfillment.usecases.ReceiveTransferUseCase
import com.eventverse.app.domain.fulfillment.usecases.RejectTransferUseCase
import com.eventverse.app.domain.fulfillment.usecases.ResubmitTransferUseCase
import com.eventverse.app.domain.fulfillment.usecases.SubmitTransferUseCase
import com.eventverse.app.domain.rbac.AccessLevel
import com.eventverse.app.domain.rbac.BusinessModule
import com.eventverse.app.domain.rbac.RoleId
import com.eventverse.app.domain.rbac.RoleRepository
import com.eventverse.app.domain.tenant.TenantContext
import com.eventverse.app.domain.traceability.TraceContainerRepository
import com.eventverse.app.infrastructure.storage.BenchmarkImageStorage
import com.eventverse.app.plugins.callerPrincipalOrNull
import com.eventverse.app.plugins.tenantContextOrNull
import com.eventverse.app.shared.fulfillment.InternalTransferCodec
import com.eventverse.app.shared.json.JsonParser
import com.eventverse.app.shared.json.JsonValue
import com.eventverse.app.shared.json.jsonArrayOf
import com.eventverse.app.shared.json.jsonObjectOf
import com.eventverse.app.shared.json.jsonOf
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receive
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import kotlinx.datetime.Clock

/**
 * Rute transfer karung antar divisi (kurir internal).
 *
 * Gerbang wewenang: baca butuh konteks tenant saja; aksi kerja (ajukan/terima) butuh OPERATE;
 * ACC dan tolak butuh MANAGE pada modul FULFILLMENT — admin produksi yang berhak menutup gerbang.
 */
fun Route.fulfillmentTransferRoutes(
    transfers: InternalTransferRepository,
    containers: TraceContainerRepository,
    routeConfigRepository: FulfillmentRouteConfigRepository,
    /** Daftar rute per tenant (V96) — sumber `knownRoutes` untuk submit dan layar `/routes`. */
    handoverRoutes: HandoverRouteRepository,
    imageStorage: BenchmarkImageStorage? = null,
    roleRepository: RoleRepository? = null
) {
    val resubmitUseCase = ResubmitTransferUseCase(transfers)
    val approve = ApproveTransferUseCase(transfers)
    val reject = RejectTransferUseCase(transfers)
    val receive = ReceiveTransferUseCase(transfers)
    val list = ListTransfersUseCase(transfers)

    route("/api/tenant/fulfillment") {

        get("/transfers") {
            val tenant = call.requireFulfillmentTenant() ?: return@get
            val statuses = call.request.queryParameters["status"].orEmpty()
                .split(",").mapNotNull { raw -> SackTransferStatus.entries.firstOrNull { it.name == raw.trim() } }
                .toSet()

            list(tenant.tenantId, statuses)
                .onSuccess { rows ->
                    call.respondJson(jsonArrayOf(rows.map(InternalTransferCodec::encode)).encode())
                }
                .onFailure { call.respond(HttpStatusCode.BadRequest, it.message ?: "Gagal memuat transfer") }
        }

        post("/transfers") {
            val tenant = call.requireFulfillmentTenant() ?: return@post
            if (!call.canOperateFulfillment(tenant, roleRepository)) {
                return@post call.respond(
                    HttpStatusCode.Forbidden,
                    "Akses ditolak: mengajukan antar karung butuh wewenang kerja modul FULFILLMENT"
                )
            }
            val body = call.fulfillmentBody()
                ?: return@post call.respond(HttpStatusCode.BadRequest, "Body JSON tidak terbaca")

            // Kode rute kini data tenant (V96): parser tunggal menolak yang tidak sah —
            // keanggotaan & status aktif divalidasi terhadap daftar efektif di bawah.
            val routeCode = HandoverRouteCode.parse(body.string("leg"))
                .getOrElse {
                    return@post call.respond(HttpStatusCode.BadRequest, it.message ?: "Kode rute perjalanan tidak sah")
                }
            if (tenant.effectiveRoutes(handoverRoutes).find(routeCode)?.active != true) {
                return@post call.respond(
                    HttpStatusCode.BadRequest,
                    "Rute '${routeCode.value}' tidak dikenal atau sudah dinonaktifkan untuk tenant ini"
                )
            }

            // Mode diselesaikan di sini supaya bukti yang kurang dijawab 400 (salah isi form),
            // bukan 409 dari invarian domain — dua hal yang menuntun operator ke arah berbeda.
            val mode = (routeConfigRepository.findByTenantId(tenant.tenantId)
                ?: FulfillmentRouteConfig(tenant.tenantId)).modeFor(routeCode)

            var weight: WeightKg? = null
            var photoKey: String? = null
            if (mode == HandoverMode.ADMIN_HUB) {
                val weightRaw = body.string("dispatchWeightKg")?.takeIf { it.isNotBlank() }
                    ?: return@post call.respond(HttpStatusCode.BadRequest, "Berat timbangan wajib diisi")
                weight = WeightKg.parse(weightRaw)
                    ?: return@post call.respond(HttpStatusCode.BadRequest, "Berat '$weightRaw' tidak sah")
                photoKey = body.string("dispatchScalePhotoKey")?.takeIf { it.isNotBlank() }
                    ?: return@post call.respond(
                        HttpStatusCode.BadRequest, "Foto timbangan wajib ada sebelum mengajukan"
                    )
            }

            // Rute yang sah kini dari data tenant (V96), bukan lagi isi `SackRoute` —
            // jembatan S0–S2 berakhir di rebase PR B (plan §Track B, catatan).
            val submit = SubmitTransferUseCase(
                transfers,
                containers,
                routeConfigRepository,
                knownRoutes = { tenantId ->
                    TenantHandoverRoutes.resolve(
                        tenantId,
                        handoverRoutes.findByTenantId(tenantId),
                        tenant.pack.handoverRouteTemplate
                    )
                }
            )
            submit(
                tenantId = tenant.tenantId,
                rawSackPayload = body.string("sackCode").orEmpty(),
                route = routeCode,
                dispatchWeightKg = weight,
                dispatchScalePhotoKey = photoKey,
                requestedBy = body.string("requestedBy").orEmpty(),
                now = Clock.System.now(),
                notes = body.string("notes").orEmpty(),
                declaredPcsOverride = body.int("declaredPcs")?.takeIf { it > 0 }
            )
                .onSuccess { call.respondJson(InternalTransferCodec.encode(it).encode()) }
                .onFailure { call.respond(HttpStatusCode.Conflict, it.message ?: "Gagal mengajukan transfer") }
        }

        post("/transfers/{id}/approve") {
            val tenant = call.requireFulfillmentTenant() ?: return@post
            if (!call.canManageFulfillment(tenant, roleRepository)) {
                return@post call.respond(
                    HttpStatusCode.Forbidden,
                    "Akses ditolak: ACC transfer butuh wewenang kelola modul FULFILLMENT"
                )
            }
            val body = call.fulfillmentBody()
                ?: return@post call.respond(HttpStatusCode.BadRequest, "Body JSON tidak terbaca")

            approve(
                tenantId = tenant.tenantId,
                transferId = SackTransferId(call.parameters["id"].orEmpty()),
                approverName = body.string("approverName").orEmpty(),
                signatureKey = body.string("signatureKey").orEmpty(),
                now = Clock.System.now()
            )
                .onSuccess { call.respondJson(InternalTransferCodec.encode(it).encode()) }
                .onFailure { call.respond(HttpStatusCode.Conflict, it.message ?: "Gagal ACC transfer") }
        }

        post("/transfers/{id}/reject") {
            val tenant = call.requireFulfillmentTenant() ?: return@post
            if (!call.canManageFulfillment(tenant, roleRepository)) {
                return@post call.respond(
                    HttpStatusCode.Forbidden,
                    "Akses ditolak: menolak transfer butuh wewenang kelola modul FULFILLMENT"
                )
            }
            val body = call.fulfillmentBody()
                ?: return@post call.respond(HttpStatusCode.BadRequest, "Body JSON tidak terbaca")

            reject(
                tenantId = tenant.tenantId,
                transferId = SackTransferId(call.parameters["id"].orEmpty()),
                reason = body.string("reason").orEmpty(),
                rejectedBy = body.string("approverName").orEmpty(),
                now = Clock.System.now()
            )
                .onSuccess { call.respondJson(InternalTransferCodec.encode(it).encode()) }
                .onFailure { call.respond(HttpStatusCode.Conflict, it.message ?: "Gagal menolak transfer") }
        }

        post("/transfers/{id}/resubmit") {
            val tenant = call.requireFulfillmentTenant() ?: return@post
            if (!call.canOperateFulfillment(tenant, roleRepository)) {
                return@post call.respond(HttpStatusCode.Forbidden, "Akses ditolak")
            }
            val body = call.fulfillmentBody()
                ?: return@post call.respond(HttpStatusCode.BadRequest, "Body JSON tidak terbaca")
            val weight = WeightKg.parse(body.string("dispatchWeightKg").orEmpty())
                ?: return@post call.respond(HttpStatusCode.BadRequest, "Berat timbangan tidak sah")

            resubmitUseCase(
                tenantId = tenant.tenantId,
                transferId = SackTransferId(call.parameters["id"].orEmpty()),
                dispatchWeightKg = weight,
                dispatchScalePhotoKey = body.string("dispatchScalePhotoKey").orEmpty(),
                requestedBy = body.string("requestedBy").orEmpty(),
                now = Clock.System.now()
            )
                .onSuccess { call.respondJson(InternalTransferCodec.encode(it).encode()) }
                .onFailure { call.respond(HttpStatusCode.Conflict, it.message ?: "Gagal mengajukan ulang") }
        }

        post("/transfers/{id}/receive") {
            val tenant = call.requireFulfillmentTenant() ?: return@post
            if (!call.canOperateFulfillment(tenant, roleRepository)) {
                return@post call.respond(
                    HttpStatusCode.Forbidden,
                    "Akses ditolak: mencatat penerimaan butuh wewenang kerja modul FULFILLMENT"
                )
            }
            val body = call.fulfillmentBody()
                ?: return@post call.respond(HttpStatusCode.BadRequest, "Body JSON tidak terbaca")
            val proof = decodeHandoverProof(body)
                ?: return@post call.respond(HttpStatusCode.BadRequest, "Bukti serah terima tidak lengkap")

            receive(
                tenantId = tenant.tenantId,
                transferId = SackTransferId(call.parameters["id"].orEmpty()),
                proof = proof,
                receivedWeightKg = body.string("receivedWeightKg")?.let(WeightKg::parse),
                receivedPcs = body.int("receivedPcs"),
                recordedBy = body.string("recordedBy").orEmpty(),
                now = Clock.System.now()
            )
                .onSuccess { call.respondJson(InternalTransferCodec.encode(it).encode()) }
                .onFailure { call.respond(HttpStatusCode.Conflict, it.message ?: "Gagal mencatat penerimaan") }
        }

        // Upload bukti (foto timbangan / TTD / resi): body mentah, metadata di query —
        // konvensi yang sama dengan upload PO & arsip HPP, tanpa multipart di lima target.
        post("/evidence/upload") {
            val tenant = call.requireFulfillmentTenant() ?: return@post
            if (!call.canOperateFulfillment(tenant, roleRepository)) {
                return@post call.respond(HttpStatusCode.Forbidden, "Akses ditolak")
            }
            val storage = imageStorage
                ?: return@post call.respond(HttpStatusCode.ServiceUnavailable, "Penyimpanan berkas belum dikonfigurasi")
            val fileName = call.request.queryParameters["fileName"]?.takeIf { it.isNotBlank() }
                ?: return@post call.respond(HttpStatusCode.BadRequest, "Query 'fileName' wajib diisi")
            val contentType = call.request.queryParameters["contentType"] ?: "image/png"
            val bytes = call.receive<ByteArray>()
            if (bytes.isEmpty()) return@post call.respond(HttpStatusCode.BadRequest, "Berkas kosong")

            val key = storage.store(tenant.slug.value, fileName, contentType, bytes)
                ?: return@post call.respond(HttpStatusCode.InternalServerError, "Gagal menyimpan berkas")
            call.respondJson(jsonObjectOf("key" to jsonOf(key)).encode())
        }
    }
}

/** Bukti serah terima dari body — tepat dua jalur, sesuai domain. */
private fun decodeHandoverProof(body: JsonValue.Obj): HandoverProof? = when (body.string("handoverType")) {
    "RECEIVER" -> HandoverProof.ReceiverHandover(
        receiverName = body.string("receiverName").orEmpty(),
        signatureKey = body.string("receiverSignatureKey")?.takeIf { it.isNotBlank() },
        evidencePhotoKey = body.string("handoverPhotoKey").orEmpty()
    )
    "COURIER" -> body.string("chargeableWeightKg")?.let(WeightKg::parse)?.let { weight ->
        HandoverProof.CourierShipment(
            carrier = body.string("carrier").orEmpty(),
            trackingNumber = body.string("trackingNumber").orEmpty(),
            chargeableWeightKg = weight,
            evidencePhotoKey = body.string("handoverPhotoKey").orEmpty()
        )
    }
    else -> null
}

private suspend fun ApplicationCall.requireFulfillmentTenant(): TenantContext? {
    val ctx = tenantContextOrNull
    if (ctx == null) respond(HttpStatusCode.BadRequest, "Konteks tenant tidak ditemukan")
    return ctx
}

private suspend fun ApplicationCall.fulfillmentBody(): JsonValue.Obj? =
    runCatching { JsonParser.parse(receiveText()) as? JsonValue.Obj }.getOrNull()

private suspend fun ApplicationCall.respondJson(json: String) =
    respondText(text = json, contentType = io.ktor.http.ContentType.Application.Json)

/** Aksi kerja (ajukan, terima, upload): OPERATE ke atas. */
private suspend fun ApplicationCall.canOperateFulfillment(
    tenant: TenantContext,
    roleRepository: RoleRepository?
): Boolean = hasFulfillmentAccess(tenant, roleRepository, AccessLevel.OPERATE)

/** Gerbang keputusan (ACC/tolak): MANAGE saja. */
private suspend fun ApplicationCall.canManageFulfillment(
    tenant: TenantContext,
    roleRepository: RoleRepository?
): Boolean = hasFulfillmentAccess(tenant, roleRepository, AccessLevel.MANAGE)

private suspend fun ApplicationCall.hasFulfillmentAccess(
    tenant: TenantContext,
    roleRepository: RoleRepository?,
    minimum: AccessLevel
): Boolean {
    val principal = callerPrincipalOrNull ?: return false
    if (principal.isPlatformSuperadmin) return true
    if (minimum == AccessLevel.MANAGE && principal.role.defaultPermissions.contains(Permission.APPROVE_COSTING)) {
        return true
    }
    val customRoleId = principal.customRoleId ?: return false
    val role = roleRepository?.findById(tenant.tenantId, RoleId(customRoleId)) ?: return false
    return role.hasAccess(GarmentModules.FULFILLMENT, minimum) || role.isSystemOwnerRole
}
