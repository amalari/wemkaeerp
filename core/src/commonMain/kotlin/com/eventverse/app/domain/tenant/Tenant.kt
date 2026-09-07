package com.eventverse.app.domain.tenant

/**
 * Core domain entity representing a tenant (Factory / Convection business account).
 * Follows DDD immutability rules: mutations return a new copy.
 */
data class Tenant(
    val id: TenantId,
    val slug: TenantSlug,
    val name: TenantName,
    val status: TenantStatus = TenantStatus.TRIAL,
    val tier: SubscriptionTier = SubscriptionTier.PRO,
    val activeMachineCount: Int = 0
) {
    val isAccessible: Boolean
        get() = status.isAccessible

    fun canAccessPlatform(): Boolean = isAccessible

    fun activate(): Tenant = copy(status = TenantStatus.ACTIVE)

    fun suspend(): Tenant = copy(status = TenantStatus.SUSPENDED)

    fun markDue(): Tenant = copy(status = TenantStatus.DUE)

    fun markPastDue(): Tenant = copy(status = TenantStatus.PAST_DUE)

    fun upgradeTier(newTier: SubscriptionTier): Tenant = copy(tier = newTier)

    fun updateActiveMachineCount(count: Int): Tenant {
        require(count >= 0) { "Active machine count cannot be negative: $count" }
        require(count <= tier.maxActiveMachines) {
            "Machine count ($count) exceeds tier limit of ${tier.maxActiveMachines} for ${tier.name}"
        }
        return copy(activeMachineCount = count)
    }

    fun canAddMachine(): Boolean = activeMachineCount < tier.maxActiveMachines
}
