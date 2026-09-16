package com.eventverse.app.domain.deal

import com.eventverse.app.domain.common.DomainEvent
import com.eventverse.app.domain.tenant.TenantId
import kotlinx.datetime.Instant

data class DealCreated(
    val tenantId: TenantId,
    val dealId: DealId,
    val contactId: com.eventverse.app.domain.crm.ContactId,
    val sourceLeadId: com.eventverse.app.domain.crm.LeadId?,
    val occurredAt: Instant
) : DomainEvent

data class DealStageChanged(
    val tenantId: TenantId,
    val dealId: DealId,
    val fromStage: DealStage,
    val toStage: DealStage,
    val occurredAt: Instant
) : DomainEvent

data class DealArchivedByLeadDemotion(
    val tenantId: TenantId,
    val dealId: DealId,
    val sourceLeadId: com.eventverse.app.domain.crm.LeadId,
    val occurredAt: Instant
) : DomainEvent

data class PurchaseOrderAttached(
    val tenantId: TenantId,
    val dealId: DealId,
    val purchaseOrderId: PurchaseOrderId,
    val origin: PoOrigin,
    val occurredAt: Instant
) : DomainEvent
