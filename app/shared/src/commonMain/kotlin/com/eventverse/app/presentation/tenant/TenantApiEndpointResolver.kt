package com.eventverse.app.presentation.tenant

import com.eventverse.app.domain.tenant.TenantSlug

/**
 * Resolves API Base URL and request headers for multi-tenant requests.
 */
class TenantApiEndpointResolver(
    private val rootDomain: String = "wemade.id",
    private val devPort: Int? = 8081,
    private val isDevEnvironment: Boolean = false
) {
    /**
     * Resolves the target base URL for a given tenant slug.
     * In production: https://{subdomain}.wemade.id
     * In development/localhost: http://localhost:8081 (repo B; repo A memakai 8080)
     */
    fun resolveBaseUrl(slug: TenantSlug?): String {
        if (isDevEnvironment || slug == null) {
            val portSuffix = if (devPort != null) ":$devPort" else ""
            return "http://localhost$portSuffix"
        }
        return "https://${slug.value}.$rootDomain"
    }

    /**
     * Builds required headers for identifying the tenant in HTTP API requests.
     */
    fun buildTenantHeaders(session: TenantSession?): Map<String, String> {
        if (session == null) return emptyMap()
        return mapOf(
            "X-Tenant-Slug" to session.slug.value,
            "X-Tenant-ID" to session.tenantId.value
        )
    }
}
