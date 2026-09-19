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
import com.eventverse.app.domain.orgchart.DepartmentRepository
import com.eventverse.app.domain.orgchart.EmployeeRepository
import com.eventverse.app.infrastructure.PostgresRoleRepository
import com.eventverse.app.infrastructure.PostgresDepartmentRepository
import com.eventverse.app.infrastructure.PostgresEmployeeRepository
import com.eventverse.app.routes.rbacRoutes
import com.eventverse.app.routes.departmentRoutes
import com.eventverse.app.routes.employeeRoutes
import com.eventverse.app.routes.pipelineRoutes
import com.eventverse.app.routes.adminRoutes
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
    embeddedServer(Netty, port = 8080, host = "0.0.0.0", module = Application::module)
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
    costingBenchmarkRepository: com.eventverse.app.domain.costing.CostingBenchmarkRepository? = null
) {
    val repository = tenantRepository ?: run {
        DatabaseFactory.init()
        PostgresTenantRepository()
    }
    val userRepo = userRepository ?: PostgresUserRepository()
    val roleRepo = roleRepository ?: PostgresRoleRepository()
    val assignmentRepo = moduleAssignmentRepository ?: PostgresModuleAssignmentRepository()
    val deptRepo = departmentRepository ?: PostgresDepartmentRepository()
    val empRepo = employeeRepository ?: PostgresEmployeeRepository()
    val pipeRepo = pipelineRepository ?: PostgresTenantPipelineRepository()
    val entitlementRepo = entitlementRepository ?: PostgresTenantEntitlementRepository()
    val auditLogRepo = auditLogRepository ?: PostgresAuditLogRepository()
    val catalogRepo = moduleCatalogRepository ?: PostgresModuleCatalogRepository()
    val buildRepo = moduleBuildRepository ?: PostgresModuleBuildRepository()
    val quoteRepo = modulePricingQuoteRepository ?: PostgresModulePricingQuoteRepository()
    val customizationRequestRepo =
        moduleCustomizationRequestRepository ?: PostgresModuleCustomizationRequestRepository()
    val sizingWeightsRepo = sizingWeightsRepository ?: PostgresSizingWeightsRepository()
    val crmLeadRepo = crmLeadRepository ?: PostgresCrmLeadRepository()
    val crmContactRepo = contactRepository ?: PostgresContactRepository()
    val crmDealRepo = dealRepository ?: PostgresDealRepository()
    val poFileStorage = poFileStorage ?: S3PoFileStorage()
    val leadActivityRepo = leadActivityRepository ?: PostgresLeadActivityRepository()
    val customFieldRepo = customFieldDefinitionRepository ?: PostgresCustomFieldDefinitionRepository()
    val samplingOrderRepo = samplingOrderRepository ?: PostgresSamplingOrderRepository()
    val bulkWorkOrderRepo = PostgresBulkWorkOrderRepository()
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

    // Keyword matching, not comprehension — see KeywordFlowTranslator. Safe to run on a public
    // endpoint because it costs nothing; a real model needs rate limiting first.
    val flowTranslatorImpl = flowTranslator ?: KeywordFlowTranslator()

    // Derived from REAL productive hours (~4/day), not a nominal 160-hour month. Using a nominal
    // rate while logging honest hours recovers only half the cost on every quote.
    val blendedHourlyRate = MoneyIdr(
        System.getenv("WEMADE_BLENDED_HOURLY_RATE_IDR")?.toLongOrNull() ?: 250_000L
    )
    val defaultMargin = Percentage(
        System.getenv("WEMADE_DEFAULT_MARGIN_PERCENT")?.toDoubleOrNull() ?: 35.0
    )

    val registerTenantUseCase = RegisterTenantUseCase(repository)
    val checkSubdomainUseCase = CheckSubdomainAvailabilityUseCase(repository)
    val authenticateWithGoogleUseCase = AuthenticateWithGoogleUseCase(userRepo, repository)

    val googleAuthService = GoogleAuthService()
    val jwtTokenService = JwtTokenService()

    install(TenantResolutionPlugin) {
        this.tenantRepository = repository
        this.jwtTokenService = jwtTokenService
        this.publicRoutePrefixes = listOf("/api/public", "/health")
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

        route("/api/public/onboarding") {
            get("/check-subdomain") {
                val slug = call.request.queryParameters["slug"] ?: ""
                val result = checkSubdomainUseCase(CheckSubdomainQuery(slug))
                if (result.isSuccess) {
                    val availability = result.getOrThrow()
                    call.respondText(
                        text = "{\"slug\":\"${availability.slug}\",\"isAvailable\":${availability.isAvailable}}",
                        contentType = ContentType.Application.Json
                    )
                } else {
                    call.respond(HttpStatusCode.BadRequest, result.exceptionOrNull()?.message ?: "Invalid request")
                }
            }

            post("/register") {
                val params = call.receiveParameters()
                val id = params["id"] ?: "ten-${System.currentTimeMillis()}"
                val slug = params["slug"] ?: ""
                val name = params["name"] ?: ""
                val tierName = params["tier"] ?: "PRO"
                val tier = runCatching { SubscriptionTier.valueOf(tierName.uppercase()) }.getOrDefault(SubscriptionTier.PRO)

                val result = registerTenantUseCase(RegisterTenantCommand(id, slug, name, tier))
                if (result.isSuccess) {
                    val tenant = result.getOrThrow()
                    call.respondText(
                        text = "{\"id\":\"${tenant.id.value}\",\"slug\":\"${tenant.slug.value}\",\"name\":\"${tenant.name.value}\",\"tier\":\"${tenant.tier.name}\"}",
                        status = HttpStatusCode.Created,
                        contentType = ContentType.Application.Json
                    )
                } else {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        result.exceptionOrNull()?.message ?: "Registration failed"
                    )
                }
            }
        }

        route("/api/public/auth") {
            get("/google/url") {
                val redirectUri = call.request.queryParameters["redirect_uri"] ?: "http://localhost:8080/api/auth/google/callback"
                val state = call.request.queryParameters["state"]
                val url = googleAuthService.buildAuthorizationUrl(redirectUri, state)
                call.respondText(
                    text = "{\"url\":\"$url\",\"clientId\":\"${googleAuthService.clientId}\"}",
                    contentType = ContentType.Application.Json
                )
            }

            post("/google") {
                val params = call.receiveParameters()
                val idToken = params["idToken"] ?: ""
                val tenantSlug = params["tenantSlug"] ?: ""

                if (idToken.isBlank() || tenantSlug.isBlank()) {
                    call.respond(HttpStatusCode.BadRequest, "idToken and tenantSlug are required")
                    return@post
                }

                val verifyResult = googleAuthService.verifyIdToken(idToken)
                if (verifyResult.isFailure) {
                    call.respond(
                        HttpStatusCode.Unauthorized,
                        verifyResult.exceptionOrNull()?.message ?: "Google token verification failed"
                    )
                    return@post
                }

                val googleProfile = verifyResult.getOrThrow()
                val authResult = authenticateWithGoogleUseCase(
                    AuthenticateWithGoogleCommand(googleProfile, tenantSlug)
                )

                if (authResult.isSuccess) {
                    val user = authResult.getOrThrow()
                    val sessionToken = jwtTokenService.generateToken(user, tenantSlug)
                    val permissionsJson = user.effectivePermissions.joinToString(",") { "\"${it.name}\"" }

                    val responseJson = "{\"token\":\"${sessionToken.value}\",\"user\":{\"id\":\"${user.id.value}\",\"tenantId\":\"${user.tenantId?.value ?: ""}\",\"username\":\"${user.username.value}\",\"email\":\"${user.email.value}\",\"role\":\"${user.role.name}\",\"permissions\":[$permissionsJson]},\"tenantSlug\":\"$tenantSlug\"}"

                    call.respondText(responseJson, contentType = ContentType.Application.Json)
                } else {
                    call.respond(
                        HttpStatusCode.Forbidden,
                        authResult.exceptionOrNull()?.message ?: "Authentication failed"
                    )
                }
            }

            post("/demo") {
                val params = runCatching { call.receiveParameters() }.getOrNull()
                val tenantSlug = params?.get("tenantSlug")?.ifBlank { null }
                    ?: call.request.queryParameters["tenantSlug"]?.ifBlank { null }
                    ?: "wemade-demo"

                val tenant = repository.findBySlug(TenantSlug(tenantSlug))
                if (tenant == null) {
                    call.respond(HttpStatusCode.NotFound, "Tenant dengan slug '$tenantSlug' tidak ditemukan")
                    return@post
                }

                fun field(name: String): String? = params?.get(name)?.ifBlank { null }
                    ?: call.request.queryParameters[name]?.ifBlank { null }

                val requestedRole = field("role")
                val isSuperAdmin = requestedRole.equals("PLATFORM_SUPERADMIN", ignoreCase = true) ||
                    requestedRole.equals("superadmin", ignoreCase = true)

                // Persona pengujian: nama bebas + jabatan rakitan tenant + divisi. Dikenali dari
                // adanya `username`, karena login demo lama tidak pernah mengirimkannya.
                val personaName = field("username")
                if (!isSuperAdmin && personaName != null) {
                    val personaResult = resolvePersonaUser(
                        userRepo = userRepo,
                        roleRepo = roleRepo,
                        tenantId = tenant.id,
                        personaName = personaName,
                        tenantSlug = tenantSlug,
                        requestedRoleId = requestedRole,
                        departmentId = field("departmentId")
                    )

                    personaResult
                        .onSuccess { personaUser ->
                            val personaToken = jwtTokenService.generateToken(personaUser, tenantSlug)
                            call.respondText(
                                authSessionJson(personaUser, personaToken.value, tenantSlug),
                                contentType = ContentType.Application.Json
                            )
                        }
                        .onFailure {
                            call.respond(
                                HttpStatusCode.BadRequest,
                                it.message ?: "Gagal menyiapkan persona pengujian"
                            )
                        }
                    return@post
                }

                // Query real user from DB for this tenant or create fallback
                val user = if (isSuperAdmin) {
                    userRepo.findByEmail(EmailAddress("superadmin@wemade.id"))
                        ?: run {
                            val superadmin = User(
                                id = UserId("usr-superadmin-001"),
                                tenantId = tenant.id,
                                username = Username("superadmin_apps"),
                                email = EmailAddress("superadmin@wemade.id"),
                                role = Role.PLATFORM_SUPERADMIN,
                                isActive = true
                            )
                            userRepo.save(superadmin)
                            superadmin
                        }
                } else {
                    userRepo.findAllByTenant(tenant.id)
                        .firstOrNull { it.role == Role.TENANT_ADMIN }
                        ?: userRepo.findByEmail(EmailAddress("student.achmad@gmail.com"))
                        ?: run {
                            val fallback = User(
                                id = UserId("usr-owner-001"),
                                tenantId = tenant.id,
                                username = Username("achmad_owner"),
                                email = EmailAddress("student.achmad@gmail.com"),
                                role = Role.TENANT_ADMIN,
                                isActive = true
                            )
                            userRepo.save(fallback)
                            fallback
                        }
                }

                val sessionToken = jwtTokenService.generateToken(user, tenantSlug)

                call.respondText(
                    authSessionJson(user, sessionToken.value, tenantSlug),
                    contentType = ContentType.Application.Json
                )
            }

            get("/me") {
                val authHeader = call.request.header("Authorization") ?: ""
                val token = if (authHeader.startsWith("Bearer ")) authHeader.removePrefix("Bearer ").trim() else authHeader.trim()
                if (token.isBlank()) {
                    call.respond(HttpStatusCode.Unauthorized, "No token provided")
                    return@get
                }

                val verifyResult = jwtTokenService.verifyToken(token)
                if (verifyResult.isFailure) {
                    call.respond(HttpStatusCode.Unauthorized, "Token expired or invalid")
                    return@get
                }

                val jwt = verifyResult.getOrThrow()
                val userId = jwt.subject ?: ""
                val tenantSlug = jwt.getClaim("tenant_slug").asString() ?: "wemade-demo"
                val username = jwt.getClaim("username").asString() ?: ""
                val email = jwt.getClaim("email").asString() ?: ""
                val roleName = jwt.getClaim("role").asString()
                // Role tak dikenal dulu jatuh ke TENANT_ADMIN. Artinya identitas yang tidak dapat
                // dibaca justru diberi wewenang paling luas — persis kebalikan dari yang aman.
                // Sekarang jatuh ke STAFF, wewenang tersempit yang masih bisa login.
                val role = roleName
                    ?.let { name -> runCatching { Role.valueOf(name) }.getOrNull() }
                    ?: Role.OPERATOR
                val tenantIdStr = jwt.getClaim("tenant_id").asString()

                val user = userRepo.findById(UserId(userId)) ?: User(
                    id = UserId(userId),
                    tenantId = tenantIdStr?.let { TenantId(it) },
                    username = Username(username),
                    email = EmailAddress(email),
                    role = role,
                    isActive = true,
                    // Baca ulang identitas tenant dari token, bukan diturunkan kembali. Inilah yang
                    // membuat persona bertahan setelah halaman di-reload.
                    departmentId = jwt.getClaim("department_id").asString(),
                    customRoleId = jwt.getClaim("custom_role_id").asString()
                )

                call.respondText(
                    authSessionJson(user, token, tenantSlug),
                    contentType = ContentType.Application.Json
                )
            }
        }

        // Protected tenant-scoped route
        route("/api/tenant") {
            get("/info") {
                val context = call.tenantContextOrNull
                if (context != null) {
                    call.respondText(
                        text = "{\"tenantId\":\"${context.tenantId.value}\",\"slug\":\"${context.slug.value}\",\"tier\":\"${context.tier.name}\",\"accessible\":${context.isAccessible}}",
                        contentType = ContentType.Application.Json
                    )
                } else {
                    call.respond(HttpStatusCode.NotFound, "No tenant context found")
                }
            }
        }

        rbacRoutes(roleRepo)
        moduleAssignmentRoutes(assignmentRepo)
        departmentRoutes(deptRepo, empRepo, roleRepo, assignmentRepo)
        // roleRepo + assignmentRepo dipakai untuk menghitung jangkauan data Bagan Organisasi
        // (ScopeCapability.HIERARCHICAL), bukan untuk CRUD karyawan.
        employeeRoutes(empRepo, deptRepo, roleRepo, assignmentRepo)
        pipelineRoutes(pipeRepo, entitlementRepo)
        adminRoutes(repository, pipeRepo, entitlementRepo, auditLogRepo)
        moduleDevRoutes(
            catalogRepository = catalogRepo,
            buildRepository = buildRepo,
            quoteRepository = quoteRepo,
            requestRepository = customizationRequestRepo,
            sizingWeightsRepository = sizingWeightsRepo,
            pipelineRepository = pipeRepo,
            embeddingProvider = embeddingProviderImpl,
            auditLogRepository = auditLogRepo
        )
        prospectRoutes(
            leadRepository = leadRepo,
            translationRepository = translationRepo,
            priceEstimateRepository = prospectEstimateRepo,
            submitLeadUseCase = SubmitProspectLeadUseCase(leadRepo),
            translateUseCase = TranslateProspectFlowUseCase(
                flowTranslatorImpl, translationRepo, leadRepo
            ),
            analyzeCoverageUseCase = AnalyzeCoverageUseCase(catalogRepo),
            priceUseCase = PriceProspectFlowUseCase(
                buildRepository = buildRepo,
                sizingWeightsRepository = sizingWeightsRepo,
                embeddingProvider = embeddingProviderImpl,
                defaultBlendedHourlyRate = blendedHourlyRate
            ),
            defaultMarginPercent = defaultMargin
        )
        crmRoutes(
            leadRepository = crmLeadRepo,
            contactRepository = crmContactRepo,
            dealRepository = crmDealRepo,
            customFieldRepository = customFieldRepo,
            employeeRepository = empRepo,
            roleRepository = roleRepo,
            moduleAssignmentRepository = assignmentRepo,
            invoiceRepository = invoiceRepo,
            leadActivityRepository = leadActivityRepo
        )
        dealRoutes(
            dealRepository = crmDealRepo,
            contactRepository = crmContactRepo,
            employeeRepository = empRepo,
            roleRepository = roleRepo,
            moduleAssignmentRepository = assignmentRepo,
            poFileStorage = poFileStorage,
            samplingOrderRepository = samplingOrderRepo
        )
        samplingRoutes(
            repository = samplingOrderRepo,
            dealRepository = crmDealRepo
        )
        productionRoutes(
            workOrderRepository = bulkWorkOrderRepo,
            dealRepository = crmDealRepo,
            samplingOrderRepository = samplingOrderRepo
        )
        masterDataRoutes(
            materialRepository = materialRepo,
            priceRepository = materialPriceRepo,
            customFieldRepository = customFieldRepo
        )
        techPackRoutes(
            techPackRepository = techPackRepo,
            samplingOrderRepository = samplingOrderRepo,
            materialRepository = materialRepo,
            materialPriceRepository = materialPriceRepo,
            roleRepository = roleRepo,
            moduleAssignmentRepository = assignmentRepo
        )
        invoicingRoutes(
            invoiceRepository = invoiceRepo,
            templateRepository = invoiceTemplateRepo,
            paymentRepository = invoicePaymentRepo,
            issuerProfileRepository = invoiceIssuerProfileRepo,
            samplingOrderRepository = samplingOrderRepo
        )
        costingRoutes(
            sheetRepository = costingSheetRepo,
            rateCardRepository = costingRateCardRepo,
            techPackRepository = techPackRepo,
            materialRepository = materialRepo,
            materialPriceRepository = materialPriceRepo,
            roleRepository = roleRepo,
            moduleAssignmentRepository = assignmentRepo,
            benchmarkRepository = costingBenchmarkRepo,
            tenantPipelineRepository = pipeRepo,
            historicalCostingParser = historicalCostingParser,
            designVisionAnalyzer = designVisionAnalyzer,
            benchmarkImageStorage = benchmarkImageStorage
        )
    }
}
/**
 * Bentuk JSON sesi terotentikasi yang dipakai seluruh endpoint auth publik.
 *
 * Diangkat jadi satu fungsi karena tiga endpoint (`/demo`, `/google`, `/me`) sebelumnya merakit
 * string yang sama secara terpisah, dan penambahan field identitas tenant harus muncul di
 * ketiganya sekaligus — kalau tidak, client melihat persona hanya di sebagian jalur masuk.
 */
