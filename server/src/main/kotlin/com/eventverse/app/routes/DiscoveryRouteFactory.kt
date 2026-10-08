package com.eventverse.app.routes

import com.eventverse.app.domain.audit.AuditLogRepository
import com.eventverse.app.domain.discovery.DiscoveryAgent
import com.eventverse.app.domain.discovery.DiscoveryDemandRepository
import com.eventverse.app.domain.discovery.DiscoveryDraftRepository
import com.eventverse.app.domain.discovery.usecases.HandoffDiscoveryDraftUseCase
import com.eventverse.app.domain.discovery.usecases.PriceDiscoveryDraftUseCase
import com.eventverse.app.domain.discovery.usecases.SubmitDiscoveryDraftUseCase
import com.eventverse.app.domain.discovery.PrototypePatternRepository
import com.eventverse.app.domain.moduledev.EmbeddingProvider
import com.eventverse.app.domain.moduledev.MoneyIdr
import com.eventverse.app.domain.moduledev.ModuleBuildRepository
import com.eventverse.app.domain.moduledev.ModuleCatalogRepository
import com.eventverse.app.domain.moduledev.SizingWeightsRepository
import com.eventverse.app.domain.pack.DomainPackRepository
import com.eventverse.app.domain.pack.usecases.TenantOperationalDataProbe
import com.eventverse.app.domain.prospect.ProspectLeadRepository
import com.eventverse.app.domain.prospect.usecases.PriceProspectFlowUseCase
import com.eventverse.app.domain.prospect.usecases.SubmitProspectLeadUseCase
import com.eventverse.app.domain.tenant.TenantRepository
import io.ktor.server.routing.Route

/**
 * Pabrik wiring rute discovery (Fase B): merakit use case estimasi/funnel/handoff dari dependensi
 * modul-dev & prospek, supaya `Application.kt` — yang sudah di atas hard limit — hanya memanggil satu
 * fungsi ini (Aturan Ratchet, file-size-rules.md §2).
 */
@Suppress("LongParameterList")
fun Route.discoveryPlatformRoutes(
    draftRepository: DiscoveryDraftRepository,
    tenantRepository: TenantRepository,
    domainPackRepository: DomainPackRepository,
    probe: TenantOperationalDataProbe,
    catalogRepository: ModuleCatalogRepository,
    buildRepository: ModuleBuildRepository,
    sizingWeightsRepository: SizingWeightsRepository,
    embeddingProvider: EmbeddingProvider,
    blendedHourlyRate: MoneyIdr,
    leadRepository: ProspectLeadRepository,
    agent: DiscoveryAgent,
    auditLogRepository: AuditLogRepository,
    prototypePatterns: PrototypePatternRepository? = null,
    discoveryDemands: DiscoveryDemandRepository? = null,
    builderChats: com.eventverse.app.domain.builder.BuilderChatRepository? = null
) {
    val priceDraft = PriceDiscoveryDraftUseCase(
        billableCatalog = { catalogRepository.findBillable() },
        priceProspectFlow = PriceProspectFlowUseCase(
            buildRepository = buildRepository,
            sizingWeightsRepository = sizingWeightsRepository,
            embeddingProvider = embeddingProvider,
            defaultBlendedHourlyRate = blendedHourlyRate
        )
    )
    // Harga draf kerja tenant untuk panel di /builder/prototype (gerbang builder, bukan pemilik draf).
    builderPriceRoutes(draftRepository, priceDraft)
    // C4/C6 (PLAN-proto-C): brief kebutuhan dan usulan operasi spec — keduanya baca-saja, di belakang gerbang builder.
    builderBriefRoutes(draftRepository, priceDraft, builderChats ?: com.eventverse.app.infrastructure.PostgresBuilderChatRepository())
    builderSpecOpRoutes()
    val demands = discoveryDemands ?: com.eventverse.app.infrastructure.PostgresDiscoveryDemandRepository()
    discoveryInterviewRoutes(draftRepository, demands, com.eventverse.app.infrastructure.discovery.InterviewAgents.fromEnv(),
        com.eventverse.app.infrastructure.discovery.InterviewAgents.plannerFromEnv())
    discoveryRoutes(
        repository = draftRepository,
        agent = agent,
        tenantRepository = tenantRepository,
        priceDraft = priceDraft,
        submitDraft = SubmitDiscoveryDraftUseCase(draftRepository, leadRepository, SubmitProspectLeadUseCase(leadRepository)),
        handoffDraft = HandoffDiscoveryDraftUseCase(draftRepository, tenantRepository, domainPackRepository, probe),
        // Default di sini, bukan di Application.kt: file itu sudah di atas hard limit (ratchet),
        // dan test meng-inject in-memory lewat parameter supaya tidak menulis ke DB pengembang.
        prototypePatterns = prototypePatterns ?: com.eventverse.app.infrastructure.PostgresPrototypePatternRepository(),
        demands = demands,
        auditLogRepository = auditLogRepository
    )
    // Cetakan blueprint (Fase D) terdaftar terpisah karena gerbangnya berbeda: ia menerima tiket
    // pendek `?ticket=` di samping Bearer, agar PDF bisa dibuka di tab browser.
    discoveryBlueprintPdfRoutes(repository = draftRepository)
}
