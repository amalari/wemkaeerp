package com.eventverse.app.domain.traceability

import kotlinx.datetime.Instant

sealed interface TraceabilityDomainEvent

data class TraceContainerOpened(
    val code: TraceCode,
    val tier: TraceTier,
    val workOrder: TraceWorkOrderRef,
    val occurredAt: Instant
) : TraceabilityDomainEvent

data class TraceBundleTallied(
    val code: TraceCode,
    val completeSets: Int,
    val occurredAt: Instant
) : TraceabilityDomainEvent

data class TraceSackClosed(
    val code: TraceCode,
    val declaredPcs: Int,
    val consumedBundleCount: Int,
    val shrinkagePcs: Int,
    val occurredAt: Instant
) : TraceabilityDomainEvent
