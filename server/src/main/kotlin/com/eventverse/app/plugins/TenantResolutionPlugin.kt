package com.eventverse.app.plugins

import com.eventverse.app.domain.auth.Role
import com.eventverse.app.domain.pipeline.TenantEntitlementRepository
import com.eventverse.app.domain.pipeline.usecases.GetTenantEntitlementUseCase
import com.eventverse.app.domain.rbac.BusinessModule
import com.eventverse.app.domain.tenant.*
import com.eventverse.app.infrastructure.auth.JwtTokenService
import com.eventverse.app.infrastructure.auth.PrintTicketService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.util.*

val TenantContextAttributeKey = AttributeKey<TenantContext>("TenantContext")

class TenantResolutionConfig {
    var tenantRepository: TenantRepository? = null

    /** Verifies session tokens. Required: without it no route could be authenticated. */
    var jwtTokenService: JwtTokenService? = null

    /** Accepts `?ticket=` on PDF routes opened in a browser tab, which cannot send a header. */
    var printTicketService: PrintTicketService? = PrintTicketService()

    var publicRoutePrefixes: List<String> = listOf("/api/public", "/health", "/favicon.ico")

    /**
     * Routes that act on a tenant named by a **path parameter** rather than the caller's
     * own workspace — platform administration, where a superadmin manages a tenant it is
     * not a member of. These still require authentication, and still require the
     * `PLATFORM_SUPERADMIN` role, but skip automatic tenant-context resolution: the target
     * tenant is whatever the route itself looks up from the path, not from a header.
     */
    var platformRoutePrefixes: List<String> = listOf("/api/admin")

    /**
     * Sumber grant modul per tenant. Bila diisi, grant dimuat sekali per request dan dibaca guard
     * modul lewat [grantedModulesOrNull] — entitlement paket ditegakkan di server, bukan hanya
     * disembunyikan dari menu klien (TRD-FLOW-002 Fase 2).
     */
    var entitlementRepository: TenantEntitlementRepository? = null
}

/** Modul yang di-grant untuk tenant request ini; `null` = tidak dimuat (guard tidak membatasi paket). */
val GrantedModulesAttributeKey = AttributeKey<Set<BusinessModule>>("GrantedModules")

val ApplicationCall.grantedModulesOrNull: Set<BusinessModule>?
    get() = attributes.getOrNull(GrantedModulesAttributeKey)

/**
 * Authenticates the caller and resolves which tenant the request acts on.
 *
 * Tenant identity comes from the **verified** JWT, not from a request header. A tenant-bound
 * caller can only ever act on its own tenant; naming another tenant is refused rather than
 * silently honoured. Only a platform superadmin may target a different tenant, via
 * `X-Tenant-Slug` / `X-Tenant-ID` — which is what makes the workspace switcher legitimate
 * instead of a hole.
 */
