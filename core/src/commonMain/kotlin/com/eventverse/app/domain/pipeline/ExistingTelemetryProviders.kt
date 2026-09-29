package com.eventverse.app.domain.pipeline

import com.eventverse.app.domain.costing.usecases.GetCostingNodeTelemetryUseCase
import com.eventverse.app.domain.production.usecases.GetProductionTelemetryUseCase
import com.eventverse.app.domain.rbac.BusinessModule
import com.eventverse.app.domain.tenant.TenantId

/** Adaptor telemetri modul yang sudah punya use case sendiri ke kontrak [ModuleTelemetryProvider]. */
class ProductionTelemetryProvider(private val useCase: GetProductionTelemetryUseCase) : ModuleTelemetryProvider {
    override val module = BusinessModule.PRODUCTION_MRP
    override suspend fun read(tenantId: TenantId) = useCase(tenantId).map {
        ModuleTelemetry(module, it.wipPieces, it.cycleTimeHours, it.healthStatus)
    }
}

/** HPP: WIP = lembar yang menunggu persetujuan; cycle time = rata-rata jam persetujuan. */
class CostingTelemetryProvider(private val useCase: GetCostingNodeTelemetryUseCase) : ModuleTelemetryProvider {
    override val module = BusinessModule.COSTING_HPP
    override suspend fun read(tenantId: TenantId) = useCase(tenantId).map {
        ModuleTelemetry(module, it.pendingSheetCount, it.avgApprovalCycleHours, it.healthStatus)
    }
}
