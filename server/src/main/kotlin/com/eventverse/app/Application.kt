package com.eventverse.app

import com.eventverse.app.domain.tenant.*
import com.eventverse.app.infrastructure.DatabaseFactory
import com.eventverse.app.infrastructure.InMemoryTenantRepository
import com.eventverse.app.infrastructure.PostgresTenantRepository
import com.eventverse.app.plugins.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.http.content.staticFiles

import com.eventverse.app.domain.auth.*
import com.eventverse.app.infrastructure.PostgresUserRepository
import com.eventverse.app.infrastructure.auth.GoogleAuthService
import com.eventverse.app.infrastructure.auth.JwtTokenService

import com.eventverse.app.domain.rbac.ModuleAssignmentRepository
import com.eventverse.app.domain.rbac.RoleRepository
import com.eventverse.app.infrastructure.PostgresModuleAssignmentRepository
import com.eventverse.app.routes.moduleAssignmentRoutes
import com.eventverse.app.routes.builderRoutes
import com.eventverse.app.routes.onboardingRoutes
import com.eventverse.app.routes.publicAuthRoutes
import com.eventverse.app.domain.orgchart.DepartmentRepository
import com.eventverse.app.domain.orgchart.EmployeeRepository
import com.eventverse.app.infrastructure.PostgresRoleRepository
import com.eventverse.app.infrastructure.PostgresDepartmentRepository
import com.eventverse.app.infrastructure.PostgresEmployeeRepository
import com.eventverse.app.routes.rbacRoutes
import com.eventverse.app.routes.departmentRoutes
import com.eventverse.app.routes.discoveryPlatformRoutes
import com.eventverse.app.routes.employeeRoutes
import com.eventverse.app.routes.pipelineRoutes
import com.eventverse.app.routes.adminRoutes
import com.eventverse.app.routes.domainPackRoutes
import com.eventverse.app.infrastructure.PostgresTenantEntitlementRepository
import com.eventverse.app.infrastructure.PostgresTenantPipelineRepository
import com.eventverse.app.infrastructure.PostgresAuditLogRepository
import com.eventverse.app.domain.pipeline.TenantPipelineRepository
import com.eventverse.app.domain.audit.AuditLogRepository
import com.eventverse.app.domain.moduledev.EmbeddingProvider
import com.eventverse.app.domain.moduledev.ModuleBuildRepository
import com.eventverse.app.domain.moduledev.ModuleCatalogRepository
import com.eventverse.app.domain.moduledev.ModuleCustomizationRequestRepository
import com.eventverse.app.domain.moduledev.ModulePricingQuoteRepository
import com.eventverse.app.domain.moduledev.SizingWeightsRepository
import com.eventverse.app.infrastructure.LexicalEmbeddingProvider
import com.eventverse.app.infrastructure.PostgresModuleBuildRepository
import com.eventverse.app.infrastructure.PostgresModuleCatalogRepository
import com.eventverse.app.infrastructure.PostgresModuleCustomizationRequestRepository
import com.eventverse.app.infrastructure.PostgresModulePricingQuoteRepository
import com.eventverse.app.infrastructure.PostgresSizingWeightsRepository
import com.eventverse.app.routes.moduleDevRoutes
import com.eventverse.app.domain.costing.CostingSheetRepository
import com.eventverse.app.domain.costing.CostingRateCardRepository
import com.eventverse.app.infrastructure.PostgresCostingSheetRepository
import com.eventverse.app.infrastructure.PostgresCostingRateCardRepository
import com.eventverse.app.infrastructure.PostgresCostingBenchmarkRepository
import com.eventverse.app.infrastructure.storage.LocalBenchmarkImageStorage
import com.eventverse.app.services.GeminiCostingParserService
import com.eventverse.app.services.HeuristicCostingParser
import com.eventverse.app.services.NoopDesignVisionAnalyzer
import com.eventverse.app.routes.costingRoutes
import com.eventverse.app.domain.moduledev.Percentage
import com.eventverse.app.domain.prospect.FlowTranslationRepository
import com.eventverse.app.domain.prospect.FlowTranslator
import com.eventverse.app.domain.prospect.ProspectLeadRepository
import com.eventverse.app.domain.prospect.ProspectPriceEstimateRepository
import com.eventverse.app.domain.prospect.usecases.AnalyzeCoverageUseCase
import com.eventverse.app.domain.prospect.usecases.PriceProspectFlowUseCase
import com.eventverse.app.domain.prospect.usecases.SubmitProspectLeadUseCase
import com.eventverse.app.domain.prospect.usecases.TranslateProspectFlowUseCase
import com.eventverse.app.domain.moduledev.MoneyIdr
import com.eventverse.app.infrastructure.KeywordFlowTranslator
import com.eventverse.app.infrastructure.PostgresFlowTranslationRepository
import com.eventverse.app.infrastructure.PostgresProspectLeadRepository
import com.eventverse.app.infrastructure.PostgresProspectPriceEstimateRepository
import com.eventverse.app.routes.prospectRoutes

