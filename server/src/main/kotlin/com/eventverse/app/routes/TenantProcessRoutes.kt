package com.eventverse.app.routes

import com.eventverse.app.domain.pipeline.ModuleArchetype
import com.eventverse.app.domain.process.TenantProcessCatalogRepository
import com.eventverse.app.domain.sampling.SamplingPipelineStage
import com.eventverse.app.domain.tenant.TenantContext
import com.eventverse.app.domain.workqueue.WorkExecutionMode
import com.eventverse.app.domain.workqueue.WorkStationCode
import com.eventverse.app.domain.process.usecases.AddOptionalProcessCommand
import com.eventverse.app.domain.process.usecases.AddOptionalProcessUseCase
import com.eventverse.app.domain.process.usecases.GetTenantProcessCatalogQuery
import com.eventverse.app.domain.process.usecases.GetTenantProcessCatalogUseCase
import com.eventverse.app.domain.process.usecases.RemoveOptionalProcessCommand
import com.eventverse.app.domain.process.usecases.RemoveOptionalProcessUseCase
import com.eventverse.app.domain.process.usecases.RepositionOptionalProcessUseCase
import com.eventverse.app.domain.process.usecases.RepositionProcessCommand
import com.eventverse.app.plugins.tenantContextOrNull
import com.eventverse.app.shared.json.JsonParser
import com.eventverse.app.shared.json.JsonValue
import com.eventverse.app.shared.json.jsonObjectOf
import com.eventverse.app.shared.json.jsonOf
import com.eventverse.app.shared.process.ProcessCatalogCodec
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.route

/**
 * CRUD katalog proses opsional per tenant — "Adjust Flow" divisi sampling.
 * Basis path mengikuti konvensi workqueue: /api/tenant/...
 */
fun Route.tenantProcessRoutes(repository: TenantProcessCatalogRepository) {
    val getCatalogUseCase = GetTenantProcessCatalogUseCase(repository)
    val addProcessUseCase = AddOptionalProcessUseCase(repository)
    val repositionUseCase = RepositionOptionalProcessUseCase(repository)
    val removeUseCase = RemoveOptionalProcessUseCase(repository)

    route("/api/tenant/process-catalog") {

        // GET — Seluruh tahapan opsional tenant + posisi jangkarnya (render flow)
        get {
            val tenant = call.requireProcessTenant() ?: return@get
            getCatalogUseCase(GetTenantProcessCatalogQuery(tenant.tenantId))
                .onSuccess { catalog ->
                    val body = jsonObjectOf(
                        "tenantId" to jsonOf(tenant.tenantId.value),
                        "processes" to ProcessCatalogCodec.encodeCatalog(catalog)
                    )
                    call.respondProcessJson(body.encode())
                }
                .onFailure { err ->
                    call.respondProcessError(HttpStatusCode.InternalServerError, err.message ?: "Failed to load catalog")
                }
        }

        // POST — Sisipkan tahapan opsional baru (tombol `+` / drop chip dari palet)
        post {
            val tenant = call.requireProcessTenant() ?: return@post
            val body = call.parseProcessBody() ?: return@post call.respondProcessError(
                HttpStatusCode.BadRequest, "Invalid JSON body"
            )

            val command = AddOptionalProcessCommand(
                tenantId = tenant.tenantId,
                code = body.string("code")?.trim()?.uppercase() ?: "",
                displayName = body.string("displayName") ?: "",
                archetype = body.string("archetype")
                    ?.let { runCatching { ModuleArchetype.valueOf(it) }.getOrNull() }
                    ?: ModuleArchetype.CUSTOM_EXTENSION,
                samplingAnchorAfter = body.string("samplingAnchorAfter")
                    ?.let { SamplingPipelineStage.parseOrNull(it) },
                stationAnchorAfter = body.string("stationAnchorAfter")
                    ?.takeIf { it.isNotBlank() }
                    ?.let(::WorkStationCode),
                executionMode = body.string("executionMode")
                    ?.let { runCatching { WorkExecutionMode.valueOf(it) }.getOrNull() }
                    ?: WorkExecutionMode.IN_HOUSE,
                vendorRef = body.string("vendorRef"),
                piecerateTariffIdr = body.long("piecerateTariffIdr") ?: 0L,
                standardMinutesPerPiece = body.double("standardMinutesPerPiece") ?: 0.0
            )

            addProcessUseCase(command)
                .onSuccess { process ->
                    call.respondProcessJson(ProcessCatalogCodec.encodeProcess(process).encode())
                }
                .onFailure { err ->
                    call.respondProcessError(HttpStatusCode.BadRequest, err.message ?: "Failed to add process")
                }
        }

        // PATCH — Reposisi tahapan (drag-and-drop antar celah flow)
        patch("/{processId}") {
            val tenant = call.requireProcessTenant() ?: return@patch
            val processId = call.parameters["processId"] ?: return@patch call.respondProcessError(
                HttpStatusCode.BadRequest, "Missing path param processId"
            )
            val body = call.parseProcessBody() ?: return@patch call.respondProcessError(
                HttpStatusCode.BadRequest, "Invalid JSON body"
            )

            val command = RepositionProcessCommand(
                tenantId = tenant.tenantId,
                processId = processId,
                samplingAnchorAfter = body.string("samplingAnchorAfter")
                    ?.let { SamplingPipelineStage.parseOrNull(it) },
                stationAnchorAfter = body.string("stationAnchorAfter")
                    ?.takeIf { it.isNotBlank() }
                    ?.let(::WorkStationCode)
            )

            repositionUseCase(command)
                .onSuccess { catalog ->
                    val payload = jsonObjectOf("processes" to ProcessCatalogCodec.encodeCatalog(catalog))
                    call.respondProcessJson(payload.encode())
                }
                .onFailure { err ->
                    call.respondProcessError(HttpStatusCode.BadRequest, err.message ?: "Failed to reposition process")
                }
        }

        // DELETE — Keluarkan tahapan dari flow (tombol `×` pada chip)
        delete("/{processId}") {
            val tenant = call.requireProcessTenant() ?: return@delete
            val processId = call.parameters["processId"] ?: return@delete call.respondProcessError(
                HttpStatusCode.BadRequest, "Missing path param processId"
            )

            removeUseCase(RemoveOptionalProcessCommand(tenantId = tenant.tenantId, processId = processId))
                .onSuccess {
                    call.respondProcessJson(jsonObjectOf("removed" to jsonOf(processId)).encode())
                }
                .onFailure { err ->
                    call.respondProcessError(HttpStatusCode.BadRequest, err.message ?: "Failed to remove process")
                }
        }
    }
}

private suspend fun ApplicationCall.requireProcessTenant(): TenantContext? {
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

private suspend fun ApplicationCall.parseProcessBody(): JsonValue.Obj? =
    runCatching { JsonParser.parse(receiveText()) as? JsonValue.Obj }.getOrNull()

private suspend fun ApplicationCall.respondProcessJson(json: String) =
    respondText(text = json, contentType = ContentType.Application.Json)

private suspend fun ApplicationCall.respondProcessError(status: HttpStatusCode, message: String) =
    respondText(
        text = jsonObjectOf("error" to jsonOf(message)).encode(),
        status = status,
        contentType = ContentType.Application.Json
    )