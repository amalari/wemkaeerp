package com.eventverse.app.domain.pipeline

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
