package com.eventverse.app.routes

import com.eventverse.app.domain.tenant.TenantContext
import com.eventverse.app.domain.transfer.CartonId
import com.eventverse.app.domain.transfer.LocationId
import com.eventverse.app.domain.transfer.SuratJalanId
import com.eventverse.app.domain.transfer.SuratJalanNumber
import com.eventverse.app.domain.transfer.SuratJalanRepository
import com.eventverse.app.domain.transfer.TransferType
import com.eventverse.app.domain.transfer.usecases.CreateInternalTransferCommand
import com.eventverse.app.domain.transfer.usecases.CreateInternalTransferSuratJalanUseCase
import com.eventverse.app.domain.transfer.usecases.CreateMakloonOutboundCommand
import com.eventverse.app.domain.transfer.usecases.CreateMakloonOutboundSuratJalanUseCase
import com.eventverse.app.domain.transfer.usecases.CreatePartialCustomerShipmentCommand
import com.eventverse.app.domain.transfer.usecases.CreatePartialCustomerShipmentUseCase
import com.eventverse.app.domain.transfer.usecases.CustomerDispatchCartonInput
import com.eventverse.app.domain.transfer.usecases.ReceiveSuratJalanCommand
import com.eventverse.app.domain.transfer.usecases.ReceiveSuratJalanUseCase
import com.eventverse.app.domain.workqueue.WorkCardId
import com.eventverse.app.domain.workqueue.WorkCardRepository
import com.eventverse.app.domain.workqueue.WorkSubjectKind
import com.eventverse.app.domain.workqueue.WorkSubjectRef
import com.eventverse.app.plugins.tenantContextOrNull
import com.eventverse.app.shared.json.JsonParser
import com.eventverse.app.shared.json.JsonValue
import com.eventverse.app.shared.json.jsonArrayOf
import com.eventverse.app.shared.json.jsonObjectOf
import com.eventverse.app.shared.json.jsonOf
import com.eventverse.app.shared.transfer.SuratJalanCodec
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
import kotlinx.datetime.LocalDate

