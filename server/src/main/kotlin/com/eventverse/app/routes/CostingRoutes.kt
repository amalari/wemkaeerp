package com.eventverse.app.routes

import com.eventverse.app.domain.auth.Permission
import com.eventverse.app.domain.costing.*
import com.eventverse.app.domain.costing.usecases.*
import com.eventverse.app.domain.masterdata.MaterialItemRepository
import com.eventverse.app.domain.masterdata.MaterialPriceRepository
import com.eventverse.app.domain.pipeline.CostingBehavior
import com.eventverse.app.domain.rbac.AccessLevel
import com.eventverse.app.domain.rbac.BusinessModule
import com.eventverse.app.domain.rbac.ModuleAssignmentRepository
import com.eventverse.app.domain.rbac.RoleId
import com.eventverse.app.domain.rbac.RoleRepository
import com.eventverse.app.domain.techpack.TechPackRepository
import com.eventverse.app.domain.tenant.TenantContext
import com.eventverse.app.plugins.callerPrincipalOrNull
import com.eventverse.app.plugins.tenantContextOrNull
import com.eventverse.app.shared.contracts.CostingCalculationResultCodec
import com.eventverse.app.shared.costing.CostingRateCardCodec
import com.eventverse.app.shared.costing.CostingSheetCodec
import com.eventverse.app.shared.json.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.datetime.Clock

