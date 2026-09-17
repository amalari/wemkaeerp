package com.eventverse.app.domain.production.usecases

import com.eventverse.app.domain.pipeline.FlowHealthStatus
import com.eventverse.app.domain.production.BulkWorkOrderRepository
import com.eventverse.app.domain.tenant.TenantId

/**
 * Telemetri node `PRODUCTION_MRP` untuk kanvas Alur Pabrik (Kontrak 6).
 *
 * [cycleTimeHours] sengaja dihitung dari jam nyata sejak SPK diterbitkan dibagi pcs yang sudah
 * tuntas — bukan dari target harian yang direncanakan. Target adalah harapan; yang perlu dilihat
 * di kanvas adalah kecepatan yang benar-benar terjadi.
 */
data class ProductionNodeTelemetry(
    val wipPieces: Int,
    val cycleTimeHours: Double,
    val healthStatus: FlowHealthStatus,
    val activeWorkOrders: Int,
    val totalOrderedPcs: Int,
    val completedPcs: Int
)

class GetProductionTelemetryUseCase(
    private val repository: BulkWorkOrderRepository,
    private val clock: kotlinx.datetime.Clock = kotlinx.datetime.Clock.System
) {
    suspend operator fun invoke(tenantId: TenantId): Result<ProductionNodeTelemetry> = runCatching {
        val active = repository.findAll(tenantId)
            .filter { !it.status.isTerminal && !it.isArchived }

        val wip = active.sumOf { it.wipPieces }
        val completed = active.sumOf { it.completedPcs }
        val now = clock.now()

        val elapsedHours = active.sumOf { order ->
            val since = order.releasedAt ?: order.createdAt
            ((now - since).inWholeMinutes.coerceAtLeast(0)).toDouble() / 60.0
        }

        ProductionNodeTelemetry(
            wipPieces = wip,
            cycleTimeHours = if (completed > 0) elapsedHours / completed else 0.0,
            healthStatus = active
                .map { it.healthStatus }
                // Node memakai kondisi terburuk di antara SPK-nya: satu lini macet sudah cukup
                // membuat seluruh node pantas berkedip, walau SPK lain lancar.
                .maxByOrNull { status ->
                    when (status) {
                        FlowHealthStatus.CRITICAL -> 3
                        FlowHealthStatus.BOTTLENECK -> 2
                        FlowHealthStatus.HEALTHY -> 1
                        FlowHealthStatus.BYPASSED -> 0
                    }
                } ?: FlowHealthStatus.HEALTHY,
            activeWorkOrders = active.size,
            totalOrderedPcs = active.sumOf { it.totalOrderedPcs },
            completedPcs = completed
        )
    }
}