private fun authSessionJson(user: User, token: String, tenantSlug: String): String {
    val permissionsJson = user.effectivePermissions.joinToString(",") { "\"${it.name}\"" }
    fun nullableJson(value: String?): String = if (value == null) "null" else "\"$value\""

    return "{\"token\":\"$token\",\"user\":{" +
        "\"id\":\"${user.id.value}\"," +
        "\"tenantId\":\"${user.tenantId?.value ?: ""}\"," +
        "\"username\":\"${user.username.value}\"," +
        "\"email\":\"${user.email.value}\"," +
        "\"role\":\"${user.role.name}\"," +
        "\"departmentId\":${nullableJson(user.departmentId)}," +
        "\"customRoleId\":${nullableJson(user.customRoleId)}," +
        "\"permissions\":[$permissionsJson]}," +
        "\"tenantSlug\":\"$tenantSlug\"}"
}

/**
 * Menemukan atau membuat akun untuk sebuah persona pengujian.
 *
 * Tiga hal yang membuat fungsi ini tidak sesederhana "insert user":
 *
 *  1. **Idempoten.** `users.email` UNIQUE dan `uq_tenant_username` UNIQUE. Login persona yang sama
 *     dua kali harus menemukan baris yang sama, bukan menabrak constraint. Karena itu email dan id
 *     diturunkan secara deterministik dari nama persona, bukan diacak.
 *  2. **Dua sumbu identitas.** `Role` platform menentukan izin tingkat sistem; `custom_role_id`
 *     menentukan isi layar. Jabatan tenant dipetakan ke `Role` yang paling mendekati agar izin
 *     sistem tidak melebar, sementara id jabatan aslinya disimpan apa adanya.
 *  3. **Jabatan harus nyata.** Id jabatan yang tidak ada di tenant ini ditolak, bukan diabaikan
 *     diam-diam — persona dengan jabatan hantu akan tampak "tidak punya akses apa pun" dan
 *     dilaporkan sebagai kerusakan.
 */
