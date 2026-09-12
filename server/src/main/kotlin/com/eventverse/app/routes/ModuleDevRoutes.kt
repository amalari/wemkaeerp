package com.eventverse.app.routes

import com.eventverse.app.domain.audit.AuditAction
import com.eventverse.app.domain.audit.AuditLogEntry
import com.eventverse.app.domain.audit.AuditLogRepository
import com.eventverse.app.domain.moduledev.CustomizationRequestId
import com.eventverse.app.domain.moduledev.EffortEntryId
import com.eventverse.app.domain.moduledev.EmbeddingProvider
import com.eventverse.app.domain.moduledev.ModuleBuildEffortEntry
import com.eventverse.app.domain.moduledev.ModuleBuildId
import com.eventverse.app.domain.moduledev.ModuleBuildRecord
import com.eventverse.app.domain.moduledev.ModuleBuildRepository
import com.eventverse.app.domain.moduledev.ModuleCatalogEntryId
import com.eventverse.app.domain.moduledev.ModuleCatalogRepository
import com.eventverse.app.domain.moduledev.ModuleCustomizationRequestRepository
import com.eventverse.app.domain.moduledev.ModulePricingQuoteRepository
import com.eventverse.app.domain.moduledev.MoneyIdr
import com.eventverse.app.domain.moduledev.QuoteId
import com.eventverse.app.domain.moduledev.SizingWeightsRepository
import com.eventverse.app.domain.moduledev.WorkHours
import com.eventverse.app.domain.moduledev.usecases.CompleteModuleBuildUseCase
import com.eventverse.app.domain.moduledev.usecases.EstimateModuleBuildUseCase
import com.eventverse.app.domain.moduledev.usecases.GetTenantBillingPreviewUseCase
import com.eventverse.app.domain.moduledev.usecases.LogBuildEffortUseCase
import com.eventverse.app.domain.moduledev.usecases.QuoteModulePriceUseCase
import com.eventverse.app.domain.moduledev.usecases.SubmitCustomizationRequestUseCase
import com.eventverse.app.domain.pipeline.TenantPipelineRepository
import com.eventverse.app.domain.tenant.TenantContext
import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.plugins.callerPrincipalOrNull
import com.eventverse.app.plugins.tenantContextOrNull
import com.eventverse.app.routes.dto.ModuleDevDto
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.datetime.Clock

/**
 * HTTP surface for the module development ledger.
 *
 * Split along the same line as the schema:
 *
 *  - everything under `/api/admin/module-dev` — what work cost us. Already restricted to a
 *    `PLATFORM_SUPERADMIN` by [com.eventverse.app.plugins.TenantResolutionPlugin]'s
 *    `platformRoutePrefixes`, so no extra guard is written here.
 *  - everything under `/api/tenant` — what a factory asked for and what it pays. Tenant-scoped,
 *    with RLS enforced in the repository.
 *
 * No tenant-facing response carries hours, rates or build cost.
 */
