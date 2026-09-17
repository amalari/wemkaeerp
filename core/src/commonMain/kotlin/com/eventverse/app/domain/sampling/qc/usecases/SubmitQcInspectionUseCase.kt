package com.eventverse.app.domain.sampling.qc.usecases

import com.eventverse.app.domain.sampling.SamplingOrderId
import com.eventverse.app.domain.sampling.SamplingOrderRepository
import com.eventverse.app.domain.sampling.qc.QcDefectType
import com.eventverse.app.domain.sampling.qc.QcInspection
import com.eventverse.app.domain.sampling.qc.QcInspectionRepository
import com.eventverse.app.domain.sampling.qc.QcInspectionResult
import com.eventverse.app.domain.sampling.qc.QcPomMeasurement
import com.eventverse.app.domain.tenant.TenantId
import kotlinx.datetime.Clock

data class SubmitQcInspectionCommand(
    val tenantId: TenantId,
    val samplingOrderId: SamplingOrderId,
    val inspectorName: String,
    val measurements: List<QcPomMeasurement> = emptyList(),
    val defects: List<QcDefectType> = emptyList(),
    val result: QcInspectionResult,
    val notes: String = "",
    val verifiedPhotoFrontKey: String? = null,
    val verifiedPhotoBackKey: String? = null
)

/**
 * Menyimpan satu lembar inspeksi QC.
 *
 * Gerbangnya hanya berlaku untuk keputusan `PASSED`: baju yang dinyatakan lolos akan difoto dan
 * dikirim ke buyer, jadi foto verifikasi dan hasil ukur fisiknya wajib ada. `REWORK` dan `REJECT`
 * justru harus selalu bisa dicatat — menghalangi pencatatan temuan buruk adalah cara tercepat
 * membuat temuan itu tidak dicatat sama sekali.
 */
class SubmitQcInspectionUseCase(
    private val inspectionRepository: QcInspectionRepository,
    private val samplingOrderRepository: SamplingOrderRepository,
    private val clock: Clock = Clock.System
) {
    suspend operator fun invoke(command: SubmitQcInspectionCommand): Result<QcInspection> = runCatching {
        val order = samplingOrderRepository.findById(command.samplingOrderId)
            ?: error("SPK sampling tidak ditemukan: ${command.samplingOrderId.value}")
        require(order.tenantId == command.tenantId) { "SPK sampling bukan milik pabrik ini" }

        val inspection = QcInspection(
            id = inspectionRepository.nextId(command.tenantId),
            tenantId = command.tenantId,
            samplingOrderId = command.samplingOrderId,
            inspectorName = command.inspectorName.trim(),
            inspectedAt = clock.now(),
            measurements = command.measurements,
            defects = command.defects.distinct(),
            result = command.result,
            notes = command.notes.trim(),
            verifiedPhotoFrontKey = command.verifiedPhotoFrontKey?.trim()?.takeIf { it.isNotEmpty() },
            verifiedPhotoBackKey = command.verifiedPhotoBackKey?.trim()?.takeIf { it.isNotEmpty() }
        )

        val blockers = inspection.missingPassRequirements
        require(blockers.isEmpty()) { blockers.joinToString(" ") }

        inspectionRepository.save(inspection)
    }
}
