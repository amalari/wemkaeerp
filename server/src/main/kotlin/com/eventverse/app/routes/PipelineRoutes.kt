package com.eventverse.app.routes

import com.eventverse.app.domain.pipeline.GarmentBusinessPreset
import com.eventverse.app.domain.pipeline.TenantPipelineRepository
import com.eventverse.app.domain.pipeline.usecases.GetTenantPipelineUseCase
import com.eventverse.app.domain.pipeline.usecases.ResetTenantPipelineUseCase
import com.eventverse.app.domain.pipeline.usecases.SaveTenantPipelineUseCase
import com.eventverse.app.plugins.tenantContextOrNull
import com.eventverse.app.routes.dto.PipelineDto
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.pipelineRoutes(
    pipelineRepository: TenantPipelineRepository
) {
    val getPipelineUseCase = GetTenantPipelineUseCase(pipelineRepository)
    val savePipelineUseCase = SaveTenantPipelineUseCase(pipelineRepository)
    val resetPipelineUseCase = ResetTenantPipelineUseCase(pipelineRepository)

    route("/api/tenant/pipeline") {

        // 1. GET active pipeline for tenant
        get {
            val tenant = call.tenantContextOrNull ?: run {
                call.respond(HttpStatusCode.NotFound, "No tenant context found")
                return@get
            }

            val result = getPipelineUseCase(tenant.tenantId, GarmentBusinessPreset.DEFAULT)
            if (result.isSuccess) {
                call.respondText(
                    text = PipelineDto.toJson(result.getOrThrow()),
                    contentType = ContentType.Application.Json
                )
            } else {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    result.exceptionOrNull()?.message ?: "Failed to load tenant pipeline"
                )
            }
        }

        // 2. PUT update / save customized pipeline topology
        put {
            val tenant = call.tenantContextOrNull ?: run {
                call.respond(HttpStatusCode.NotFound, "No tenant context found")
                return@put
            }

            val body = call.receiveText()
            if (body.isBlank()) {
                call.respond(HttpStatusCode.BadRequest, "Request body cannot be empty")
                return@put
            }

            val parsedPipeline = runCatching {
                PipelineDto.fromJson(tenant.tenantId, body)
            }.getOrElse {
                call.respond(HttpStatusCode.BadRequest, "Malformed pipeline JSON: ${it.message}")
                return@put
            }

            val saveResult = savePipelineUseCase(parsedPipeline)
            if (saveResult.isSuccess) {
                call.respondText(
                    text = PipelineDto.toJson(saveResult.getOrThrow()),
                    status = HttpStatusCode.OK,
                    contentType = ContentType.Application.Json
                )
            } else {
                call.respond(
                    HttpStatusCode.BadRequest,
                    saveResult.exceptionOrNull()?.message ?: "Failed to save pipeline"
                )
            }
        }

        // 3. POST reset pipeline back to a standard starter preset
        post("/reset") {
            val tenant = call.tenantContextOrNull ?: run {
                call.respond(HttpStatusCode.NotFound, "No tenant context found")
                return@post
            }

            val rawBody = call.receiveText()
            val presetCode = if (rawBody.contains("\"preset\":")) {
                "\"preset\"\\s*:\\s*\"([^\"]*)\"".toRegex().find(rawBody)?.groupValues?.get(1)
            } else null

            val targetPreset = GarmentBusinessPreset.fromCode(presetCode)

            val resetResult = resetPipelineUseCase(tenant.tenantId, targetPreset)
            if (resetResult.isSuccess) {
                call.respondText(
                    text = PipelineDto.toJson(resetResult.getOrThrow()),
                    status = HttpStatusCode.OK,
                    contentType = ContentType.Application.Json
                )
            } else {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    resetResult.exceptionOrNull()?.message ?: "Failed to reset pipeline"
                )
            }
        }
    }
}
