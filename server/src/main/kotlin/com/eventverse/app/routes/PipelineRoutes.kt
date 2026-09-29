package com.eventverse.app.routes

import com.eventverse.app.domain.blueprint.Blueprint

import com.eventverse.app.domain.pack.GarmentBlueprints

import com.eventverse.app.domain.pipeline.defaultProducedOutputType

import com.eventverse.app.domain.pipeline.defaultExpectedInputType

import com.eventverse.app.domain.pack.GarmentSlots

import com.eventverse.app.domain.pipeline.DynamicModuleDescriptor
import com.eventverse.app.domain.pipeline.ModuleArchetype
import com.eventverse.app.domain.pipeline.TenantEntitlementGrants
import com.eventverse.app.domain.pipeline.TenantEntitlementRepository
import com.eventverse.app.domain.pipeline.TenantModuleEntitlement
import com.eventverse.app.domain.pipeline.TenantPipelineRepository
import com.eventverse.app.domain.pipeline.usecases.GetTenantEntitlementUseCase
import com.eventverse.app.domain.pipeline.usecases.GetTenantModuleCatalogUseCase
import com.eventverse.app.domain.pipeline.usecases.GetTenantPipelineUseCase
import com.eventverse.app.domain.pipeline.usecases.InstallCustomModuleUseCase
import com.eventverse.app.domain.pipeline.usecases.RenameTenantModuleUseCase
import com.eventverse.app.domain.pipeline.usecases.ResetTenantPipelineUseCase
import com.eventverse.app.domain.pipeline.usecases.SaveTenantPipelineUseCase
import com.eventverse.app.domain.pipeline.usecases.SetTenantModuleActivationUseCase
import com.eventverse.app.domain.pipeline.usecases.SyncTenantPipelineWithCatalogUseCase
import com.eventverse.app.domain.rbac.AccessLevel
import com.eventverse.app.domain.rbac.ModuleAssignmentRepository
import com.eventverse.app.domain.rbac.RoleRepository
import com.eventverse.app.domain.tenant.TenantContext
import com.eventverse.app.plugins.callerPrincipalOrNull
import com.eventverse.app.plugins.tenantContextOrNull
import com.eventverse.app.routes.dto.PipelineDto
import com.eventverse.app.shared.json.JsonWriter
import com.eventverse.app.shared.pipeline.TenantEntitlementGrantsCodec
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.pipelineRoutes(
    pipelineRepository: TenantPipelineRepository,
    entitlementRepository: TenantEntitlementRepository,
    roleRepository: RoleRepository? = null,
    moduleAssignmentRepository: ModuleAssignmentRepository? = null
) {
    // Menulis topologi = MANAGE atas Alur Pabrik, fail-closed (tenant-variability-rules Kontrak 7).
    suspend fun ApplicationCall.manageTenant(): TenantContext? {
        val tenant = requireTenant() ?: return null
        val decision = factoryFlowDecision(tenant, roleRepository, moduleAssignmentRepository)
        if (!mayEditWithoutDecision(decision, callerPrincipalOrNull?.role)) {
            respond(HttpStatusCode.Forbidden, "Butuh wewenang Kelola atas Alur Pabrik untuk mengubah alur tenant")
            return null
        }
        return tenant.takeIf { requireFactoryFlowAccess(decision, AccessLevel.MANAGE) }
    }

    val getEntitlementUseCase = GetTenantEntitlementUseCase(entitlementRepository)
    val getPipelineUseCase = GetTenantPipelineUseCase(pipelineRepository)
    val syncPipelineUseCase = SyncTenantPipelineWithCatalogUseCase(pipelineRepository, getPipelineUseCase)
    val savePipelineUseCase = SaveTenantPipelineUseCase(pipelineRepository)
    val resetPipelineUseCase = ResetTenantPipelineUseCase(pipelineRepository)
    val getModuleCatalogUseCase = GetTenantModuleCatalogUseCase(pipelineRepository)
    val setModuleActivationUseCase = SetTenantModuleActivationUseCase(pipelineRepository)
    val renameModuleUseCase = RenameTenantModuleUseCase(pipelineRepository)
    val installCustomModuleUseCase = InstallCustomModuleUseCase(pipelineRepository, entitlementRepository)

    // Modul apa saja yang disambungkan ke tenant ini — termasuk modul tata kelola, yang sengaja
    // tidak muncul di `/pipeline/modules` karena katalog itu khusus stasiun produksi.
    //
    // Dibutuhkan klien untuk menyusun menu: tanpa ini, memutus sebuah modul lewat billing tidak
    // berpengaruh apa pun pada apa yang dilihat orang di dalam aplikasi.
    get("/api/tenant/entitlement") {
        val tenant = call.requireTenant() ?: return@get

        getEntitlementUseCase(tenant.tenantId, tenant.tier, tenant.pack)
            .onSuccess { entitlement ->
                call.respondText(
                    // Selalu daftar eksplisit, bukan `toGrants()` yang memadatkan "semua" menjadi
                    // null. Klien tidak perlu tahu aturan tier untuk memuluskan null itu kembali.
                    text = JsonWriter.write(
                        TenantEntitlementGrantsCodec.encode(
                            TenantEntitlementGrants(
                                grantedModules = entitlement.grantedModules,
                                grantedCustomModuleIds = entitlement.grantedCustomModuleIds
                            )
                        )
                    ),
                    contentType = ContentType.Application.Json
                )
            }
            .onFailure {
                call.respondFailure(HttpStatusCode.InternalServerError, it, "Gagal memuat entitlement modul")
            }
    }

    route("/api/tenant/pipeline") {

        // 1. GET active pipeline for tenant, topped up with catalogue modules added since it
        //    was provisioned (inserted bypassed, so the running flow is unchanged).
        get {
            val tenant = call.requireTenant() ?: return@get

            syncPipelineUseCase(tenant.tenantId, getEntitlementUseCase.forTenant(tenant), tenant.starterPreset)
                .onSuccess { call.respondPipeline(it) }
                .onFailure { call.respondFailure(HttpStatusCode.InternalServerError, it, "Gagal memuat alur tenant") }
        }

        // 2. PUT update / save customized pipeline topology
        put {
            val tenant = call.manageTenant() ?: return@put

            val body = call.receiveText()
            if (body.isBlank()) {
                call.respond(HttpStatusCode.BadRequest, "Request body cannot be empty")
                return@put
            }

            val parsedPipeline = runCatching { PipelineDto.fromJson(tenant.tenantId, body) }
                .getOrElse {
                    call.respond(HttpStatusCode.BadRequest, "Malformed pipeline JSON: ${it.message}")
                    return@put
                }

            savePipelineUseCase(parsedPipeline, getEntitlementUseCase.forTenant(tenant))
                .onSuccess { call.respondPipeline(it) }
                .onFailure { call.respondFailure(HttpStatusCode.BadRequest, it, "Gagal menyimpan alur") }
        }

        // 3. POST reset pipeline back to a standard starter preset
        post("/reset") {
            val tenant = call.manageTenant() ?: return@post

            val presetCode = PipelineDto.readPresetCode(call.receiveText())
            // B4d: kode tak dikenal = 400, bukan diam-diam reset ke FOB (Kontrak 4).
            val targetPreset = if (presetCode == null) tenant.starterPreset else
                GarmentBlueprints.findByCode(presetCode)
                    ?: return@post call.respond(HttpStatusCode.BadRequest, "Starter alur tidak dikenal: '$presetCode'")

            resetPipelineUseCase(tenant.tenantId, targetPreset)
                .onSuccess { call.respondPipeline(it) }
                .onFailure { call.respondFailure(HttpStatusCode.InternalServerError, it, "Gagal mereset alur") }
        }

        // 4. GET the module catalogue for this tenant, annotated with plan entitlements.
        get("/modules") {
            val tenant = call.requireTenant() ?: return@get

            val entitlement = getEntitlementUseCase.forTenant(tenant)
            getModuleCatalogUseCase(tenant.tenantId, entitlement, tenant.starterPreset)
                .onSuccess {
                    call.respondText(
                        text = PipelineDto.catalogToJson(entitlement, it),
                        contentType = ContentType.Application.Json
                    )
                }
                .onFailure { call.respondFailure(HttpStatusCode.InternalServerError, it, "Gagal memuat katalog modul") }
        }

        // 5. POST switch one module on or off for this tenant.
        post("/modules/activation") {
            val tenant = call.manageTenant() ?: return@post

            val request = PipelineDto.readModuleActivation(call.receiveText())
            if (request == null) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    "Body harus berisi {\"moduleId\":\"...\",\"isActive\":true|false}"
                )
                return@post
            }

            setModuleActivationUseCase(
                tenantId = tenant.tenantId,
                moduleId = request.moduleId,
                isActive = request.isActive,
                entitlement = getEntitlementUseCase.forTenant(tenant),
                fallbackPreset = tenant.starterPreset
            )
                .onSuccess { call.respondPipeline(it) }
                .onFailure { call.respondFailure(HttpStatusCode.BadRequest, it, "Gagal mengubah status modul") }
        }

        // 6. PUT rename a module (and optionally its tenant-specific formula parameters).
        put("/modules/{nodeId}") {
            val tenant = call.manageTenant() ?: return@put
            val nodeId = call.parameters["nodeId"]
            if (nodeId.isNullOrBlank()) {
                call.respond(HttpStatusCode.BadRequest, "Parameter nodeId wajib diisi")
                return@put
            }

            val request = PipelineDto.readModuleRename(call.receiveText())
            if (request == null) {
                call.respond(HttpStatusCode.BadRequest, "Body harus berisi {\"displayName\":\"...\"}")
                return@put
            }

            renameModuleUseCase(
                tenantId = tenant.tenantId,
                nodeId = nodeId,
                newDisplayName = request.displayName,
                formulaParameters = request.formulaParameters,
                entitlement = getEntitlementUseCase.forTenant(tenant),
                fallbackPreset = tenant.starterPreset
            )
                .onSuccess { call.respondPipeline(it) }
                .onFailure { call.respondFailure(HttpStatusCode.BadRequest, it, "Gagal mengubah nama modul") }
        }

        // 7. POST install a custom / third-party plugin module (Enterprise plans only).
        post("/modules/custom") {
            val tenant = call.manageTenant() ?: return@post

            val request = PipelineDto.readCustomModule(call.receiveText())
            if (request == null) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    "Body harus berisi {\"moduleId\":\"...\",\"name\":\"...\"}"
                )
                return@post
            }

            val archetype = GarmentSlots.fromCode(request.archetypeCode)
                ?: GarmentSlots.CUSTOM_EXTENSION

            val descriptor = runCatching {
                DynamicModuleDescriptor(
                    moduleId = request.moduleId,
                    archetype = archetype,
                    name = request.name,
                    description = request.description,
                    acceptedInputDataTypes = setOf(archetype.defaultExpectedInputType),
                    producedOutputDataType = archetype.defaultProducedOutputType,
                    isCustomTenantPlugin = true,
                    customConfigSchemaJson = request.configSchemaJson
                )
            }.getOrElse {
                call.respond(HttpStatusCode.BadRequest, it.message ?: "Deskriptor modul kustom tidak valid")
                return@post
            }

            installCustomModuleUseCase(
                tenantId = tenant.tenantId,
                descriptor = descriptor,
                entitlement = getEntitlementUseCase.forTenant(tenant),
                attachAfterNodeId = request.attachAfterNodeId,
                formulaParameters = request.formulaParameters,
                fallbackPreset = tenant.starterPreset
            )
                .onSuccess { call.respondPipeline(it, HttpStatusCode.Created) }
                .onFailure { call.respondFailure(HttpStatusCode.Forbidden, it, "Gagal memasang modul kustom") }
        }
    }
}

