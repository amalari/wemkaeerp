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

import com.eventverse.app.domain.rbac.BusinessModule
import com.eventverse.app.domain.stageflow.StageCode
import com.eventverse.app.domain.tenant.TenantId

/**
 * Telemetri nyata satu node kanvas (module-integration-rules Kontrak 6). Node tanpa telemetri tetap
 * tampil dengan angka seed, tetapi **ditandai estimasi** — angka contoh tidak boleh terlihat nyata.
 *
 * @param stageWip jumlah dokumen per tahap (kanvas level 2), bila modul punya kerangka tahap.
 */
data class ModuleTelemetry(
    val module: BusinessModule,
    val wipPieces: Int,
    val cycleTimeHours: Double,
    val healthStatus: FlowHealthStatus,
    val stageWip: Map<StageCode, Int> = emptyMap()
)

/**
 * Sumber telemetri satu modul. **Modul operasional baru yang ingin angka nyata di kanvas cukup
 * menambah satu provider** dan mendaftarkannya di wiring server (`PipelineTelemetryRoutes`).
 */
interface ModuleTelemetryProvider {
    val module: BusinessModule
    suspend fun read(tenantId: TenantId): Result<ModuleTelemetry>
}
