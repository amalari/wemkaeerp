package com.eventverse.app.domain.costing

import kotlinx.datetime.Instant

/**
 * Domain Events untuk modul COSTING_HPP.
 * Semua event dalam past tense sesuai konvensi DDD.
 */
sealed interface CostingDomainEvent

data class CostingSheetCreated(
    val sheetId: CostingSheetId,
    val techPackId: String,
    val behavior: com.eventverse.app.domain.pipeline.CostingBehavior,
    val occurredAt: Instant
) : CostingDomainEvent

data class CostingSheetCalculated(
    val sheetId: CostingSheetId,
    val costingId: String,
    val billablePerUnitMinorUnits: Long,
    val occurredAt: Instant
) : CostingDomainEvent

data class CostingSheetSubmittedForApproval(
    val sheetId: CostingSheetId,
    val submittedByUserId: String,
    val occurredAt: Instant
) : CostingDomainEvent

data class CostingSheetApproved(
    val sheetId: CostingSheetId,
    val snapshotId: String,
    val approvedByUserId: String,
    val billablePerUnitMinorUnits: Long,
    val occurredAt: Instant
) : CostingDomainEvent

data class CostingSheetRejected(
    val sheetId: CostingSheetId,
    val rejectedByUserId: String,
    val reason: String,
    val occurredAt: Instant
) : CostingDomainEvent

data class CostingSheetDriftDetected(
    val sheetId: CostingSheetId,
    val snapshotBillableMinorUnits: Long,
    val currentBillableMinorUnits: Long,
    val deltaPercent: Double,
    val occurredAt: Instant
) : CostingDomainEvent

data class CostingRateCardUpdated(
    val rateCardId: CostingRateCardId,
    val behavior: com.eventverse.app.domain.pipeline.CostingBehavior,
    val updatedByUserId: String,
    val occurredAt: Instant
) : CostingDomainEvent
