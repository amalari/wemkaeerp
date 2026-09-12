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

import com.eventverse.app.domain.auth.*
import com.eventverse.app.infrastructure.PostgresUserRepository
import com.eventverse.app.infrastructure.auth.GoogleAuthService
import com.eventverse.app.infrastructure.auth.JwtTokenService

import com.eventverse.app.domain.rbac.RoleRepository
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

fun main() {
    embeddedServer(Netty, port = 8080, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

fun Application.module(
    tenantRepository: TenantRepository? = null,
    userRepository: UserRepository? = null,
    roleRepository: RoleRepository? = null,
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
    flowTranslator: FlowTranslator? = null
) {
    val repository = tenantRepository ?: run {
        DatabaseFactory.init()
        PostgresTenantRepository()
    }
    val userRepo = userRepository ?: PostgresUserRepository()
    val roleRepo = roleRepository ?: PostgresRoleRepository()
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

                val requestedRole = params?.get("role")?.ifBlank { null }
                    ?: call.request.queryParameters["role"]?.ifBlank { null }
                val isSuperAdmin = requestedRole.equals("PLATFORM_SUPERADMIN", ignoreCase = true) ||
                    requestedRole.equals("superadmin", ignoreCase = true)

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
                val permissionsJson = user.effectivePermissions.joinToString(",") { "\"${it.name}\"" }

                val responseJson = "{\"token\":\"${sessionToken.value}\",\"user\":{\"id\":\"${user.id.value}\",\"tenantId\":\"${user.tenantId?.value ?: ""}\",\"username\":\"${user.username.value}\",\"email\":\"${user.email.value}\",\"role\":\"${user.role.name}\",\"permissions\":[$permissionsJson]},\"tenantSlug\":\"$tenantSlug\"}"

                call.respondText(responseJson, contentType = ContentType.Application.Json)
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
                val roleName = jwt.getClaim("role").asString() ?: Role.TENANT_ADMIN.name
                val role = runCatching { Role.valueOf(roleName) }.getOrDefault(Role.TENANT_ADMIN)
                val tenantIdStr = jwt.getClaim("tenant_id").asString()

                val user = userRepo.findById(UserId(userId)) ?: User(
                    id = UserId(userId),
                    tenantId = tenantIdStr?.let { TenantId(it) },
                    username = Username(username),
                    email = EmailAddress(email),
                    role = role,
                    isActive = true
                )

                val permissionsJson = user.effectivePermissions.joinToString(",") { "\"${it.name}\"" }
                val responseJson = "{\"token\":\"$token\",\"user\":{\"id\":\"${user.id.value}\",\"tenantId\":\"${user.tenantId?.value ?: ""}\",\"username\":\"${user.username.value}\",\"email\":\"${user.email.value}\",\"role\":\"${user.role.name}\",\"permissions\":[$permissionsJson]},\"tenantSlug\":\"$tenantSlug\"}"

                call.respondText(responseJson, contentType = ContentType.Application.Json)
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
        departmentRoutes(deptRepo, empRepo)
        employeeRoutes(empRepo, deptRepo)
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
    }
}