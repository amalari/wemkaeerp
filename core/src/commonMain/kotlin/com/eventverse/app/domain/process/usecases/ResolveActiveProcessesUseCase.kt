package com.eventverse.app.domain.process.usecases

import com.eventverse.app.domain.process.ResolvedProcessRouting
import com.eventverse.app.domain.process.TenantProcessCatalog
import com.eventverse.app.domain.process.TenantProcessCatalogRepository
import com.eventverse.app.domain.tenant.TenantId

data class ResolveActiveProcessesQuery(
    val tenantId: TenantId,
    /**
     * Kode proses yang aktif untuk SPK/artikel terkait.
     * `null` = semua proses pada katalog tenant dianggap aktif (mode template flow).
     */
    val activeProcessCodes: Set<String>? = null
)

/**
 * Menyatukan katalog proses opsional tenant dengan aktivasi per SPK/artikel.
 *
 * Hasilnya dipakai dua lapisan flow tanpa duplikasi logika:
 * - [ResolvedProcessRouting.customStations] → line workqueue via `WorkStationCatalog.line`.
 * - [ResolvedProcessRouting.samplingSteps] → kolom tersisip di papan kanban sampling.
 */
class ResolveActiveProcessesUseCase(
    private val repository: TenantProcessCatalogRepository
) {
    suspend operator fun invoke(query: ResolveActiveProcessesQuery): Result<ResolvedProcessRouting> = runCatching {
        val catalog = repository.findByTenantId(query.tenantId)
            ?: TenantProcessCatalog(tenantId = query.tenantId)

        val active = if (query.activeProcessCodes == null) {
            catalog.processes
        } else {
            catalog.processes.filter { it.code in query.activeProcessCodes }
        }

        ResolvedProcessRouting(
            customStations = active.filter { it.hasStationPlacement }.map { it.toWorkStationSpec() },
            samplingSteps = active
                .filter { it.hasSamplingPlacement }
                .sortedBy { it.samplingAnchorAfter?.order ?: Int.MAX_VALUE }
        )
    }
}