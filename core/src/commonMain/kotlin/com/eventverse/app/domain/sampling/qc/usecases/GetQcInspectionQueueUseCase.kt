package com.eventverse.app.domain.sampling.qc.usecases

import com.eventverse.app.domain.sampling.SamplingOrder
import com.eventverse.app.domain.sampling.SamplingOrderRepository
import com.eventverse.app.domain.sampling.SamplingStatus
import com.eventverse.app.domain.sampling.SizeChartRow
import com.eventverse.app.domain.sampling.finishing.FinishingDepositRepository
import com.eventverse.app.domain.sampling.finishing.FinishingProgress
import com.eventverse.app.domain.sampling.finishing.progressAgainst
import com.eventverse.app.domain.sampling.finishing.usecases.finishingTargetPcs
import com.eventverse.app.domain.sampling.isQtyRow
import com.eventverse.app.domain.sampling.qc.DEFAULT_POM_TOLERANCE_CM
import com.eventverse.app.domain.sampling.qc.QcInspection
import com.eventverse.app.domain.sampling.qc.QcInspectionRepository
import com.eventverse.app.domain.sampling.qc.QcInspectionResult
import com.eventverse.app.domain.sampling.qc.QcPomMeasurement
import com.eventverse.app.domain.tenant.TenantId

/** Satu baris meja inspeksi QC: SPK-nya, kesiapan fisiknya, dan riwayat lembar inspeksinya. */
data class QcInspectionTask(
    val order: SamplingOrder,
    val finishingProgress: FinishingProgress,
    val inspections: List<QcInspection>
) {
    val latestInspection: QcInspection? get() = inspections.maxByOrNull { it.inspectedAt }

    /**
     * Baju baru layak naik ke meja QC setelah finishing-nya tuntas. SPK yang dilempar ke vendor
     * makloon tidak punya setoran internal, jadi kesiapannya ditentukan konfirmasi terima admin —
     * di sini diwakili target yang sudah bukan nol tanpa sisa setoran internal.
     */
    val isReadyForInspection: Boolean get() = finishingProgress.isComplete

    val isCleared: Boolean get() = latestInspection?.result == QcInspectionResult.PASSED
}

/**
 * Antrean meja inspeksi QC beserta baris POM yang sudah terisi nilai targetnya dari size chart SPK —
 * petugas tinggal mengisi kolom "aktual", bukan mengetik ulang ukuran target yang bisa salah salin.
 */
class GetQcInspectionQueueUseCase(
    private val samplingOrderRepository: SamplingOrderRepository,
    private val inspectionRepository: QcInspectionRepository,
    private val depositRepository: FinishingDepositRepository
) {
    suspend operator fun invoke(tenantId: TenantId): Result<List<QcInspectionTask>> = runCatching {
        val deposits = depositRepository.findAll(tenantId).groupBy { it.samplingOrderId }
        val inspections = inspectionRepository.findAll(tenantId).groupBy { it.samplingOrderId }

        samplingOrderRepository.findAll(tenantId)
            .filter { it.status != SamplingStatus.CANCELLED }
            .map { order ->
                QcInspectionTask(
                    order = order,
                    finishingProgress = deposits[order.id].orEmpty().progressAgainst(finishingTargetPcs(order)),
                    inspections = inspections[order.id].orEmpty().sortedBy { it.inspectedAt }
                )
            }
            .sortedWith(compareBy({ it.isCleared }, { !it.isReadyForInspection }))
    }
}

/**
 * Menyiapkan baris pengukuran QC dari matriks ukuran SPK untuk satu kolom ukuran.
 *
 * Baris kuantitas dilewati — jumlah pcs bukan Point of Measurement, dan kalau ikut terbawa,
 * "2 pcs" akan tampil sebagai deviasi ukuran raksasa di tabel inspeksi.
 */
fun buildQcMeasurementTemplate(
    sizeMatrix: List<SizeChartRow>,
    sizeColumn: String,
    toleranceCm: Double = DEFAULT_POM_TOLERANCE_CM
): List<QcPomMeasurement> = sizeMatrix
    .filter { !it.isQtyRow }
    .mapNotNull { row ->
        val target = row.values[sizeColumn]?.trim()?.replace(',', '.')?.toDoubleOrNull() ?: return@mapNotNull null
        QcPomMeasurement(
            pomName = row.pomName,
            sizeColumn = sizeColumn,
            targetCm = target,
            actualCm = target,
            toleranceCm = toleranceCm
        )
    }
