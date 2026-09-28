package com.eventverse.app.routes

import com.eventverse.app.domain.deal.storage.PoFileStorage
import com.eventverse.app.domain.deal.DealId
import com.eventverse.app.domain.deal.DealRepository
import com.eventverse.app.domain.deal.DealStage
import com.eventverse.app.domain.process.TenantProcessCatalogRepository
import com.eventverse.app.domain.sampling.*
import com.eventverse.app.domain.sampling.usecases.*
import com.eventverse.app.domain.stageflow.IndustryTemplateCode
import com.eventverse.app.domain.stageflow.StageCode
import com.eventverse.app.domain.stageflow.usecases.GetTenantStageFlowUseCase
import com.eventverse.app.domain.tenant.TenantContext
import com.eventverse.app.domain.transfer.usecases.GetFlowTransferLegsUseCase
import com.eventverse.app.plugins.callerPrincipalOrNull
import com.eventverse.app.plugins.tenantContextOrNull
import com.eventverse.app.shared.json.*
import com.eventverse.app.shared.process.ProcessCatalogCodec
import com.eventverse.app.shared.sampling.SamplingOrderCodec
import com.eventverse.app.shared.sampling.StageWorkInputCodec
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

fun Route.samplingRoutes(
    repository: SamplingOrderRepository,
    dealRepository: DealRepository? = null,
    processCatalogRepository: TenantProcessCatalogRepository? = null,
    poFileStorage: PoFileStorage? = null,
    /**
     * Gerbang perpindahan barang antar lokasi. `null` mematikan gerbang — mempertahankan
     * perilaku lama bagi pemasangan route dan pengujian yang tidak menyuntikkannya.
     */
    flowLegsUseCase: GetFlowTransferLegsUseCase? = null,
    storageRepository: com.eventverse.app.domain.sampling.storage.SampleStorageRecordRepository? = null,
    phaseTagsRepository: com.eventverse.app.domain.process.TenantStagePhaseTagsRepository? = null,
    stageFlowRepository: com.eventverse.app.domain.stageflow.TenantStageFlowRepository? = null
) {
    val listOrdersUseCase = GetSamplingOrderListUseCase(repository)
    val getDetailUseCase = GetSamplingOrderDetailUseCase(repository)
    val createOrderUseCase = CreateSamplingOrderUseCase(repository)
    val updateTechSpecUseCase = UpdateSamplingTechnicalSpecUseCase(repository)
    val toggleMilestoneUseCase = ToggleSamplingMilestoneUseCase(repository)
    val approveOrderUseCase = ApproveSamplingOrderUseCase(repository)

    route("/api/tenant/sampling/orders") {

        // GET /api/tenant/sampling/orders (List)
        get {
            val tenant = call.requireTenant() ?: return@get
            val statusParam = call.request.queryParameters["status"]?.let {
                runCatching { SamplingStatus.valueOf(it) }.getOrNull()
            }

            listOrdersUseCase(tenant.tenantId, statusParam)
                .onSuccess { orders ->
                    // Presign URL mockup segar per order — client menerima link gambar valid,
                    // bukan storage key mentah (lihat SamplingMockupResolution.kt).
                    val resolved = orders.map { withResolvedMockups(it, poFileStorage) }
                    val jsonArray = jsonArrayOf(resolved.map { SamplingOrderCodec.encode(it) })
                    call.respondJson(jsonArray.encode())
                }
                .onFailure { call.respondFailure(HttpStatusCode.InternalServerError, it) }
        }

        // POST /api/tenant/sampling/orders (Create Draft / Standalone / from CRM)
        post {
            val tenant = call.requireTenant() ?: return@post
            val body = call.receiveText()
            val json = JsonParser.parse(body) as? JsonValue.Obj
                ?: return@post call.respond(HttpStatusCode.BadRequest, "Invalid JSON body")

            val clientName = json.string("clientName") ?: ""
            val styleName = json.string("styleName") ?: ""
            val sizeMode = json.string("sizeMode")?.let {
                runCatching { SizeMode.valueOf(it) }.getOrNull()
            } ?: SizeMode.ALL_SIZE

            val deadlineProgram = json.string("deadlineProgram")?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            val deadlineFinishing = json.string("deadlineFinishing")?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            val deadlineDelivery = json.string("deadlineDelivery")?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            val leadId = json.string("leadId")
            val notes = json.string("notes") ?: ""
            val useFactoryPreset = json.boolean("useFactoryAllSizePreset") ?: true

            val command = CreateSamplingOrderCommand(
                tenantId = tenant.tenantId,
                clientName = clientName,
                styleName = styleName,
                sizeMode = sizeMode,
                deadlineProgram = deadlineProgram,
                deadlineFinishing = deadlineFinishing,
                deadlineDelivery = deadlineDelivery,
                leadId = leadId,
                notes = notes,
                useFactoryAllSizePreset = useFactoryPreset
            )

            createOrderUseCase(command)
                .onSuccess { order ->
                    call.respondJson(SamplingOrderCodec.encode(order).encode())
                }
                .onFailure { call.respondFailure(HttpStatusCode.BadRequest, it) }
        }

        // GET /api/tenant/sampling/orders/{id} (Detail)
        get("/{id}") {
            val idParam = call.parameters["id"] ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing ID")
            getDetailUseCase(SamplingOrderId(idParam))
                .onSuccess { order ->
                    val resolved = withResolvedMockups(order, poFileStorage)
                    call.respondJson(SamplingOrderCodec.encode(resolved).encode())
                }
                .onFailure { call.respondFailure(HttpStatusCode.NotFound, it) }
        }

        // PUT /api/tenant/sampling/orders/{id}/technical-spec (Update Tech Spec)
        put("/{id}/technical-spec") {
            val idParam = call.parameters["id"] ?: return@put call.respond(HttpStatusCode.BadRequest, "Missing ID")
            val body = call.receiveText()
            val json = JsonParser.parse(body) as? JsonValue.Obj
                ?: return@put call.respond(HttpStatusCode.BadRequest, "Invalid JSON body")

            val decodedOrder = SamplingOrderCodec.decode(json)
            val command = UpdateSamplingTechnicalSpecCommand(
                orderId = SamplingOrderId(idParam),
                knitSpec = decodedOrder.knitSpec,
                finishedSizes = decodedOrder.finishedSizeCharts,
                rawSizes = decodedOrder.rawKnitSizeCharts,
                machineProgram = decodedOrder.machineProgram,
                yieldAndTiming = decodedOrder.yieldAndTiming
            )

            updateTechSpecUseCase(command)
                .onSuccess { order ->
                    call.respondJson(SamplingOrderCodec.encode(order).encode())
                }
                .onFailure { call.respondFailure(HttpStatusCode.BadRequest, it) }
        }

        // PATCH /api/tenant/sampling/orders/{id}/milestones/{step} (Toggle Milestone)
        patch("/{id}/milestones/{step}") {
            val idParam = call.parameters["id"] ?: return@patch call.respond(HttpStatusCode.BadRequest, "Missing ID")
            val stepParam = call.parameters["step"] ?: return@patch call.respond(HttpStatusCode.BadRequest, "Missing step")
            val step = runCatching { MilestoneStep.valueOf(stepParam) }.getOrNull()
                ?: return@patch call.respond(HttpStatusCode.BadRequest, "Invalid milestone step: $stepParam")

            val body = call.receiveText()
            val json = JsonParser.parse(body) as? JsonValue.Obj
            val isCompleted = json?.boolean("isCompleted") ?: true
            val completedAt = json?.string("completedAt")?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            val notes = json?.string("notes")

            val command = ToggleSamplingMilestoneCommand(
                orderId = SamplingOrderId(idParam),
                step = step,
                isCompleted = isCompleted,
                completedAt = completedAt,
                notes = notes
            )

            toggleMilestoneUseCase(command)
                .onSuccess { order ->
                    call.respondJson(SamplingOrderCodec.encode(order).encode())
                }
                .onFailure { call.respondFailure(HttpStatusCode.BadRequest, it) }
        }

        // POST /api/tenant/sampling/orders/{id}/approve (ACC Produksi / Revisi)
        post("/{id}/approve") {
            val idParam = call.parameters["id"] ?: return@post call.respond(HttpStatusCode.BadRequest, "Missing ID")
            val body = call.receiveText()
            val json = JsonParser.parse(body) as? JsonValue.Obj
            val isApproved = json?.boolean("isApproved") ?: true
            val accNotes = json?.string("accNotes") ?: ""

            val command = ApproveSamplingOrderCommand(
                orderId = SamplingOrderId(idParam),
                isApproved = isApproved,
                notes = accNotes
            )

            approveOrderUseCase(command)
                .onSuccess { order ->
                    call.respondJson(SamplingOrderCodec.encode(order).encode())
                }
                .onFailure { call.respondFailure(HttpStatusCode.BadRequest, it) }
        }

        // PUT /api/tenant/sampling/orders/{id} (Full update)
        put("/{id}") {
            val idParam = call.parameters["id"] ?: return@put call.respond(HttpStatusCode.BadRequest, "Missing ID")
            val body = call.receiveText()
            val json = JsonParser.parse(body) as? JsonValue.Obj
                ?: return@put call.respond(HttpStatusCode.BadRequest, "Invalid JSON body")

            val decodedOrder = SamplingOrderCodec.decode(json)
            val updated = repository.save(decodedOrder.copy(id = SamplingOrderId(idParam), updatedAt = Clock.System.now()))
            call.respondJson(SamplingOrderCodec.encode(updated).encode())
        }

        // POST /api/tenant/sampling/orders/{id}/stage (Advance / Change stage)
        //
        // Body opsional memuat `stageInputs`: lembar kerja dinamis tahap terkait.
        // Jejak audit (siapa yang memindahkan + kapan) dirakit SERVER dari JWT —
        // bukan dari body — supaya identitas aktor tidak bisa dipalsukan klien.
        post("/{id}/stage") {
            val idParam = call.parameters["id"] ?: return@post call.respond(HttpStatusCode.BadRequest, "Missing ID")
            val body = call.receiveText()
            val json = JsonParser.parse(body) as? JsonValue.Obj
            val stageName = json?.string("targetStage") ?: return@post call.respond(HttpStatusCode.BadRequest, "Missing targetStage")
            // Longgar di sini; keanggotaan tahap di kerangka SPK diputuskan domain (advancePipelineStage).
            val targetStage = parseLegacyStageCodeOrNull(stageName) ?: StageCode.parseOrNull(stageName)
                ?: return@post call.respond(HttpStatusCode.BadRequest, "Invalid stage: $stageName")

            val order = repository.findById(SamplingOrderId(idParam))
                ?: return@post call.respond(HttpStatusCode.NotFound, "Sampling order not found")

            val now = Clock.System.now()
            val stageInputs = StageWorkInputCodec.decodeInputs(json?.string("stageInputs"))
            val withInputs = stageInputs.firstOrNull()?.let {
                order.fillStageInput(it.stageCode, it.sections, now)
            } ?: order

            val caller = call.callerPrincipalOrNull
            try {
                // Gerbang perpindahan barang butuh alur efektif SPK ini: alur kustomnya bila ada,
                // kalau tidak template pabrik. Sumber yang sama dipakai panel alur, supaya yang
                // ditolak gerbang persis yang ditandai merah di layar.
                val tenantStageFlow = stageFlowRepository?.let { GetTenantStageFlowUseCase(it)(withInputs.tenantId, call.tenantContextOrNull?.industryTemplate ?: IndustryTemplateCode.KNIT_SWEATER).getOrThrow() }
                val effectiveProcesses = withInputs.customFlowProcesses
                    ?: processCatalogRepository?.findByTenantId(withInputs.tenantId)?.processes
                    ?: emptyList()

                val advanced = AdvanceSamplingStageUseCase(flowLegsUseCase)(
                    AdvanceSamplingStageCommand(
                        order = withInputs,
                        target = targetStage,
                        stages = (withInputs.frozenStageFlow ?: tenantStageFlow?.stages ?: SamplingRoute.DEFAULT_STAGES).map { it.code },
                        processes = effectiveProcesses,
                        actorEmail = caller?.email ?: "unknown",
                        actorRole = caller?.role?.name ?: "UNKNOWN",
                        overrideReason = json.string("overrideReason")?.takeIf { it.isNotBlank() },
                        tenantPhaseTags = phaseTagsRepository?.findByTenantId(withInputs.tenantId),
                        tenantStageFlow = tenantStageFlow,
                        now = now
                    )
                ).getOrThrow()

                val updated = repository.save(advanced)

                val dealId = updated.dealId
                if (!dealId.isNullOrBlank() && dealRepository != null) {
                    val deal = dealRepository.findById(updated.tenantId, DealId(dealId))
                    if (deal != null && deal.stage == DealStage.OPEN) {
                        dealRepository.save(
                            deal.transitionTo(DealStage.PO_RECEIVED, now)
                                .getOrDefault(deal.copy(stage = DealStage.PO_RECEIVED, updatedAt = now))
                        )
                    }
                }

                call.respondJson(SamplingOrderCodec.encode(updated).encode())
            } catch (e: IllegalArgumentException) {
                // Gerbang tahap gagal (mis. lembar CAM belum lengkap) — 422 + pesan domain.
                call.respondFailure(HttpStatusCode.UnprocessableEntity, e)
            }
        }

        // POST /api/tenant/sampling/orders/{id}/finishing/deposits (Finishing Setoran Pcs & Kg)
        post("/{id}/finishing/deposits") {
            val idParam = call.parameters["id"] ?: return@post call.respond(HttpStatusCode.BadRequest, "Missing ID")
            val body = call.receiveText()
            val json = JsonParser.parse(body) as? JsonValue.Obj
                ?: return@post call.respond(HttpStatusCode.BadRequest, "Invalid JSON body")

            val order = repository.findById(SamplingOrderId(idParam))
                ?: return@post call.respond(HttpStatusCode.NotFound, "Sampling order not found")

            val deposit = FinishingDeposit(
                id = "dep_${order.id.value}_${Clock.System.now().toEpochMilliseconds()}",
                samplingOrderId = order.id.value,
                depositDate = json.string("depositDate")?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                    ?: Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date,
                qtyPcs = json.int("qtyPcs") ?: 1,
                weightKg = json.double("weightKg") ?: 0.0,
                scalePhotoKey = json.string("scalePhotoKey"),
                garmentPhotoKey = json.string("garmentPhotoKey"),
                operatorName = json.string("operatorName") ?: "",
                notes = json.string("notes") ?: "",
                createdAt = Clock.System.now()
            )

            val updated = repository.save(order.addFinishingDeposit(deposit, Clock.System.now()))
            call.respondJson(SamplingOrderCodec.encode(updated).encode())
        }

        // POST /api/tenant/sampling/orders/{id}/finishing/vendor (Assign to Makloon Vendor)
        post("/{id}/finishing/vendor") {
            val idParam = call.parameters["id"] ?: return@post call.respond(HttpStatusCode.BadRequest, "Missing ID")
            val body = call.receiveText()
            val json = JsonParser.parse(body) as? JsonValue.Obj
                ?: return@post call.respond(HttpStatusCode.BadRequest, "Invalid JSON body")

            val order = repository.findById(SamplingOrderId(idParam))
                ?: return@post call.respond(HttpStatusCode.NotFound, "Sampling order not found")

            val info = MakloonVendorInfo(
                vendorName = json.string("vendorName") ?: "",
                vendorPhone = json.string("vendorPhone") ?: "",
                sentAt = json.string("sentAt")?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
                expectedReturnAt = json.string("expectedReturnAt")?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
                costPerPcsIdr = json.long("costPerPcsIdr") ?: 0L,
                notes = json.string("notes") ?: ""
            )

            val updated = repository.save(order.assignMakloonVendor(info, Clock.System.now(), call.callerPrincipalOrNull?.email ?: "unknown"))
            call.respondJson(SamplingOrderCodec.encode(updated).encode())
        }

        // POST /api/tenant/sampling/orders/{id}/finishing/vendor-receive (Receive from Makloon Vendor)
        post("/{id}/finishing/vendor-receive") {
            val idParam = call.parameters["id"] ?: return@post call.respond(HttpStatusCode.BadRequest, "Missing ID")
            val body = call.receiveText()
            val json = JsonParser.parse(body) as? JsonValue.Obj
            val returnedAt = json?.string("returnedAt")?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                ?: Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date

            val order = repository.findById(SamplingOrderId(idParam))
                ?: return@post call.respond(HttpStatusCode.NotFound, "Sampling order not found")

            val updated = repository.save(order.recordVendorReturn(returnedAt, Clock.System.now(), call.callerPrincipalOrNull?.email ?: "unknown"))
            call.respondJson(SamplingOrderCodec.encode(updated).encode())
        }

        // POST /api/tenant/sampling/orders/{id}/qc/inspect (Submit QC Report)
        post("/{id}/qc/inspect") {
            val idParam = call.parameters["id"] ?: return@post call.respond(HttpStatusCode.BadRequest, "Missing ID")
            val body = call.receiveText()
            val json = JsonParser.parse(body) as? JsonValue.Obj
                ?: return@post call.respond(HttpStatusCode.BadRequest, "Invalid JSON body")

            val order = repository.findById(SamplingOrderId(idParam))
                ?: return@post call.respond(HttpStatusCode.NotFound, "Sampling order not found")

            val pomList = json.objectArray("pomMeasurements").map {
                QcPomMeasurement(
                    pomName = it.string("pomName") ?: "",
                    targetCm = it.double("targetCm") ?: 0.0,
                    actualCm = it.double("actualCm") ?: 0.0,
                    toleranceCm = it.double("toleranceCm") ?: 1.0,
                    notes = it.string("notes") ?: "",
                    carriedOver = it.boolean("carriedOver") ?: false
                )
            }
            val defects = json.stringArray("defectsFound")
            val qcNotes = json.string("qcNotes") ?: ""
            val kind = json.string("kind")?.let { runCatching { QcInspectionKind.valueOf(it) }.getOrNull() }
                ?: QcInspectionKind.FINISHING

            val report = QcInspectionReport(
                id = "qc_${order.id.value}_${Clock.System.now().toEpochMilliseconds()}",
                samplingOrderId = order.id.value,
                kind = kind,
                inspectorName = json.string("inspectorName") ?: "",
                inspectedAt = Clock.System.now(),
                pieceNo = (json.int("pieceNo") ?: 1).coerceAtLeast(1),
                inspectedQty = (json.int("inspectedQty") ?: 1).coerceAtLeast(1),
                pomMeasurements = pomList,
                defectsFound = defects,
                // Hasil diturunkan di server, bukan diterima dari klien: aturannya milik domain,
                // dan klien mana pun (termasuk yang belum ditulis) harus tunduk pada aturan yang sama.
                qcResult = QcInspectionReport.deriveResult(pomList, qcNotes),
                qcNotes = qcNotes,
                verifiedPhotoFrontKey = json.string("verifiedPhotoFrontKey"),
                verifiedPhotoBackKey = json.string("verifiedPhotoBackKey")
            )

            val updated = repository.save(order.completeQcInspection(report, Clock.System.now()))
            call.respondJson(SamplingOrderCodec.encode(updated).encode())
        }

        // POST /api/tenant/sampling/orders/{id}/revision (Request Revision)
        post("/{id}/revision") {
            val idParam = call.parameters["id"] ?: return@post call.respond(HttpStatusCode.BadRequest, "Missing ID")
            val body = call.receiveText()
            val json = JsonParser.parse(body) as? JsonValue.Obj
            val notes = json?.string("notes") ?: ""

            val order = repository.findById(SamplingOrderId(idParam))
                ?: return@post call.respond(HttpStatusCode.NotFound, "Sampling order not found")

            val updated = repository.save(order.requestRevision(notes, Clock.System.now()))
            call.respondJson(SamplingOrderCodec.encode(updated).encode())
        }

        samplingFlowRoutes(
            repository = repository,
            processCatalogRepository = processCatalogRepository,
            flowLegsUseCase = flowLegsUseCase,
            phaseTagsRepository = phaseTagsRepository
        )
        storageRepository?.let {
            samplingStorageRoutes(repository, it, processCatalogRepository, flowLegsUseCase)
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
