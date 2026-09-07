package com.eventverse.app.presentation.tenant

import com.eventverse.app.domain.tenant.SubscriptionTier
import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.domain.tenant.TenantSlug
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Client-side session state for the active tenant workspace.
 */
data class TenantSession(
    val tenantId: TenantId,
    val slug: TenantSlug,
    val name: String,
    val tier: SubscriptionTier,
    val isAccessible: Boolean = true
)

interface TenantSessionStorage {
    val currentSession: StateFlow<TenantSession?>
    fun setSession(session: TenantSession)
    fun clearSession()
}

class InMemoryTenantSessionStorage(
    initialSession: TenantSession? = null
) : TenantSessionStorage {
    private val _currentSession = MutableStateFlow(initialSession)
    override val currentSession: StateFlow<TenantSession?> = _currentSession.asStateFlow()

    override fun setSession(session: TenantSession) {
        _currentSession.value = session
    }

    override fun clearSession() {
        _currentSession.value = null
    }
}