private suspend fun resolvePersonaUser(
    userRepo: UserRepository,
    roleRepo: RoleRepository,
    tenantId: TenantId,
    personaName: String,
    tenantSlug: String,
    requestedRoleId: String?,
    departmentId: String?
): Result<User> = runCatching {
    val trimmedName = personaName.trim()
    require(trimmedName.isNotBlank()) { "Nama persona tidak boleh kosong" }

    val slug = trimmedName.lowercase()
        .replace("[^a-z0-9]+".toRegex(), "-")
        .trim('-')
        .ifBlank { "anon" }

    val customRole = requestedRoleId
        ?.takeIf { it.isNotBlank() }
        ?.let { roleId ->
            roleRepo.findById(tenantId, com.eventverse.app.domain.rbac.RoleId(roleId))
                ?: error("Jabatan '$roleId' tidak ditemukan pada tenant ini")
        }

    val email = EmailAddress("persona-$tenantSlug-$slug@testing.local")
    val existing = userRepo.findByEmail(email)

    val persona = User(
        id = existing?.id ?: UserId("usr-persona-$slug".take(64)),
        tenantId = tenantId,
        username = Username("persona_${slug.replace('-', '_')}".take(50)),
        email = email,
        role = platformRoleFor(customRole?.name, requestedRoleId),
        isActive = true,
        departmentId = departmentId?.takeIf { it.isNotBlank() } ?: customRole?.departmentId,
        customRoleId = customRole?.id?.value
    )

    userRepo.save(persona).getOrThrow()
}

/**
 * Memetakan jabatan rakitan tenant ke [Role] platform yang paling mendekati.
 *
 * Default-nya sengaja [Role.OPERATOR] — wewenang tersempit. Jabatan yang tidak dikenali sebaiknya
 * membuat persona melihat terlalu sedikit, bukan terlalu banyak: yang pertama dilaporkan penguji,
 * yang kedua lolos tanpa disadari.
 */
private fun platformRoleFor(roleName: String?, roleId: String?): Role {
    val haystack = "${roleName.orEmpty()} ${roleId.orEmpty()}".lowercase()
    return when {
        haystack.contains("owner") || haystack.contains("direktur") -> Role.TENANT_ADMIN
        haystack.contains("sales") || haystack.contains("penjualan") -> Role.SALES
        haystack.contains("ppic") || haystack.contains("produksi") -> Role.PPIC_SUPERVISOR
        haystack.contains("qc") || haystack.contains("quality") -> Role.QC_INSPECTOR
        haystack.contains("gudang") || haystack.contains("warehouse") || haystack.contains("logistik") -> Role.WAREHOUSE
        else -> Role.OPERATOR
    }
}
