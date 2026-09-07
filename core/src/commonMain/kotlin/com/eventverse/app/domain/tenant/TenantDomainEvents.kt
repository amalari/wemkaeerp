package com.eventverse.app.domain.tenant

import com.eventverse.app.domain.common.DomainEvent

sealed interface TenantDomainEvent : DomainEvent

data class TenantRegistered(
    val tenantId: TenantId,
    val slug: TenantSlug,
    val name: TenantName,
    val tier: SubscriptionTier
) : TenantDomainEvent

data class TenantActivated(
    val tenantId: TenantId
) : TenantDomainEvent

data class TenantSuspended(
    val tenantId: TenantId,
    val reason: String
) : TenantDomainEvent

data class TenantTierUpdated(
    val tenantId: TenantId,
    val oldTier: SubscriptionTier,
    val newTier: SubscriptionTier
) : TenantDomainEvent
