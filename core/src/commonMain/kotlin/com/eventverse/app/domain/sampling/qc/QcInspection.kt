package com.eventverse.app.domain.sampling.qc

import com.eventverse.app.domain.sampling.SamplingOrderId
import com.eventverse.app.domain.tenant.TenantId
import kotlin.jvm.JvmInline
import kotlin.math.abs
import kotlinx.datetime.Instant

@JvmInline
value class QcInspectionId(val value: String) {
    init {
        require(value.isNotBlank()) { "QcInspectionId cannot be blank" }
        require(value.length <= 64) { "QcInspectionId must be at most 64 characters" }
    }
}

enum class QcInspectionResult(val displayName: String) {
    PASSED("Lolos QC"),
    REWORK("Perbaikan Ulang"),
    REJECT("Reject / Rajut Ulang");
}

/**
 * Katalog cacat rajut & linking yang paling sering ditemukan di meja inspeksi.
 *
 * [liability] menentukan siapa menanggung biaya pengerjaan ulang (Kontrak 5 aturan modul):
 * cacat serat bawaan benang titipan buyer tidak boleh dibebankan ke penjahit.
 */
enum class QcDefectType(val displayName: String, val liability: DefectLiability) {
    BROKEN_NEEDLE("Jarum patah", DefectLiability.FACTORY_WORKMANSHIP),
    DROP_STITCH("Bolong / drop stitch", DefectLiability.FACTORY_WORKMANSHIP),
    LINKING_SKIP("Jahitan linking loncat", DefectLiability.FACTORY_WORKMANSHIP),
    LOOSE_BUTTON("Kancing kendor", DefectLiability.FACTORY_WORKMANSHIP),
    STEAM_MARK("Bekas setrika / steam", DefectLiability.FACTORY_WORKMANSHIP),
    COLOR_SHADING("Belang warna benang", DefectLiability.SUPPLIER_VENDOR_DEFECT),
    YARN_FLAW("Cacat serat benang bawaan", DefectLiability.CLIENT_SUPPLIED_DEFECT),
    OTHER("Lain-lain", DefectLiability.FACTORY_WORKMANSHIP);
}

enum class DefectLiability(val displayName: String) {
    FACTORY_WORKMANSHIP("Tanggungan Pabrik"),
    CLIENT_SUPPLIED_DEFECT("Cacat Bahan Klien"),
    SUPPLIER_VENDOR_DEFECT("Cacat Supplier");
}

/** Toleransi baku deviasi ukuran sampel rajut, dalam sentimeter. */
const val DEFAULT_POM_TOLERANCE_CM = 1.0

/**
 * Satu baris verifikasi Point of Measurement: ukuran target dari size chart buyer
 * dibanding hasil ukur meteran kain di meja QC.
 */
data class QcPomMeasurement(
    val pomName: String,
    val sizeColumn: String,
    val targetCm: Double,
    val actualCm: Double,
    val toleranceCm: Double = DEFAULT_POM_TOLERANCE_CM
) {
    val deviationCm: Double get() = actualCm - targetCm

    val isWithinTolerance: Boolean get() = abs(deviationCm) <= toleranceCm
}

/**
 * Lembar inspeksi QC satu sampel. Agregat tersendiri: satu SPK bisa diinspeksi berkali-kali
 * (setelah rework, setelah revisi), dan tiap lembar adalah berita acara yang berdiri sendiri.
 */
data class QcInspection(
    val id: QcInspectionId,
    val tenantId: TenantId,
    val samplingOrderId: SamplingOrderId,
    val inspectorName: String,
    val inspectedAt: Instant,
    val measurements: List<QcPomMeasurement> = emptyList(),
    val defects: List<QcDefectType> = emptyList(),
    val result: QcInspectionResult = QcInspectionResult.PASSED,
    val notes: String = "",
    val verifiedPhotoFrontKey: String? = null,
    val verifiedPhotoBackKey: String? = null
) {
    init {
        require(inspectorName.isNotBlank()) { "Nama petugas QC wajib diisi" }
    }

    val outOfToleranceMeasurements: List<QcPomMeasurement>
        get() = measurements.filter { !it.isWithinTolerance }

    val hasSizeDeviation: Boolean get() = outOfToleranceMeasurements.isNotEmpty()

    /** Cacat yang biayanya jatuh ke pabrik — dasar keputusan rework tanpa merugikan penjahit. */
    val factoryLiableDefects: List<QcDefectType>
        get() = defects.filter { it.liability == DefectLiability.FACTORY_WORKMANSHIP }

    /**
     * Keputusan yang *disarankan* domain dari temuan fisik. Petugas QC tetap boleh memutuskan
     * lain (mis. buyer sudah setuju deviasi 1,5 cm), tapi kalau menyimpang dia harus sadar
     * sedang menyimpang — [isResultConsistentWithFindings] yang menyatakannya.
     */
    val suggestedResult: QcInspectionResult
        get() = when {
            defects.any { it == QcDefectType.YARN_FLAW || it == QcDefectType.COLOR_SHADING } -> QcInspectionResult.REJECT
            hasSizeDeviation -> QcInspectionResult.REJECT
            defects.isNotEmpty() -> QcInspectionResult.REWORK
            else -> QcInspectionResult.PASSED
        }

    val isResultConsistentWithFindings: Boolean
        get() = result == suggestedResult ||
            // Menaikkan keketatan selalu boleh; melonggarkan yang perlu dipertanyakan.
            result.ordinal > suggestedResult.ordinal

    /** Foto verifikasi tampak depan wajib saat lolos — itu yang dikirim ke buyer. */
    val missingPassRequirements: List<String>
        get() = buildList {
            if (result != QcInspectionResult.PASSED) return@buildList
            if (verifiedPhotoFrontKey.isNullOrBlank()) {
                add("Foto verifikasi Tampak Depan wajib diunggah sebelum QC dinyatakan lolos.")
            }
            if (measurements.isEmpty()) {
                add("Minimal satu baris pengukuran fisik wajib diisi sebelum QC dinyatakan lolos.")
            }
        }

    val isReadyToPass: Boolean get() = missingPassRequirements.isEmpty()
}
