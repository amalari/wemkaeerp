package com.eventverse.app.domain.techpack

import com.eventverse.app.domain.tenant.TenantId
import kotlinx.datetime.Instant

sealed interface TechPackDomainEvent {
    val techPackId: TechPackId
    val tenantId: TenantId
    val occurredAt: Instant
}

data class TechPackDrafted(
    override val techPackId: TechPackId,
    override val tenantId: TenantId,
    val styleCode: StyleCode,
    val sourceSampleSpecId: String?,
    override val occurredAt: Instant
) : TechPackDomainEvent

data class TechPackBomChanged(
    override val techPackId: TechPackId,
    override val tenantId: TenantId,
    val lineCount: Int,
    override val occurredAt: Instant
) : TechPackDomainEvent

data class TechPackReleased(
    override val techPackId: TechPackId,
    override val tenantId: TenantId,
    val styleCode: StyleCode,
    val version: Int,
    override val occurredAt: Instant
) : TechPackDomainEvent

data class TechPackRevised(
    override val techPackId: TechPackId,
    override val tenantId: TenantId,
    val fromVersion: Int,
    val toVersion: Int,
    override val occurredAt: Instant
) : TechPackDomainEvent

data class TechPackArchived(
    override val techPackId: TechPackId,
    override val tenantId: TenantId,
    override val occurredAt: Instant
) : TechPackDomainEvent
