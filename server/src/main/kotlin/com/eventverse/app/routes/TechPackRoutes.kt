package com.eventverse.app.routes

import com.eventverse.app.domain.rbac.isScopeSupported

import com.eventverse.app.domain.rbac.isFoundation

import com.eventverse.app.domain.rbac.isOperational

import com.eventverse.app.domain.rbac.isGovernance

import com.eventverse.app.domain.rbac.isHierarchical

import com.eventverse.app.domain.rbac.isGlobalOnly


import com.eventverse.app.domain.rbac.supportedScopes

import com.eventverse.app.domain.rbac.kind

import com.eventverse.app.domain.rbac.scopeCapability

import com.eventverse.app.domain.rbac.iconKey

import com.eventverse.app.domain.rbac.description

import com.eventverse.app.domain.rbac.displayName

import com.eventverse.app.domain.rbac.name

import com.eventverse.app.domain.rbac.code

import com.eventverse.app.domain.pack.GarmentModules

import com.eventverse.app.domain.common.Ratio
import com.eventverse.app.domain.pipeline.StockOwnershipSemantics
import com.eventverse.app.domain.rbac.*
import com.eventverse.app.domain.sampling.SamplingOrderId
import com.eventverse.app.domain.sampling.SamplingOrderRepository
import com.eventverse.app.domain.sampling.toApprovedSampleSpecification
import com.eventverse.app.domain.masterdata.MaterialItemRepository
import com.eventverse.app.domain.masterdata.MaterialPriceRepository
import com.eventverse.app.domain.masterdata.PriceSource
import com.eventverse.app.domain.masterdata.PriceSourceResolver
import com.eventverse.app.domain.masterdata.StandardPriceSource
import com.eventverse.app.domain.techpack.*
import com.eventverse.app.domain.techpack.usecases.*
import com.eventverse.app.domain.tenant.TenantContext
import com.eventverse.app.plugins.callerPrincipalOrNull
import com.eventverse.app.plugins.tenantContextOrNull
import com.eventverse.app.shared.common.MeasureCodec
import com.eventverse.app.shared.contracts.TechPackAndYieldDataCodec
import com.eventverse.app.shared.json.*
import com.eventverse.app.shared.techpack.BomCostPreviewCodec
import com.eventverse.app.shared.techpack.TechPackCodec
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.datetime.Clock

