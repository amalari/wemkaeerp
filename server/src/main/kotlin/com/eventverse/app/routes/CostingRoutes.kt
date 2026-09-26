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
import com.eventverse.app.services.DesignVisionAnalyzer
import com.eventverse.app.services.HistoricalCostingParser
import com.eventverse.app.services.HistoricalCostingWorkbookReader
import com.eventverse.app.services.QuickEstimateRatesAssembler
import com.eventverse.app.infrastructure.storage.BenchmarkImageStorage
import com.eventverse.app.shared.costing.CostingBenchmarkCodec
import com.eventverse.app.shared.costing.CostingRateCardCodec
import com.eventverse.app.shared.costing.CostingSheetCodec
import com.eventverse.app.shared.costing.QuickEstimateCodec
import com.eventverse.app.shared.json.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.datetime.Clock
import java.io.File

fun Route.costingRoutes(
    sheetRepository: CostingSheetRepository,
    rateCardRepository: CostingRateCardRepository,
    techPackRepository: TechPackRepository,
    materialRepository: MaterialItemRepository,
    materialPriceRepository: MaterialPriceRepository,
    roleRepository: RoleRepository? = null,
    moduleAssignmentRepository: ModuleAssignmentRepository? = null,
    benchmarkRepository: CostingBenchmarkRepository? = null,
    tenantPipelineRepository: com.eventverse.app.domain.pipeline.TenantPipelineRepository? = null,
    historicalCostingParser: HistoricalCostingParser? = null,
    designVisionAnalyzer: DesignVisionAnalyzer? = null,
    benchmarkImageStorage: BenchmarkImageStorage? = null,
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
    val estimateUseCase = benchmarkRepository?.let { EstimateCostingFromAiDesignUseCase(it) }
    val getTuningUseCase = GetQuickEstimateTuningUseCase(tenantPipelineRepository)
    val importUseCase = benchmarkRepository?.let {
        ImportHistoricalCostingUseCase(
            benchmarkRepository = it,
            idGenerator = { "bmk-" + java.util.UUID.randomUUID().toString() },
            clock = clock
        )
    }
    val ratesAssembler = QuickEstimateRatesAssembler(
        rateCardRepository = rateCardRepository,
        materialRepository = materialRepository,
        materialPriceRepository = materialPriceRepository
    )
    val workbookReader = HistoricalCostingWorkbookReader()
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

        // ── Knowledge Base produk historis & estimator cepat CS ──────────────────────────

        // GET /api/tenant/costing/benchmarks
        get("/benchmarks") {
            val tenant = call.requireTenant() ?: return@get
            val repo = benchmarkRepository
                ?: return@get call.respond(HttpStatusCode.ServiceUnavailable, BENCHMARKS_DISABLED)

            val limit = call.request.queryParameters["limit"]?.toIntOrNull()?.coerceIn(1, 1000) ?: 500
            val benchmarks = repo.listAll(tenant.tenantId, limit)
            call.respondText(
                jsonArrayOf(benchmarks.map(CostingBenchmarkCodec::encode)).encode(),
                ContentType.Application.Json
            )
        }

        /*
         * POST /api/tenant/costing/benchmarks/import?fileName=HPP-PARINARA.xlsx
         *
         * Metadata lewat query, bytes lewat body mentah — sama seperti upload PO di DealRoutes,
         * sehingga client tidak perlu merakit multipart dan server tidak perlu plugin tambahan.
         * Satu request = satu berkas; impor massal 100 berkas dijalankan lewat task Gradle
         * `importHistoricalCosting`, bukan lewat HTTP, karena durasinya menit-menitan.
         */
        post("/benchmarks/import") {
            val tenant = call.requireTenant() ?: return@post
            val repo = benchmarkRepository
                ?: return@post call.respond(HttpStatusCode.ServiceUnavailable, BENCHMARKS_DISABLED)
            val useCase = importUseCase
                ?: return@post call.respond(HttpStatusCode.ServiceUnavailable, BENCHMARKS_DISABLED)

            if (!call.canManageCosting(tenant, roleRepository)) {
                return@post call.respond(
                    HttpStatusCode.Forbidden,
                    "Akses ditolak: impor arsip HPP membutuhkan wewenang kelola modul COSTING_HPP"
                )
            }

            val fileName = call.request.queryParameters["fileName"]?.takeIf { it.isNotBlank() }
                ?: return@post call.respond(HttpStatusCode.BadRequest, "Query 'fileName' wajib diisi")
            if (!fileName.endsWith(".xlsx", ignoreCase = true)) {
                return@post call.respond(
                    HttpStatusCode.UnsupportedMediaType,
                    "Hanya berkas .xlsx yang didukung. Untuk .xls lama, simpan ulang sebagai .xlsx."
                )
            }

            val bytes = call.receive<ByteArray>()
            if (bytes.isEmpty()) {
                return@post call.respond(HttpStatusCode.BadRequest, "Berkas kosong")
            }
            if (bytes.size > MAX_IMPORT_BYTES) {
                return@post call.respond(
                    HttpStatusCode.PayloadTooLarge,
                    "Berkas melebihi batas ${MAX_IMPORT_BYTES / (1024 * 1024)} MB"
                )
            }

            // POI membaca dari berkas, bukan dari stream, supaya gambar yang tertanam bisa diambil.
            val tempFile = File.createTempFile("wemade-hpp-", ".xlsx")
            val report = try {
                tempFile.writeBytes(bytes)
                val extract = workbookReader.read(tempFile).copy(fileName = fileName)
                val imageUrl = extract.embeddedImages.firstOrNull()?.let { image ->
                    benchmarkImageStorage?.store(
                        tenant.slug.value,
                        image.suggestedFileName,
                        image.contentType,
                        image.bytes
                    )
                }
                val parser = historicalCostingParser
                    ?: com.eventverse.app.services.HeuristicCostingParser()
                val parsed = parser.parse(extract, imageUrl)

                useCase(tenant.tenantId, listOf(parsed), skipAlreadyImported = true).getOrElse {
                    return@post call.respond(
                        HttpStatusCode.BadRequest,
                        it.message ?: "Gagal menyimpan hasil impor"
                    )
                }
            } catch (error: Throwable) {
                return@post call.respond(
                    HttpStatusCode.BadRequest,
                    "Berkas tidak dapat dibaca: ${error.message}"
                )
            } finally {
                tempFile.delete()
            }

            call.respondText(
                jsonObjectOf(
                    "importedCount" to jsonOf(report.importedCount),
                    "skippedCount" to jsonOf(report.skippedCount),
                    "imported" to jsonArrayOf(report.imported.map(CostingBenchmarkCodec::encode)),
                    "skipped" to jsonArrayOf(
                        report.skipped.map {
                            jsonObjectOf(
                                "sourceFileName" to jsonOf(it.sourceFileName),
                                "reason" to jsonOf(it.reason)
                            )
                        }
                    ),
                    "totalBenchmarks" to jsonOf(repo.count(tenant.tenantId))
                ).encode(),
                ContentType.Application.Json
            )
        }

        /*
         * POST /api/tenant/costing/analyze-mockup?fileName=&mimeType=
         *
         * Dipisah dari /estimate-quick supaya CS melihat pembacaan AI segera setelah gambar
         * di-upload, lalu bebas mengubah 5 parameter dan menghitung ulang berkali-kali tanpa
         * mengirim gambarnya lagi.
         */
        post("/analyze-mockup") {
            val tenant = call.requireTenant() ?: return@post
            val analyzer = designVisionAnalyzer
                ?: return@post call.respond(
                    HttpStatusCode.ServiceUnavailable,
                    "Analisis gambar belum aktif (GEMINI_API_KEY belum diatur)."
                )

            val mimeType = call.request.queryParameters["mimeType"]?.takeIf { it.isNotBlank() }
                ?: "image/png"
            if (mimeType !in ALLOWED_MOCKUP_MIME_TYPES) {
                return@post call.respond(
                    HttpStatusCode.UnsupportedMediaType,
                    "Tipe gambar $mimeType tidak didukung"
                )
            }

            val bytes = call.receive<ByteArray>()
            if (bytes.isEmpty()) {
                return@post call.respond(HttpStatusCode.BadRequest, "Gambar kosong")
            }
            if (bytes.size > MAX_MOCKUP_BYTES) {
                return@post call.respond(
                    HttpStatusCode.PayloadTooLarge,
                    "Gambar melebihi batas ${MAX_MOCKUP_BYTES / (1024 * 1024)} MB"
                )
            }

            val storedUrl = call.request.queryParameters["fileName"]?.let { fileName ->
                benchmarkImageStorage?.store(tenant.slug.value, fileName, mimeType, bytes)
            }
            val hints = analyzer.analyze(bytes, mimeType)

            call.respondText(
                jsonObjectOf(
                    "hints" to QuickEstimateCodec.encodeVisionHints(hints),
                    "mockupImageUrl" to jsonOf(storedUrl)
                ).encode(),
                ContentType.Application.Json
            )
        }

        // POST /api/tenant/costing/estimate-quick
        post("/estimate-quick") {
            val tenant = call.requireTenant() ?: return@post
            val useCase = estimateUseCase
                ?: return@post call.respond(HttpStatusCode.ServiceUnavailable, BENCHMARKS_DISABLED)

            val bodyText = call.receiveText()
            val json = runCatching { JsonParser.parse(bodyText) as? JsonValue.Obj }.getOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest, "Invalid JSON body")

            val input = runCatching { QuickEstimateCodec.decodeInput(json) }.getOrElse {
                return@post call.respond(HttpStatusCode.BadRequest, it.message ?: "Parameter estimasi tidak valid")
            }
            val hints = json.obj("visionHints")?.let(QuickEstimateCodec::decodeVisionHints)

            val behavior = json.string("behavior")?.let { code ->
                CostingBehavior.entries.firstOrNull { it.code == code || it.name.equals(code, ignoreCase = true) }
            } ?: CostingBehavior.FULL_PACKAGE_COGS

            val rates = ratesAssembler.assemble(
                tenantId = tenant.tenantId,
                behavior = behavior,
                character = input.materialCharacter,
                at = clock.now()
            )

            // Koefisien rumus milik tenant ini — susut benang, tier run kecil, gramasi baku.
            // Tenant yang belum menyetel apa pun tetap dapat default sistem.
            val resolvedTuning = getTuningUseCase(tenant.tenantId)

            val result = useCase(tenant.tenantId, input, rates, hints, resolvedTuning.tuning).getOrElse {
                return@post call.respond(
                    HttpStatusCode.UnprocessableEntity,
                    it.message ?: "Estimasi tidak dapat dihitung"
                )
            }

            // Peringatan setelan tenant (mis. salah ketik di konfigurasi pipeline) digabung ke
            // peringatan estimasi supaya muncul di layar CS, bukan hanya di log server.
            val enriched = result.copy(warnings = result.warnings + resolvedTuning.warnings)

            call.respondText(
                QuickEstimateCodec.encodeResult(enriched).encode(),
                ContentType.Application.Json
            )
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

private const val BENCHMARKS_DISABLED =
    "Knowledge Base produk historis belum aktif di server ini (repository benchmark tidak terpasang)."

/** Lembar HPP tulisan tangan jarang lewat 5 MB; batas ini menahan berkas salah unggah. */
private const val MAX_IMPORT_BYTES = 15 * 1024 * 1024

private const val MAX_MOCKUP_BYTES = 8 * 1024 * 1024

private val ALLOWED_MOCKUP_MIME_TYPES = setOf("image/png", "image/jpeg", "image/jpg", "image/webp")

/**
 * Wewenang kelola modul HPP — dipakai untuk aksi yang mengubah acuan harga seluruh pabrik
 * (impor arsip), sejajar dengan pagar pada endpoint approve.
 */
private suspend fun ApplicationCall.canManageCosting(
    tenant: TenantContext,
    roleRepository: RoleRepository?
): Boolean {
    val principal = callerPrincipalOrNull ?: return false
    if (principal.isPlatformSuperadmin) return true
    if (principal.role.defaultPermissions.contains(Permission.APPROVE_COSTING)) return true

    val customRoleId = principal.customRoleId ?: return false
    val role = roleRepository?.findById(tenant.tenantId, RoleId(customRoleId)) ?: return false
    return role.hasAccess(BusinessModule.COSTING_HPP, AccessLevel.MANAGE) || role.isSystemOwnerRole
}
