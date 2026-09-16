package com.eventverse.app.domain.sampling

import com.eventverse.app.domain.tenant.TenantId
import kotlinx.datetime.Instant

sealed interface SamplingOrderDomainEvent {
    val orderId: SamplingOrderId
    val tenantId: TenantId
    val occurredAt: Instant
}

data class SamplingOrderCreated(
    override val orderId: SamplingOrderId,
    override val tenantId: TenantId,
    val spkNumber: SpkNumber,
    val clientName: String,
    val styleName: String,
    override val occurredAt: Instant
) : SamplingOrderDomainEvent

data class SamplingTechnicalSpecUpdated(
    override val orderId: SamplingOrderId,
    override val tenantId: TenantId,
    override val occurredAt: Instant
) : SamplingOrderDomainEvent

data class SamplingMilestoneToggled(
    override val orderId: SamplingOrderId,
    override val tenantId: TenantId,
    val step: MilestoneStep,
    val isCompleted: Boolean,
    override val occurredAt: Instant
) : SamplingOrderDomainEvent

data class SamplingOrderApproved(
    override val orderId: SamplingOrderId,
    override val tenantId: TenantId,
    val accNotes: String,
    override val occurredAt: Instant
) : SamplingOrderDomainEvent

data class SamplingOrderRevisionRequested(
    override val orderId: SamplingOrderId,
    override val tenantId: TenantId,
    val revisionNotes: String,
    override val occurredAt: Instant
) : SamplingOrderDomainEvent

/** Sampling lahir dari deal CRM — membawa [dealId] sebagai jejak Golden Sample Lock. */
data class SamplingOrderCreatedFromDeal(
    override val orderId: SamplingOrderId,
    override val tenantId: TenantId,
    val dealId: String,
    val styleName: String,
    val sampleQuantity: Int,
    override val occurredAt: Instant
) : SamplingOrderDomainEvent

/** Sampel di-ACC buyer — spesifikasi terkunci sebagai acuan produksi massal deal terkait. */
data class SamplingOrderAccApproved(
    override val orderId: SamplingOrderId,
    override val tenantId: TenantId,
    val dealId: String,
    val accNotes: String,
    override val occurredAt: Instant
) : SamplingOrderDomainEvent
