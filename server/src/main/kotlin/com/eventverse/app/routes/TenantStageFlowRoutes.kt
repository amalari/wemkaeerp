package com.eventverse.app.routes

import com.eventverse.app.domain.stageflow.TenantStageFlowRepository
import com.eventverse.app.domain.stageflow.usecases.GetTenantStageFlowUseCase
import com.eventverse.app.plugins.tenantContextOrNull
import com.eventverse.app.shared.stageflow.TenantStageFlowCodec
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

/**
 * Kerangka tahap alur tenant (TRD-FLOW-001). Tenant tanpa kerangka di-provision dari template
 * industrinya (`tenants.industry_template`, V74).
 */
fun Route.tenantStageFlowRoutes(repository: TenantStageFlowRepository) {
    val getStageFlow = GetTenantStageFlowUseCase(repository)

    get("/api/tenant/stage-flow") {
        val tenant = call.tenantContextOrNull ?: return@get call.respond(HttpStatusCode.NotFound, "No tenant context found")
        getStageFlow(tenant.tenantId, tenant.industryTemplate)
            .onSuccess { call.respondText(TenantStageFlowCodec.encode(it).encode(), ContentType.Application.Json) }
            .onFailure { call.respond(HttpStatusCode.InternalServerError, it.message ?: "Gagal memuat kerangka alur") }
    }
}
