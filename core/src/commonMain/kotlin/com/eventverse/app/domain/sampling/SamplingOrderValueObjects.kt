package com.eventverse.app.domain.sampling

import kotlin.jvm.JvmInline
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate

@JvmInline
value class SamplingOrderId(val value: String) {
    init {
        require(value.isNotBlank()) { "SamplingOrderId cannot be blank" }
        require(value.length <= 64) { "SamplingOrderId must be at most 64 characters" }
    }
}

@JvmInline
value class SpkNumber(val value: String) {
    init {
        require(value.isNotBlank()) { "SpkNumber cannot be blank" }
        require(value.length <= 50) { "SpkNumber must be at most 50 characters" }
    }
}

enum class SamplingStatus(val displayName: String) {
    DRAFT("Draft SPK"),
    IN_PROGRESS("Sedang Pengerjaan"),
    REVISION("Revisi Sample"),
    ACC_APPROVED("ACC Produksi"),
    CANCELLED("Dibatalkan");
}

enum class SizeMode(val displayName: String) {
    ALL_SIZE("All Size (Satu Ukuran)"),
    MULTI_SIZE("Multi-Size (Grading S/M/L/XL)");
}

enum class SizeCategory(val displayName: String) {
    FINISHED_SIZE("Ukuran Jadi (Finished)"),
    KNIT_RAW_SIZE("Ukuran Rajut Mentah (Machine Raw)");
}

enum class MilestoneStep(val displayName: String, val defaultOrder: Int) {
    PROGRAM("Program Mesin", 1),
    RAJUT("Rajut Turun Mesin", 2),
    PROSES_TAMBAHAN("Proses Tambahan", 3),
    LINKING("Jahit & Linking", 4),
    WASHING("Washing / Steam", 5),
    KIRIM("Kirim Sample", 6),
    HPP("Kalkulasi HPP", 7);
}

enum class SamplingPipelineStage(val displayName: String, val order: Int) {
    NEW_INTAKE("SPK Masuk (Sales Deal)", 1),
    FLOW_REVIEW("Penentuan Alur Desain", 2),
    CAM_PROGRAMMING("Program CAM", 3),
    MACHINE_KNITTING("Rajut Turun Mesin", 4),
    LINKING_ASSEMBLY("Linking & Tambahan", 5),
    FINISHING_QC("Finishing & QC", 6),
    IN_DELIVERY("Terkirim (Tunggu ACC)", 7),
    ACC_APPROVED("ACC Produksi", 8);
}

enum class FinishingPath(val displayName: String) {
    INTERNAL("Internal Pabrik"),
    MAKLOON_VENDOR("Vendor Luar (Makloon)");
}

enum class VendorFollowUpStatus(val displayName: String) {
    NONE("Belum Ada"),
    WITH_VENDOR("Sedang di Vendor"),
    OVERDUE("Terlambat"),
    RETURNED("Sudah Kembali");
}

data class MakloonVendorInfo(
    val vendorName: String = "",
    val vendorPhone: String = "",
    val sentAt: LocalDate? = null,
    val expectedReturnAt: LocalDate? = null,
    val returnedAt: LocalDate? = null,
    val costPerPcsIdr: Long = 0L,
    val status: VendorFollowUpStatus = VendorFollowUpStatus.NONE,
    val notes: String = ""
)

data class FinishingDeposit(
    val id: String = "",
    val samplingOrderId: String = "",
    val depositDate: LocalDate,
    val qtyPcs: Int,
    val weightKg: Double = 0.0,
    val scalePhotoKey: String? = null,
    val garmentPhotoKey: String? = null,
    val operatorName: String = "",
    val notes: String = "",
    val createdAt: Instant? = null
)

data class TenselityEntry(
    val parameter: String,
    val body: String = "",
    val sleeve: String = "",
    val collar: String = ""
)

data class SizeMeasurement(
    val sizeLabel: String = "ALL SIZE",
    val bodyLength: Double = 0.0,
    val bodyWidth: Double = 0.0,
    val sleeveLength: Double = 0.0,
    val armHole: Double = 0.0,
    val neckDrop: Double = 0.0,
    val neckWidth: Double = 0.0,
    val shoulderWidth: Double = 0.0,
    val ribHeight: Double = 0.0,
    val collarHeight: Double = 0.0,
    val placketWidth: Double = 0.0,
    val sleeveOpening: Double = 0.0
)

data class KnitSpec(
    val yarnType: String = "Viscose",
    val knitType: String = "Jaquard 3-Color",
    val ribSpec: String = "1x1 (2 Play)",
    val collarSpec: String = "1x1 (2 Play)",
    val placketSpec: String = "Fullneedle",
    val colorwayNotes: String = "",
    val mockupImageUrls: List<String> = emptyList()
)

data class FeederEntry(
    val feederNumber: Int,
    val name: String,
    val ply: String,
    val color: String
)

data class PatternFormulas(
    val bodyLengthK: Double = 2.94,
    val bodyWidthN: Double = 6.6,
    val ribK: Double = 4.7
)

