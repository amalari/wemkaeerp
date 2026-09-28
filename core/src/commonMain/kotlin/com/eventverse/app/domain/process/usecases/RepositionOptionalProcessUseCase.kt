package com.eventverse.app.domain.process.usecases

import com.eventverse.app.domain.process.TenantProcessCatalog
import com.eventverse.app.domain.process.TenantProcessCatalogRepository
import com.eventverse.app.domain.stageflow.StageCode
import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.domain.workqueue.WorkStationCode

data class RepositionProcessCommand(
    val tenantId: TenantId,
    val processId: String,
    val samplingAnchorAfter: StageCode? = null,
    val stationAnchorAfter: WorkStationCode? = null
)

/**
 * Memindahkan posisi tahapan opsional (hasil drag-and-drop antar celah flow).
 * Reposisi hanya mengubah template flow tenant; SPK yang sudah melewati proses
 * tersebut tidak terdampak (aktivasi per SPK ditangani lapisan aplikasi).
 */
class RepositionOptionalProcessUseCase(
    private val repository: TenantProcessCatalogRepository
) {
    suspend operator fun invoke(command: RepositionProcessCommand): Result<TenantProcessCatalog> = runCatching {
        val catalog = repository.findByTenantId(command.tenantId)
            ?: error("Katalog proses tenant tidak ditemukan: ${command.tenantId.value}")
        repository.save(
            catalog.reposition(
                processId = command.processId,
                samplingAnchorAfter = command.samplingAnchorAfter,
                stationAnchorAfter = command.stationAnchorAfter
            )
        ).getOrThrow()
    }
}