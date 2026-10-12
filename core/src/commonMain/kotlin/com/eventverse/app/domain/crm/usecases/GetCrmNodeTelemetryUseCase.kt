package com.eventverse.app.domain.crm.usecases

import com.eventverse.app.domain.crm.CrmLeadRepository
import com.eventverse.app.domain.pipeline.FlowHealthStatus
import com.eventverse.app.domain.pipeline.ModuleTelemetry
import com.eventverse.app.domain.tenant.TenantId
import kotlinx.datetime.Clock

class GetCrmNodeTelemetryUseCase(
    private val leadRepository: CrmLeadRepository,
    private val kpiUseCase: GetCrmLeadKpiMetricsUseCase = GetCrmLeadKpiMetricsUseCase()
) {
    suspend operator fun invoke(tenantId: TenantId): Result<ModuleTelemetry> = runCatching {
        val leads = leadRepository.findActive(tenantId, ownerReachIds = null)
        val kpi = kpiUseCase(leads, Clock.System.now())

        val healthStatus = when {
            kpi.followUpNeededCount > kpi.activeLeadsCount * 0.5 -> FlowHealthStatus.CRITICAL
            kpi.followUpNeededCount > kpi.activeLeadsCount * 0.25 -> FlowHealthStatus.BOTTLENECK
            kpi.followUpNeededCount > kpi.activeLeadsCount * 0.1 -> FlowHealthStatus.BOTTLENECK
            else -> FlowHealthStatus.HEALTHY
        }

        ModuleTelemetry(
            module = com.eventverse.app.domain.pack.GarmentModules.CRM_SALES,
            wipPieces = kpi.activeLeadsCount,
            cycleTimeHours = 0.0,
            healthStatus = healthStatus
        )
    }
}