import com.eventverse.app.domain.crm.CrmLeadRepository
import com.eventverse.app.domain.crm.LeadActivityRepository
import com.eventverse.app.domain.customfield.CustomFieldDefinitionRepository
import com.eventverse.app.infrastructure.PostgresCrmLeadRepository
import com.eventverse.app.infrastructure.PostgresLeadActivityRepository
import com.eventverse.app.infrastructure.PostgresCustomFieldDefinitionRepository
import com.eventverse.app.infrastructure.PostgresBulkWorkOrderRepository
import com.eventverse.app.infrastructure.PostgresTraceContainerRepository
import com.eventverse.app.infrastructure.PostgresInternalTransferRepository
import com.eventverse.app.infrastructure.traceability.BulkTraceWorkOrderProvider
import com.eventverse.app.infrastructure.traceability.CompositeTraceWorkOrderProvider
import com.eventverse.app.infrastructure.traceability.KnitWorksheetBuilder
import com.eventverse.app.infrastructure.traceability.SamplingTraceWorkOrderProvider
import com.eventverse.app.infrastructure.PostgresSamplingOrderRepository
import com.eventverse.app.domain.masterdata.MaterialItemRepository
import com.eventverse.app.domain.masterdata.MaterialPriceRepository
import com.eventverse.app.infrastructure.PostgresMaterialItemRepository
import com.eventverse.app.infrastructure.PostgresMaterialPriceRepository
import com.eventverse.app.routes.crmRoutes
import com.eventverse.app.routes.masterDataRoutes
import com.eventverse.app.routes.productionRoutes
import com.eventverse.app.routes.samplingRoutes
import com.eventverse.app.domain.techpack.TechPackRepository
import com.eventverse.app.infrastructure.PostgresTechPackRepository
import com.eventverse.app.routes.techPackRoutes
import com.eventverse.app.domain.invoicing.InvoiceIssuerProfileRepository
import com.eventverse.app.domain.invoicing.InvoicePaymentRepository
import com.eventverse.app.domain.invoicing.InvoiceRepository
import com.eventverse.app.domain.invoicing.InvoiceTemplateRepository
import com.eventverse.app.infrastructure.PostgresInvoiceIssuerProfileRepository
import com.eventverse.app.infrastructure.PostgresInvoicePaymentRepository
import com.eventverse.app.infrastructure.PostgresInvoiceRepository
import com.eventverse.app.infrastructure.PostgresInvoiceTemplateRepository
import com.eventverse.app.routes.invoicingRoutes
import com.eventverse.app.routes.dealRoutes
import com.eventverse.app.infrastructure.PostgresContactRepository
import com.eventverse.app.infrastructure.PostgresDealRepository
import com.eventverse.app.infrastructure.storage.S3PoFileStorage

fun main() {
    embeddedServer(Netty, port = System.getenv("PORT")?.toIntOrNull() ?: 8081, host = "0.0.0.0", watchPaths = listOf("classes"), module = Application::module)
        .start(wait = true)
}

