package com.eventverse.app.domain.process

import com.eventverse.app.domain.stageflow.StageCode
import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.domain.workqueue.WorkStationCode
import com.eventverse.app.domain.workqueue.WorkStationSpec

/**
 * Agregat katalog proses opsional milik satu tenant.
 *
 * Invarian:
 * - `processId` dan `code` unik di dalam satu katalog.
 * - Semua proses milik tenant pemilik katalog.
 * - Setiap proses selalu punya minimal satu jangkar posisi.
 */
data class TenantProcessCatalog(
    val tenantId: TenantId,
    val processes: List<TenantOptionalProcess> = emptyList()
) {
    init {
        val duplicateIds = processes.groupingBy { it.processId }.eachCount().filterValues { it > 1 }.keys
        require(duplicateIds.isEmpty()) { "Duplicate process IDs in catalog: $duplicateIds" }
        val duplicateCodes = processes.groupingBy { it.code }.eachCount().filterValues { it > 1 }.keys
        require(duplicateCodes.isEmpty()) { "Duplicate process codes in catalog: $duplicateCodes" }
        require(processes.all { it.tenantId == tenantId }) {
            "Semua proses pada katalog harus milik tenant $tenantId"
        }
    }

    val isEmpty: Boolean get() = processes.isEmpty()

    fun findProcess(processId: String): TenantOptionalProcess? =
        processes.firstOrNull { it.processId == processId }

    fun findByCode(code: String): TenantOptionalProcess? =
        processes.firstOrNull { it.code == code }

    fun addProcess(process: TenantOptionalProcess): TenantProcessCatalog {
        require(process.tenantId == tenantId) {
            "Proses ${process.code} milik tenant ${process.tenantId}, bukan $tenantId"
        }
        require(findByCode(process.code) == null) { "Duplicate process code: ${process.code}" }
        require(findProcess(process.processId) == null) { "Duplicate process ID: ${process.processId}" }
        return copy(processes = processes + process)
    }

    /** Mengeluarkan proses dari flow. Proses tetap bisa dipasang kembali di celah lain. */
    fun removeProcess(processId: String): TenantProcessCatalog {
        requireNotNull(findProcess(processId)) { "Process not found in catalog: $processId" }
        return copy(processes = processes.filterNot { it.processId == processId })
    }

    /**
     * Memindahkan posisi proses (hasil drag-and-drop / adjust flow divisi sampling).
     * Setidaknya satu jangkar wajib tetap terisi.
     */
    fun reposition(
        processId: String,
        samplingAnchorAfter: StageCode?,
        stationAnchorAfter: WorkStationCode?
    ): TenantProcessCatalog {
        requireNotNull(findProcess(processId)) { "Process not found in catalog: $processId" }
        require(samplingAnchorAfter != null || stationAnchorAfter != null) {
            "Reposisi harus menyisakan minimal satu jangkar posisi"
        }
        return copy(processes = processes.map { process ->
            if (process.processId == processId) {
                process.withAnchors(samplingAnchorAfter, stationAnchorAfter)
            } else {
                process
            }
        })
    }
}

/**
 * Hasil resolusi proses aktif untuk satu SPK/artikel, siap dipakai dua lapisan flow:
 * - [customStations] dilempar ke `WorkStationCatalog.line(customStations)` oleh workqueue.
 * - [samplingSteps] menjadi kolom tersisip pada papan kanban sampling,
 *   terurut sesuai urutan tahap jangkarnya.
 */
data class ResolvedProcessRouting(
    val customStations: List<WorkStationSpec>,
    val samplingSteps: List<TenantOptionalProcess>
)