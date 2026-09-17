package com.eventverse.app.domain.production

import kotlin.jvm.JvmInline
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate

@JvmInline
value class BulkWorkOrderId(val value: String) {
    init {
        require(value.isNotBlank()) { "BulkWorkOrderId cannot be blank" }
        require(value.length <= 64) { "BulkWorkOrderId must be at most 64 characters" }
    }
}

@JvmInline
value class BulkSpkNumber(val value: String) {
    init {
        require(value.isNotBlank()) { "BulkSpkNumber cannot be blank" }
        require(value.length <= 50) { "BulkSpkNumber must be at most 50 characters" }
    }
}

@JvmInline
value class ProductionLineName(val value: String) {
    init {
        require(value.isNotBlank()) { "ProductionLineName cannot be blank" }
        require(value.length <= 60) { "ProductionLineName must be at most 60 characters" }
    }
}

enum class BulkProductionStatus(val displayName: String, val order: Int) {
    DRAFT("Draft SPK Massal", 1),
    RELEASED("SPK Diterbitkan", 2),
    CUTTING("Proses Potong", 3),
    SEWING("Proses Jahit", 4),
    FINISHING("Finishing & Packing", 5),
    COMPLETED("Selesai Produksi", 6),
    CANCELLED("Dibatalkan", 7);

    val isTerminal: Boolean get() = this == COMPLETED || this == CANCELLED
}

/**
 * Tiga tahap lantai produksi yang dilaporkan progresnya.
 *
 * Urutannya mengikat: potongan tidak bisa dijahit sebelum dipotong, jadi setoran jahit
 * dibatasi oleh hasil potong — lihat `BulkWorkOrder.recordStageProgress`.
 */
enum class ProductionStage(val displayName: String, val order: Int) {
    CUTTING("Potong", 1),
    SEWING("Jahit", 2),
    FINISHING("Finishing", 3);

    val previous: ProductionStage? get() = entries.firstOrNull { it.order == order - 1 }

    /** Status SPK yang mewakili tahap ini saat sedang berjalan. */
    val runningStatus: BulkProductionStatus
        get() = when (this) {
            CUTTING -> BulkProductionStatus.CUTTING
            SEWING -> BulkProductionStatus.SEWING
            FINISHING -> BulkProductionStatus.FINISHING
        }
}

/**
 * Satu baris size breakdown pesanan massal — berapa pcs untuk tiap ukuran.
 *
 * Inilah yang membedakan SPK massal dari SPK sampling: buyer memesan 1.000 pcs yang
 * terpecah ke S/M/L/XL dengan komposisi tertentu, dan lantai potong perlu angka per ukuran,
 * bukan totalnya saja.
 */
data class BulkSizeLine(
    val sizeLabel: String,
    val orderedPcs: Int
) {
    init {
        require(sizeLabel.isNotBlank()) { "Label ukuran tidak boleh kosong" }
        require(orderedPcs > 0) { "Jumlah pesanan ukuran $sizeLabel harus lebih dari 0 pcs" }
    }
}

/**
 * Alokasi satu lini/kelompok mesin untuk mengerjakan sebagian pesanan.
 *
 * [assignedPcs] adalah beban kerja lini ini, bukan hasil kerjanya — hasil dilaporkan lewat
 * [ProductionStageProgress]. Memisahkan rencana dari realisasi membuat selisihnya terlihat.
 */
data class MachineLineAllocation(
    val lineName: ProductionLineName,
    val machineCount: Int,
    val assignedPcs: Int,
    val startDate: LocalDate? = null,
    val targetFinishDate: LocalDate? = null,
    val operatorCount: Int = 0,
    val notes: String = ""
) {
    init {
        require(machineCount >= 0) { "Jumlah mesin tidak boleh negatif" }
        require(assignedPcs > 0) { "Beban lini ${lineName.value} harus lebih dari 0 pcs" }
        require(operatorCount >= 0) { "Jumlah operator tidak boleh negatif" }
        if (startDate != null && targetFinishDate != null) {
            require(targetFinishDate >= startDate) {
                "Target selesai lini ${lineName.value} tidak boleh mendahului tanggal mulai"
            }
        }
    }
}

/**
 * Realisasi kumulatif satu tahap produksi.
 *
 * [rejectPcs] keluar dari alur (harus dipotong/dijahit ulang), sedangkan [reworkPcs] masih
 * dihitung selesai di tahap ini tapi ditandai perlu perbaikan — pembedaan ini yang menentukan
 * apakah tahap berikutnya boleh menerima barangnya.
 */
data class ProductionStageProgress(
    val stage: ProductionStage,
    val completedPcs: Int = 0,
    val reworkPcs: Int = 0,
    val rejectPcs: Int = 0,
    val lastUpdatedAt: Instant? = null
) {
    init {
        require(completedPcs >= 0) { "Jumlah selesai tidak boleh negatif" }
        require(reworkPcs >= 0) { "Jumlah rework tidak boleh negatif" }
        require(rejectPcs >= 0) { "Jumlah reject tidak boleh negatif" }
        require(reworkPcs <= completedPcs) {
            "Rework (${reworkPcs}) tidak boleh melebihi jumlah selesai (${completedPcs}) di tahap ${stage.displayName}"
        }
    }

    /** Pcs yang benar-benar lolos ke tahap berikutnya. */
    val passedPcs: Int get() = completedPcs - reworkPcs
}
