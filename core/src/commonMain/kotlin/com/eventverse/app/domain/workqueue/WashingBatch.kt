package com.eventverse.app.domain.workqueue

import kotlin.jvm.JvmInline
import kotlinx.datetime.Instant

@JvmInline
value class WashingBatchId(val value: String) {
    init {
        require(value.isNotBlank()) { "WashingBatchId cannot be blank" }
    }
}

enum class WashingBatchStatus {
    IN_WASHER,
    IN_DRYER,
    SORTED_COMPLETED
}

/**
 * Rincian bundle yang dimasukkan ke dalam drum mesin cuci.
 * Menegakkan invarian bukti foto fisik per bundle sebelum dicuci.
 */
data class WashingBatchItem(
    val id: String,
    val workCardId: WorkCardId,
    val subjectId: String,
    val orderNumber: String,
    val articleName: String,
    val bundleNo: Int,
    val sizeLabel: String,
    val inputPcs: Int,
    val bundlePhotoKey: String,
    val createdAt: Instant
) {
    init {
        require(bundlePhotoKey.isNotBlank()) { "Setiap bundle wajib menyertakan foto bukti fisik sebelum masuk cuci" }
        require(inputPcs > 0) { "inputPcs must be positive, was $inputPcs" }
        require(bundleNo > 0) { "bundleNo must be positive, was $bundleNo" }
        require(sizeLabel.isNotBlank()) { "sizeLabel cannot be blank" }
    }
}

/**
 * Hasil sortir meja pasca-dryer per PO (subjectId) dan per ukuran (sizeLabel).
 */
data class WashingSortOutput(
    val subjectId: String,
    val orderNumber: String,
    val sizeLabel: String,
    val outputPcs: Int,
    val scrapPcs: Int = 0,
    val defectPcs: Int = 0,
    val notes: String = ""
) {
    init {
        require(subjectId.isNotBlank()) { "subjectId cannot be blank" }
        require(sizeLabel.isNotBlank()) { "sizeLabel cannot be blank" }
        require(outputPcs >= 0) { "outputPcs cannot be negative, was $outputPcs" }
        require(scrapPcs >= 0) { "scrapPcs cannot be negative, was $scrapPcs" }
        require(defectPcs >= 0) { "defectPcs cannot be negative, was $defectPcs" }
    }

    val totalAccountedPcs: Int get() = outputPcs + scrapPcs + defectPcs
}

/**
 * Agregat sesi cuci masal drum (Washing Batch) yang melebur bundle-bundle individual.
 */
data class WashingBatch(
    val id: WashingBatchId,
    val tenantId: String,
    val batchCode: String,
    val machineDrumNo: String,
    val washRecipe: String,
    val operatorName: String,
    val items: List<WashingBatchItem>,
    val sortOutputs: List<WashingSortOutput> = emptyList(),
    val totalOutputPcs: Int = 0,
    val missingPcs: Int = 0,
    val status: WashingBatchStatus = WashingBatchStatus.IN_WASHER,
    val notes: String = "",
    val createdAt: Instant,
    val completedAt: Instant? = null
) {
    init {
        require(tenantId.isNotBlank()) { "tenantId cannot be blank" }
        require(batchCode.isNotBlank()) { "batchCode cannot be blank" }
        require(items.isNotEmpty()) { "Washing batch must contain at least one bundle" }
    }

    val totalBundles: Int get() = items.size
    val totalInputPcs: Int get() = items.sumOf { it.inputPcs }

    fun advanceToDryer(): WashingBatch {
        require(status == WashingBatchStatus.IN_WASHER) { "Cannot advance to dryer from $status" }
        return copy(status = WashingBatchStatus.IN_DRYER)
    }

    fun completeSorting(
        outputs: List<WashingSortOutput>,
        now: Instant
    ): WashingBatch {
        require(status != WashingBatchStatus.SORTED_COMPLETED) { "Batch is already sorted and completed" }
        require(outputs.isNotEmpty()) { "Sort outputs cannot be empty" }
        val outPcs = outputs.sumOf { it.outputPcs }
        val accounted = outputs.sumOf { it.totalAccountedPcs }
        val deltaMissing = (totalInputPcs - accounted).coerceAtLeast(0)

        return copy(
            sortOutputs = outputs,
            totalOutputPcs = outPcs,
            missingPcs = deltaMissing,
            status = WashingBatchStatus.SORTED_COMPLETED,
            completedAt = now
        )
    }
}
