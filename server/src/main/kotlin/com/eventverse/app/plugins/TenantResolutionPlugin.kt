package com.eventverse.app.plugins

import com.eventverse.app.domain.tenant.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.util.*

val TenantContextAttributeKey = AttributeKey<TenantContext>("TenantContext")

class TenantResolutionConfig {
    var tenantRepository: TenantRepository? = null
    var publicRoutePrefixes: List<String> = listOf("/api/public", "/health", "/favicon.ico")
}

val TenantResolutionPlugin = createApplicationPlugin(
    name = "TenantResolutionPlugin",
    createConfiguration = ::TenantResolutionConfig
) {
    val repository = pluginConfig.tenantRepository 
        ?: error("TenantRepository must be configured in TenantResolutionPlugin")
    val publicPrefixes = pluginConfig.publicRoutePrefixes

    onCall { call ->
        val path = call.request.path()

        // Root path ("/") or public routes are bypassed
        if (path == "/" || publicPrefixes.any { path.startsWith(it) }) {
            return@onCall
        }

        // 1. Check Header: X-Tenant-Slug or X-Tenant-ID
        val headerSlug = call.request.header("X-Tenant-Slug")
        val headerId = call.request.header("X-Tenant-ID")

        // 2. Check Host / Subdomain
        val host = call.request.host()
        val subdomain = extractSubdomain(host)

        val resolvedTenant = when {
            !headerSlug.isNullOrBlank() -> {
                runCatching { TenantSlug(headerSlug.trim().lowercase()) }
                    .getOrNull()
                    ?.let { repository.findBySlug(it) }
            }
            !headerId.isNullOrBlank() -> {
                runCatching { TenantId(headerId.trim()) }
                    .getOrNull()
                    ?.let { repository.findById(it) }
            }
            !subdomain.isNullOrBlank() -> {
                runCatching { TenantSlug(subdomain) }
                    .getOrNull()
                    ?.let { repository.findBySlug(it) }
            }
            else -> null
        }

        if (resolvedTenant == null) {
            call.respond(
                HttpStatusCode.NotFound,
                "Tenant could not be resolved from request headers or subdomain"
            )
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
    }
}

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