// ---------------------------------------------------------------------------
// Shared request/response plumbing
// ---------------------------------------------------------------------------

/**
 * Modules a tenant may run come from its plan tier COMBINED with grants provisioned for it
 * specifically. Resolving from the tier alone drops custom plugin grants, which would make
 * every edit fail for a tenant that runs one.
 */
private suspend fun GetTenantEntitlementUseCase.forTenant(
    tenant: TenantContext
): TenantModuleEntitlement =
    invoke(tenant.tenantId, tenant.tier, tenant.pack).getOrDefault(tenant.moduleEntitlement)

/** Preset used to provision a tenant that has no pipeline yet: its own business model. */
private val TenantContext.starterPreset: Blueprint
    get() = businessPreset

private suspend fun ApplicationCall.requireTenant(): TenantContext? {
    val tenant = tenantContextOrNull
    if (tenant == null) {
        respond(HttpStatusCode.NotFound, "No tenant context found")
    }
    return tenant
}

private suspend fun ApplicationCall.respondPipeline(
    pipeline: com.eventverse.app.domain.pipeline.CustomTenantPipeline,
    status: HttpStatusCode = HttpStatusCode.OK
) {
    respondText(
        text = PipelineDto.toJson(pipeline),
        status = status,
        contentType = ContentType.Application.Json
    )
}

private suspend fun ApplicationCall.respondFailure(
    status: HttpStatusCode,
    cause: Throwable,
    fallbackMessage: String
) {
    respond(status, cause.message ?: fallbackMessage)
}
