package com.eventverse.app.domain.costing.usecases

import com.eventverse.app.domain.costing.CostingNodeTelemetry
import com.eventverse.app.domain.costing.CostingSheetRepository
import com.eventverse.app.domain.costing.CostingSheetStatus
import com.eventverse.app.domain.tenant.TenantId
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.time.Duration.Companion.hours

/**
 * Mengambil telemetri node `costing_hpp` untuk visualisasi kanvas Factory Flow.
 *
 * Memenuhi Kontrak 6 (Operational Telemetry) dari module-integration-rules.md.
 *
 * @param sheetRepository Repository untuk mengambil data lembar HPP.
 */
class GetCostingNodeTelemetryUseCase(
    private val sheetRepository: CostingSheetRepository,
    private val clock: Clock = Clock.System
) {
    suspend operator fun invoke(tenantId: TenantId): Result<CostingNodeTelemetry> = runCatching {
        val pendingCount = sheetRepository.countByStatus(tenantId, CostingSheetStatus.DRAFT) +
            sheetRepository.countByStatus(tenantId, CostingSheetStatus.CALCULATED)

        // Overdue = PENDING_APPROVAL yang sudah menunggu > 48 jam
        val overdueThreshold: Instant = clock.now() - 48.hours
        val overdueCount = sheetRepository.findPendingApprovalOlderThan(tenantId, overdueThreshold).size

        val avgCycleHours = sheetRepository.avgApprovalCycleHours(tenantId, withinDays = 30)

        val healthStatus = CostingNodeTelemetry.deriveHealthStatus(
            pendingSheetCount = pendingCount,
            overdueApprovalCount = overdueCount
        )

        CostingNodeTelemetry(
            pendingSheetCount = pendingCount,
            overdueApprovalCount = overdueCount,
            avgApprovalCycleHours = avgCycleHours,
            healthStatus = healthStatus
        )
    }
}
