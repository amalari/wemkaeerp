package com.eventverse.app.routes

import com.eventverse.app.domain.process.StagePhaseTags
import com.eventverse.app.domain.process.TenantOptionalProcess
import com.eventverse.app.domain.process.TenantStagePhaseTagsRepository
import com.eventverse.app.domain.process.TenantProcessCatalogRepository
import com.eventverse.app.domain.sampling.SamplingOrder
import com.eventverse.app.domain.sampling.SamplingOrderId
import com.eventverse.app.domain.sampling.SamplingOrderRepository
import com.eventverse.app.domain.sampling.SamplingRoute
import com.eventverse.app.domain.sampling.effectivePhaseTags
import com.eventverse.app.domain.sampling.isFlowLocked
import com.eventverse.app.domain.sampling.customizeProcessFlow
import com.eventverse.app.domain.sampling.resetProcessFlowToDefault
import com.eventverse.app.domain.sampling.withPhaseTags
import com.eventverse.app.domain.tenant.TenantContext
import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.domain.transfer.FlowLegBoard
import com.eventverse.app.domain.transfer.FlowLegStatus
import com.eventverse.app.domain.transfer.FlowLegView
import com.eventverse.app.domain.transfer.usecases.GetFlowTransferLegsQuery
import com.eventverse.app.domain.transfer.usecases.GetFlowTransferLegsUseCase
import com.eventverse.app.plugins.tenantContextOrNull
import com.eventverse.app.shared.json.JsonParser
import com.eventverse.app.shared.json.JsonValue
import com.eventverse.app.shared.json.jsonObjectOf
import com.eventverse.app.shared.json.jsonOf
import com.eventverse.app.shared.process.ProcessCatalogCodec
import com.eventverse.app.shared.process.StagePhaseTagsCodec
import com.eventverse.app.shared.transfer.FlowLegCodec
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.put
import kotlinx.datetime.Clock

/**
 * Rute alur proses per-SPK sampling — dipisahkan dari [samplingRoutes] karena file itu sudah
 * mendekati batas ukuran lapisan server, dan karena alur punya sumbu perubahan sendiri:
 * ia tumbuh mengikuti kebutuhan penyusunan proses, bukan siklus hidup SPK.
 *
 * Dipasang dari dalam blok `route("/api/tenant/sampling/orders")` milik [samplingRoutes],
 * sehingga path-nya tetap `/api/tenant/sampling/orders/{id}/flow`.
 *
 * Pola prototipe yang dijaga di sini: katalog proses tenant adalah **template**, dan
 * `customFlowProcesses` pada SPK adalah **salinan** yang boleh menyimpang. GET mengembalikan
 * yang efektif — salinan bila SPK sudah dikustomisasi, template bila belum.
 */
