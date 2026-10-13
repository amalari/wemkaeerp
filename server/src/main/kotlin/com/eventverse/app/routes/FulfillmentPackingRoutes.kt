package com.eventverse.app.routes

import com.eventverse.app.domain.fulfillment.carton.CartonId
import com.eventverse.app.domain.fulfillment.carton.CartonNumber
import com.eventverse.app.domain.fulfillment.carton.CartonRepository
import com.eventverse.app.domain.fulfillment.carton.CartonStatus
import com.eventverse.app.domain.fulfillment.carton.PackingCarton
import com.eventverse.app.domain.fulfillment.carton.SealNumber
import com.eventverse.app.domain.fulfillment.shipment.CarrierInfo
import com.eventverse.app.domain.fulfillment.shipment.DestinationInfo
import com.eventverse.app.domain.fulfillment.shipment.DispatchShipment
import com.eventverse.app.domain.fulfillment.shipment.ShipmentId
import com.eventverse.app.domain.fulfillment.shipment.ShipmentNumber
import com.eventverse.app.domain.fulfillment.shipment.ShipmentRepository
import com.eventverse.app.domain.fulfillment.shipment.ShipmentStatus
import com.eventverse.app.domain.fulfillment.shipment.StockOwnership
import com.eventverse.app.domain.i18n.ServerErrorCode
import com.eventverse.app.domain.rbac.AccessLevel
import com.eventverse.app.domain.rbac.RoleId
import com.eventverse.app.domain.rbac.RoleRepository
import com.eventverse.app.domain.tenant.TenantContext
import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.plugins.callerPrincipalOrNull
import com.eventverse.app.plugins.tenantContextOrNull
import com.eventverse.app.shared.fulfillment.PackingCartonCodec
import com.eventverse.app.shared.fulfillment.DispatchShipmentCodec
import com.eventverse.app.shared.json.JsonValue
import com.eventverse.app.shared.json.jsonArrayOf
import com.eventverse.app.shared.json.jsonObjectOf
import com.eventverse.app.shared.json.jsonOf
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.patch
import io.ktor.server.routing.delete
import io.ktor.server.routing.route
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Rute packing karton & pengiriman eksternal (TRD-FUL-001 Track B).
 *
 * Gerbang wewenang: VIEW (baca), OPERATE ( buat/ seal/ in-transit/ delivered),
 * MANAGE (reopen/ cancel).
 */
