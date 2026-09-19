package com.eventverse.app.routes

import com.eventverse.app.domain.contracts.GarmentPanel
import com.eventverse.app.domain.traceability.*
import com.eventverse.app.domain.traceability.usecases.*
import com.eventverse.app.plugins.tenantContextOrNull
import com.eventverse.app.shared.json.*
import com.eventverse.app.shared.traceability.TraceAllocationCodec
import com.eventverse.app.shared.traceability.TraceContainerCodec
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

/**
 * Rute pemindaian & pencatatan wadah telusur.
 *
 * Dipisah dari rute cetak sejak awal, bukan setelah gemuk: keduanya punya alasan berubah yang
 * berbeda — yang satu mengikuti alur kerja lantai, yang satu mengikuti bentuk kertas.
 */
fun Route.traceabilityScanRoutes(
    containers: TraceContainerRepository,
    workOrders: TraceWorkOrderProvider
) {
    val resolveCode = ResolveTraceCodeUseCase(containers, workOrders)
    val openContainer = OpenTraceContainerUseCase(containers, workOrders)
    val recordTally = RecordBundleTallyUseCase(containers)
    val closeSack = CloseTraceSackUseCase(containers, workOrders)
    val reconciliation = GetTraceReconciliationUseCase(containers, workOrders)

    route("/api/tenant/traceability") {

        // Resolusi kartu: apa ini, milik SPK mana, dan sudah tercatat apa belum.
        get("/codes/{code}") {
            val tenant = call.traceTenant() ?: return@get
            val raw = call.parameters["code"].orEmpty()

            resolveCode(tenant, raw)
                .onSuccess { call.respondTraceJson(encodeScanResult(it).encode()) }
                // Kode yang sah tapi bukan milik pabrik ini dijawab 404, bukan 403: 403 mengakui
                // bahwa kodenya ada dan membocorkan keberadaan SPK milik tenant lain.
                .onFailure { call.respondTraceFailure(HttpStatusCode.NotFound, it) }
        }

        // Scan pertama sebuah kartu pra-cetak. Idempoten.
        post("/codes/{code}/open") {
            val tenant = call.traceTenant() ?: return@post
            val body = call.traceBody()
            val raw = call.parameters["code"].orEmpty()

            openContainer(tenant, raw, body?.string("colorway").orEmpty(), Clock.System.now())
                .onSuccess { call.respondTraceJson(TraceContainerCodec.encode(it).encode()) }
                .onFailure { call.respondTraceFailure(HttpStatusCode.BadRequest, it) }
        }

        post("/codes/{code}/tally") {
            val tenant = call.traceTenant() ?: return@post
            val body = call.traceBody()
                ?: return@post call.respond(HttpStatusCode.BadRequest, "Body JSON tidak terbaca")
            val code = TraceCodec.parseCode(call.parameters["code"].orEmpty())
                ?: return@post call.respond(HttpStatusCode.BadRequest, "Kode kartu tidak sah")

            val tallies = body.objectArray("panelTallies").mapNotNull { row ->
                val panel = GarmentPanel.entries.firstOrNull { it.name == row.string("panel") }
                    ?: return@mapNotNull null
                val pieces = row.int("pieces") ?: return@mapNotNull null
                if (pieces < 0) null else PanelTally(panel, pieces)
            }
            val now = Clock.System.now()

            recordTally(
                tenantId = tenant,
                code = code,
                tallies = tallies,
                operatorName = body.string("operatorName").orEmpty(),
                shift = ShiftLabel(body.string("shift").orEmpty()),
                // Jam kejadian datang dari operator, bukan dari jam server: bundel yang dibentuk
                // pukul 22.00 sering baru terkunci pukul 07.00 karena jaringan pabrik mati semalaman.
                recordedAt = body.instant("recordedAt") ?: now,
                now = now,
                notes = body.string("notes").orEmpty()
            )
                .onSuccess { call.respondTraceJson(TraceContainerCodec.encode(it).encode()) }
                .onFailure { call.respondTraceFailure(HttpStatusCode.Conflict, it) }
        }

        post("/sacks/{code}/close") {
            val tenant = call.traceTenant() ?: return@post
            val body = call.traceBody()
                ?: return@post call.respond(HttpStatusCode.BadRequest, "Body JSON tidak terbaca")
            val sackCode = TraceCodec.parseCode(call.parameters["code"].orEmpty())
                ?: return@post call.respond(HttpStatusCode.BadRequest, "Kode karung tidak sah")

            val bundleCodes = body.stringArray("bundleCodes").mapNotNull { TraceCodec.parseCode(it) }
            val now = Clock.System.now()

            closeSack(
                tenantId = tenant,
                sackCode = sackCode,
                bundleCodes = bundleCodes,
                declaredPcs = body.int("declaredPcs") ?: 0,
                weightKg = body.double("weightKg") ?: 0.0,
                operatorName = body.string("operatorName").orEmpty(),
                recordedAt = body.instant("recordedAt") ?: now,
                now = now,
                notes = body.string("notes").orEmpty()
            )
                .onSuccess { event ->
                    call.respondTraceJson(
                        jsonObjectOf(
                            "code" to jsonOf(event.code.value),
                            "declaredPcs" to jsonOf(event.declaredPcs),
                            "consumedBundleCount" to jsonOf(event.consumedBundleCount),
                            "shrinkagePcs" to jsonOf(event.shrinkagePcs)
                        ).encode()
                    )
                }
                // Penolakan keseragaman adalah konflik keadaan, bukan galat sintaks — dan pesannya
                // sudah menyebut size/warna yang bertabrakan supaya operator tahu apa yang dipisahkan.
                .onFailure { call.respondTraceFailure(HttpStatusCode.Conflict, it) }
        }

        get("/work-orders/{kind}/{id}/containers") {
            val tenant = call.traceTenant() ?: return@get
            val ref = call.traceRef() ?: return@get
            val rows = containers.findByWorkOrder(tenant, ref)
            call.respondTraceJson(jsonArrayOf(rows.map(TraceContainerCodec::encode)).encode())
        }

        get("/work-orders/{kind}/{id}/reconciliation") {
            val tenant = call.traceTenant() ?: return@get
            val ref = call.traceRef() ?: return@get

            reconciliation(tenant, ref)
                .onSuccess { call.respondTraceJson(TraceAllocationCodec.encodeReconciliation(it).encode()) }
                .onFailure { call.respondTraceFailure(HttpStatusCode.NotFound, it) }
        }
    }
}

