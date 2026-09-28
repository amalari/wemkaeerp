package com.eventverse.app.domain.process.usecases

import com.eventverse.app.domain.pipeline.ModuleArchetype
import com.eventverse.app.domain.process.TenantOptionalProcess
import com.eventverse.app.domain.process.TenantProcessCatalog
import com.eventverse.app.domain.process.TenantProcessCatalogRepository
import com.eventverse.app.domain.stageflow.StageCode
import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.domain.workqueue.WorkExecutionMode
import com.eventverse.app.domain.workqueue.WorkStationCode

data class AddOptionalProcessCommand(
    val tenantId: TenantId,
    val code: String,
    val displayName: String,
    val archetype: ModuleArchetype,
    val samplingAnchorAfter: StageCode? = null,
    val stationAnchorAfter: WorkStationCode? = null,
    val executionMode: WorkExecutionMode = WorkExecutionMode.IN_HOUSE,
    val vendorRef: String? = null,
    val piecerateTariffIdr: Long = 0L,
    val standardMinutesPerPiece: Double = 0.0
)

/**
 * Menyisipkan tahapan opsional baru ke flow tenant (tombol `+` di celah flow / drop
 * chip dari palet). Jangkar posisi didapat otomatis dari celah yang dipilih pengguna:
 * celah "setelah tahap X" berarti anchor = X.
 */
class AddOptionalProcessUseCase(
    private val repository: TenantProcessCatalogRepository
) {
    suspend operator fun invoke(command: AddOptionalProcessCommand): Result<TenantOptionalProcess> = runCatching {
        val existing = repository.findByTenantId(command.tenantId)
        val catalog = existing ?: TenantProcessCatalog(tenantId = command.tenantId)

        val process = TenantOptionalProcess(
            processId = processIdFor(command.code),
            tenantId = command.tenantId,
            code = command.code,
            displayName = command.displayName,
            archetype = command.archetype,
            samplingAnchorAfter = command.samplingAnchorAfter,
            stationAnchorAfter = command.stationAnchorAfter,
            executionMode = command.executionMode,
            vendorRef = command.vendorRef,
            piecerateTariffIdr = command.piecerateTariffIdr,
            standardMinutesPerPiece = command.standardMinutesPerPiece
        )

        repository.save(catalog.addProcess(process)).getOrThrow()
        process
    }

    companion object {
        /**
         * ID deterministik dari kode: satu proses = satu posisi per tenant.
         * Menyisipkan kode yang sama dua kali akan ditolak invariant katalog.
         */
        fun processIdFor(code: String): String = "proc-${code.trim().lowercase()}"
    }
}