fun Route.fulfillmentPackingRoutes(
    cartonRepository: CartonRepository,
    shipmentRepository: ShipmentRepository,
    roleRepository: RoleRepository? = null
) {
    route("/api/tenant/fulfillment") {

        // GET /packing-queue - VIEW level
        get("/packing-queue") {
            val tenant = call.requireFulfillmentTenant() ?: return@get

            // Ambil karton OPEN dan shipment aktif
            val openCartons = cartonRepository.findOpenCartons(tenant.tenantId)
            val activeShipments = shipmentRepository.findActiveShipments(tenant.tenantId)

            val response = jsonObjectOf(
                "cartons" to jsonArrayOf(openCartons.map { PackingCartonCodec.encode(it) }),
                "shipments" to jsonArrayOf(activeShipments.map { DispatchShipmentCodec.encode(it, emptyList()) })
            )

            call.respondJson(response.encode())
        }

        // POST /cartons - OPERATE level
        post("/cartons") {
            val tenant = call.requireFulfillmentTenant() ?: return@post
            if (!call.canOperateFulfillment(tenant, roleRepository)) {
                return@post call.respondError(
                    HttpStatusCode.Forbidden, ServerErrorCode.FORBIDDEN,
                    "Akses ditolak: membuat karton butuh wewenang kerja modul FULFILLMENT"
                )
            }

            val body = call.fulfillmentBody()
                ?: return@post call.respondError(HttpStatusCode.BadRequest, ServerErrorCode.INVALID_BODY, "Body JSON tidak terbaca")

            val productionRef = body.string("productionRef").orEmpty()
            if (productionRef.isBlank()) {
                return@post call.respondError(HttpStatusCode.BadRequest, ServerErrorCode.VALIDATION_ERROR, "Referensi produksi wajib diisi")
            }

            // Generate carton number
            val nowForCarton = Clock.System.now()
            val year = nowForCarton.toLocalDateTime(TimeZone.UTC).year
            val sequence = cartonRepository.getNextCartonSequence(tenant.tenantId, year)
            val cartonNumber = CartonNumber.generate(year, sequence)

            val carton = PackingCarton(
                id = CartonId("ctn-${nowForCarton.toEpochMilliseconds()}"),
                tenantId = tenant.tenantId,
                productionRef = productionRef,
                cartonNumber = cartonNumber,
                itemBreakdown = emptyMap(),
                quantityPcs = 0,
                grossWeightKg = 0.0,
                unitTicketIds = emptyList(),
                status = CartonStatus.OPEN,
                createdAt = nowForCarton,
                updatedAt = nowForCarton,
                notes = body.string("notes").orEmpty()
            )

            try {
                val saved = cartonRepository.save(carton)
                call.respondJson(PackingCartonCodec.encode(saved).encode())
            } catch (e: Exception) {
                call.respondError(HttpStatusCode.Conflict, ServerErrorCode.CONFLICT, e.message ?: "Gagal membuat karton")
            }
        }

            // PATCH /cartons/{id}/seal - OPERATE level
        patch("/cartons/{id}/seal") {
            val tenant = call.requireFulfillmentTenant() ?: return@patch
            if (!call.canOperateFulfillment(tenant, roleRepository)) {
                return@patch call.respondError(
                    HttpStatusCode.Forbidden, ServerErrorCode.FORBIDDEN,
                    "Akses ditolak: menyegel karton butuh wewenang kerja modul FULFILLMENT"
                )
            }

            val cartonId = CartonId(call.parameters["id"].orEmpty())
            val carton = cartonRepository.findById(tenant.tenantId, cartonId)
                ?: return@patch call.respondError(HttpStatusCode.NotFound, ServerErrorCode.NOT_FOUND, "Karton tidak ditemukan")

            val body = call.fulfillmentBody()
                ?: return@patch call.respondError(HttpStatusCode.BadRequest, ServerErrorCode.INVALID_BODY, "Body JSON tidak terbaca")

            val sealNumberValue = body.string("sealNumber")
            if (sealNumberValue.isNullOrBlank()) {
                return@patch call.respondError(HttpStatusCode.BadRequest, ServerErrorCode.VALIDATION_ERROR, "Nomor seal wajib diisi")
            }

            val grossWeight = body.double("grossWeightKg")
            if (grossWeight == null || grossWeight <= 0) {
                return@patch call.respondError(HttpStatusCode.BadRequest, ServerErrorCode.VALIDATION_ERROR, "Berat kotor wajib lebih dari 0")
            }

            val actor = body.string("actor").orEmpty().ifBlank { "system" }

            try {
                val sealed = carton.seal(SealNumber.generate(), grossWeight, actor)
                val saved = cartonRepository.save(sealed)

                // Record event
                cartonRepository.recordEvent(
                    tenantId = tenant.tenantId,
                    cartonId = saved.id,
                    eventType = "SEALED",
                    actor = actor,
                    detail = "Carton sealed with number ${saved.sealNumber?.value}",
                    occurredAt = Clock.System.now()
                )

                call.respondJson(PackingCartonCodec.encode(saved).encode())
            } catch (e: IllegalStateException) {
                call.respondError(HttpStatusCode.Conflict, ServerErrorCode.INVALID_STATE, e.message ?: "Tidak dapat menyegel karton")
            } catch (e: Exception) {
                call.respondError(HttpStatusCode.InternalServerError, ServerErrorCode.INTERNAL_ERROR, e.message ?: "Gagal menyegel karton")
            }
        }

        // PATCH /cartons/{id}/reopen - MANAGE level
        patch("/cartons/{id}/reopen") {
            val tenant = call.requireFulfillmentTenant() ?: return@patch
            if (!call.canManageFulfillment(tenant, roleRepository)) {
                return@patch call.respondError(
                    HttpStatusCode.Forbidden, ServerErrorCode.FORBIDDEN,
                    "Akses ditolak: membuka kembali karton butuh wewenang kelola modul FULFILLMENT"
                )
            }

            val cartonId = CartonId(call.parameters["id"].orEmpty())
            val carton = cartonRepository.findById(tenant.tenantId, cartonId)
                ?: return@patch call.respondError(HttpStatusCode.NotFound, ServerErrorCode.NOT_FOUND, "Karton tidak ditemukan")

            val body = call.fulfillmentBody()
                ?: return@patch call.respondError(HttpStatusCode.BadRequest, ServerErrorCode.INVALID_BODY, "Body JSON tidak terbaca")

            val reason = body.string("reason").orEmpty()
            if (reason.isBlank()) {
                return@patch call.respondError(HttpStatusCode.BadRequest, ServerErrorCode.VALIDATION_ERROR, "Alasan reopen wajib diisi")
            }

            val actor = body.string("actor").orEmpty().ifBlank { "system" }
            val now = Clock.System.now()

            try {
                val reopened = carton.reopen(reason, actor, now)
                val saved = cartonRepository.save(reopened)

                // Record event
                cartonRepository.recordEvent(
                    tenantId = tenant.tenantId,
                    cartonId = saved.id,
                    eventType = "REOPENED",
                    actor = actor,
                    detail = reason,
                    occurredAt = now
                )

                call.respondJson(PackingCartonCodec.encode(saved).encode())
            } catch (e: IllegalStateException) {
                call.respondError(HttpStatusCode.Conflict, ServerErrorCode.INVALID_STATE, e.message ?: "Tidak dapat membuka kembali karton")
            } catch (e: Exception) {
                call.respondError(HttpStatusCode.InternalServerError, ServerErrorCode.INTERNAL_ERROR, e.message ?: "Gagal membuka kembali karton")
            }
        }

        // POST /shipments - OPERATE level
        post("/shipments") {
            val tenant = call.requireFulfillmentTenant() ?: return@post
            if (!call.canOperateFulfillment(tenant, roleRepository)) {
                return@post call.respondError(
                    HttpStatusCode.Forbidden, ServerErrorCode.FORBIDDEN,
                    "Akses ditolak: menerbitkan pengiriman butuh wewenang kerja modul FULFILLMENT"
                )
            }

            val body = call.fulfillmentBody()
                ?: return@post call.respondError(HttpStatusCode.BadRequest, ServerErrorCode.INVALID_BODY, "Body JSON tidak terbaca")

            val stockOwnershipRaw = body.string("stockOwnership").orEmpty()
            val stockOwnership = try {
                StockOwnership.valueOf(stockOwnershipRaw.uppercase())
            } catch (e: Exception) {
                return@post call.respondError(HttpStatusCode.BadRequest, ServerErrorCode.VALIDATION_ERROR, "Stock ownership tidak sah: $stockOwnershipRaw")
            }

            // Parse carrier info
            val carrierObj = body.obj("carrier")
            val carrier = CarrierInfo(
                mode = carrierObj?.string("mode").orEmpty(),
                expedition = carrierObj?.string("expedition").orEmpty(),
                container = carrierObj?.string("container").orEmpty(),
                driver = carrierObj?.string("driver").orEmpty(),
                trackingNumber = carrierObj?.string("trackingNumber").orEmpty()
            )

            // Parse destination info
            val destObj = body.obj("destination")
            val destination = DestinationInfo(
                buyer = destObj?.string("buyer").orEmpty(),
                address = destObj?.string("address").orEmpty(),
                contact = destObj?.string("contact").orEmpty(),
                city = destObj?.string("city").orEmpty()
            )

            if (carrier.expedition.isBlank() || destination.buyer.isBlank()) {
                return@post call.respondError(HttpStatusCode.BadRequest, ServerErrorCode.VALIDATION_ERROR, "Ekspedisi dan buyer wajib diisi")
            }

            // Generate shipment number
            val nowForShipment = Clock.System.now()
            val year = nowForShipment.toLocalDateTime(TimeZone.UTC).year
            val sequence = shipmentRepository.getNextShipmentSequence(tenant.tenantId, year)
            val shipmentNumber = ShipmentNumber.generate(year, sequence)

            val shipment = DispatchShipment(
                id = ShipmentId("sj-${nowForShipment.toEpochMilliseconds()}"),
                tenantId = tenant.tenantId,
                shipmentNumber = shipmentNumber,
                stockOwnership = stockOwnership,
                carrier = carrier,
                destination = destination,
                status = ShipmentStatus.ISSUED,
                createdAt = nowForShipment,
                updatedAt = nowForShipment,
                notes = body.string("notes").orEmpty()
            )

            try {
                val saved = shipmentRepository.save(shipment)

                // Link cartons if provided
                val cartonIdsRaw = body.stringArray("cartonIds")
                if (cartonIdsRaw.isNotEmpty()) {
                    val cartonIds = cartonIdsRaw.map { CartonId(it) }
                    shipmentRepository.linkCartons(tenant.tenantId, saved.id, cartonIds)
                }

                call.respondJson(DispatchShipmentCodec.encode(saved, emptyList()).encode())
            } catch (e: Exception) {
                call.respondError(HttpStatusCode.Conflict, ServerErrorCode.CONFLICT, e.message ?: "Gagal menerbitkan pengiriman")
            }
        }

        // PATCH /shipments/{id}/in-transit - OPERATE level
        patch("/shipments/{id}/in-transit") {
            val tenant = call.requireFulfillmentTenant() ?: return@patch
            if (!call.canOperateFulfillment(tenant, roleRepository)) {
                return@patch call.respondError(
                    HttpStatusCode.Forbidden, ServerErrorCode.FORBIDDEN,
                    "Akses ditolak: menandai pengiriman butuh wewenang kerja modul FULFILLMENT"
                )
            }

            val shipmentId = ShipmentId(call.parameters["id"].orEmpty())
            val shipment = shipmentRepository.findById(tenant.tenantId, shipmentId)
                ?: return@patch call.respondError(HttpStatusCode.NotFound, ServerErrorCode.NOT_FOUND, "Pengiriman tidak ditemukan")

            val body = call.fulfillmentBody()
                ?: return@patch call.respondError(HttpStatusCode.BadRequest, ServerErrorCode.INVALID_BODY, "Body JSON tidak terbaca")

            val actor = body.string("actor").orEmpty().ifBlank { "system" }
            val now = Clock.System.now()

            try {
                val inTransit = shipment.markInTransit(now)
                val saved = shipmentRepository.save(inTransit)

                // Record event
                shipmentRepository.recordEvent(
                    tenantId = tenant.tenantId,
                    shipmentId = saved.id,
                    eventType = "IN_TRANSIT",
                    actor = actor,
                    detail = "Shipment marked as in transit",
                    occurredAt = now
                )

                call.respondJson(DispatchShipmentCodec.encode(saved, emptyList()).encode())
            } catch (e: IllegalStateException) {
                call.respondError(HttpStatusCode.Conflict, ServerErrorCode.INVALID_STATE, e.message ?: "Tidak dapat menandai pengiriman")
            } catch (e: Exception) {
                call.respondError(HttpStatusCode.InternalServerError, ServerErrorCode.INTERNAL_ERROR, e.message ?: "Gagal menandai pengiriman")
            }
        }

        // PATCH /shipments/{id}/delivered - OPERATE level
        patch("/shipments/{id}/delivered") {
            val tenant = call.requireFulfillmentTenant() ?: return@patch
            if (!call.canOperateFulfillment(tenant, roleRepository)) {
                return@patch call.respondError(
                    HttpStatusCode.Forbidden, ServerErrorCode.FORBIDDEN,
                    "Akses ditolak: mengonfirmasi penerimaan butuh wewenang kerja modul FULFILLMENT"
                )
            }

            val shipmentId = ShipmentId(call.parameters["id"].orEmpty())
            val shipment = shipmentRepository.findById(tenant.tenantId, shipmentId)
                ?: return@patch call.respondError(HttpStatusCode.NotFound, ServerErrorCode.NOT_FOUND, "Pengiriman tidak ditemukan")

            val body = call.fulfillmentBody()
                ?: return@patch call.respondError(HttpStatusCode.BadRequest, ServerErrorCode.INVALID_BODY, "Body JSON tidak terbaca")

            val actor = body.string("actor").orEmpty().ifBlank { "system" }
            val now = Clock.System.now()

            try {
                val delivered = shipment.confirmDelivered(now, actor)
                val saved = shipmentRepository.save(delivered)

                // Record event
                shipmentRepository.recordEvent(
                    tenantId = tenant.tenantId,
                    shipmentId = saved.id,
                    eventType = "DELIVERED",
                    actor = actor,
                    detail = "Shipment confirmed as delivered",
                    occurredAt = now
                )

                call.respondJson(DispatchShipmentCodec.encode(saved, emptyList()).encode())
            } catch (e: IllegalStateException) {
                call.respondError(HttpStatusCode.Conflict, ServerErrorCode.INVALID_STATE, e.message ?: "Tidak dapat mengonfirmasi pengiriman")
            } catch (e: Exception) {
                call.respondError(HttpStatusCode.InternalServerError, ServerErrorCode.INTERNAL_ERROR, e.message ?: "Gagal mengonfirmasi pengiriman")
            }
        }

        // DELETE /shipments/{id} - MANAGE level (cancel)
        delete("/shipments/{id}") {
            val tenant = call.requireFulfillmentTenant() ?: return@delete
            if (!call.canManageFulfillment(tenant, roleRepository)) {
                return@delete call.respondError(
                    HttpStatusCode.Forbidden, ServerErrorCode.FORBIDDEN,
                    "Akses ditolak: membatalkan pengiriman butuh wewenang kelola modul FULFILLMENT"
                )
            }

            val shipmentId = ShipmentId(call.parameters["id"].orEmpty())
            val shipment = shipmentRepository.findById(tenant.tenantId, shipmentId)
                ?: return@delete call.respondError(HttpStatusCode.NotFound, ServerErrorCode.NOT_FOUND, "Pengiriman tidak ditemukan")

            val body = call.fulfillmentBody()
                ?: return@delete call.respondError(HttpStatusCode.BadRequest, ServerErrorCode.INVALID_BODY, "Body JSON tidak terbaca")

            val reason = body.string("reason").orEmpty()
            if (reason.isBlank()) {
                return@delete call.respondError(HttpStatusCode.BadRequest, ServerErrorCode.VALIDATION_ERROR, "Alasan pembatalan wajib diisi")
            }

            val actor = body.string("actor").orEmpty().ifBlank { "system" }
            val now = Clock.System.now()

            try {
                val cancelled = shipment.cancel(actor, reason, now)
                val saved = shipmentRepository.save(cancelled)

                // Note: cartons remain linked for audit trail; they can be reused in new shipments

                // Record event
                shipmentRepository.recordEvent(
                    tenantId = tenant.tenantId,
                    shipmentId = saved.id,
                    eventType = "CANCELLED",
                    actor = actor,
                    detail = reason,
                    occurredAt = now
                )

                call.respondJson(DispatchShipmentCodec.encode(saved, emptyList()).encode())
            } catch (e: IllegalStateException) {
                call.respondError(HttpStatusCode.Conflict, ServerErrorCode.INVALID_STATE, e.message ?: "Tidak dapat membatalkan pengiriman")
            } catch (e: Exception) {
                call.respondError(HttpStatusCode.InternalServerError, ServerErrorCode.INTERNAL_ERROR, e.message ?: "Gagal membatalkan pengiriman")
            }
        }
    }
}

