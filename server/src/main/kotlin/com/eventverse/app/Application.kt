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
import com.eventverse.app.routes.DomainRouteWiring
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
    // Funnel discovery (plan Fase A): kill-switch env — deterministik selama Koog belum dipasang (A8).
    val discoveryDraftRepo = discoveryDraftRepository ?: com.eventverse.app.infrastructure.PostgresDiscoveryDraftRepository()

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
        // Rute domain bisnis (corong prospek, CRM/deal, modul operasional) — lihat DomainRouteWiring.
        DomainRouteWiring(
            tenants = repository, pipeRepo = pipeRepo, roleRepo = roleRepo, assignmentRepo = assignmentRepo,
            empRepo = empRepo, catalogRepo = catalogRepo, buildRepo = buildRepo, quoteRepo = quoteRepo,
            auditLogRepo = auditLogRepo, domainPackRepo = domainPackRepo, discoveryDraftRepo = discoveryDraftRepo,
            embeddingProvider = embeddingProvider, flowTranslator = flowTranslator,
            discoveryDemandRepository = discoveryDemandRepository, crmLeadRepository = crmLeadRepository,
            contactRepository = contactRepository, dealRepository = dealRepository,
            poFileStorageOverride = poFileStorage, customFieldDefinitionRepository = customFieldDefinitionRepository,
            leadActivityRepository = leadActivityRepository, samplingOrderRepository = samplingOrderRepository, materialItemRepository = materialItemRepository,
            techPackRepository = techPackRepository, invoiceRepository = invoiceRepository,
            invoiceTemplateRepository = invoiceTemplateRepository, invoicePaymentRepository = invoicePaymentRepository,
            invoiceIssuerProfileRepository = invoiceIssuerProfileRepository, costingSheetRepository = costingSheetRepository,
            costingRateCardRepository = costingRateCardRepository, costingBenchmarkRepository = costingBenchmarkRepository,
            moduleCustomizationRequestRepository = moduleCustomizationRequestRepository,
            sizingWeightsRepository = sizingWeightsRepository, prospectLeadRepository = prospectLeadRepository,
            flowTranslationRepository = flowTranslationRepository,
            prospectPriceEstimateRepository = prospectPriceEstimateRepository
        ).registerIn(this)
    }
}
