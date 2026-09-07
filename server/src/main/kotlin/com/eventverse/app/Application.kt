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

fun main() {
    embeddedServer(Netty, port = 8080, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

fun Application.module(
    tenantRepository: TenantRepository? = null,
    userRepository: UserRepository? = null
) {
    val repository = tenantRepository ?: run {
        DatabaseFactory.init()
        PostgresTenantRepository()
    }
    val userRepo = userRepository ?: PostgresUserRepository()

    val registerTenantUseCase = RegisterTenantUseCase(repository)
    val checkSubdomainUseCase = CheckSubdomainAvailabilityUseCase(repository)
    val authenticateWithGoogleUseCase = AuthenticateWithGoogleUseCase(userRepo, repository)

    val googleAuthService = GoogleAuthService()
    val jwtTokenService = JwtTokenService()

    install(TenantResolutionPlugin) {
        this.tenantRepository = repository
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
    }
}