// Helper functions

private suspend fun ApplicationCall.requireFulfillmentTenant(): TenantContext? {
    val ctx = tenantContextOrNull
    if (ctx == null) respondError(HttpStatusCode.BadRequest, ServerErrorCode.NO_TENANT_CONTEXT, "Konteks tenant tidak ditemukan")
    return ctx
}

private suspend fun ApplicationCall.fulfillmentBody(): JsonValue.Obj? =
    runCatching { com.eventverse.app.shared.json.JsonParser.parse(receiveText()) as? JsonValue.Obj }.getOrNull()

private suspend fun ApplicationCall.respondJson(json: String) =
    respondText(text = json, contentType = io.ktor.http.ContentType.Application.Json)

/** Aksi kerja (seal, create, in-transit, delivered): OPERATE ke atas. */
private suspend fun ApplicationCall.canOperateFulfillment(
    tenant: TenantContext,
    roleRepository: RoleRepository?
): Boolean = hasFulfillmentAccess(tenant, roleRepository, AccessLevel.OPERATE)

/** Gerbang keputusan (reopen, cancel): MANAGE saja. */
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

    val customRoleId = principal.customRoleId ?: return false
    val role = roleRepository?.findById(tenant.tenantId, RoleId(customRoleId)) ?: return false
    return role.hasAccess(com.eventverse.app.domain.pack.GarmentModules.FULFILLMENT, minimum) || role.isSystemOwnerRole
}