fun Route.costingRoutes(
    sheetRepository: CostingSheetRepository,
    rateCardRepository: CostingRateCardRepository,
    techPackRepository: TechPackRepository,
    materialRepository: MaterialItemRepository,
    materialPriceRepository: MaterialPriceRepository,
    roleRepository: RoleRepository? = null,
    moduleAssignmentRepository: ModuleAssignmentRepository? = null,
    clock: Clock = Clock.System
) {
    val createDraftUseCase = CreateCostingSheetDraftUseCase(sheetRepository, clock)
    val calculateUseCase = CalculateCostingSheetUseCase(
        sheetRepository = sheetRepository,
        techPackRepository = techPackRepository,
        materialRepository = materialRepository,
        materialPriceRepository = materialPriceRepository,
        rateCardRepository = rateCardRepository,
        clock = clock
    )
    val repriceUseCase = RepriceCostingSheetUseCase(sheetRepository, calculateUseCase, clock)
    val overrideParamsUseCase = OverrideCostingParametersUseCase(sheetRepository, clock)
    val submitUseCase = SubmitCostingSheetForApprovalUseCase(sheetRepository, rateCardRepository, clock)
    val approveUseCase = ApproveCostingSheetUseCase(sheetRepository, clock)
    val rejectUseCase = RejectCostingSheetUseCase(sheetRepository, clock)
    val reviseUseCase = ReviseCostingSheetUseCase(sheetRepository, createDraftUseCase, clock)
    val driftUseCase = DetectCostingSheetDriftUseCase(
        sheetRepository = sheetRepository,
        techPackRepository = techPackRepository,
        materialRepository = materialRepository,
        materialPriceRepository = materialPriceRepository,
        rateCardRepository = rateCardRepository,
        clock = clock
    )
    val simulateUseCase = SimulateCostingUseCase(
        techPackRepository = techPackRepository,
        materialRepository = materialRepository,
        materialPriceRepository = materialPriceRepository,
        rateCardRepository = rateCardRepository,
        clock = clock
    )
    val upsertRateCardUseCase = UpsertCostingRateCardUseCase(rateCardRepository, clock)
    val getListUseCase = GetCostingSheetListUseCase(sheetRepository)
    val telemetryUseCase = GetCostingNodeTelemetryUseCase(sheetRepository, clock)

    route("/api/tenant/costing") {

        // GET /api/tenant/costing/sheets
        get("/sheets") {
            val tenant = call.requireTenant() ?: return@get
            val techPackId = call.request.queryParameters["techPackId"]?.takeIf { it.isNotBlank() }
            val status = call.request.queryParameters["status"]?.let {
                runCatching { CostingSheetStatus.valueOf(it) }.getOrNull()
            }

            val sheets = getListUseCase(
                tenantId = tenant.tenantId,
                techPackId = techPackId,
                status = status
            ).getOrElse {
                return@get call.respond(HttpStatusCode.InternalServerError, it.message ?: "Failed to list costing sheets")
            }

            val jsonArr = jsonArrayOf(sheets.map(CostingSheetCodec::encode))
            call.respondText(jsonArr.encode(), ContentType.Application.Json)
        }

        // POST /api/tenant/costing/sheets (Create Draft)
        post("/sheets") {
            val tenant = call.requireTenant() ?: return@post
            val bodyText = call.receiveText()
            val json = runCatching { JsonParser.parse(bodyText) as? JsonValue.Obj }.getOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest, "Invalid JSON body")

            val techPackId = json.string("techPackId")
                ?: return@post call.respond(HttpStatusCode.BadRequest, "Field 'techPackId' is required")
            val orderQuantity = json.long("orderQuantity")
                ?: return@post call.respond(HttpStatusCode.BadRequest, "Field 'orderQuantity' is required")
            val behaviorStr = json.string("behavior")
            val behavior = behaviorStr?.let { code ->
                CostingBehavior.entries.firstOrNull { it.code == code || it.name.equals(code, ignoreCase = true) }
            } ?: CostingBehavior.FULL_PACKAGE_COGS

            val pricingAsOf = json.string("pricingAsOf")?.let {
                com.eventverse.app.shared.common.DateTimeCodec.parseInstantOrNull(it)
            } ?: clock.now()

            val overrides = json.obj("parameterOverrides")?.entries?.mapNotNull { (k, v) ->
                when (v) {
                    is JsonValue.Str -> k to v.value
                    is JsonValue.Num -> k to v.raw
                    is JsonValue.Bool -> k to v.value.toString()
                    else -> null
                }
            }?.toMap() ?: emptyMap()

            val caller = call.callerPrincipalOrNull?.userId

            val command = CreateCostingSheetCommand(
                tenantId = tenant.tenantId,
                techPackId = techPackId,
                orderQuantity = orderQuantity,
                behavior = behavior,
                pricingAsOf = pricingAsOf,
                parameterOverrides = overrides,
                createdByUserId = caller
            )

            val created = createDraftUseCase(command).getOrElse {
                return@post call.respond(HttpStatusCode.BadRequest, it.message ?: "Failed to create costing draft")
            }

            call.respondText(CostingSheetCodec.encode(created).encode(), ContentType.Application.Json, HttpStatusCode.Created)
        }

        // GET /api/tenant/costing/sheets/{id}
        get("/sheets/{id}") {
            val tenant = call.requireTenant() ?: return@get
            val idStr = call.parameters["id"] ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing sheet id")
            val sheet = sheetRepository.findById(tenant.tenantId, CostingSheetId(idStr))
                ?: return@get call.respond(HttpStatusCode.NotFound, "Costing sheet not found: $idStr")

            call.respondText(CostingSheetCodec.encode(sheet).encode(), ContentType.Application.Json)
        }

        // POST /api/tenant/costing/sheets/{id}/calculate
        post("/sheets/{id}/calculate") {
            val tenant = call.requireTenant() ?: return@post
            val idStr = call.parameters["id"] ?: return@post call.respond(HttpStatusCode.BadRequest, "Missing sheet id")
            val bodyText = call.receiveText().trim()
            val nodeParams = if (bodyText.isNotBlank()) {
                (JsonParser.parse(bodyText) as? JsonValue.Obj)?.stringMap("nodeParams") ?: emptyMap()
            } else emptyMap()

            val updated = calculateUseCase(
                tenantId = tenant.tenantId,
                sheetId = CostingSheetId(idStr),
                nodeParams = nodeParams
            ).getOrElse {
                return@post call.respond(HttpStatusCode.BadRequest, it.message ?: "Failed to calculate costing sheet")
            }

            call.respondText(CostingSheetCodec.encode(updated).encode(), ContentType.Application.Json)
        }

        // POST /api/tenant/costing/sheets/{id}/recalculate (alias)
        post("/sheets/{id}/recalculate") {
            val tenant = call.requireTenant() ?: return@post
            val idStr = call.parameters["id"] ?: return@post call.respond(HttpStatusCode.BadRequest, "Missing sheet id")
            val updated = calculateUseCase(tenant.tenantId, CostingSheetId(idStr)).getOrElse {
                return@post call.respond(HttpStatusCode.BadRequest, it.message ?: "Failed to recalculate costing sheet")
            }
            call.respondText(CostingSheetCodec.encode(updated).encode(), ContentType.Application.Json)
        }

        // POST /api/tenant/costing/sheets/{id}/reprice
        post("/sheets/{id}/reprice") {
            val tenant = call.requireTenant() ?: return@post
            val idStr = call.parameters["id"] ?: return@post call.respond(HttpStatusCode.BadRequest, "Missing sheet id")
            val bodyText = call.receiveText().trim()
            val json = if (bodyText.isNotBlank()) runCatching { JsonParser.parse(bodyText) as? JsonValue.Obj }.getOrNull() else null
            val pricingAsOf = json?.string("pricingAsOf")?.let {
                com.eventverse.app.shared.common.DateTimeCodec.parseInstantOrNull(it)
            } ?: clock.now()

            val updated = repriceUseCase(
                tenantId = tenant.tenantId,
                sheetId = CostingSheetId(idStr),
                pricingAsOf = pricingAsOf
            ).getOrElse {
                return@post call.respond(HttpStatusCode.BadRequest, it.message ?: "Failed to reprice costing sheet")
            }

            call.respondText(CostingSheetCodec.encode(updated).encode(), ContentType.Application.Json)
        }

        // POST /api/tenant/costing/sheets/{id}/override-parameters
        post("/sheets/{id}/override-parameters") {
            val tenant = call.requireTenant() ?: return@post
            val idStr = call.parameters["id"] ?: return@post call.respond(HttpStatusCode.BadRequest, "Missing sheet id")
            val bodyText = call.receiveText()
            val json = runCatching { JsonParser.parse(bodyText) as? JsonValue.Obj }.getOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest, "Invalid JSON body")

            val overrides = json.obj("parameterOverrides")?.entries?.mapNotNull { (k, v) ->
                when (v) {
                    is JsonValue.Str -> k to v.value
                    is JsonValue.Num -> k to v.raw
                    is JsonValue.Bool -> k to v.value.toString()
                    else -> null
                }
            }?.toMap() ?: json.entries.mapNotNull { (k, v) ->
                when (v) {
                    is JsonValue.Str -> k to v.value
                    is JsonValue.Num -> k to v.raw
                    is JsonValue.Bool -> k to v.value.toString()
                    else -> null
                }
            }.toMap()

            val updated = overrideParamsUseCase(
                tenantId = tenant.tenantId,
                sheetId = CostingSheetId(idStr),
                overrides = overrides
            ).getOrElse {
                return@post call.respond(HttpStatusCode.BadRequest, it.message ?: "Failed to override parameters")
            }

            call.respondText(CostingSheetCodec.encode(updated).encode(), ContentType.Application.Json)
        }

        // POST /api/tenant/costing/sheets/{id}/submit
        post("/sheets/{id}/submit") {
            val tenant = call.requireTenant() ?: return@post
            val idStr = call.parameters["id"] ?: return@post call.respond(HttpStatusCode.BadRequest, "Missing sheet id")
            val caller = call.callerPrincipalOrNull?.userId ?: "system"
            val updated = submitUseCase(
                tenantId = tenant.tenantId,
                sheetId = CostingSheetId(idStr),
                submittedByUserId = caller
            ).getOrElse {
                return@post call.respond(HttpStatusCode.BadRequest, it.message ?: "Failed to submit for approval")
            }
            call.respondText(CostingSheetCodec.encode(updated).encode(), ContentType.Application.Json)
        }

        // POST /api/tenant/costing/sheets/{id}/approve (Gap #2: RBAC check)
        post("/sheets/{id}/approve") {
            val tenant = call.requireTenant() ?: return@post
            val idStr = call.parameters["id"] ?: return@post call.respond(HttpStatusCode.BadRequest, "Missing sheet id")

            // RBAC Check for APPROVE_COSTING
            val principal = call.callerPrincipalOrNull
            val isAuthorized = when {
                principal == null -> false
                principal.isPlatformSuperadmin -> true
                principal.role.defaultPermissions.contains(Permission.APPROVE_COSTING) -> true
                roleRepository != null && principal.customRoleId != null -> {
                    val role = roleRepository.findById(tenant.tenantId, RoleId(principal.customRoleId))
                    role != null && (
                        role.hasAccess(BusinessModule.COSTING_HPP, AccessLevel.MANAGE) ||
                        role.isSystemOwnerRole
                    )
                }
                else -> false
            }

            if (!isAuthorized) {
                return@post call.respond(
                    HttpStatusCode.Forbidden,
                    "Akses ditolak: Anda tidak memiliki wewenang APPROVE_COSTING untuk menyetujui lembar HPP ini"
                )
            }

            val approverId = principal?.userId?.ifBlank { "system" } ?: "system"
            val updated = approveUseCase(
                tenantId = tenant.tenantId,
                sheetId = CostingSheetId(idStr),
                approvedByUserId = approverId
            ).getOrElse {
                return@post call.respond(HttpStatusCode.BadRequest, it.message ?: "Failed to approve costing sheet")
            }

            call.respondText(CostingSheetCodec.encode(updated).encode(), ContentType.Application.Json)
        }

        // POST /api/tenant/costing/sheets/{id}/reject
        post("/sheets/{id}/reject") {
            val tenant = call.requireTenant() ?: return@post
            val idStr = call.parameters["id"] ?: return@post call.respond(HttpStatusCode.BadRequest, "Missing sheet id")
            val bodyText = call.receiveText()
            val json = runCatching { JsonParser.parse(bodyText) as? JsonValue.Obj }.getOrNull()
            val reason = json?.string("reason")?.takeIf { it.isNotBlank() } ?: "Ditolak tanpa keterangan"
            val rejecterId = call.callerPrincipalOrNull?.userId ?: "system"

            val updated = rejectUseCase(
                tenantId = tenant.tenantId,
                sheetId = CostingSheetId(idStr),
                rejectedByUserId = rejecterId,
                reason = reason
            ).getOrElse {
                return@post call.respond(HttpStatusCode.BadRequest, it.message ?: "Failed to reject costing sheet")
            }

            call.respondText(CostingSheetCodec.encode(updated).encode(), ContentType.Application.Json)
        }

        // POST /api/tenant/costing/sheets/{id}/revise
        post("/sheets/{id}/revise") {
            val tenant = call.requireTenant() ?: return@post
            val idStr = call.parameters["id"] ?: return@post call.respond(HttpStatusCode.BadRequest, "Missing sheet id")
            val caller = call.callerPrincipalOrNull?.userId ?: "system"

            val newDraft = reviseUseCase(
                tenantId = tenant.tenantId,
                sheetId = CostingSheetId(idStr),
                revisedByUserId = caller
            ).getOrElse {
                return@post call.respond(HttpStatusCode.BadRequest, it.message ?: "Failed to revise costing sheet")
            }

            call.respondText(CostingSheetCodec.encode(newDraft).encode(), ContentType.Application.Json, HttpStatusCode.Created)
        }

        // GET /api/tenant/costing/sheets/{id}/drift
        get("/sheets/{id}/drift") {
            val tenant = call.requireTenant() ?: return@get
            val idStr = call.parameters["id"] ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing sheet id")
            val drift = driftUseCase(tenant.tenantId, CostingSheetId(idStr)).getOrElse {
                return@get call.respond(HttpStatusCode.BadRequest, it.message ?: "Failed to detect drift")
            }

            val json = when (drift) {
                is CostingDrift.None -> jsonObjectOf("driftStatus" to jsonOf("NONE"))
                is CostingDrift.Detected -> jsonObjectOf(
                    "driftStatus" to jsonOf("DETECTED"),
                    "deltaPerUnit" to com.eventverse.app.shared.common.MeasureCodec.encodeMoney(drift.deltaPerUnit),
                    "deltaPercent" to jsonOf(drift.deltaPercent),
                    "changedInputs" to jsonArrayOf(
                        drift.changedInputs.map { input ->
                            jsonObjectOf(
                                "inputKey" to jsonOf(input.inputKey),
                                "previousValue" to jsonOf(input.previousValue),
                                "currentValue" to jsonOf(input.currentValue)
                            )
                        }
                    ),
                    "currentResult" to CostingCalculationResultCodec.encode(drift.currentResult)
                )
            }
            call.respondText(json.encode(), ContentType.Application.Json)
        }

        // POST /api/tenant/costing/simulate
        post("/simulate") {
            val tenant = call.requireTenant() ?: return@post
            val bodyText = call.receiveText()
            val json = runCatching { JsonParser.parse(bodyText) as? JsonValue.Obj }.getOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest, "Invalid JSON body")

            val techPackId = json.string("techPackId")
                ?: return@post call.respond(HttpStatusCode.BadRequest, "Field 'techPackId' is required")
            val orderQuantity = json.long("orderQuantity")
                ?: return@post call.respond(HttpStatusCode.BadRequest, "Field 'orderQuantity' is required")
            val behaviorStr = json.string("behavior")
            val behavior = behaviorStr?.let { code ->
                CostingBehavior.entries.firstOrNull { it.code == code || it.name.equals(code, ignoreCase = true) }
            } ?: CostingBehavior.FULL_PACKAGE_COGS

            val pricingAsOf = json.string("pricingAsOf")?.let {
                com.eventverse.app.shared.common.DateTimeCodec.parseInstantOrNull(it)
            } ?: clock.now()

            val overrides = json.obj("parameterOverrides")?.entries?.mapNotNull { (k, v) ->
                when (v) {
                    is JsonValue.Str -> k to v.value
                    is JsonValue.Num -> k to v.raw
                    is JsonValue.Bool -> k to v.value.toString()
                    else -> null
                }
            }?.toMap() ?: emptyMap()

            val result = simulateUseCase(
                SimulateCostingCommand(
                    tenantId = tenant.tenantId,
                    techPackId = techPackId,
                    orderQuantity = orderQuantity,
                    behavior = behavior,
                    parameterOverrides = overrides,
                    pricingAsOf = pricingAsOf
                )
            ).getOrElse {
                return@post call.respond(HttpStatusCode.BadRequest, it.message ?: "Failed to simulate costing")
            }

            call.respondText(CostingCalculationResultCodec.encode(result).encode(), ContentType.Application.Json)
        }

        // GET /api/tenant/costing/rate-card
        get("/rate-card") {
            val tenant = call.requireTenant() ?: return@get
            val behaviorStr = call.request.queryParameters["behavior"]
            val behavior = behaviorStr?.let { code ->
                CostingBehavior.entries.firstOrNull { it.code == code || it.name.equals(code, ignoreCase = true) }
            } ?: CostingBehavior.FULL_PACKAGE_COGS

            val at = call.request.queryParameters["at"]?.let {
                com.eventverse.app.shared.common.DateTimeCodec.parseInstantOrNull(it)
            }

            val card = if (at != null) {
                rateCardRepository.findEffectiveAt(tenant.tenantId, behavior, at)
            } else {
                rateCardRepository.findActive(tenant.tenantId, behavior)
            }

            if (card == null) {
                return@get call.respond(HttpStatusCode.NotFound, "Rate card not found for behavior $behavior")
            }

            call.respondText(CostingRateCardCodec.encode(card).encode(), ContentType.Application.Json)
        }

        // PUT /api/tenant/costing/rate-card
        put("/rate-card") {
            val tenant = call.requireTenant() ?: return@put
            val bodyText = call.receiveText()
            val json = runCatching { JsonParser.parse(bodyText) as? JsonValue.Obj }.getOrNull()
                ?: return@put call.respond(HttpStatusCode.BadRequest, "Invalid JSON body")

            val card = CostingRateCardCodec.decode(json)
            val caller = call.callerPrincipalOrNull?.userId

            val saved = upsertRateCardUseCase(
                tenantId = tenant.tenantId,
                behavior = card.behavior,
                updater = {
                    copy(
                        description = card.description,
                        laborRatePerSamMinute = card.laborRatePerSamMinute,
                        subcontractRatePerSamMinute = card.subcontractRatePerSamMinute,
                        serviceFeePerUnit = card.serviceFeePerUnit,
                        overheadPerUnit = card.overheadPerUnit,
                        packingCostPerUnit = card.packingCostPerUnit,
                        packingCostPerOrder = card.packingCostPerOrder,
                        marginRatio = card.marginRatio,
                        retailMarkupRatio = card.retailMarkupRatio,
                        marketplaceFeeRatio = card.marketplaceFeeRatio,
                        fabricWastageToleranceRatio = card.fabricWastageToleranceRatio,
                        includeFabricCost = card.includeFabricCost,
                        seededFromNodeId = card.seededFromNodeId
                    )
                },
                updatedByUserId = caller
            ).getOrElse {
                return@put call.respond(HttpStatusCode.BadRequest, it.message ?: "Failed to upsert rate card")
            }

            call.respondText(CostingRateCardCodec.encode(saved).encode(), ContentType.Application.Json)
        }

        // GET /api/tenant/costing/telemetry (Gap #1)
        get("/telemetry") {
            val tenant = call.requireTenant() ?: return@get
            val telemetry = telemetryUseCase(tenant.tenantId).getOrElse {
                return@get call.respond(HttpStatusCode.InternalServerError, it.message ?: "Failed to get telemetry")
            }

            val json = jsonObjectOf(
                "pendingSheetCount" to jsonOf(telemetry.pendingSheetCount),
                "overdueApprovalCount" to jsonOf(telemetry.overdueApprovalCount),
                "avgApprovalCycleHours" to jsonOf(telemetry.avgApprovalCycleHours),
                "healthStatus" to jsonOf(telemetry.healthStatus.name)
            )
            call.respondText(json.encode(), ContentType.Application.Json)
        }
    }
}

private suspend fun ApplicationCall.requireTenant(): TenantContext? {
    val ctx = tenantContextOrNull
    if (ctx == null) {
        respond(HttpStatusCode.BadRequest, "Missing or invalid tenant context")
        return null
    }
    return ctx
}
