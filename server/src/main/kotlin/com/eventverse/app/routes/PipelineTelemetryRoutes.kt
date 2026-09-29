package com.eventverse.app.routes

import com.eventverse.app.domain.pipeline.ModuleTelemetryProvider
import com.eventverse.app.domain.rbac.AccessLevel
import com.eventverse.app.domain.rbac.ModuleAssignmentRepository
import com.eventverse.app.domain.rbac.RoleRepository
import com.eventverse.app.plugins.tenantContextOrNull
import com.eventverse.app.shared.json.JsonWriter
import com.eventverse.app.shared.pipeline.ModuleTelemetryCodec
import io.ktor.http.*
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

/**
 * `GET /api/tenant/pipeline/telemetry` — telemetri nyata per modul untuk kanvas Factory Flow
 * (TRD-FLOW-002 Fase 5). Modul yang provider-nya gagal atau belum ada **tidak** dikirim; klien
 * menandai node itu "estimasi" alih-alih memamerkan angka seed seolah nyata.
 */
fun Route.pipelineTelemetryRoutes(
    providers: List<ModuleTelemetryProvider>,
    roleRepository: RoleRepository? = null,
    moduleAssignmentRepository: ModuleAssignmentRepository? = null
) {
    get("/api/tenant/pipeline/telemetry") {
        val tenant = call.tenantContextOrNull
            ?: return@get call.respond(HttpStatusCode.NotFound, "No tenant context found")
        val decision = call.factoryFlowDecision(tenant, roleRepository, moduleAssignmentRepository)
        if (!call.requireFactoryFlowAccess(decision, AccessLevel.VIEW)) return@get

        // Paralel dan dibatasi waktu: satu modul yang lambat tidak boleh menahan kanvas.
        val readings = coroutineScope {
            providers.map { p -> async { withTimeoutOrNull(PROVIDER_TIMEOUT_MS) { p.read(tenant.tenantId).getOrNull() } } }
                .awaitAll().filterNotNull()
        }
        call.respondText(
            JsonWriter.write(ModuleTelemetryCodec.encode(readings)),
            ContentType.Application.Json
        )
    }
}

private const val PROVIDER_TIMEOUT_MS = 2_000L
