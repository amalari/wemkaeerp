package com.eventverse.app.domain.auth

import com.eventverse.app.domain.common.DomainEvent
import com.eventverse.app.domain.tenant.TenantId

sealed interface AuthDomainEvent : DomainEvent

data class UserRegistered(
    val userId: UserId,
    val tenantId: TenantId?,
    val username: Username,
    val role: Role
) : AuthDomainEvent

data class UserLoggedIn(
    val userId: UserId,
    val tenantId: TenantId?
) : AuthDomainEvent

data class UserRoleUpdated(
    val userId: UserId,
    val oldRole: Role,
    val newRole: Role
) : AuthDomainEvent

data class UserDeactivated(
    val userId: UserId,
    val tenantId: TenantId?
) : AuthDomainEvent