fun Route.moduleDevRoutes(
    catalogRepository: ModuleCatalogRepository,
    buildRepository: ModuleBuildRepository,
    quoteRepository: ModulePricingQuoteRepository,
    requestRepository: ModuleCustomizationRequestRepository,
    sizingWeightsRepository: SizingWeightsRepository,
    pipelineRepository: TenantPipelineRepository,
    embeddingProvider: EmbeddingProvider,
    auditLogRepository: AuditLogRepository
) {
    val estimateUseCase = EstimateModuleBuildUseCase(
        buildRepository, sizingWeightsRepository, embeddingProvider
    )
    val logEffortUseCase = LogBuildEffortUseCase(buildRepository)
    val completeBuildUseCase = CompleteModuleBuildUseCase(buildRepository)
    val quoteUseCase = QuoteModulePriceUseCase(buildRepository, quoteRepository)
    val submitRequestUseCase = SubmitCustomizationRequestUseCase(requestRepository, catalogRepository)
    val billingPreviewUseCase = GetTenantBillingPreviewUseCase(
        pipelineRepository, catalogRepository, quoteRepository
    )

    // =========================================================================
    // Platform admin
    // =========================================================================
    route("/api/admin/module-dev") {

        get("/catalog") {
            runCatching { catalogRepository.findAll() }
                .onSuccess { call.respondJson(ModuleDevDto.catalogToJson(it)) }
                .onFailure { call.respondFailure(HttpStatusCode.InternalServerError, it, "Gagal memuat katalog modul") }
        }

        /**
         * Creates a build record and estimates it in one step.
         *
         * Deliberately one call: the estimate has to be frozen at the moment the work is described,
         * because a feature breakdown written after the fact is contaminated by hindsight and is
         * worthless as a predictor. Separate endpoints would make skipping that order easy.
         */
        post("/builds") {
            val body = ModuleDevDto.readCreateBuild(call.receiveText())
            if (body == null) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    "Body harus berisi buildId, catalogEntryId, dan requirementText."
                )
                return@post
            }

            val catalogEntry = catalogRepository.findById(ModuleCatalogEntryId(body.catalogEntryId))
            if (catalogEntry == null) {
                call.respond(HttpStatusCode.NotFound, "Modul ${body.catalogEntryId} tidak ada di katalog.")
                return@post
            }

            val draft = runCatching {
                ModuleBuildRecord(
                    id = ModuleBuildId(body.buildId),
                    catalogEntryId = catalogEntry.id,
                    buildType = body.buildType,
                    archetypeCode = catalogEntry.archetypeCode,
                    requirementText = body.requirementText,
                    customizationRequestId = body.customizationRequestId
                        ?.let { CustomizationRequestId(it) },
                    // Written at the start, not at the end: this is the only moment the real start
                    // time is known, and git cannot reconstruct it afterwards.
                    startedAt = Clock.System.now()
                )
            }.getOrElse {
                call.respondFailure(HttpStatusCode.BadRequest, it, "Data build tidak valid")
                return@post
            }

            estimateUseCase(draft, body.features, body.clarityScore, Clock.System.now())
                .onSuccess { estimation ->
                    call.respondJson(
                        ModuleDevDto.estimationToJson(estimation),
                        HttpStatusCode.Created
                    )
                }
                .onFailure { call.respondFailure(HttpStatusCode.BadRequest, it, "Gagal mengestimasi build") }
        }

        get("/builds/{buildId}") {
            val buildId = call.parameters["buildId"].orEmpty()
            val record = runCatching { buildRepository.findById(ModuleBuildId(buildId)) }.getOrNull()
            if (record == null) {
                call.respond(HttpStatusCode.NotFound, "Build $buildId tidak ditemukan.")
                return@get
            }
            call.respondJson(ModuleDevDto.buildToJson(record))
        }

        post("/builds/{buildId}/effort") {
            val buildId = call.parameters["buildId"].orEmpty()
            val body = ModuleDevDto.readEffort(call.receiveText())
            if (body == null) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    "Body harus berisi entryId, role, phase, hours (> 0), dan hourlyRateIdr. " +
                        "Satuannya JAM, bukan hari."
                )
                return@post
            }

            val entry = runCatching {
                ModuleBuildEffortEntry(
                    id = EffortEntryId(body.entryId),
                    buildRecordId = ModuleBuildId(buildId),
                    role = body.role,
                    phase = body.phase,
                    hours = WorkHours(body.hours),
                    hourlyRateIdr = MoneyIdr(body.hourlyRateIdr),
                    performedBy = body.performedBy,
                    note = body.note,
                    loggedAt = Clock.System.now()
                )
            }.getOrElse {
                call.respondFailure(HttpStatusCode.BadRequest, it, "Catatan jam tidak valid")
                return@post
            }

            logEffortUseCase(entry)
                .onSuccess { call.respondJson(ModuleDevDto.buildToJson(it), HttpStatusCode.Created) }
                .onFailure { call.respondFailure(HttpStatusCode.BadRequest, it, "Gagal mencatat jam") }
        }

        post("/builds/{buildId}/complete") {
            val buildId = call.parameters["buildId"].orEmpty()
            val body = ModuleDevDto.readComplete(call.receiveText())

            completeBuildUseCase(
                buildId = ModuleBuildId(buildId),
                completedAt = Clock.System.now(),
                revisionRoundCount = body.revisionRoundCount,
                leadTimeDays = body.leadTimeDays,
                discoveredScopeDelta = body.discoveredScopeDelta,
                retrospectiveNotes = body.retrospectiveNotes,
                gitRef = body.gitRef
            )
                .onSuccess { record ->
                    call.recordAudit(
                        auditLogRepository,
                        AuditAction.MODULE_BUILD_RECORDED,
                        "Build ${record.id.value} ditutup: ${record.actualHours?.hours} jam, " +
                            "biaya ${record.totalBuildCostIdr?.amount} IDR."
                    )
                    call.respondJson(ModuleDevDto.buildToJson(record))
                }
                .onFailure { call.respondFailure(HttpStatusCode.BadRequest, it, "Gagal menutup build") }
        }

        /**
         * Retrieval preview: what past work the estimator would draw on for this text.
         *
         * Worth exposing on its own because a bad estimate is usually a retrieval problem, and
         * seeing the neighbours is the fastest way to tell whether it picked sensible ones.
         */
        get("/builds/similar") {
            val text = call.request.queryParameters["text"].orEmpty()
            val archetype = call.request.queryParameters["archetype"].orEmpty()
            if (text.isBlank() || archetype.isBlank()) {
                call.respond(HttpStatusCode.BadRequest, "Butuh parameter ?text= dan ?archetype=")
                return@get
            }

            runCatching {
                val probe = embeddingProvider.embed(text)
                buildRepository.findEstimationCandidates(archetype, limit = 20)
                    .mapNotNull { candidate ->
                        val embedding = candidate.embedding ?: return@mapNotNull null
                        val similarity = probe.cosineSimilarityTo(embedding) ?: return@mapNotNull null
                        candidate to similarity
                    }
                    .sortedByDescending { it.second }
            }
                .onSuccess { matches ->
                    call.respondJson(
                        buildString {
                            append("{\"neighbors\":[")
                            matches.forEachIndexed { index, (record, similarity) ->
                                if (index > 0) append(',')
                                append("{\"id\":\"").append(record.id.value)
                                append("\",\"similarity\":").append(similarity)
                                append(",\"sizePoints\":").append(record.sizePoints.value)
                                append(",\"actualHours\":").append(record.actualHours?.hours)
                                append(",\"productivity\":").append(record.productivity)
                                append('}')
                            }
                            append("]}")
                        }
                    )
                }
                .onFailure { call.respondFailure(HttpStatusCode.InternalServerError, it, "Gagal mencari build serupa") }
        }

        post("/quotes") {
            val body = ModuleDevDto.readQuote(call.receiveText())
            if (body == null) {
                call.respond(HttpStatusCode.BadRequest, "Body harus berisi quoteId dan buildId.")
                return@post
            }

            quoteUseCase(
                quoteId = QuoteId(body.quoteId),
                buildId = ModuleBuildId(body.buildId),
                expectedTenantCount = body.expectedTenantCount,
                amortizationMonths = body.amortizationMonths,
                marginPercent = body.marginPercent,
                monthlyMaintenancePercent = body.monthlyMaintenancePercent,
                monthlyInfraCost = body.monthlyInfraCost,
                discountPercent = body.discountPercent,
                tenantId = body.tenantId?.let { TenantId(it) },
                quotedAt = Clock.System.now(),
                blendedHourlyRateOverride = body.blendedHourlyRateIdr
            )
                .onSuccess { quote ->
                    call.recordAudit(
                        auditLogRepository,
                        AuditAction.MODULE_PRICE_QUOTED,
                        "Quote ${quote.id.value}: ${quote.result.monthlyPrice.amount} IDR/bulan " +
                            "(${quote.inputs.basisHours.hours} jam, dibagi " +
                            "${quote.inputs.expectedTenantCount} tenant).",
                        quote.tenantId
                    )
                    call.respondJson(ModuleDevDto.quoteToJson(quote), HttpStatusCode.Created)
                }
                // A low-confidence estimate is refused here, not priced with a caveat.
                .onFailure { call.respondFailure(HttpStatusCode.UnprocessableEntity, it, "Gagal menerbitkan harga") }
        }
    }

    // =========================================================================
    // Tenant-facing
    // =========================================================================
    route("/api/tenant/customization-requests") {

        get {
            val tenant = call.requireTenantContext() ?: return@get
            runCatching { requestRepository.findByTenant(tenant.tenantId) }
                .onSuccess { call.respondJson(ModuleDevDto.customizationRequestToJson(it)) }
                .onFailure { call.respondFailure(HttpStatusCode.InternalServerError, it, "Gagal memuat permintaan") }
        }

        post {
            val tenant = call.requireTenantContext() ?: return@post
            val body = ModuleDevDto.readCustomizationRequest(call.receiveText())
            if (body == null) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    "Body harus berisi requestId, title, dan descriptionRaw."
                )
                return@post
            }

            submitRequestUseCase(
                id = CustomizationRequestId(body.requestId),
                tenantId = tenant.tenantId,
                title = body.title,
                descriptionRaw = body.descriptionRaw,
                catalogEntryId = body.catalogEntryId?.let { ModuleCatalogEntryId(it) },
                requestedByUserId = call.callerPrincipalOrNull?.userId,
                requestedAt = Clock.System.now()
            )
                .onSuccess {
                    call.respondJson(
                        ModuleDevDto.customizationRequestToJson(listOf(it)),
                        HttpStatusCode.Created
                    )
                }
                .onFailure { call.respondFailure(HttpStatusCode.BadRequest, it, "Gagal menyimpan permintaan") }
        }
    }

    get("/api/tenant/billing-preview") {
        val tenant = call.requireTenantContext() ?: return@get
        billingPreviewUseCase(tenant.tenantId)
            .onSuccess { call.respondJson(ModuleDevDto.billingPreviewToJson(it)) }
            .onFailure { call.respondFailure(HttpStatusCode.InternalServerError, it, "Gagal menghitung tagihan") }
    }
}