data class MachineProgram(
    val programFront: String = "",
    val programBack: String = "",
    val programSleeve: String = "",
    val programCollar: String = "",
    val programPlacket: String = "",
    val feederInstructions: List<FeederEntry> = emptyList(),
    val patternFormulas: PatternFormulas = PatternFormulas(),
    val tensionSettings: Map<String, String> = emptyMap(),
    val tenselityEntries: List<TenselityEntry> = emptyList()
) {
    val effectiveTenselity: List<TenselityEntry>
        get() = tenselityEntries.ifEmpty { FactorySizePresets.DEFAULT_TENSELITY }
}

data class PanelWeightGrams(
    val front: Double = 0.0,
    val back: Double = 0.0,
    val sleeve: Double = 0.0,
    val collar: Double = 0.0,
    val placket: Double = 0.0
) {
    val total: Double get() = front + back + sleeve + collar + placket
}

data class PanelKnittingMinutes(
    val front: Int = 0,
    val back: Int = 0,
    val sleeve: Int = 0,
    val collar: Int = 0,
    val placket: Int = 0
) {
    val total: Int get() = front + back + sleeve + collar + placket
}

data class YieldAndTiming(
    /** Angka acuan tingkat SPK; tetap dipakai costing dan jadi fallback size yang belum ditimbang. */
    val panelWeights: PanelWeightGrams = PanelWeightGrams(),
    val panelMinutes: PanelKnittingMinutes = PanelKnittingMinutes(),
    /** Spek per ukuran; kosong berarti seluruh ukuran memakai angka acuan di atas. Lihat [PanelSizeSpec]. */
    val perSize: List<PanelSizeSpec> = emptyList(),
    val linkingNotes: String = "",
    val additionalProcess: String = "Pasang Kancing",
    val isWashed: Boolean = false,
    val estimatedHppIdr: Long = 0L
)

data class MilestoneProgress(
    val step: MilestoneStep,
    val isCompleted: Boolean = false,
    val completedAt: LocalDate? = null,
    val notes: String = ""
)

/**
 * Standard factory presets for rapid size chart generation.
 */
object FactorySizePresets {
    val ALL_SIZE_CARDIGAN_FINISHED = SizeMeasurement(
        sizeLabel = "ALL SIZE",
        bodyLength = 60.0,
        bodyWidth = 55.0,
        sleeveLength = 60.0,
        armHole = 25.0,
        neckDrop = 6.0,
        neckWidth = 19.0,
        shoulderWidth = 43.0,
        ribHeight = 10.0,
        collarHeight = 3.0,
        placketWidth = 2.8,
        sleeveOpening = 10.0
    )

    val ALL_SIZE_CARDIGAN_RAW_KNIT = SizeMeasurement(
        sizeLabel = "ALL SIZE",
        bodyLength = 55.0,
        bodyWidth = 56.0,
        sleeveLength = 55.0,
        armHole = 25.0,
        neckDrop = 6.0,
        neckWidth = 19.0,
        shoulderWidth = 43.0,
        ribHeight = 10.0,
        collarHeight = 6.0,
        placketWidth = 2.8,
        sleeveOpening = 10.0
    )

    val DEFAULT_FEEDERS = listOf(
        FeederEntry(1, "RIB STRIPE", "1 PLAY", "HITAM"),
        FeederEntry(2, "RIB", "1 PLAY", "BW"),
        FeederEntry(3, "DASAR", "1 PLAY", "BW"),
        FeederEntry(4, "STRIPE", "1 PLAY", "M71"),
        FeederEntry(5, "STRIPE", "1 PLAY", "DARK GREY"),
        FeederEntry(6, "RIB", "1 PLAY", "HITAM"),
        FeederEntry(7, "BS POLY", "-", "-")
    )

    val DEFAULT_TENSELITY = listOf(
        TenselityEntry("1 BS POLY"),
        TenselityEntry("2 BS TARIK"),
        TenselityEntry("3 BS TARIK"),
        TenselityEntry("4 SILANG"),
        TenselityEntry("5 SILANG"),
        TenselityEntry("6 RIB"),
        TenselityEntry("7 TIF RIB"),
        TenselityEntry("8 PRODUKSI"),
        TenselityEntry("10 JAIT MATI"),
        TenselityEntry("16 BS WARNA"),
        TenselityEntry("23 MOTONG")
    )
}

/**
 * 5 Tahapan proses transaksi & fisik garmen di level Sales Deal:
 * 1. Input Spek & Pola
 * 2. Rilis SPK
 * 3. Sampling (mencakup CAM, Rajut, Linking, QC 1, Finishing, dan QC 2)
 * 4. Siap Kirim
 * 5. ACC Buyer
 *
 * Detail operasional fisik sampling dimonitor melalui Sampling Monitoring Timeline di kartu deal.
 */
enum class GarmentTrackingStep(val displayName: String, val order: Int) {
    INPUT_SPEK("Input Spek & Pola", 1),
    SPK_RELEASED("Rilis SPK", 2),
    SAMPLING("Sampling", 3),
    READY_TO_SHIP("Siap Kirim", 4),
    ACC_APPROVED("ACC Buyer", 5);
}

/**
 * Status riil satu node stepper garmen pada saat tertentu.
 */
data class GarmentStepState(
    val step: GarmentTrackingStep,
    val isCompleted: Boolean,
    val isActive: Boolean,
    val subtitle: String? = null,
    val badgeText: String? = null
)
