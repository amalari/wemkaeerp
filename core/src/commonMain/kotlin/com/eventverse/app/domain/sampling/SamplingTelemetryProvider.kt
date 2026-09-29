package com.eventverse.app.domain.sampling

import com.eventverse.app.domain.rbac.isScopeSupported

import com.eventverse.app.domain.rbac.isFoundation

import com.eventverse.app.domain.rbac.isOperational

import com.eventverse.app.domain.rbac.isGovernance

import com.eventverse.app.domain.rbac.isHierarchical

import com.eventverse.app.domain.rbac.isGlobalOnly

import com.eventverse.app.domain.rbac.category

import com.eventverse.app.domain.rbac.supportedScopes

import com.eventverse.app.domain.rbac.kind

import com.eventverse.app.domain.rbac.scopeCapability

import com.eventverse.app.domain.rbac.iconKey

import com.eventverse.app.domain.rbac.description

import com.eventverse.app.domain.rbac.displayName

import com.eventverse.app.domain.rbac.name

import com.eventverse.app.domain.rbac.code

import com.eventverse.app.domain.pack.GarmentModules

import com.eventverse.app.domain.pipeline.FlowHealthStatus
import com.eventverse.app.domain.pipeline.ModuleTelemetry
import com.eventverse.app.domain.pipeline.ModuleTelemetryProvider
import com.eventverse.app.domain.rbac.BusinessModule
import com.eventverse.app.domain.stageflow.StageKind
import com.eventverse.app.domain.tenant.TenantId
import kotlinx.datetime.Clock

/**
 * Telemetri modul Sampling: WIP = pcs SPK yang sedang di tahap kerja, WIP per tahap untuk kanvas
 * level 2, cycle time = rata-rata jam di tahap saat ini. Tahap masuk & keluar tidak dihitung WIP —
 * barangnya belum/tidak lagi di lantai.
 */
class SamplingTelemetryProvider(
    private val repository: SamplingOrderRepository,
    private val clock: Clock = Clock.System
) : ModuleTelemetryProvider {
    override val module = GarmentModules.SAMPLING_ORDER

    override suspend fun read(tenantId: TenantId): Result<ModuleTelemetry> = runCatching {
        val onFloor = repository.findAll(tenantId)
            .filter { !it.isArchived && it.status != SamplingStatus.CANCELLED }
            .filter { order -> order.stageFrame.firstOrNull { it.code == order.stageCode }?.kind == StageKind.WORK }
        val now = clock.now()
        val hours = onFloor.mapNotNull { it.enteredCurrentStageAt?.let { at -> (now - at).inWholeMinutes / 60.0 } }
        val stalled = onFloor.count { (it.daysInCurrentStage(now) ?: 0) >= RD_STALL_WARNING_DAYS }
        ModuleTelemetry(
            module = module,
            wipPieces = onFloor.sumOf { it.sampleQuantity },
            cycleTimeHours = if (hours.isEmpty()) 0.0 else kotlin.math.round(hours.average() * 10) / 10.0,
            healthStatus = when {
                stalled >= 3 -> FlowHealthStatus.CRITICAL
                stalled >= 1 -> FlowHealthStatus.BOTTLENECK
                else -> FlowHealthStatus.HEALTHY
            },
            stageWip = onFloor.groupingBy { it.stageCode }.eachCount()
        )
    }
}