fun Application.module(
    tenantRepository: TenantRepository? = null,
    userRepository: UserRepository? = null,
    roleRepository: RoleRepository? = null,
    moduleAssignmentRepository: ModuleAssignmentRepository? = null,
    departmentRepository: DepartmentRepository? = null,
    employeeRepository: EmployeeRepository? = null,
    pipelineRepository: com.eventverse.app.domain.pipeline.TenantPipelineRepository? = null,
    entitlementRepository: com.eventverse.app.domain.pipeline.TenantEntitlementRepository? = null,
    auditLogRepository: AuditLogRepository? = null,
    moduleCatalogRepository: ModuleCatalogRepository? = null,
    moduleBuildRepository: ModuleBuildRepository? = null,
    modulePricingQuoteRepository: ModulePricingQuoteRepository? = null,
    moduleCustomizationRequestRepository: ModuleCustomizationRequestRepository? = null,
    sizingWeightsRepository: SizingWeightsRepository? = null,
    embeddingProvider: EmbeddingProvider? = null,
    prospectLeadRepository: ProspectLeadRepository? = null,
    flowTranslationRepository: FlowTranslationRepository? = null,
    prospectPriceEstimateRepository: ProspectPriceEstimateRepository? = null,
    flowTranslator: FlowTranslator? = null,
    discoveryDraftRepository: com.eventverse.app.domain.discovery.DiscoveryDraftRepository? = null,
    discoveryDemandRepository: com.eventverse.app.domain.discovery.DiscoveryDemandRepository? = null,
    crmLeadRepository: CrmLeadRepository? = null,
    contactRepository: com.eventverse.app.domain.crm.ContactRepository? = null,
    dealRepository: com.eventverse.app.domain.deal.DealRepository? = null,
    poFileStorage: com.eventverse.app.domain.deal.storage.PoFileStorage? = null,
    customFieldDefinitionRepository: CustomFieldDefinitionRepository? = null,
    leadActivityRepository: LeadActivityRepository? = null,
    samplingOrderRepository: com.eventverse.app.domain.sampling.SamplingOrderRepository? = null,
    materialItemRepository: MaterialItemRepository? = null,
    materialPriceRepository: MaterialPriceRepository? = null,
    techPackRepository: TechPackRepository? = null,
    invoiceRepository: InvoiceRepository? = null,
    invoiceTemplateRepository: InvoiceTemplateRepository? = null,
    invoicePaymentRepository: InvoicePaymentRepository? = null,
    invoiceIssuerProfileRepository: InvoiceIssuerProfileRepository? = null,
    costingSheetRepository: CostingSheetRepository? = null,
    costingRateCardRepository: CostingRateCardRepository? = null,
    costingBenchmarkRepository: com.eventverse.app.domain.costing.CostingBenchmarkRepository? = null,
    domainPackRepository: com.eventverse.app.domain.pack.DomainPackRepository? = null,
    builderDeploymentRepository: com.eventverse.app.domain.builder.BuilderDeploymentRepository? = null,
    builderChatRepository: com.eventverse.app.domain.builder.BuilderChatRepository? = null,
    builderAgent: com.eventverse.app.domain.builder.BuilderAgent? = null,
    builderBuildRequests: com.eventverse.app.domain.builder.BuilderBuildRequestRepository? = null,
    builderProbe: com.eventverse.app.domain.pack.usecases.TenantOperationalDataProbe? = null,
    builderAuditLog: com.eventverse.app.domain.audit.AuditLogRepository? = null,
    builderBillingInvoices: com.eventverse.app.domain.builder.SubscriptionInvoiceRepository? = null,
    builderBillingPreview: com.eventverse.app.domain.builder.TenantBillingPreviewSource? = null
) {
    val repository = tenantRepository ?: run { DatabaseFactory.init(); PostgresTenantRepository() }
    val userRepo = userRepository ?: PostgresUserRepository()
    val roleRepo = roleRepository ?: PostgresRoleRepository()
    val assignmentRepo = moduleAssignmentRepository ?: PostgresModuleAssignmentRepository()
    val deptRepo = departmentRepository ?: PostgresDepartmentRepository()
    val empRepo = employeeRepository ?: PostgresEmployeeRepository()
    val pipeRepo = pipelineRepository ?: PostgresTenantPipelineRepository()
    val entitlementRepo = entitlementRepository ?: PostgresTenantEntitlementRepository()
    val domainPackRepo = domainPackRepository ?: com.eventverse.app.infrastructure.PostgresDomainPackRepository()
    val auditLogRepo = auditLogRepository ?: PostgresAuditLogRepository()
    val catalogRepo = moduleCatalogRepository ?: PostgresModuleCatalogRepository()
    val buildRepo = moduleBuildRepository ?: PostgresModuleBuildRepository()
    val quoteRepo = modulePricingQuoteRepository ?: PostgresModulePricingQuoteRepository()
    val customizationRequestRepo = moduleCustomizationRequestRepository ?: PostgresModuleCustomizationRequestRepository()
    val sizingWeightsRepo = sizingWeightsRepository ?: PostgresSizingWeightsRepository()
    val crmLeadRepo = crmLeadRepository ?: PostgresCrmLeadRepository()
    val crmContactRepo = contactRepository ?: PostgresContactRepository()
    val crmDealRepo = dealRepository ?: PostgresDealRepository()
    val poFileStorage = poFileStorage ?: S3PoFileStorage()
    val leadActivityRepo = leadActivityRepository ?: PostgresLeadActivityRepository()
    val customFieldRepo = customFieldDefinitionRepository ?: PostgresCustomFieldDefinitionRepository()
    val samplingOrderRepo = samplingOrderRepository ?: PostgresSamplingOrderRepository()
    val bulkWorkOrderRepo = PostgresBulkWorkOrderRepository(); val traceContainerRepo = PostgresTraceContainerRepository(); val internalTransferRepo = PostgresInternalTransferRepository()
    val traceWorkOrderProvider = CompositeTraceWorkOrderProvider(
        sampling = SamplingTraceWorkOrderProvider(samplingOrderRepo, traceContainerRepo),
        bulk = BulkTraceWorkOrderProvider(bulkWorkOrderRepo, samplingOrderRepo, traceContainerRepo)
    )
    val knitWorksheetBuilder = KnitWorksheetBuilder(samplingOrderRepo)
    // Host ini tercetak di setiap QR: mengubahnya kelak mematikan seluruh kartu yang sudah beredar di lantai.
    val traceScanHost = System.getenv("TRACE_SCAN_HOST")?.takeIf { it.isNotBlank() } ?: "wemade.local"
    val materialRepo = materialItemRepository ?: PostgresMaterialItemRepository()
    val materialPriceRepo = materialPriceRepository ?: PostgresMaterialPriceRepository()
    val techPackRepo = techPackRepository ?: PostgresTechPackRepository()
    val invoiceRepo = invoiceRepository ?: PostgresInvoiceRepository()
    val invoiceTemplateRepo = invoiceTemplateRepository ?: PostgresInvoiceTemplateRepository()
    val invoicePaymentRepo = invoicePaymentRepository ?: PostgresInvoicePaymentRepository()
    val invoiceIssuerProfileRepo = invoiceIssuerProfileRepository ?: PostgresInvoiceIssuerProfileRepository()
    val costingSheetRepo = costingSheetRepository ?: PostgresCostingSheetRepository()
    val costingRateCardRepo = costingRateCardRepository ?: PostgresCostingRateCardRepository()
    val costingBenchmarkRepo = costingBenchmarkRepository ?: PostgresCostingBenchmarkRepository()

    // Tanpa GEMINI_API_KEY seluruh fitur tetap hidup: impor memakai parser heuristik berbasis
    // label, dan estimator berjalan tanpa petunjuk visual. Yang hilang hanya kenyamanannya,
    // bukan fungsinya — server tidak boleh gagal start karena satu kunci API belum diisi.
    val geminiApiKey = System.getenv("GEMINI_API_KEY")?.takeIf { it.isNotBlank() }
    val geminiService = geminiApiKey?.let { GeminiCostingParserService(apiKey = it) }
    val historicalCostingParser = geminiService ?: HeuristicCostingParser()
    val designVisionAnalyzer = geminiService ?: NoopDesignVisionAnalyzer
    val benchmarkImageStorage = LocalBenchmarkImageStorage()

    // Word-overlap retrieval, not semantic. Adequate while the corpus is small and the confidence
    // gate turns weak matches into refusals rather than bad prices — see LexicalEmbeddingProvider.
    val embeddingProviderImpl = embeddingProvider ?: LexicalEmbeddingProvider()

    val leadRepo = prospectLeadRepository ?: PostgresProspectLeadRepository()
    val translationRepo = flowTranslationRepository ?: PostgresFlowTranslationRepository()
    val prospectEstimateRepo =
        prospectPriceEstimateRepository ?: PostgresProspectPriceEstimateRepository()

    // Keyword matching, not comprehension (KeywordFlowTranslator): free, so safe on a public endpoint; a real model needs rate limiting.
    val flowTranslatorImpl = flowTranslator ?: KeywordFlowTranslator()

    // Funnel discovery (plan Fase A): kill-switch env — deterministik selama Koog belum dipasang (A8).
    val discoveryDraftRepo = discoveryDraftRepository ?: com.eventverse.app.infrastructure.PostgresDiscoveryDraftRepository()

    // Derived from REAL productive hours (~4/day), not a nominal 160-hour month. Using a nominal
    // rate while logging honest hours recovers only half the cost on every quote.
    val blendedHourlyRate = MoneyIdr(
        System.getenv("WEMADE_BLENDED_HOURLY_RATE_IDR")?.toLongOrNull() ?: 250_000L
    )
    val defaultMargin = Percentage(
        System.getenv("WEMADE_DEFAULT_MARGIN_PERCENT")?.toDoubleOrNull() ?: 35.0
    )
    val registerTenantUseCase = RegisterTenantUseCase(repository, userRepo)
    val checkSubdomainUseCase = CheckSubdomainAvailabilityUseCase(repository)
    val authenticateWithGoogleUseCase = AuthenticateWithGoogleUseCase(userRepo, repository)
    val googleAuthService = GoogleAuthService()
    val jwtTokenService = JwtTokenService()
    install(TenantResolutionPlugin) {
        this.tenantRepository = repository
        this.jwtTokenService = jwtTokenService
        this.publicRoutePrefixes = listOf("/api/public", "/health")
        this.entitlementRepository = entitlementRepo
        this.domainPackRepository = domainPackRepo
    }

    routing {
        get("/") {
            call.respondText(sayHello("WeMade ERP Multi-Tenant"))
        }

        get("/health") {
            call.respondText("OK", status = HttpStatusCode.OK)
        }

        // Gambar mockup yang diekstrak dari berkas Excel arsip disimpan di folder lokal
        // (lihat LocalBenchmarkImageStorage) dan di-serve dari sini supaya UI Knowledge Base
        // bisa menampilkan thumbnail-nya tanpa menunggu object storage dikonfigurasi.
        staticFiles(
            remotePath = "/uploads",
            dir = java.io.File(
                System.getenv("WEMADE_UPLOAD_DIR")?.takeIf { it.isNotBlank() } ?: "data/uploads"
            )
        )

        // FR-M2-7: gerbang daftar publik. Bawaan tertutup — membuka tenant creation ke internet
        // harus keputusan sadar (`WEMADE_PUBLIC_SIGNUP=on`), bukan keadaan default.
        onboardingRoutes(
            registerTenantUseCase,
            checkSubdomainUseCase,
            publicSignupEnabled = System.getenv("WEMADE_PUBLIC_SIGNUP")?.lowercase() in setOf("on", "true", "1")
        )

        publicAuthRoutes(googleAuthService, authenticateWithGoogleUseCase, jwtTokenService, repository, userRepo, roleRepo)

        // Protected tenant-scoped route
        route("/api/tenant") {
            get("/info") {
                val context = call.tenantContextOrNull
                if (context != null) {
                    call.respondText(
                        "{\"tenantId\":\"${context.tenantId.value}\",\"slug\":\"${context.slug.value}\",\"tier\":\"${context.tier.name}\",\"accessible\":${context.isAccessible}}",
                        ContentType.Application.Json
                    )
                } else {
                    call.respond(HttpStatusCode.NotFound, "No tenant context found")
                }
            }
        }

        rbacRoutes(roleRepo, assignmentRepo)
        builderRoutes(
            tenants = repository,
            deployments = builderDeploymentRepository ?: com.eventverse.app.infrastructure.PostgresBuilderDeploymentRepository(),
            chats = builderChatRepository ?: com.eventverse.app.infrastructure.PostgresBuilderChatRepository(),
            agent = builderAgent ?: com.eventverse.app.infrastructure.builder.DiscoveryBackedBuilderAgent(
                com.eventverse.app.infrastructure.discovery.DiscoveryAgents.fromEnv()
            ),
            drafts = discoveryDraftRepo, buildRequests = builderBuildRequests ?: com.eventverse.app.infrastructure.PostgresBuilderBuildRequestRepository(),
            probe = builderProbe ?: com.eventverse.app.infrastructure.PostgresTenantOperationalDataProbe(),
            auditLog = builderAuditLog ?: com.eventverse.app.infrastructure.PostgresAuditLogRepository(),
            billingInvoices = builderBillingInvoices
                ?: com.eventverse.app.infrastructure.PostgresSubscriptionInvoiceRepository(),
            billingPreview = builderBillingPreview ?: com.eventverse.app.domain.builder.TenantBillingPreviewSource { tenantId ->
                com.eventverse.app.domain.moduledev.usecases.GetTenantBillingPreviewUseCase(
                    pipeRepo, catalogRepo, quoteRepo
                )(tenantId)
            }
        )
        moduleAssignmentRoutes(assignmentRepo, roleRepo)
        departmentRoutes(deptRepo, empRepo, roleRepo, assignmentRepo)
        employeeRoutes(empRepo, deptRepo, roleRepo, assignmentRepo)
        pipelineRoutes(pipeRepo, entitlementRepo, roleRepo, assignmentRepo)
        adminRoutes(repository, pipeRepo, entitlementRepo, auditLogRepo)
        domainPackRoutes(repository, domainPackRepo, com.eventverse.app.infrastructure.PostgresTenantOperationalDataProbe(), auditLogRepo)
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
            draftRepository = discoveryDraftRepo, tenantRepository = repository, domainPackRepository = domainPackRepo,
            probe = com.eventverse.app.infrastructure.PostgresTenantOperationalDataProbe(), catalogRepository = catalogRepo,
            buildRepository = buildRepo, sizingWeightsRepository = sizingWeightsRepo, embeddingProvider = embeddingProviderImpl,
            blendedHourlyRate = blendedHourlyRate, leadRepository = leadRepo, discoveryDemands = discoveryDemandRepository,
            agent = com.eventverse.app.infrastructure.discovery.DiscoveryAgents.fromEnv())
        crmRoutes(
            leadRepository = crmLeadRepo, contactRepository = crmContactRepo,
            dealRepository = crmDealRepo, customFieldRepository = customFieldRepo,
            employeeRepository = empRepo, roleRepository = roleRepo,
            moduleAssignmentRepository = assignmentRepo, invoiceRepository = invoiceRepo,
            leadActivityRepository = leadActivityRepo
        )
        dealRoutes(
            dealRepository = crmDealRepo, contactRepository = crmContactRepo,
            employeeRepository = empRepo, roleRepository = roleRepo,
            moduleAssignmentRepository = assignmentRepo,
            poFileStorage = poFileStorage, samplingOrderRepository = samplingOrderRepo
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
