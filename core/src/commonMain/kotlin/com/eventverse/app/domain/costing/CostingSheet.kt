package com.eventverse.app.domain.costing

import com.eventverse.app.domain.common.Money
import com.eventverse.app.domain.contracts.CostingCalculationResult
import com.eventverse.app.domain.tenant.TenantId
import kotlinx.datetime.Instant

/**
 * Snapshot komersial yang disimpan saat HPP disetujui.
 * Setelah disetujui, angka ini tidak boleh berubah — bahkan jika harga material berubah.
 * Snapshot inilah yang menjadi dasar pembuatan invoice dan kontrak produksi.
 */
data class CostingSnapshot(
    val snapshotId: String,
    val approvedAt: Instant,
    val approvedByUserId: String,
    val result: CostingCalculationResult,
    /** Fingerprint dari semua input kalkulasi — dasar deteksi drift. */
    val inputFingerprint: String
)

/**
 * Lembar HPP (Harga Pokok Produksi) — agregat utama modul COSTING_HPP.
 *
 * Satu lembar HPP mewakili satu siklus kalkulasi untuk satu Tech Pack + order quantity.
 * Siklus hidup: `DRAFT → CALCULATED → PENDING_APPROVAL → APPROVED` (terminal komersial).
 *
 * ## Mengapa `approvedSnapshot` sebagai freeze point?
 * Setelah klien menyetujui harga, harga bahan baku bisa berubah.
 * `approvedSnapshot` memastikan invoice tetap menggunakan angka saat persetujuan,
 * bukan harga hari ini. Ini prinsip "commercial commitment" — pabrik tidak bisa
 * mengubah HPP setelah deal ditandatangani.
 *
 * ## Mengapa `pricingAsOf` bukan `Clock.System.now()`?
 * `pricingAsOf` ditetapkan saat pembuatan draft (biasanya = tanggal order masuk).
 * Ini memungkinkan back-dating: menghitung HPP untuk order bulan lalu menggunakan
 * harga material bulan lalu, bukan harga hari ini.
 */