fun Route.suratJalanRoutes(
    suratJalanRepository: SuratJalanRepository,
    cardRepository: WorkCardRepository
) {
    val internalTransferUseCase = CreateInternalTransferSuratJalanUseCase(cardRepository, suratJalanRepository)
    val makloonOutboundUseCase = CreateMakloonOutboundSuratJalanUseCase(cardRepository, suratJalanRepository)
    val customerShipmentUseCase = CreatePartialCustomerShipmentUseCase(suratJalanRepository)
    val receiveUseCase = ReceiveSuratJalanUseCase(suratJalanRepository, cardRepository)

    route("/api/tenant/transfers/manifests") {

        // GET — daftar surat jalan (bisa filter ?type=INTERNAL_SITE_TRANSFER / SUBCONTRACT_OUTBOUND / CUSTOMER_DISPATCH)
        get {
            val tenant = call.requireSuratJalanTenant() ?: return@get
            val typeParam = call.request.queryParameters["type"]
            val transferType = typeParam?.let { runCatching { TransferType.valueOf(it) }.getOrNull() }

            val manifests = suratJalanRepository.findByTenant(tenant.tenantId.value, transferType)
            val json = jsonArrayOf(manifests.map(SuratJalanCodec::encode)).encode()
            call.respondSuratJalanJson(json)
        }

        // GET — detail surat jalan beserta rincian barang/bundle/kardus
        get("/{id}") {
            val tenant = call.requireSuratJalanTenant() ?: return@get
            val idParam = call.parameters["id"] ?: return@get call.respondSuratJalanError(
                HttpStatusCode.BadRequest, "Missing manifest id"
            )

            val manifest = suratJalanRepository.findById(SuratJalanId(idParam))
            if (manifest == null || manifest.tenantId != tenant.tenantId.value) {
                call.respondSuratJalanError(HttpStatusCode.NotFound, "Surat Jalan not found")
                return@get
            }

            call.respondSuratJalanJson(SuratJalanCodec.encode(manifest).encode())
        }

        // POST — terbitkan Surat Jalan Mutasi Internal (mempertahankan tiket bundle individual)
        post("/internal") {
            val tenant = call.requireSuratJalanTenant() ?: return@post
            val body = call.parseBody() ?: return@post call.respondSuratJalanError(
                HttpStatusCode.BadRequest, "Invalid JSON body"
            )

            val cardIds = body.stringArray("cardIds").map(::WorkCardId)
            val now = Clock.System.now()

            val command = CreateInternalTransferCommand(
                tenantId = tenant.tenantId.value,
                sjNumber = SuratJalanNumber(body.string("sjNumber") ?: "SJ-INT-${now.toEpochMilliseconds()}"),
                subject = WorkSubjectRef(
                    kind = WorkSubjectKind.valueOf(body.string("subjectKind") ?: WorkSubjectKind.BULK_WORK_ORDER.name),
                    subjectId = body.string("subjectId") ?: "",
                    orderNumber = body.string("orderNumber") ?: "",
                    articleName = body.string("articleName") ?: ""
                ),
                originLocationId = LocationId(body.string("originLocationId") ?: ""),
                destinationLocationId = LocationId(body.string("destinationLocationId") ?: ""),
                cardIdsToTransfer = cardIds,
                carrierName = body.string("carrierName"),
                driverName = body.string("driverName"),
                vehiclePlate = body.string("vehiclePlate"),
                notes = body.string("notes") ?: "",
                now = now
            )

            internalTransferUseCase(command)
                .onSuccess { manifest ->
                    call.respondSuratJalanJson(SuratJalanCodec.encode(manifest).encode())
                }
                .onFailure { err ->
                    call.respondSuratJalanError(HttpStatusCode.BadRequest, err.message ?: "Failed to create internal transfer")
                }
        }

        // POST — terbitkan Surat Jalan Makloon Outbound (melebur bundle menjadi lot masal ber-SLA & tarif)
        post("/makloon-outbound") {
            val tenant = call.requireSuratJalanTenant() ?: return@post
            val body = call.parseBody() ?: return@post call.respondSuratJalanError(
                HttpStatusCode.BadRequest, "Invalid JSON body"
            )

            val cardIds = body.stringArray("cardIds").map(::WorkCardId)
            val now = Clock.System.now()
            val expectedDateStr = body.string("expectedReturnDate")
            val expectedDate = expectedDateStr?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                ?: error("Valid expectedReturnDate (YYYY-MM-DD) is required for makloon transfer")

            val command = CreateMakloonOutboundCommand(
                tenantId = tenant.tenantId.value,
                sjNumber = SuratJalanNumber(body.string("sjNumber") ?: "SJ-MAK-${now.toEpochMilliseconds()}"),
                subject = WorkSubjectRef(
                    kind = WorkSubjectKind.valueOf(body.string("subjectKind") ?: WorkSubjectKind.BULK_WORK_ORDER.name),
                    subjectId = body.string("subjectId") ?: "",
                    orderNumber = body.string("orderNumber") ?: "",
                    articleName = body.string("articleName") ?: ""
                ),
                vendorRef = body.string("vendorRef") ?: "",
                cardIdsToSubcontract = cardIds,
                unitServiceFeeIdr = body.long("unitServiceFeeIdr") ?: 0L,
                expectedReturnDate = expectedDate,
                carrierName = body.string("carrierName"),
                driverName = body.string("driverName"),
                vehiclePlate = body.string("vehiclePlate"),
                notes = body.string("notes") ?: "",
                now = now
            )

            makloonOutboundUseCase(command)
                .onSuccess { manifest ->
                    call.respondSuratJalanJson(SuratJalanCodec.encode(manifest).encode())
                }
                .onFailure { err ->
                    call.respondSuratJalanError(HttpStatusCode.BadRequest, err.message ?: "Failed to create makloon transfer")
                }
        }

        // POST — terbitkan Surat Jalan Pengiriman ke Buyer (partial shipment berdasar dus & tracking backlog)
        post("/customer-dispatch") {
            val tenant = call.requireSuratJalanTenant() ?: return@post
            val body = call.parseBody() ?: return@post call.respondSuratJalanError(
                HttpStatusCode.BadRequest, "Invalid JSON body"
            )

            val cartons = body.objectArray("cartons").map { c ->
                CustomerDispatchCartonInput(
                    cartonId = CartonId(c.string("cartonId") ?: ""),
                    sizeLabel = c.string("sizeLabel") ?: "",
                    colorway = c.string("colorway") ?: "",
                    qtyPcs = c.int("qtyPcs") ?: 0,
                    notes = c.string("notes") ?: ""
                )
            }
            val now = Clock.System.now()

            val command = CreatePartialCustomerShipmentCommand(
                tenantId = tenant.tenantId.value,
                sjNumber = SuratJalanNumber(body.string("sjNumber") ?: "SJ-CUS-${now.toEpochMilliseconds()}"),
                subject = WorkSubjectRef(
                    kind = WorkSubjectKind.valueOf(body.string("subjectKind") ?: WorkSubjectKind.BULK_WORK_ORDER.name),
                    subjectId = body.string("subjectId") ?: "",
                    orderNumber = body.string("orderNumber") ?: "",
                    articleName = body.string("articleName") ?: ""
                ),
                customerName = body.string("customerName") ?: "",
                customerAddress = body.string("customerAddress") ?: "",
                totalOrderedPcs = body.int("totalOrderedPcs") ?: 0,
                previouslyShippedPcs = body.int("previouslyShippedPcs") ?: 0,
                cartons = cartons,
                carrierName = body.string("carrierName"),
                driverName = body.string("driverName"),
                vehiclePlate = body.string("vehiclePlate"),
                notes = body.string("notes") ?: "",
                now = now
            )

            customerShipmentUseCase(command)
                .onSuccess { result ->
                    val respObj = jsonObjectOf(
                        "manifest" to SuratJalanCodec.encode(result.manifest),
                        "thisShipmentPcs" to jsonOf(result.thisShipmentPcs),
                        "totalShippedPcs" to jsonOf(result.totalShippedPcs),
                        "totalOrderedPcs" to jsonOf(result.totalOrderedPcs),
                        "remainingBacklogPcs" to jsonOf(result.remainingBacklogPcs),
                        "isFullyShipped" to jsonOf(result.isFullyShipped)
                    )
                    call.respondSuratJalanJson(respObj.encode())
                }
                .onFailure { err ->
                    call.respondSuratJalanError(HttpStatusCode.BadRequest, err.message ?: "Failed to create customer shipment")
                }
        }

        // POST — terima Surat Jalan di tujuan (Gedung baru / kembali dari vendor)
        post("/{id}/receive") {
            val tenant = call.requireSuratJalanTenant() ?: return@post
            val idParam = call.parameters["id"] ?: return@post call.respondSuratJalanError(
                HttpStatusCode.BadRequest, "Missing manifest id"
            )
            val body = call.parseBody() ?: jsonObjectOf()
            val receiverName = body.string("receiverName") ?: "Staff Gudang"
            val notes = body.string("notes") ?: ""
            val now = Clock.System.now()

            val command = ReceiveSuratJalanCommand(
                manifestId = SuratJalanId(idParam),
                receiverName = receiverName,
                notes = notes,
                now = now
            )

            receiveUseCase(command)
                .onSuccess { manifest ->
                    call.respondSuratJalanJson(SuratJalanCodec.encode(manifest).encode())
                }
                .onFailure { err ->
                    call.respondSuratJalanError(HttpStatusCode.BadRequest, err.message ?: "Failed to receive surat jalan")
                }
        }
    }
}

private suspend fun ApplicationCall.requireSuratJalanTenant(): TenantContext? {
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

private suspend fun ApplicationCall.respondSuratJalanJson(json: String) =
    respondText(text = json, contentType = ContentType.Application.Json)

private suspend fun ApplicationCall.respondSuratJalanError(status: HttpStatusCode, message: String) =
    respondText(
        text = jsonObjectOf("error" to jsonOf(message)).encode(),
        status = status,
        contentType = ContentType.Application.Json
    )
