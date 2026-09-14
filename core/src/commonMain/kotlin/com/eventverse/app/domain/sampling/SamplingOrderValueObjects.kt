package com.eventverse.app.domain.sampling

import kotlin.jvm.JvmInline
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
    val tensionSettings: Map<String, String> = emptyMap()
)

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
    val panelWeights: PanelWeightGrams = PanelWeightGrams(),
    val panelMinutes: PanelKnittingMinutes = PanelKnittingMinutes(),
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
}