val TenantResolutionPlugin = createApplicationPlugin(
    name = "TenantResolutionPlugin",
    createConfiguration = ::TenantResolutionConfig
) {
    val repository = pluginConfig.tenantRepository
        ?: error("TenantRepository must be configured in TenantResolutionPlugin")
    val jwtTokenService = pluginConfig.jwtTokenService
        ?: error("JwtTokenService must be configured in TenantResolutionPlugin")
    val publicPrefixes = pluginConfig.publicRoutePrefixes
    val platformPrefixes = pluginConfig.platformRoutePrefixes
    val printTickets = pluginConfig.printTicketService
    val entitlements = pluginConfig.entitlementRepository?.let(::GetTenantEntitlementUseCase)

    onCall { call ->
        val path = call.request.path()

        // Root path ("/") or public routes (login, registration, health) are bypassed.
        if (path == "/" || publicPrefixes.any { path.startsWith(it) }) {
            return@onCall
        }

        // --- 1. Authenticate -------------------------------------------------
        val bearerToken = call.request.bearerToken()
        val printTicket = call.request.queryParameters[PrintTicketService.QUERY_PARAM]
        if (bearerToken.isNullOrBlank() && printTickets != null && !printTicket.isNullOrBlank()) {
            // The ticket already names its tenant: it was minted after this plugin resolved
            // the caller's tenant on an authenticated request, superadmin act-as included.
            val tenant = printTickets.verify(printTicket, path)?.let { repository.findById(it) }
            when {
                tenant == null ->
                    call.respond(HttpStatusCode.Unauthorized, "Tiket cetak tidak sah atau sudah kedaluwarsa.")
                !tenant.isAccessible ->
                    call.respond(HttpStatusCode.Forbidden, "Tenant workspace '${tenant.slug.value}' is suspended.")
                else -> call.attributes.put(TenantContextAttributeKey, TenantContext.fromTenant(tenant))
            }
            return@onCall
        }
        if (bearerToken.isNullOrBlank()) {
            call.respond(
                HttpStatusCode.Unauthorized,
                "Authentication required: sertakan header 'Authorization: Bearer <token>'."
            )
            return@onCall
        }

        val decoded = jwtTokenService.verifyToken(bearerToken).getOrNull()
        if (decoded == null) {
            call.respond(HttpStatusCode.Unauthorized, "Sesi tidak valid atau sudah kedaluwarsa.")
            return@onCall
        }

        val principal = CallerPrincipal(
            userId = decoded.subject ?: "",
            role = decoded.getClaim("role").asString()
                ?.let { name -> runCatching { Role.valueOf(name) }.getOrNull() }
                ?: Role.TENANT_ADMIN,
            tenantId = decoded.getClaim("tenant_id").asString()
                ?.takeIf { it.isNotBlank() }
                ?.let { TenantId(it) },
            tenantSlug = decoded.getClaim("tenant_slug").asString()?.takeIf { it.isNotBlank() },
            departmentId = decoded.getClaim("department_id").asString()?.takeIf { it.isNotBlank() },
            customRoleId = decoded.getClaim("custom_role_id").asString()?.takeIf { it.isNotBlank() },
            email = decoded.getClaim("email").asString()?.takeIf { it.isNotBlank() }
        )
        call.attributes.put(CallerPrincipalAttributeKey, principal)

        // --- 1b. Platform administration routes: authenticated superadmin only, no
        //         automatic tenant context — the route decides its target tenant from a
        //         path parameter instead. ------------------------------------------
        if (platformPrefixes.any { path.startsWith(it) }) {
            if (!principal.isPlatformSuperadmin) {
                call.respond(
                    HttpStatusCode.Forbidden,
                    "Endpoint ini khusus untuk platform superadmin."
                )
            }
            return@onCall
        }

        // --- 2. Decide which tenant this request may act on ------------------
        val requestedSlug = call.request.header("X-Tenant-Slug")?.trim()?.lowercase()
            ?.takeIf { it.isNotBlank() }
        val requestedId = call.request.header("X-Tenant-ID")?.trim()?.takeIf { it.isNotBlank() }

        if (principal.isTenantBound) {
            // Refuse loudly instead of quietly serving the caller's own tenant: a client
            // asking for another tenant's data is a bug or an attack, and either way the
            // caller must not be told the request succeeded against something else.
            val mismatchedSlug = requestedSlug != null &&
                principal.tenantSlug != null &&
                requestedSlug != principal.tenantSlug.lowercase()
            val mismatchedId = requestedId != null &&
                principal.tenantId != null &&
                requestedId != principal.tenantId.value

            if (mismatchedSlug || mismatchedId) {
                call.respond(
                    HttpStatusCode.Forbidden,
                    "Akun ini terikat pada tenant '${principal.tenantSlug ?: principal.tenantId?.value}' " +
                        "dan tidak boleh mengakses tenant lain."
                )
                return@onCall
            }
        }

        val resolvedTenant = when {
            // A platform superadmin may act as any tenant it explicitly names.
            principal.isPlatformSuperadmin && requestedSlug != null ->
                repository.findBySlugOrNull(requestedSlug)

            principal.isPlatformSuperadmin && requestedId != null ->
                repository.findById(TenantId(requestedId))

            // Otherwise the token itself decides.
            principal.tenantId != null -> repository.findById(principal.tenantId)

            principal.tenantSlug != null -> repository.findBySlugOrNull(principal.tenantSlug)

            // Fall back to the subdomain, which a superadmin token without a tenant claim
            // still needs in order to address a workspace.
            else -> extractSubdomain(call.request.host())?.let { repository.findBySlugOrNull(it) }
        }

        if (resolvedTenant == null) {
            val message = if (principal.isPlatformSuperadmin) {
                "Tenant tidak ditemukan. Sertakan header 'X-Tenant-Slug' berisi workspace tujuan."
            } else {
                "Tenant pada sesi ini tidak ditemukan."
            }
            call.respond(HttpStatusCode.NotFound, message)
            return@onCall
        }

        if (!resolvedTenant.isAccessible) {
            call.respond(
                HttpStatusCode.Forbidden,
                "Tenant workspace '${resolvedTenant.slug.value}' is suspended. Please contact support."
            )
            return@onCall
        }

        call.attributes.put(TenantContextAttributeKey, TenantContext.fromTenant(resolvedTenant))
        entitlements?.invoke(resolvedTenant.id, resolvedTenant.tier)?.getOrNull()
            ?.let { call.attributes.put(GrantedModulesAttributeKey, it.grantedModules) }
    }
}

private fun ApplicationRequest.bearerToken(): String? {
    val header = header(HttpHeaders.Authorization) ?: return null
    return if (header.startsWith("Bearer ", ignoreCase = true)) {
        header.removePrefix("Bearer ").removePrefix("bearer ").trim()
    } else {
        header.trim()
    }
}

/** A malformed slug is "not found" rather than an exception at this boundary. */
private suspend fun TenantRepository.findBySlugOrNull(rawSlug: String): Tenant? =
    runCatching { TenantSlug(rawSlug.trim().lowercase()) }.getOrNull()?.let { findBySlug(it) }

private fun extractSubdomain(host: String): String? {
    val cleanHost = host.substringBefore(":") // strip port if any
    val parts = cleanHost.split(".")
    if (parts.size >= 3) {
        val candidate = parts[0].lowercase()
        if (candidate !in TenantSlug.FORBIDDEN_SLUGS && candidate != "localhost") {
            return candidate
        }
    }
    return null
}

val ApplicationCall.tenantContext: TenantContext
    get() = attributes[TenantContextAttributeKey]

val ApplicationCall.tenantContextOrNull: TenantContext?
    get() = attributes.getOrNull(TenantContextAttributeKey)
