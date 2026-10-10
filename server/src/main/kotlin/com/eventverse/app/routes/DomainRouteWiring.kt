package com.eventverse.app.routes

import com.eventverse.app.domain.audit.AuditLogRepository
import com.eventverse.app.domain.costing.CostingBenchmarkRepository
import com.eventverse.app.domain.costing.CostingRateCardRepository
import com.eventverse.app.domain.costing.CostingSheetRepository
import com.eventverse.app.domain.crm.ContactRepository
import com.eventverse.app.domain.crm.CrmLeadRepository
import com.eventverse.app.domain.crm.LeadActivityRepository
import com.eventverse.app.domain.customfield.CustomFieldDefinitionRepository
import com.eventverse.app.domain.deal.DealRepository
import com.eventverse.app.domain.deal.storage.PoFileStorage
import com.eventverse.app.domain.discovery.DiscoveryDemandRepository
import com.eventverse.app.domain.discovery.DiscoveryDraftRepository
import com.eventverse.app.domain.invoicing.InvoiceIssuerProfileRepository
import com.eventverse.app.domain.invoicing.InvoicePaymentRepository
import com.eventverse.app.domain.invoicing.InvoiceRepository
import com.eventverse.app.domain.invoicing.InvoiceTemplateRepository
import com.eventverse.app.domain.masterdata.MaterialItemRepository
import com.eventverse.app.domain.masterdata.MaterialPriceRepository
import com.eventverse.app.domain.moduledev.EmbeddingProvider
import com.eventverse.app.domain.moduledev.ModuleBuildRepository
import com.eventverse.app.domain.moduledev.ModuleCatalogRepository
import com.eventverse.app.domain.moduledev.ModuleCustomizationRequestRepository
import com.eventverse.app.domain.moduledev.ModulePricingQuoteRepository
import com.eventverse.app.domain.moduledev.Percentage
import com.eventverse.app.domain.moduledev.SizingWeightsRepository
import com.eventverse.app.domain.orgchart.EmployeeRepository
import com.eventverse.app.domain.pipeline.TenantPipelineRepository
import com.eventverse.app.domain.prospect.FlowTranslationRepository
import com.eventverse.app.domain.prospect.FlowTranslator
import com.eventverse.app.domain.prospect.ProspectLeadRepository
import com.eventverse.app.domain.prospect.ProspectPriceEstimateRepository
import com.eventverse.app.domain.rbac.ModuleAssignmentRepository
import com.eventverse.app.domain.rbac.RoleRepository
import com.eventverse.app.domain.sampling.SamplingOrderRepository
import com.eventverse.app.domain.techpack.TechPackRepository
import com.eventverse.app.domain.tenant.TenantRepository
import com.eventverse.app.infrastructure.KeywordFlowTranslator
import com.eventverse.app.infrastructure.LexicalEmbeddingProvider
import com.eventverse.app.infrastructure.storage.LocalBenchmarkImageStorage
import com.eventverse.app.infrastructure.PostgresBulkWorkOrderRepository
import com.eventverse.app.infrastructure.PostgresContactRepository
import com.eventverse.app.infrastructure.PostgresCostingBenchmarkRepository
import com.eventverse.app.infrastructure.PostgresCostingRateCardRepository
import com.eventverse.app.infrastructure.PostgresCostingSheetRepository
import com.eventverse.app.infrastructure.PostgresCrmLeadRepository
import com.eventverse.app.infrastructure.PostgresCustomFieldDefinitionRepository
import com.eventverse.app.infrastructure.PostgresDealRepository
import com.eventverse.app.infrastructure.PostgresDiscoveryDraftRepository
import com.eventverse.app.infrastructure.PostgresFlowTranslationRepository
import com.eventverse.app.infrastructure.PostgresInternalTransferRepository
import com.eventverse.app.infrastructure.PostgresInvoiceIssuerProfileRepository
import com.eventverse.app.infrastructure.PostgresInvoicePaymentRepository
import com.eventverse.app.infrastructure.PostgresInvoiceRepository
import com.eventverse.app.infrastructure.PostgresInvoiceTemplateRepository
import com.eventverse.app.infrastructure.PostgresLeadActivityRepository
import com.eventverse.app.infrastructure.PostgresMaterialItemRepository
import com.eventverse.app.infrastructure.PostgresMaterialPriceRepository
import com.eventverse.app.infrastructure.PostgresModuleCustomizationRequestRepository
import com.eventverse.app.infrastructure.PostgresProspectLeadRepository
import com.eventverse.app.infrastructure.PostgresProspectPriceEstimateRepository
import com.eventverse.app.infrastructure.PostgresSamplingOrderRepository
import com.eventverse.app.infrastructure.PostgresSizingWeightsRepository
import com.eventverse.app.infrastructure.PostgresTechPackRepository
import com.eventverse.app.infrastructure.PostgresTraceContainerRepository
import com.eventverse.app.infrastructure.storage.S3PoFileStorage
import com.eventverse.app.infrastructure.traceability.BulkTraceWorkOrderProvider
import com.eventverse.app.infrastructure.traceability.CompositeTraceWorkOrderProvider
import com.eventverse.app.infrastructure.traceability.KnitWorksheetBuilder
import com.eventverse.app.infrastructure.traceability.SamplingTraceWorkOrderProvider
import com.eventverse.app.services.GeminiCostingParserService
import com.eventverse.app.services.HeuristicCostingParser
import com.eventverse.app.services.NoopDesignVisionAnalyzer
import com.eventverse.app.domain.prospect.usecases.AnalyzeCoverageUseCase
import com.eventverse.app.domain.prospect.usecases.PriceProspectFlowUseCase
import com.eventverse.app.domain.prospect.usecases.SubmitProspectLeadUseCase
import com.eventverse.app.domain.prospect.usecases.TranslateProspectFlowUseCase
import com.eventverse.app.operationalModuleRoutes
import io.ktor.server.routing.Routing
/**
 * Registrasi rute domain bisnis, dipindah dari `Application.kt` (utang file-size, dipecah per batas
 * tanggung jawab — bukan per baris): corong prospek (module-dev, prospect, discovery platform),
 * CRM/deal, dan seluruh modul operasional. Kelas ini juga pemilik *defaulting* dependensi yang
 * dipakai **eksklusif** oleh blok ini; `Application.module` tetap titik komposisi untuk dependensi
 * yang dipakai lintas seksi. Test yang menyuntik repository lewat `module(...)` tetap bekerja —
 * parameter opsionalnya diteruskan satu-satu ke sini.
 */
