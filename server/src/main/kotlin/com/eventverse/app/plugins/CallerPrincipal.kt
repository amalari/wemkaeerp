package com.eventverse.app.plugins

import com.eventverse.app.domain.auth.Role
import com.eventverse.app.domain.tenant.TenantId
import io.ktor.server.application.*
import io.ktor.util.*

val CallerPrincipalAttributeKey = AttributeKey<CallerPrincipal>("CallerPrincipal")

/**
 * The authenticated caller behind the current request, derived from a **verified** JWT.
 *
 * Before this existed, tenant-scoped routes trusted the `X-Tenant-Slug` request header
 * alone, so any unauthenticated caller could read or write any tenant's data simply by
 * naming it. Tenant identity now comes from the signed token; the header is only an
 * act-as request, and only a platform superadmin may make one.
 */
data class CallerPrincipal(
    val userId: String,
    val role: Role,
    /** Tenant the token itself belongs to. Null for a platform-level account. */
    val tenantId: TenantId?,
    val tenantSlug: String?
) {
    val isPlatformSuperadmin: Boolean get() = role == Role.PLATFORM_SUPERADMIN

    /** True when this caller is bound to exactly one tenant and cannot address others. */
    val isTenantBound: Boolean get() = !isPlatformSuperadmin
}

val ApplicationCall.callerPrincipalOrNull: CallerPrincipal?
    get() = attributes.getOrNull(CallerPrincipalAttributeKey)

val ApplicationCall.callerPrincipal: CallerPrincipal
    get() = attributes[CallerPrincipalAttributeKey]