fun Route.techPackRoutes(
    techPackRepository: TechPackRepository,
    samplingOrderRepository: SamplingOrderRepository,
    materialRepository: MaterialItemRepository,
    materialPriceRepository: MaterialPriceRepository,
    roleRepository: RoleRepository? = null,
    moduleAssignmentRepository: ModuleAssignmentRepository? = null
) {
    val createDraftUseCase = CreateTechPackDraftUseCase(techPackRepository)
    val updateBomUseCase = UpdateTechPackBomUseCase(techPackRepository, materialRepository)
    val updateLaborUseCase = UpdateTechPackLaborUseCase(techPackRepository)
    val updateSizeYieldUseCase = UpdateTechPackSizeYieldUseCase(techPackRepository)
    val resolveMaterialsUseCase = ResolveBomMaterialsUseCase(techPackRepository, materialRepository)
    val releaseUseCase = ReleaseTechPackUseCase(techPackRepository)
    val reviseUseCase = ReviseTechPackUseCase(techPackRepository)
    val getListUseCase = GetTechPackListUseCase(techPackRepository)
    val getDetailUseCase = GetTechPackDetailUseCase(techPackRepository)
    val previewCostUseCase = PreviewBomMaterialCostUseCase(techPackRepository, materialRepository, materialPriceRepository)
    val explodeBomUseCase = ExplodeBomUseCase(techPackRepository)

    route("/api/tenant/tech-pack") {

        // GET /api/tenant/tech-pack (Search & List)
        get {
            val tenant = call.requireTenant() ?: return@get
            val q = call.request.queryParameters["q"]?.takeIf { it.isNotBlank() }
            val status = call.request.queryParameters["status"]?.let {
                TechPackStatus.fromCode(it)
            }
            val styleCode = call.request.queryParameters["styleCode"]?.takeIf { it.isNotBlank() }?.let { StyleCode(it) }
            val includeArchived = call.request.queryParameters["includeArchived"]?.toBooleanStrictOrNull() ?: false
            val latestVersionOnly = call.request.queryParameters["latestVersionOnly"]?.toBooleanStrictOrNull() ?: true
            val page = call.request.queryParameters["page"]?.toIntOrNull() ?: 1
            val pageSize = call.request.queryParameters["pageSize"]?.toIntOrNull() ?: 20

            val query = TechPackQuery(
                queryText = q,
                status = status,
                styleCode = styleCode,
                includeArchived = includeArchived,
                latestVersionOnly = latestVersionOnly,
                page = page,
                pageSize = pageSize
            )

            val result = getListUseCase(tenant.tenantId, query)
            if (result.isSuccess) {
                call.respondJson(TechPackCodec.encodePage(result.getOrThrow()).encode())
            } else {
                call.respondFailure(HttpStatusCode.InternalServerError, result.exceptionOrNull() ?: Exception("Unknown error"))
            }
        }

        // POST /api/tenant/tech-pack/blank (Create blank draft)
        post("/blank") {
            val tenant = call.requireTenant() ?: return@post
            val body = call.receiveText()
            val json = JsonParser.parse(body) as? JsonValue.Obj
                ?: return@post call.respond(HttpStatusCode.BadRequest, "Invalid JSON body")

            val styleName = json.string("styleName")?.trim() ?: ""
            if (styleName.isBlank()) {
                return@post call.respond(HttpStatusCode.BadRequest, "Nama style tidak boleh kosong")
            }
            val styleCode = json.string("styleCode")?.trim()?.takeIf { it.isNotBlank() }?.let { StyleCode(it) }
            val clientName = json.string("clientName")?.trim() ?: ""

            val command = CreateBlankTechPackCommand(
                tenantId = tenant.tenantId,
                styleName = styleName,
                styleCode = styleCode,
                clientName = clientName,
                createdByUserId = call.callerPrincipalOrNull?.userId
            )

            val result = createDraftUseCase.createBlank(command)
            if (result.isSuccess) {
                call.respondJson(TechPackCodec.encode(result.getOrThrow()).encode())
            } else {
                call.respondFailure(HttpStatusCode.BadRequest, result.exceptionOrNull() ?: Exception("Unknown error"))
            }
        }

        // POST /api/tenant/tech-pack/from-sampling (Create draft from ACC approved sample)
        post("/from-sampling") {
            val tenant = call.requireTenant() ?: return@post
            val body = call.receiveText()
            val json = JsonParser.parse(body) as? JsonValue.Obj
                ?: return@post call.respond(HttpStatusCode.BadRequest, "Invalid JSON body")

            val samplingOrderId = json.string("samplingOrderId")?.trim()
                ?: return@post call.respond(HttpStatusCode.BadRequest, "samplingOrderId wajib diisi")

            val samplingOrder = samplingOrderRepository.findById(SamplingOrderId(samplingOrderId))
                ?: return@post call.respond(HttpStatusCode.NotFound, "Sampling order dengan ID '$samplingOrderId' tidak ditemukan")

            val specResult = samplingOrder.toApprovedSampleSpecification(Clock.System.now())
            if (specResult.isFailure) {
                val err = specResult.exceptionOrNull()?.message ?: "Sampling belum ACC Approved"
                return@post call.respond(HttpStatusCode.BadRequest, "Gagal membuat Tech Pack: $err")
            }

            val spec = specResult.getOrThrow()
            val styleCode = json.string("styleCode")?.trim()?.takeIf { it.isNotBlank() }?.let { StyleCode(it) }
            val defaultOwnership = json.string("defaultOwnership")?.let { codeStr ->
                StockOwnershipSemantics.entries.firstOrNull { it.code == codeStr || it.name.equals(codeStr, ignoreCase = true) }
            } ?: StockOwnershipSemantics.OWNED_RAW_MATERIAL

            val wastePercent = json.double("defaultWasteAllowancePercent") ?: 5.0
            val defaultWaste = Ratio.percent(wastePercent)

            val command = CreateTechPackFromSampleCommand(
                tenantId = tenant.tenantId,
                spec = spec,
                styleCode = styleCode,
                defaultOwnership = defaultOwnership,
                defaultWasteAllowance = defaultWaste,
                createdByUserId = call.callerPrincipalOrNull?.userId
            )

            val result = createDraftUseCase.createFromSample(command)
            if (result.isSuccess) {
                call.respondJson(TechPackCodec.encode(result.getOrThrow()).encode())
            } else {
                call.respondFailure(HttpStatusCode.BadRequest, result.exceptionOrNull() ?: Exception("Unknown error"))
            }
        }

        // GET /api/tenant/tech-pack/{id}
        get("/{id}") {
            val tenant = call.requireTenant() ?: return@get
            val id = call.parameters["id"] ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing id")

            val result = getDetailUseCase.getDetail(tenant.tenantId, TechPackId(id))
            if (result.isSuccess) {
                val techPack = result.getOrThrow()
                if (techPack == null) {
                    call.respond(HttpStatusCode.NotFound, "Tech pack '$id' tidak ditemukan")
                } else {
                    call.respondJson(TechPackCodec.encode(techPack).encode())
                }
            } else {
                call.respondFailure(HttpStatusCode.NotFound, result.exceptionOrNull() ?: Exception("Unknown error"))
            }
        }

        // GET /api/tenant/tech-pack/by-style/{styleCode}/versions
        get("/by-style/{styleCode}/versions") {
            val tenant = call.requireTenant() ?: return@get
            val styleCode = call.parameters["styleCode"] ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing styleCode")

            val versions = techPackRepository.findVersions(tenant.tenantId, StyleCode(styleCode))
            val jsonArray = jsonArrayOf(versions.map(TechPackCodec::encode))
            call.respondJson(jsonArray.encode())
        }

        // GET /api/tenant/tech-pack/by-style/{styleCode}/latest-released
        get("/by-style/{styleCode}/latest-released") {
            val tenant = call.requireTenant() ?: return@get
            val styleCode = call.parameters["styleCode"] ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing styleCode")

            val released = techPackRepository.findLatestReleased(tenant.tenantId, StyleCode(styleCode))
            if (released == null) {
                call.respond(HttpStatusCode.NotFound, "Tidak ditemukan versi RELEASED untuk style '$styleCode'")
            } else {
                call.respondJson(TechPackCodec.encode(released).encode())
            }
        }

        // PUT /api/tenant/tech-pack/{id}/bom (Replace BOM lines)
        put("/{id}/bom") {
            val tenant = call.requireTenant() ?: return@put
            val id = call.parameters["id"] ?: return@put call.respond(HttpStatusCode.BadRequest, "Missing id")
            val body = call.receiveText()
            val parsed = JsonParser.parse(body)

            val linesArray = when (parsed) {
                is JsonValue.Obj -> parsed.objectArray("lines")
                is JsonValue.Arr -> parsed.items.mapNotNull { it as? JsonValue.Obj }
                else -> emptyList()
            }

            val bomLines = linesArray.map(TechPackAndYieldDataCodec::decodeBomLine)

            val result = updateBomUseCase.replaceLines(
                ReplaceBomLinesCommand(tenant.tenantId, TechPackId(id), bomLines)
            )
            if (result.isSuccess) {
                call.respondJson(TechPackCodec.encode(result.getOrThrow()).encode())
            } else {
                call.respondFailure(HttpStatusCode.BadRequest, result.exceptionOrNull() ?: Exception("Unknown error"))
            }
        }

        // PUT /api/tenant/tech-pack/{id}/labor (Replace labor operations)
        put("/{id}/labor") {
            val tenant = call.requireTenant() ?: return@put
            val id = call.parameters["id"] ?: return@put call.respond(HttpStatusCode.BadRequest, "Missing id")
            val body = call.receiveText()
            val parsed = JsonParser.parse(body)

            val opsArray = when (parsed) {
                is JsonValue.Obj -> parsed.objectArray("operations")
                is JsonValue.Arr -> parsed.items.mapNotNull { it as? JsonValue.Obj }
                else -> emptyList()
            }

            val laborOps = opsArray.map(TechPackAndYieldDataCodec::decodeLaborOperation)

            val result = updateLaborUseCase.replaceOperations(
                ReplaceLaborOperationsCommand(tenant.tenantId, TechPackId(id), laborOps)
            )
            if (result.isSuccess) {
                call.respondJson(TechPackCodec.encode(result.getOrThrow()).encode())
            } else {
                call.respondFailure(HttpStatusCode.BadRequest, result.exceptionOrNull() ?: Exception("Unknown error"))
            }
        }

        // PUT /api/tenant/tech-pack/{id}/size-yield (Replace size yield factors)
        put("/{id}/size-yield") {
            val tenant = call.requireTenant() ?: return@put
            val id = call.parameters["id"] ?: return@put call.respond(HttpStatusCode.BadRequest, "Missing id")
            val body = call.receiveText()
            val parsed = JsonParser.parse(body)

            val factorsArray = when (parsed) {
                is JsonValue.Obj -> parsed.objectArray("factors")
                is JsonValue.Arr -> parsed.items.mapNotNull { it as? JsonValue.Obj }
                else -> emptyList()
            }

            val factors = factorsArray.map(TechPackAndYieldDataCodec::decodeSizeYieldFactor)

            val result = updateSizeYieldUseCase.setFactors(
                SetSizeYieldFactorsCommand(tenant.tenantId, TechPackId(id), factors)
            )
            if (result.isSuccess) {
                call.respondJson(TechPackCodec.encode(result.getOrThrow()).encode())
            } else {
                call.respondFailure(HttpStatusCode.BadRequest, result.exceptionOrNull() ?: Exception("Unknown error"))
            }
        }

        // POST /api/tenant/tech-pack/{id}/resolve-materials (Auto match free-text against master data)
        post("/{id}/resolve-materials") {
            val tenant = call.requireTenant() ?: return@post
            val id = call.parameters["id"] ?: return@post call.respond(HttpStatusCode.BadRequest, "Missing id")

            val result = resolveMaterialsUseCase(ResolveBomMaterialsCommand(tenant.tenantId, TechPackId(id)))
            if (result.isSuccess) {
                val res = result.getOrThrow()
                call.respondJson(TechPackCodec.encode(res.techPack).encode())
            } else {
                call.respondFailure(HttpStatusCode.BadRequest, result.exceptionOrNull() ?: Exception("Unknown error"))
            }
        }

        // POST /api/tenant/tech-pack/{id}/release (Release draft)
        post("/{id}/release") {
            val tenant = call.requireTenant() ?: return@post
            val id = call.parameters["id"] ?: return@post call.respond(HttpStatusCode.BadRequest, "Missing id")

            val result = releaseUseCase(ReleaseTechPackCommand(tenant.tenantId, TechPackId(id)))
            if (result.isSuccess) {
                call.respondJson(TechPackCodec.encode(result.getOrThrow()).encode())
            } else {
                call.respondFailure(HttpStatusCode.BadRequest, result.exceptionOrNull() ?: Exception("Unknown error"))
            }
        }

        // POST /api/tenant/tech-pack/{id}/revise (Create revision v+1)
        post("/{id}/revise") {
            val tenant = call.requireTenant() ?: return@post
            val id = call.parameters["id"] ?: return@post call.respond(HttpStatusCode.BadRequest, "Missing id")

            val command = ReviseTechPackCommand(
                tenantId = tenant.tenantId,
                techPackId = TechPackId(id),
                createdByUserId = call.callerPrincipalOrNull?.userId
            )
            val result = reviseUseCase(command)
            if (result.isSuccess) {
                call.respondJson(TechPackCodec.encode(result.getOrThrow()).encode())
            } else {
                call.respondFailure(HttpStatusCode.BadRequest, result.exceptionOrNull() ?: Exception("Unknown error"))
            }
        }

        // DELETE /api/tenant/tech-pack/{id} (Archive)
        delete("/{id}") {
            val tenant = call.requireTenant() ?: return@delete
            val id = call.parameters["id"] ?: return@delete call.respond(HttpStatusCode.BadRequest, "Missing id")

            val ok = techPackRepository.archive(tenant.tenantId, TechPackId(id))
            if (ok) {
                call.respondJson(jsonObjectOf("success" to jsonOf(true)).encode())
            } else {
                call.respond(HttpStatusCode.NotFound, "Tech pack tidak ditemukan atau gagal diarsipkan")
            }
        }

        // GET /api/tenant/tech-pack/{id}/cost-preview (Preview material cost)
        get("/{id}/cost-preview") {
            val tenant = call.requireTenant() ?: return@get
            val id = call.parameters["id"] ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing id")

            // RBAC check: caller needs view access to MASTER_DATA if role repo is configured
            if (roleRepository != null && moduleAssignmentRepository != null) {
                val principal = call.callerPrincipalOrNull
                if (principal != null && (principal.customRoleId != null || principal.departmentId != null)) {
                    val role = principal.customRoleId
                        ?.let { runCatching { RoleId(it) }.getOrNull() }
                        ?.let { roleRepository.findById(tenant.tenantId, it) }

                    val persona = TestingPersona(
                        userId = principal.userId.ifBlank { "unknown" },
                        name = principal.email ?: principal.userId.ifBlank { "unknown" },
                        tenantId = tenant.tenantId,
                        tenantSlug = tenant.slug.value,
                        departmentId = principal.departmentId,
                        departmentName = "",
                        roleId = role?.id,
                        roleTitle = role?.name ?: "",
                        isOwnerOrSuperAdmin = principal.isPlatformSuperadmin && role == null,
                        isPlatformSuperAdmin = principal.isPlatformSuperadmin && role == null
                    )

                    val assignments = moduleAssignmentRepository.findAllByTenant(tenant.tenantId)
                    val decision = AccessDecisionEngine.explain(
                        persona = persona,
                        module = GarmentModules.MASTER_DATA,
                        role = role,
                        assignments = assignments[GarmentModules.MASTER_DATA].orEmpty()
                    )

                    if (decision.config.level == AccessLevel.NONE) {
                        return@get call.respond(
                            HttpStatusCode.Forbidden,
                            "Akses ditolak: Anda tidak memiliki wewenang membaca data harga bahan baku Master Data"
                        )
                    }
                }
            }

            val orderQty = call.request.queryParameters["orderQuantity"]?.toLongOrNull() ?: 1L

            val result = previewCostUseCase(tenant.tenantId, TechPackId(id), orderQty)
            if (result.isSuccess) {
                call.respondJson(BomCostPreviewCodec.encode(result.getOrThrow()).encode())
            } else {
                call.respondFailure(HttpStatusCode.BadRequest, result.exceptionOrNull() ?: Exception("Unknown error"))
            }
        }

        // GET /api/tenant/tech-pack/{id}/explosion (Explode requirements)
        get("/{id}/explosion") {
            val tenant = call.requireTenant() ?: return@get
            val id = call.parameters["id"] ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing id")
            val orderQty = call.request.queryParameters["orderQuantity"]?.toLongOrNull() ?: 1L

            val result = explodeBomUseCase(tenant.tenantId, TechPackId(id), orderQty)
            if (result.isSuccess) {
                val explosion = result.getOrThrow()
                val json = jsonObjectOf(
                    "techPackId" to jsonOf(explosion.techPackId.value),
                    "orderQuantity" to jsonOf(explosion.orderQuantity),
                    "requirements" to jsonArrayOf(explosion.requirements.map { req ->
                        jsonObjectOf(
                            "line" to TechPackAndYieldDataCodec.encodeBomLine(req.line),
                            "grossQuantityTotal" to MeasureCodec.encodeQuantity(req.grossQuantityTotal)
                        )
                    })
                )
                call.respondJson(json.encode())
            } else {
                call.respondFailure(HttpStatusCode.BadRequest, result.exceptionOrNull() ?: Exception("Unknown error"))
            }
        }
    }
}

private suspend fun ApplicationCall.requireTenant(): TenantContext? {
    val tenant = tenantContextOrNull
    if (tenant == null) {
        respond(HttpStatusCode.NotFound, "No tenant context found")
    }
    return tenant
}

private suspend fun ApplicationCall.respondJson(json: String) {
    respondText(text = json, contentType = ContentType.Application.Json)
}

private suspend fun ApplicationCall.respondFailure(status: HttpStatusCode, error: Throwable) {
    respond(status, error.message ?: "Unknown error")
}