class DomainRouteWiring(
    // Dependensi bersama yang sudah diselesaikan di Application.module.
    private val tenants: TenantRepository,
    private val pipeRepo: TenantPipelineRepository,
    private val roleRepo: RoleRepository,
    private val assignmentRepo: ModuleAssignmentRepository,
    private val empRepo: EmployeeRepository,
    private val catalogRepo: ModuleCatalogRepository,
    private val buildRepo: ModuleBuildRepository,
    private val quoteRepo: ModulePricingQuoteRepository,
    private val auditLogRepo: AuditLogRepository,
    private val domainPackRepo: com.eventverse.app.domain.pack.DomainPackRepository,
    private val discoveryDraftRepo: DiscoveryDraftRepository,
    embeddingProvider: EmbeddingProvider?,
    // Parameter opsional yang diteruskan dari Application.module (titik injeksi test).
    flowTranslator: FlowTranslator? = null,
    discoveryDemandRepository: DiscoveryDemandRepository? = null,
    /** Riwayat chat Builder untuk konteks brief developer; default Postgres, test meng-inject in-memory. */
    builderChatRepository: com.eventverse.app.domain.builder.BuilderChatRepository? = null,
    crmLeadRepository: CrmLeadRepository? = null,
    contactRepository: ContactRepository? = null,
    dealRepository: DealRepository? = null,
    poFileStorageOverride: PoFileStorage? = null,
    /** Port storage field FILE (C8, TRD-FIELD-002); default adapter S3 (bucket `S3_BUCKET_FILES`). */
    objectStorageOverride: com.eventverse.app.domain.storage.ObjectStorage? = null,
    /** Row store per modul untuk resolve ref unduhan field FILE (titik injeksi test). */
    private val fieldFileRecordRows: Map<String, com.eventverse.app.domain.discovery.handoff.PrototypeRowRepository> = emptyMap(),
    customFieldDefinitionRepository: CustomFieldDefinitionRepository? = null,
    leadActivityRepository: LeadActivityRepository? = null,
    samplingOrderRepository: SamplingOrderRepository? = null,
    materialItemRepository: MaterialItemRepository? = null,
    materialPriceRepository: MaterialPriceRepository? = null,
    techPackRepository: TechPackRepository? = null,
    invoiceRepository: InvoiceRepository? = null,
    invoiceTemplateRepository: InvoiceTemplateRepository? = null,
    invoicePaymentRepository: InvoicePaymentRepository? = null,
    invoiceIssuerProfileRepository: InvoiceIssuerProfileRepository? = null,
    costingSheetRepository: CostingSheetRepository? = null,
    costingRateCardRepository: CostingRateCardRepository? = null,
    costingBenchmarkRepository: CostingBenchmarkRepository? = null,
    moduleCustomizationRequestRepository: ModuleCustomizationRequestRepository? = null,
    sizingWeightsRepository: SizingWeightsRepository? = null,
    prospectLeadRepository: ProspectLeadRepository? = null,
    flowTranslationRepository: FlowTranslationRepository? = null,
    prospectPriceEstimateRepository: ProspectPriceEstimateRepository? = null
) {
    private val customizationRequestRepo = moduleCustomizationRequestRepository ?: PostgresModuleCustomizationRequestRepository()
    private val sizingWeightsRepo = sizingWeightsRepository ?: PostgresSizingWeightsRepository()
    private val crmLeadRepo = crmLeadRepository ?: PostgresCrmLeadRepository()
    private val crmContactRepo = contactRepository ?: PostgresContactRepository()
    private val crmDealRepo = dealRepository ?: PostgresDealRepository()
    private val poFileStorage = poFileStorageOverride ?: S3PoFileStorage()
    private val objectStorage = objectStorageOverride
        ?: com.eventverse.app.infrastructure.storage.S3ObjectStorage()
    private val leadActivityRepo = leadActivityRepository ?: PostgresLeadActivityRepository()
    private val customFieldRepo = customFieldDefinitionRepository ?: PostgresCustomFieldDefinitionRepository()
    // Sumber baris per modul (TRD-FIELD-004 B1): kontribusi pack terdaftar = produksi; parameter tes menang.
    private val recordRows = com.eventverse.app.tenant.TenantPackContributions.mergeRows(
        com.eventverse.app.tenant.TenantPackContributions.all, fieldFileRecordRows
    )
    // C7 (TRD-FIELD-001 Track B): sumber opsi rujukan & resolver target. Modul target = modul handoff
    // (recordRows) atau CRM; tanpa entri = tidak ada opsi/target (fail-closed).
    private val relationTargetRegistryFor = com.eventverse.app.relation.RelationTargetRegistry.default(crmLeadRepo, recordRows)
    private val relationTargetResolver = com.eventverse.app.relation.RegistryRelationTargetResolver(relationTargetRegistryFor)
    private val samplingOrderRepo = samplingOrderRepository ?: PostgresSamplingOrderRepository()
    private val bulkWorkOrderRepo = PostgresBulkWorkOrderRepository(); private val traceContainerRepo = PostgresTraceContainerRepository(); private val internalTransferRepo = PostgresInternalTransferRepository()
    private val traceWorkOrderProvider = CompositeTraceWorkOrderProvider(
        sampling = SamplingTraceWorkOrderProvider(samplingOrderRepo, traceContainerRepo),
        bulk = BulkTraceWorkOrderProvider(bulkWorkOrderRepo, samplingOrderRepo, traceContainerRepo)
    )
    private val knitWorksheetBuilder = KnitWorksheetBuilder(samplingOrderRepo)
    // Host ini tercetak di setiap QR: mengubahnya kelak mematikan seluruh kartu yang sudah beredar di lantai.
    private val traceScanHost = System.getenv("TRACE_SCAN_HOST")?.takeIf { it.isNotBlank() } ?: "wemade.local"
    private val materialRepo = materialItemRepository ?: PostgresMaterialItemRepository()
    private val materialPriceRepo = materialPriceRepository ?: PostgresMaterialPriceRepository()
    private val techPackRepo = techPackRepository ?: PostgresTechPackRepository()
    private val invoiceRepo = invoiceRepository ?: PostgresInvoiceRepository()
    private val invoiceTemplateRepo = invoiceTemplateRepository ?: PostgresInvoiceTemplateRepository()
    private val invoicePaymentRepo = invoicePaymentRepository ?: PostgresInvoicePaymentRepository()
    private val invoiceIssuerProfileRepo = invoiceIssuerProfileRepository ?: PostgresInvoiceIssuerProfileRepository()
    private val costingSheetRepo = costingSheetRepository ?: PostgresCostingSheetRepository()
    private val costingRateCardRepo = costingRateCardRepository ?: PostgresCostingRateCardRepository()
    private val costingBenchmarkRepo = costingBenchmarkRepository ?: PostgresCostingBenchmarkRepository()

    // Tanpa GEMINI_API_KEY seluruh fitur tetap hidup: impor memakai parser heuristik berbasis
    // label, dan estimator berjalan tanpa petunjuk visual. Yang hilang hanya kenyamanannya,
    // bukan fungsinya — server tidak boleh gagal start karena satu kunci API belum diisi.
    private val geminiService = System.getenv("GEMINI_API_KEY")?.takeIf { it.isNotBlank() }?.let { GeminiCostingParserService(apiKey = it) }
    private val historicalCostingParser = geminiService ?: HeuristicCostingParser()
    private val designVisionAnalyzer = geminiService ?: NoopDesignVisionAnalyzer
    private val benchmarkImageStorage = LocalBenchmarkImageStorage()

    // Word-overlap retrieval, not semantic. Adequate while the corpus is small and the confidence
    // gate turns weak matches into refusals rather than bad prices — see LexicalEmbeddingProvider.
    private val embeddingProviderImpl = embeddingProvider ?: LexicalEmbeddingProvider()

    private val leadRepo = prospectLeadRepository ?: PostgresProspectLeadRepository()
    private val translationRepo = flowTranslationRepository ?: PostgresFlowTranslationRepository()
    private val prospectEstimateRepo = prospectPriceEstimateRepository ?: PostgresProspectPriceEstimateRepository()

    // Keyword matching, not comprehension (KeywordFlowTranslator): free, so safe on a public endpoint; a real model needs rate limiting.
    private val flowTranslatorImpl = flowTranslator ?: KeywordFlowTranslator()

    // Derived from REAL productive hours (~4/day), not a nominal 160-hour month. Using a nominal
    // rate while logging honest hours recovers only half the cost on every quote.
    private val blendedHourlyRate = com.eventverse.app.domain.moduledev.MoneyIdr(
        System.getenv("WEMADE_BLENDED_HOURLY_RATE_IDR")?.toLongOrNull() ?: 250_000L
    )
    private val defaultMargin = Percentage(
        System.getenv("WEMADE_DEFAULT_MARGIN_PERCENT")?.toDoubleOrNull() ?: 35.0
    )
    private val discoveryDemandRepo = discoveryDemandRepository ?: com.eventverse.app.infrastructure.PostgresDiscoveryDemandRepository()
    private val builderChats = builderChatRepository ?: com.eventverse.app.infrastructure.PostgresBuilderChatRepository()

    /** Panggil di dalam `routing { … }` dari `Application.module`. */
    fun registerIn(routing: Routing) = with(routing) {
        moduleDevRoutes(
            catalogRepository = catalogRepo, buildRepository = buildRepo, quoteRepository = quoteRepo,
            requestRepository = customizationRequestRepo, sizingWeightsRepository = sizingWeightsRepo,
            pipelineRepository = pipeRepo, embeddingProvider = embeddingProviderImpl,
            auditLogRepository = auditLogRepo
        )
        prospectRoutes(
            leadRepository = leadRepo, translationRepository = translationRepo, priceEstimateRepository = prospectEstimateRepo,
            submitLeadUseCase = SubmitProspectLeadUseCase(leadRepo), translateUseCase = TranslateProspectFlowUseCase(flowTranslatorImpl, translationRepo, leadRepo),
            analyzeCoverageUseCase = AnalyzeCoverageUseCase(catalogRepo), priceUseCase = PriceProspectFlowUseCase(buildRepository = buildRepo, sizingWeightsRepository = sizingWeightsRepo,
                embeddingProvider = embeddingProviderImpl, defaultBlendedHourlyRate = blendedHourlyRate),
            defaultMarginPercent = defaultMargin
        )
        discoveryPlatformRoutes(
            draftRepository = discoveryDraftRepo, tenantRepository = tenants, domainPackRepository = domainPackRepo,
            probe = com.eventverse.app.infrastructure.PostgresTenantOperationalDataProbe(), catalogRepository = catalogRepo,
            buildRepository = buildRepo, sizingWeightsRepository = sizingWeightsRepo, embeddingProvider = embeddingProviderImpl,
            blendedHourlyRate = blendedHourlyRate, leadRepository = leadRepo, discoveryDemands = discoveryDemandRepo, builderChats = builderChats,
            agent = com.eventverse.app.infrastructure.discovery.DiscoveryAgents.fromEnv(), auditLogRepository = auditLogRepo)
        // Modul khusus tenant (J3) lewat registri — gerbang fail-closed tetap milik tiap modul (TRD-PLAT-004 P2).
        com.eventverse.app.tenant.TenantPackContributions.all.forEach { it.registerRoutes(this, roleRepo, assignmentRepo, relationTargetResolver) }
        crmRoutes(
            leadRepository = crmLeadRepo, contactRepository = crmContactRepo,
            dealRepository = crmDealRepo, customFieldRepository = customFieldRepo,
            employeeRepository = empRepo, roleRepository = roleRepo,
            moduleAssignmentRepository = assignmentRepo, invoiceRepository = invoiceRepo,
            leadActivityRepository = leadActivityRepo,
            relationTargetResolver = relationTargetResolver
        )
        // Opsi rujukan tipe field RELATION (C7, TRD-FIELD-001 Track B) — gerbang modul TARGET per query.
        relationRoutes(
            roleRepository = roleRepo,
            moduleAssignmentRepository = assignmentRepo,
            employeeRepository = empRepo,
            registry = relationTargetRegistryFor
        )
        dealRoutes(
            dealRepository = crmDealRepo, contactRepository = crmContactRepo,
            employeeRepository = empRepo, roleRepository = roleRepo,
            moduleAssignmentRepository = assignmentRepo,
            poFileStorage = poFileStorage, samplingOrderRepository = samplingOrderRepo
        )
        // Berkas tipe field FILE (C8, TRD-FIELD-002) — gerbang modul induk per path + varian CRM.
        fieldFileRoutes(
            objectStorage = objectStorage,
            roleRepository = roleRepo,
            moduleAssignmentRepository = assignmentRepo,
            crmLeadRepository = crmLeadRepo,
            employeeRepository = empRepo,
            recordRows = recordRows
        )
        operationalModuleRoutes(
            samplingOrderRepo = samplingOrderRepo,
            crmDealRepo = crmDealRepo,
            bulkWorkOrderRepo = bulkWorkOrderRepo,
            materialRepo = materialRepo,
            materialPriceRepo = materialPriceRepo,
            customFieldRepo = customFieldRepo,
            techPackRepo = techPackRepo,
            roleRepo = roleRepo,
            assignmentRepo = assignmentRepo,
            invoiceRepo = invoiceRepo,
            invoiceTemplateRepo = invoiceTemplateRepo,
            invoicePaymentRepo = invoicePaymentRepo,
            invoiceIssuerProfileRepo = invoiceIssuerProfileRepo,
            costingSheetRepo = costingSheetRepo,
            costingRateCardRepo = costingRateCardRepo,
            costingBenchmarkRepo = costingBenchmarkRepo,
            pipeRepo = pipeRepo,
            historicalCostingParser = historicalCostingParser,
            designVisionAnalyzer = designVisionAnalyzer,
            benchmarkImageStorage = benchmarkImageStorage,
            traceContainerRepo = traceContainerRepo,
            transferRepo = internalTransferRepo,
            traceWorkOrderProvider = traceWorkOrderProvider,
            knitWorksheetBuilder = knitWorksheetBuilder,
            traceScanHost = traceScanHost,
            poFileStorage = poFileStorage
        )
    }
}
