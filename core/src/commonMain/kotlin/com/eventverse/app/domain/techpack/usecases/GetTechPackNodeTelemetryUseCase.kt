package com.eventverse.app.domain.techpack.usecases

import com.eventverse.app.domain.pipeline.FlowHealthStatus
import com.eventverse.app.domain.pipeline.ModuleTelemetry
import com.eventverse.app.domain.techpack.TechPackRepository
import com.eventverse.app.domain.techpack.TechPackStatus
import com.eventverse.app.domain.tenant.TenantId
import kotlinx.datetime.Clock

class GetTechPackNodeTelemetryUseCase(
    private val repository: TechPackRepository
) {
    suspend operator fun invoke(tenantId: TenantId): Result<ModuleTelemetry> = runCatching {
        val page = repository.search(
            tenantId,
            com.eventverse.app.domain.techpack.TechPackQuery(
                includeArchived = false,
                latestVersionOnly = true,
                pageSize = 1000
            )
        )

        val drafts = page.items.count { it.status == TechPackStatus.DRAFT }
        val released = page.items.count { it.status == TechPackStatus.RELEASED }
        val wip = drafts + released

        val healthStatus = when {
            drafts > wip * 0.5 -> FlowHealthStatus.BOTTLENECK
            released == 0 && wip > 0 -> FlowHealthStatus.BOTTLENECK
            else -> FlowHealthStatus.HEALTHY
        }

        ModuleTelemetry(
            module = com.eventverse.app.domain.pack.GarmentModules.TECH_PACK_BOM,
            wipPieces = wip,
            cycleTimeHours = 0.0,
            healthStatus = healthStatus
        )
    }
}
