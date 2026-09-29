package com.eventverse.app.domain.pipeline

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

import com.eventverse.app.domain.costing.usecases.GetCostingNodeTelemetryUseCase
import com.eventverse.app.domain.production.usecases.GetProductionTelemetryUseCase
import com.eventverse.app.domain.rbac.BusinessModule
import com.eventverse.app.domain.tenant.TenantId

/** Adaptor telemetri modul yang sudah punya use case sendiri ke kontrak [ModuleTelemetryProvider]. */
class ProductionTelemetryProvider(private val useCase: GetProductionTelemetryUseCase) : ModuleTelemetryProvider {
    override val module = GarmentModules.PRODUCTION_MRP
    override suspend fun read(tenantId: TenantId) = useCase(tenantId).map {
        ModuleTelemetry(module, it.wipPieces, it.cycleTimeHours, it.healthStatus)
    }
}

/** HPP: WIP = lembar yang menunggu persetujuan; cycle time = rata-rata jam persetujuan. */
class CostingTelemetryProvider(private val useCase: GetCostingNodeTelemetryUseCase) : ModuleTelemetryProvider {
    override val module = GarmentModules.COSTING_HPP
    override suspend fun read(tenantId: TenantId) = useCase(tenantId).map {
        ModuleTelemetry(module, it.pendingSheetCount, it.avgApprovalCycleHours, it.healthStatus)
    }
}