private suspend fun ApplicationCall.requireTenantContext(): TenantContext? {
    val tenant = tenantContextOrNull
    if (tenant == null) {
        respond(HttpStatusCode.NotFound, "No tenant context found")
    }
    return tenant
}

private suspend fun ApplicationCall.respondJson(
    json: String,
    status: HttpStatusCode = HttpStatusCode.OK
) {
    respondText(json, ContentType.Application.Json, status)
}

private suspend fun ApplicationCall.respondFailure(
    status: HttpStatusCode,
    cause: Throwable,
    fallbackMessage: String
) {
    respond(status, cause.message ?: fallbackMessage)
}

/**
 * Records a money-bearing action.
 *
 * Audit failures never fail the request: the build is already closed or the quote already saved,
 * and rejecting the call would leave the caller believing neither happened.
 */
private suspend fun ApplicationCall.recordAudit(
    auditLogRepository: AuditLogRepository,
    action: AuditAction,
    summary: String,
    tenantId: TenantId? = null
) {
    val actor = callerPrincipalOrNull ?: return
    val target = tenantId ?: actor.tenantId ?: return
    runCatching {
        auditLogRepository.record(
            AuditLogEntry(
                id = "audit-${Clock.System.now().toEpochMilliseconds()}-${action.code}",
                actorUserId = actor.userId,
                actorRole = actor.role,
                targetTenantId = target,
                action = action,
                summary = summary,
                occurredAt = Clock.System.now()
            )
        )
    }
}