fun Route.samplingFlowRoutes(
    repository: SamplingOrderRepository,
    processCatalogRepository: TenantProcessCatalogRepository? = null,
    /** Penurunan leg perpindahan barang. `null` mematikan konektor dan penjagaan edit alur. */
    flowLegsUseCase: GetFlowTransferLegsUseCase? = null,
    /** Template tag fase pabrik. `null` = semua desain menjalani Cuci & Setrika di kedua fase. */
    phaseTagsRepository: TenantStagePhaseTagsRepository? = null
) {
    suspend fun templateTags(tenantId: TenantId) = phaseTagsRepository?.findByTenantId(tenantId) ?: StagePhaseTags.DEFAULT

    // GET /api/tenant/sampling/orders/{id}/flow-legs — perpindahan barang yang tersirat di alur
    get("/{id}/flow-legs") {
        val tenant = call.requireFlowTenant() ?: return@get
        val idParam = call.parameters["id"] ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing ID")
        val order = repository.findById(SamplingOrderId(idParam))
            ?: return@get call.respond(HttpStatusCode.NotFound, "Sampling order not found")

        val useCase = flowLegsUseCase
            ?: return@get call.respondFlowJson(FlowLegCodec.encodeBoard(FlowLegBoard()).encode())

        val board = useCase(
            GetFlowTransferLegsQuery(
                tenantId = tenant.tenantId.value,
                subjectId = order.id.value,
                stages = SamplingRoute.DEFAULT_FRAME,
                processes = order.effectiveFlowProcesses(processCatalogRepository, tenant.tenantId),
                skippedStages = order.effectivePhaseTags(templateTags(tenant.tenantId)).skippedSamplingStages,
                customerName = order.clientName
            )
        ).getOrElse { FlowLegBoard() }

        call.respondFlowJson(FlowLegCodec.encodeBoard(board).encode())
    }
    // GET /api/tenant/sampling/orders/{id}/flow — Alur proses efektif untuk SPK/desain
    get("/{id}/flow") {
        val tenant = call.requireFlowTenant() ?: return@get
        val idParam = call.parameters["id"] ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing ID")
        val order = repository.findById(SamplingOrderId(idParam))
            ?: return@get call.respond(HttpStatusCode.NotFound, "Sampling order not found")

        val isCustom = order.isCustomFlow && order.customFlowProcesses != null
        val processes = if (isCustom) {
            order.customFlowProcesses ?: emptyList()
        } else {
            processCatalogRepository?.findByTenantId(tenant.tenantId)?.processes ?: emptyList()
        }

        call.respondFlowJson(flowResponse(order, isCustom, processes, templateTags(tenant.tenantId)).encode())
    }

    // PUT /api/tenant/sampling/orders/{id}/flow/phase-tags — tag [Sampling ×] [Produksi ×] desain ini
    put("/{id}/flow/phase-tags") {
        val tenant = call.requireFlowTenant() ?: return@put
        val idParam = call.parameters["id"] ?: return@put call.respond(HttpStatusCode.BadRequest, "Missing ID")
        val order = repository.findById(SamplingOrderId(idParam))?.takeIf { it.tenantId == tenant.tenantId }
            ?: return@put call.respond(HttpStatusCode.NotFound, "Sampling order not found")
        val body = runCatching { JsonParser.parse(call.receiveText()) as? JsonValue.Obj }.getOrNull()
        val tags = StagePhaseTagsCodec.decode(body?.entries?.get("stagePhaseTags"))
            ?: return@put call.respond(HttpStatusCode.BadRequest, "Missing stagePhaseTags")
        if (order.isFlowLocked) {
            return@put call.respond(HttpStatusCode.UnprocessableEntity, "Alur sudah final sejak Program CAM — tag fase tidak bisa diubah lagi")
        }

        // Tahap yang dilompati tidak lagi punya leg masuk; Surat Jalan yang sudah terbit untuk
        // leg itu akan yatim, jadi ditolak dengan alasan yang sama seperti menggeser chip.
        val processes = order.effectiveFlowProcesses(processCatalogRepository, tenant.tenantId)
        val blocking = flowLegsUseCase?.issuedLegsLostBy(
            tenant.tenantId.value, order, processes, processCatalogRepository,
            currentTags = order.effectivePhaseTags(templateTags(tenant.tenantId)), proposedTags = tags
        )
        if (!blocking.isNullOrEmpty()) {
            return@put call.respond(
                HttpStatusCode.UnprocessableEntity,
                "Tag tidak bisa diubah: Surat Jalan sudah terbit untuk " + blocking.joinToString(", ") { it.leg.summary } + "."
            )
        }

        val updated = repository.save(order.withPhaseTags(tags, Clock.System.now()))
        call.respondFlowJson(flowResponse(updated, updated.isCustomFlow && updated.customFlowProcesses != null, processes, templateTags(tenant.tenantId)).encode())
    }

    // PUT /api/tenant/sampling/orders/{id}/flow — Simpan alur kustom untuk SPK/desain
    put("/{id}/flow") {
        val tenant = call.requireFlowTenant() ?: return@put
        val idParam = call.parameters["id"] ?: return@put call.respond(HttpStatusCode.BadRequest, "Missing ID")
        val order = repository.findById(SamplingOrderId(idParam))
            ?: return@put call.respond(HttpStatusCode.NotFound, "Sampling order not found")

        val body = call.receiveText()
        val json = JsonParser.parse(body)
        val processItems = when (json) {
            is JsonValue.Arr -> json.items
            is JsonValue.Obj -> json.array("processes")
            else -> null
        } ?: return@put call.respond(HttpStatusCode.BadRequest, "Invalid processes JSON payload")

        val processes = ProcessCatalogCodec.decodeProcesses(processItems, tenant.tenantId)

        // Menggeser chip setelah Surat Jalan terbit akan mengubah kunci leg, meninggalkan
        // dokumen yatim — dan, lebih buruk, membuka gerbang yang seharusnya masih tertutup.
        // Menggeser alur tidak boleh menjadi cara melewati gerbangnya.
        val tags = order.effectivePhaseTags(templateTags(tenant.tenantId))
        val blocking = flowLegsUseCase?.issuedLegsLostBy(tenant.tenantId.value, order, processes, processCatalogRepository, tags, tags)
        if (!blocking.isNullOrEmpty()) {
            return@put call.respond(
                HttpStatusCode.UnprocessableEntity,
                "Alur tidak bisa diubah: Surat Jalan sudah terbit untuk " +
                    blocking.joinToString(", ") { it.leg.summary } +
                    ". Batalkan dokumennya lebih dulu bila alur memang harus berubah."
            )
        }

        val updated = repository.save(order.customizeProcessFlow(processes, Clock.System.now()))
        call.respondFlowJson(flowResponse(updated, true, updated.customFlowProcesses.orEmpty(), templateTags(tenant.tenantId)).encode())
    }

    // DELETE /api/tenant/sampling/orders/{id}/flow — Reset alur SPK/desain ke default pabrik
    delete("/{id}/flow") {
        val tenant = call.requireFlowTenant() ?: return@delete
        val idParam = call.parameters["id"] ?: return@delete call.respond(HttpStatusCode.BadRequest, "Missing ID")
        val order = repository.findById(SamplingOrderId(idParam))
            ?: return@delete call.respond(HttpStatusCode.NotFound, "Sampling order not found")

        val updated = repository.save(order.resetProcessFlowToDefault(Clock.System.now()))
        val defaultProcesses = processCatalogRepository?.findByTenantId(tenant.tenantId)?.processes ?: emptyList()
        call.respondFlowJson(flowResponse(updated, false, defaultProcesses, templateTags(tenant.tenantId)).encode())
    }
}

