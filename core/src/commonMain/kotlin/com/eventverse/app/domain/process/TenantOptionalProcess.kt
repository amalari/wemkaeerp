package com.eventverse.app.domain.process

import com.eventverse.app.domain.pipeline.ModuleArchetype
import com.eventverse.app.domain.stageflow.StageCode
import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.domain.workqueue.WorkExecutionMode
import com.eventverse.app.domain.workqueue.WorkStationCode
import com.eventverse.app.domain.workqueue.WorkStationSpec
import com.eventverse.app.domain.workqueue.WorkTrackingUnit

/**
 * Satu tahapan opsional yang tenant aktifkan di alur produksinya (misal: Bordir, Sablon,
 * Laundry). Flow wajib tetap menjadi kerangka kode; tahapan opsional 100% data per tenant
 * — sejalan dengan paradigma Composable ERP dan Kontrak 7 (isolasi pipeline per tenant).
 *
 * Posisi dinyatakan dengan jangkar (anchor): tahapan disisipkan **setelah** tahap jangkar.
 * - [samplingAnchorAfter]: posisi di flow sampling (anchor = kode tahap kerangka tenant, [StageCode]).
 *   Tersimpan sebagai string yang sama dengan nama enum lama, jadi baris lama tetap valid.
 * - [stationAnchorAfter]: posisi di line workqueue (anchor = [WorkStationCode]).
 * Minimal satu jangkar wajib terisi; `null` berarti tahapan tidak tampil di flow tersebut.
 *
 * Satu proses = satu posisi per tenant: `processId` bersifat deterministik dari `code`
 * (lihat [com.eventverse.app.domain.process.usecases.AddOptionalProcessUseCase.processIdFor]).
 */
data class TenantOptionalProcess(
    val processId: String,
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
) {
    init {
        require(processId.isNotBlank()) { "processId cannot be blank" }
        require(processId.length <= 64) { "processId length cannot exceed 64 characters" }
        require(code.isNotBlank()) { "code cannot be blank" }
        require(code.length <= 64) { "code length cannot exceed 64 characters" }
        require(!code.contains(' ')) { "code cannot contain spaces: $code" }
        require(displayName.isNotBlank()) { "displayName cannot be blank" }
        require(piecerateTariffIdr >= 0L) { "piecerateTariffIdr cannot be negative" }
        require(standardMinutesPerPiece >= 0.0) { "standardMinutesPerPiece cannot be negative" }
        require(samplingAnchorAfter != null || stationAnchorAfter != null) {
            "Proses opsional harus punya minimal satu jangkar posisi (sampling atau workqueue)"
        }
    }

    val hasSamplingPlacement: Boolean get() = samplingAnchorAfter != null

    val hasStationPlacement: Boolean get() = stationAnchorAfter != null

    /**
     * Menerjemahkan proses menjadi spesifikasi stasiun kerja yang bisa dilempar ke
     * `WorkStationCatalog.line(customStations)` — jangkar dibawa apa adanya sehingga
     * stasiun tersisip tepat di posisi yang ditentukan tenant.
     */
    fun toWorkStationSpec(): WorkStationSpec = WorkStationSpec(
        code = WorkStationCode(code),
        displayName = displayName,
        archetype = archetype,
        inputTrackingUnit = WorkTrackingUnit.BUNDLE,
        outputTrackingUnit = WorkTrackingUnit.BUNDLE,
        piecerateTariffIdr = piecerateTariffIdr,
        standardMinutesPerPiece = standardMinutesPerPiece,
        insertAfterCode = stationAnchorAfter,
        description = "Proses opsional tenant: $displayName"
    )

    /** Mengganti posisi jangkar (hasil adjust flow / drag-and-drop divisi sampling). */
    fun withAnchors(
        samplingAnchorAfter: StageCode?,
        stationAnchorAfter: WorkStationCode?
    ): TenantOptionalProcess = copy(
        samplingAnchorAfter = samplingAnchorAfter,
        stationAnchorAfter = stationAnchorAfter
    )
}