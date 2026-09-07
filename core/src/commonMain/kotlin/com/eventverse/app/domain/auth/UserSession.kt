package com.eventverse.app.domain.auth

import com.eventverse.app.domain.tenant.TenantId
import kotlin.jvm.JvmInline

@JvmInline
value class AuthToken(val value: String) {
    init {
        require(value.isNotBlank()) { "AuthToken cannot be blank" }
    }
}

data class UserSession(
    val user: User,
    val token: AuthToken,
    val tenantSlug: String? = null
)
