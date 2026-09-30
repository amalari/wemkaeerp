package com.eventverse.app.routes

import com.eventverse.app.domain.discovery.DiscoveryAgent
import com.eventverse.app.domain.discovery.DiscoveryDraftRepository
import com.eventverse.app.domain.discovery.usecases.HandoffDiscoveryDraftUseCase
import com.eventverse.app.domain.discovery.usecases.PriceDiscoveryDraftUseCase
import com.eventverse.app.domain.discovery.usecases.SubmitDiscoveryDraftUseCase
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
    agent: DiscoveryAgent
) = discoveryRoutes(
    repository = draftRepository,
    agent = agent,
    tenantRepository = tenantRepository,
    priceDraft = PriceDiscoveryDraftUseCase(
        billableCatalog = { catalogRepository.findBillable() },
        priceProspectFlow = PriceProspectFlowUseCase(
            buildRepository = buildRepository,
            sizingWeightsRepository = sizingWeightsRepository,
            embeddingProvider = embeddingProvider,
            defaultBlendedHourlyRate = blendedHourlyRate
        )
    ),
    submitDraft = SubmitDiscoveryDraftUseCase(draftRepository, leadRepository, SubmitProspectLeadUseCase(leadRepository)),
    handoffDraft = HandoffDiscoveryDraftUseCase(draftRepository, tenantRepository, domainPackRepository, probe)
)
