package com.eventverse.app.domain.production

import com.eventverse.app.domain.tenant.TenantId
import kotlinx.datetime.Instant

sealed interface BulkProductionDomainEvent

data class BulkWorkOrderLaunched(
    val workOrderId: BulkWorkOrderId,
    val tenantId: TenantId,
    val dealId: String?,
    val totalPcs: Int,
    val occurredAt: Instant
) : BulkProductionDomainEvent

data class BulkProductionStageRecorded(
    val workOrderId: BulkWorkOrderId,
    val stage: ProductionStage,
    val completedPcs: Int,
    val occurredAt: Instant
) : BulkProductionDomainEvent

data class BulkWorkOrderCompleted(
    val workOrderId: BulkWorkOrderId,
    val totalPcs: Int,
    val rejectPcs: Int,
    val occurredAt: Instant
) : BulkProductionDomainEvent
