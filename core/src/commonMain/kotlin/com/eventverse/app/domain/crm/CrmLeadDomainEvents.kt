package com.eventverse.app.domain.crm

import com.eventverse.app.domain.common.DomainEvent
import com.eventverse.app.domain.tenant.TenantId
import kotlinx.datetime.Instant

data class LeadCreated(val tenantId: TenantId, val leadId: LeadId, val occurredAt: Instant) : DomainEvent

data class LeadStageChanged(
    val tenantId: TenantId,
    val leadId: LeadId,
    val fromStage: LeadStage,
    val toStage: LeadStage,
    val occurredAt: Instant
) : DomainEvent