private fun encodeScanResult(result: TraceScanResult): JsonValue.Obj = jsonObjectOf(
    "code" to jsonOf(result.code.value),
    "humanCode" to jsonOf(TraceCodec.grouped(result.code)),
    "tier" to jsonOf(result.parts.tier.name),
    "tierLabel" to jsonOf(result.parts.tier.displayName),
    "workOrderKind" to jsonOf(result.parts.workOrderKind.name),
    "sizeLabel" to jsonOf(result.sizeLabel),
    "sequence" to jsonOf(result.parts.sequence),
    "isNewCard" to jsonOf(result.isNewCard),
    "snapshot" to (result.snapshot?.let(TraceAllocationCodec::encodeSnapshot) ?: JsonValue.Null),
    "container" to (result.container?.let(TraceContainerCodec::encode) ?: JsonValue.Null)
)

internal suspend fun ApplicationCall.traceTenant(): com.eventverse.app.domain.tenant.TenantId? {
    val tenant = tenantContextOrNull
    if (tenant == null) respond(HttpStatusCode.NotFound, "Konteks tenant tidak ditemukan")
    return tenant?.tenantId
}

internal suspend fun ApplicationCall.traceRef(): TraceWorkOrderRef? {
    val kind = TraceWorkOrderKind.entries
        .firstOrNull { it.name.equals(parameters["kind"], ignoreCase = true) }
    val id = parameters["id"]?.takeIf { it.isNotBlank() }
    if (kind == null || id == null) {
        respond(HttpStatusCode.BadRequest, "Jenis atau id SPK tidak sah")
        return null
    }
    return TraceWorkOrderRef(kind, id)
}

internal suspend fun ApplicationCall.traceBody(): JsonValue.Obj? =
    runCatching { JsonParser.parse(receiveText()) as? JsonValue.Obj }.getOrNull()

internal suspend fun ApplicationCall.respondTraceJson(json: String) {
    respondText(text = json, contentType = ContentType.Application.Json)
}

internal suspend fun ApplicationCall.respondTraceFailure(status: HttpStatusCode, error: Throwable) {
    respond(status, error.message ?: "Terjadi kesalahan tak terduga")
}

private fun JsonValue.Obj.instant(key: String): Instant? =
    string(key)?.takeIf { it.isNotBlank() }?.let { runCatching { Instant.parse(it) }.getOrNull() }
