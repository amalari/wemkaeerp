package com.eventverse.app.routes

import com.eventverse.app.domain.stageflow.IndustryTemplateCode
import com.eventverse.app.domain.tenant.CheckSubdomainAvailabilityUseCase
import com.eventverse.app.domain.tenant.CheckSubdomainQuery
import com.eventverse.app.domain.tenant.RegisterTenantCommand
import com.eventverse.app.domain.tenant.RegisterTenantUseCase
import com.eventverse.app.domain.tenant.SubscriptionTier
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route

/**
 * Onboarding publik (`/api/public/onboarding`): cek ketersediaan slug + pendaftaran tenant.
 *
 * Dipisah dari `Application.kt` (cicilan Ratchet, PLAN-builder-console F1): blok ini satu agregat
 * yang berdiri sendiri, dan `Application.kt` berada di atas hard limit file-size-rules.
 * Daftar publik (dengan `ownerEmail` + gerbang satu-owner) baru menyala di M2 via flag; endpoint ini
 * tetap bisa dipakai alur undangan.
 */
fun Route.onboardingRoutes(
    registerTenantUseCase: RegisterTenantUseCase,
    checkSubdomainUseCase: CheckSubdomainAvailabilityUseCase
) {
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

            val result = registerTenantUseCase(
                RegisterTenantCommand(
                    id = id,
                    slug = slug,
                    name = name,
                    tier = tier,
                    industryTemplate = IndustryTemplateCode.parseOrNull(params["industryTemplate"]),
                    ownerEmail = params["ownerEmail"]
                )
            )
            if (result.isSuccess) {
                val tenant = result.getOrThrow()
                call.respondText(
                    text = "{\"id\":\"${tenant.id.value}\",\"slug\":\"${tenant.slug.value}\",\"name\":\"${tenant.name.value}\",\"tier\":\"${tenant.tier.name}\"}",
                    status = HttpStatusCode.Created,
                    contentType = ContentType.Application.Json
                )
            } else {
                val conflict = result.exceptionOrNull()?.message?.contains("(409)") == true
                call.respond(
                    if (conflict) HttpStatusCode.Conflict else HttpStatusCode.BadRequest,
                    result.exceptionOrNull()?.message ?: "Registration failed"
                )
            }
        }
    }
}
