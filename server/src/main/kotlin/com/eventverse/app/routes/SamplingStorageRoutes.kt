package com.eventverse.app.routes

import com.eventverse.app.domain.process.TenantProcessCatalogRepository
import com.eventverse.app.domain.sampling.SamplingOrder
import com.eventverse.app.domain.sampling.SamplingOrderId
import com.eventverse.app.domain.sampling.SamplingOrderRepository
import com.eventverse.app.domain.sampling.storage.SampleStorageRecordRepository
import com.eventverse.app.domain.sampling.storage.SampleStorageStatus
import com.eventverse.app.domain.sampling.storage.StorageCustodian
import com.eventverse.app.domain.sampling.storage.StorageLocationLabel
import com.eventverse.app.domain.sampling.storage.dealStorageReadiness
import com.eventverse.app.domain.sampling.usecases.AdvanceSamplingStageUseCase
import com.eventverse.app.domain.sampling.usecases.ReleaseSampleFromStorageCommand
import com.eventverse.app.domain.sampling.usecases.ReleaseSampleFromStorageUseCase
import com.eventverse.app.domain.sampling.usecases.StoreSampleCommand
import com.eventverse.app.domain.sampling.usecases.StoreSampleUseCase
import com.eventverse.app.domain.transfer.usecases.GetFlowTransferLegsUseCase
import com.eventverse.app.plugins.callerPrincipalOrNull
import com.eventverse.app.plugins.tenantContextOrNull
import com.eventverse.app.shared.json.JsonParser
import com.eventverse.app.shared.json.JsonValue
import com.eventverse.app.shared.json.jsonObjectOf
import com.eventverse.app.shared.sampling.SampleStorageCodec
import com.eventverse.app.shared.sampling.SamplingOrderCodec
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import kotlinx.datetime.Clock

/**
 * Kustodi penyimpanan sampel: masuk dari pengemasan (`/store`) dan keluar ke buyer (`/release`).
 *
 * Dipasang dari dalam blok `route("/api/tenant/sampling/orders")` milik [samplingRoutes].
 * Penerima simpan dan PIC kirim diambil dari JWT, bukan dari body — orang yang menekan
 * tombolnya adalah orang yang bertanggung jawab, dan itu tidak boleh bisa dipalsukan klien.
 */
fun Route.samplingStorageRoutes(
    repository: SamplingOrderRepository,
    storageRepository: SampleStorageRecordRepository,
    processCatalogRepository: TenantProcessCatalogRepository? = null,
    flowLegsUseCase: GetFlowTransferLegsUseCase? = null
) {
    val advance = AdvanceSamplingStageUseCase(flowLegsUseCase)
    val storeUseCase = StoreSampleUseCase(repository, storageRepository, advance)
    val releaseUseCase = ReleaseSampleFromStorageUseCase(repository, storageRepository, advance)

    // GET /api/tenant/sampling/orders/{id}/storage — kustodi + kelengkapan deal
    get("/{id}/storage") {
        val order = call.loadTenantOrder(repository) ?: return@get
        val record = storageRepository.findLatestByOrderId(order.tenantId, order.id)
        call.respondStorageJson(SampleStorageCodec.encodeStatus(storageStatusOf(order, record, repository)))
    }

    // POST /api/tenant/sampling/orders/{id}/store — Pengemasan -> Penyimpanan
    post("/{id}/store") {
        val order = call.loadTenantOrder(repository) ?: return@post
        val json = JsonParser.parse(call.receiveText()) as? JsonValue.Obj
        try {
            val stored = storeUseCase(
                StoreSampleCommand(
                    order = order,
                    location = StorageLocationLabel(json?.string("locationLabel").orEmpty().trim()),
                    qtyPcs = json?.int("qtyPcs") ?: order.sampleQuantity,
                    custodian = call.custodian(),
                    actorRole = call.callerPrincipalOrNull?.role?.name ?: "UNKNOWN",
                    processes = order.effectiveProcesses(processCatalogRepository),
                    now = Clock.System.now()
                )
            ).getOrThrow()
            call.respondStorageJson(
                jsonObjectOf(
                    "order" to SamplingOrderCodec.encode(stored.order),
                    "storage" to SampleStorageCodec.encodeStatus(storageStatusOf(stored.order, stored.record, repository))
                )
            )
        } catch (e: IllegalArgumentException) {
            call.respond(HttpStatusCode.UnprocessableEntity, e.message ?: "Gagal menyimpan barang")
        }
    }

    // POST /api/tenant/sampling/orders/{id}/release — Penyimpanan -> Terkirim (Tunggu ACC)
    post("/{id}/release") {
        val order = call.loadTenantOrder(repository) ?: return@post
        val json = JsonParser.parse(call.receiveText()) as? JsonValue.Obj
        try {
            val released = releaseUseCase(
                ReleaseSampleFromStorageCommand(
                    order = order,
                    pic = call.custodian(),
                    actorRole = call.callerPrincipalOrNull?.role?.name ?: "UNKNOWN",
                    partialReason = json?.string("partialReason"),
                    processes = order.effectiveProcesses(processCatalogRepository),
                    now = Clock.System.now()
                )
            ).getOrThrow()
            call.respondStorageJson(
                jsonObjectOf(
                    "order" to SamplingOrderCodec.encode(released.order),
                    "storage" to SampleStorageCodec.encodeStatus(storageStatusOf(released.order, released.record, repository))
                )
            )
        } catch (e: IllegalArgumentException) {
            call.respond(HttpStatusCode.UnprocessableEntity, e.message ?: "Gagal melepas barang")
        }
    }
}

private suspend fun storageStatusOf(
    order: SamplingOrder,
    record: com.eventverse.app.domain.sampling.storage.SampleStorageRecord?,
    repository: SamplingOrderRepository
): SampleStorageStatus {
    val siblings = order.dealId?.takeIf { it.isNotBlank() }
        ?.let { repository.findByDealId(order.tenantId, it) }
        ?: listOf(order)
    return SampleStorageStatus.of(record, dealStorageReadiness(siblings))
}

/** Order milik tenant pemanggil — order tenant lain dijawab 404, bukan dibocorkan. */
private suspend fun ApplicationCall.loadTenantOrder(repository: SamplingOrderRepository): SamplingOrder? {
    val tenant = tenantContextOrNull
        ?: return null.also { respond(HttpStatusCode.NotFound, "No tenant context found") }
    val id = parameters["id"]
        ?: return null.also { respond(HttpStatusCode.BadRequest, "Missing ID") }
    val order = repository.findById(SamplingOrderId(id))?.takeIf { it.tenantId == tenant.tenantId }
    if (order == null) respond(HttpStatusCode.NotFound, "Sampling order not found")
    return order
}

private fun ApplicationCall.custodian(): StorageCustodian {
    val email = callerPrincipalOrNull?.email?.takeIf { it.isNotBlank() }
        ?: callerPrincipalOrNull?.userId
        ?: "unknown"
    return StorageCustodian(email = email, name = email.substringBefore('@'))
}

private suspend fun SamplingOrder.effectiveProcesses(catalog: TenantProcessCatalogRepository?) =
    customFlowProcesses ?: catalog?.findByTenantId(tenantId)?.processes ?: emptyList()

private suspend fun ApplicationCall.respondStorageJson(json: JsonValue.Obj) {
    respondText(text = json.encode(), contentType = ContentType.Application.Json)
}