data class CostingSheet(
    val id: CostingSheetId,
    val tenantId: TenantId,
    val number: CostingNumber,
    val techPackId: String,
    val orderQuantity: Long,
    val behavior: com.eventverse.app.domain.pipeline.CostingBehavior,
    val status: CostingSheetStatus = CostingSheetStatus.DRAFT,
    val pricingAsOf: Instant,
    val parameterOverrides: Map<String, String> = emptyMap(),
    /** Hasil kalkulasi terkini — null jika status DRAFT sebelum dihitung. */
    val latestResult: CostingCalculationResult? = null,
    /** Snapshot saat disetujui — null sampai status APPROVED. */
    val approvedSnapshot: CostingSnapshot? = null,
    val rejectionReason: String? = null,
    val notes: String = "",
    val linkedSpkNumber: String? = null,
    val createdByUserId: String? = null,
    val createdAt: Instant,
    val updatedAt: Instant
) {
    init {
        require(orderQuantity > 0L) { "Kuantitas order harus lebih besar dari 0" }
        require(
            status != CostingSheetStatus.APPROVED || approvedSnapshot != null
        ) { "Lembar yang APPROVED wajib punya approvedSnapshot" }
        require(
            status != CostingSheetStatus.REJECTED || rejectionReason != null
        ) { "Lembar yang REJECTED wajib punya alasan penolakan" }
    }

    val isEditable: Boolean get() = status.isEditable
    val isCommitted: Boolean get() = status.isCommitted

    /**
     * Menyimpan hasil kalkulasi dan mengubah status ke CALCULATED.
     * Gagal jika status tidak mengizinkan perubahan (mis. APPROVED atau PENDING_APPROVAL).
     */
    fun applyCalculation(result: CostingCalculationResult, now: Instant): Result<CostingSheet> {
        if (!isEditable) {
            return Result.failure(
                IllegalStateException(
                    "Lembar HPP #$number berstatus '${status.displayName}' tidak dapat dihitung ulang. " +
                        "Buat revisi baru untuk mengubah HPP yang sudah dikunci."
                )
            )
        }
        return Result.success(
            copy(
                latestResult = result,
                status = CostingSheetStatus.CALCULATED,
                updatedAt = now
            )
        )
    }

    /**
     * Mengirim ke antrian persetujuan.
     * Gagal jika belum CALCULATED atau belum ada parameter kritis yang diisi.
     */
    fun submitForApproval(
        criticalParamsMissing: Set<String> = emptySet(),
        now: Instant
    ): Result<CostingSheet> {
        if (status != CostingSheetStatus.CALCULATED) {
            return Result.failure(
                IllegalStateException(
                    "Hanya lembar berstatus CALCULATED yang bisa diajukan. Status saat ini: ${status.displayName}"
                )
            )
        }
        if (latestResult == null) {
            return Result.failure(IllegalStateException("Lembar belum memiliki hasil kalkulasi"))
        }
        if (criticalParamsMissing.isNotEmpty()) {
            return Result.failure(
                IllegalStateException(
                    "Parameter HPP kritis masih menggunakan default sistem: ${criticalParamsMissing.joinToString()}. " +
                        "Isi rate card tenant sebelum mengajukan ke approval."
                )
            )
        }
        return Result.success(copy(status = CostingSheetStatus.PENDING_APPROVAL, updatedAt = now))
    }

    /**
     * Menyetujui lembar HPP — membuat [CostingSnapshot] yang membekukan angka komersial.
     * Hanya bisa dilakukan dari status PENDING_APPROVAL dengan permission APPROVE_COSTING.
     */
    fun approve(
        approvedByUserId: String,
        inputFingerprint: String,
        snapshotId: String,
        now: Instant
    ): Result<CostingSheet> {
        if (status != CostingSheetStatus.PENDING_APPROVAL) {
            return Result.failure(
                IllegalStateException(
                    "Hanya lembar berstatus PENDING_APPROVAL yang bisa disetujui. Status saat ini: ${status.displayName}"
                )
            )
        }
        val result = latestResult ?: return Result.failure(
            IllegalStateException("Tidak ada hasil kalkulasi untuk disetujui")
        )
        val snapshot = CostingSnapshot(
            snapshotId = snapshotId,
            approvedAt = now,
            approvedByUserId = approvedByUserId,
            result = result,
            inputFingerprint = inputFingerprint
        )
        return Result.success(
            copy(
                status = CostingSheetStatus.APPROVED,
                approvedSnapshot = snapshot,
                updatedAt = now
            )
        )
    }

    /**
     * Menolak lembar HPP dengan alasan, mengembalikan ke DRAFT untuk revisi.
     */
    fun reject(reason: String, now: Instant): Result<CostingSheet> {
        require(reason.isNotBlank()) { "Alasan penolakan tidak boleh kosong" }
        if (status != CostingSheetStatus.PENDING_APPROVAL) {
            return Result.failure(
                IllegalStateException("Hanya lembar PENDING_APPROVAL yang bisa ditolak")
            )
        }
        return Result.success(
            copy(
                status = CostingSheetStatus.REJECTED,
                rejectionReason = reason,
                updatedAt = now
            )
        )
    }

    /**
     * Membuat revisi baru dari lembar yang sudah APPROVED/REJECTED.
     * Lembar lama di-supersede, revisi baru dimulai dari DRAFT.
     */
    fun supersede(now: Instant): Result<CostingSheet> {
        if (status != CostingSheetStatus.APPROVED && status != CostingSheetStatus.REJECTED) {
            return Result.failure(
                IllegalStateException(
                    "Hanya lembar APPROVED atau REJECTED yang bisa digantikan. Status saat ini: ${status.displayName}"
                )
            )
        }
        return Result.success(copy(status = CostingSheetStatus.SUPERSEDED, updatedAt = now))
    }

    /**
     * Membandingkan snapshot yang disetujui dengan hasil kalkulasi terkini.
     * Mengembalikan [CostingDrift.None] jika belum ada snapshot atau belum ada hasil baru.
     */
    fun driftAgainst(currentResult: CostingCalculationResult): CostingDrift {
        val snapshot = approvedSnapshot ?: return CostingDrift.None
        val snapshotBillable = snapshot.result.billablePerUnit
        val currentBillable = currentResult.billablePerUnit
        if (snapshotBillable.minorUnits == 0L) return CostingDrift.None

        val delta = currentBillable - snapshotBillable
        val deltaPercent = (delta.minorUnits.toDouble() / snapshotBillable.minorUnits.toDouble()) * 100.0

        return if (delta.isZero) {
            CostingDrift.None
        } else {
            CostingDrift.Detected(
                deltaPerUnit = delta,
                deltaPercent = deltaPercent,
                changedInputs = emptyList(), // Diisi oleh DetectCostingSheetDriftUseCase
                currentResult = currentResult
            )
        }
    }

    fun archive(now: Instant): CostingSheet = copy(status = CostingSheetStatus.ARCHIVED, updatedAt = now)
}