/**
 * Bentuk respons alur. `stagePhaseTags` selalu yang efektif (milik desain, atau template bila
 * belum punya); `hasCustomPhaseTags` membedakan keduanya untuk tombol reset.
 */
private fun flowResponse(
    order: SamplingOrder,
    isCustom: Boolean,
    processes: List<TenantOptionalProcess>,
    template: StagePhaseTags
) = jsonObjectOf(
    "orderId" to jsonOf(order.id.value),
    "isCustomFlow" to jsonOf(isCustom),
    "processes" to ProcessCatalogCodec.encodeProcesses(processes),
    "stagePhaseTags" to StagePhaseTagsCodec.encode(order.effectivePhaseTags(template)),
    "hasCustomPhaseTags" to jsonOf(order.stagePhaseTags != null)
)

private suspend fun ApplicationCall.requireFlowTenant(): TenantContext? {
    val tenant = tenantContextOrNull
    if (tenant == null) {
        respond(HttpStatusCode.NotFound, "No tenant context found")
    }
    return tenant
}

private suspend fun ApplicationCall.respondFlowJson(json: String) {
    respondText(text = json, contentType = ContentType.Application.Json)
}

/** Alur efektif SPK: salinan kustomnya bila ada, kalau tidak template pabrik. */
private suspend fun SamplingOrder.effectiveFlowProcesses(
    catalog: TenantProcessCatalogRepository?,
    tenantId: TenantId
): List<TenantOptionalProcess> =
    customFlowProcesses ?: catalog?.findByTenantId(tenantId)?.processes ?: emptyList()

/**
 * Leg yang dokumennya sudah terbit tapi akan hilang bila alur diganti dengan [proposed].
 *
 * Dibandingkan lewat `legKey`, bukan lewat daftar prosesnya: menambah proses di ujung alur
 * tidak menghapus leg mana pun dan karenanya tidak perlu ditolak, sedangkan menggeser satu chip
 * ke celah lain mengubah kunci leg di sekitarnya dan memang harus ditolak.
 */
private suspend fun GetFlowTransferLegsUseCase.issuedLegsLostBy(
    tenantId: String,
    order: SamplingOrder,
    proposed: List<TenantOptionalProcess>,
    catalog: TenantProcessCatalogRepository?,
    currentTags: StagePhaseTags,
    proposedTags: StagePhaseTags
): List<FlowLegView> {
    val current = this(
        GetFlowTransferLegsQuery(
            tenantId = tenantId,
            subjectId = order.id.value,
            stages = SamplingRoute.DEFAULT_FRAME,
            processes = order.effectiveFlowProcesses(catalog, TenantId(tenantId)),
            skippedStages = currentTags.skippedSamplingStages,
            customerName = order.clientName
        )
    ).getOrNull() ?: return emptyList()

    val after = this(
        GetFlowTransferLegsQuery(
            tenantId = tenantId,
            subjectId = order.id.value,
            stages = SamplingRoute.DEFAULT_FRAME,
            processes = proposed,
            skippedStages = proposedTags.skippedSamplingStages,
            customerName = order.clientName
        )
    ).getOrNull() ?: return emptyList()

    val survivingKeys = after.legs.map { it.leg.legKey }.toSet()
    return current.legs.filter {
        it.status != FlowLegStatus.BELUM_TERBIT && it.leg.legKey !in survivingKeys
    }
}
