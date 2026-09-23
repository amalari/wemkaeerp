package com.eventverse.app.domain.vendor

import com.eventverse.app.domain.tenant.TenantId
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate

/**
 * Keputusan admin produksi: "proses [processCode] pada order [subjectId] dikerjakan oleh
 * vendor [vendorId] dengan harga ini".
 *
 * Agregat sendiri, bukan field di `SamplingOrder`, karena dua alasan: satu order bisa dikirim
 * ke beberapa vendor sekaligus (sablon ke A, bordir ke B), dan penugasan yang sama kelak
 * berlaku untuk PO produksi massal — [subjectId] sengaja tidak bertipe `SamplingOrderId`.
 *
 * Nama, telepon, dan harga vendor disalin (snapshot) saat penugasan dibuat. Vendor yang menaikkan
 * harga bulan depan tidak boleh mengubah biaya order yang sudah berjalan.
 */
data class VendorAssignment(
    val id: VendorAssignmentId,
    val tenantId: TenantId,
    val subjectId: String,
    val subjectLabel: String,
    val processCode: String,
    val processName: String,
    val vendorId: VendorId,
    val vendorName: VendorName,
    val vendorPhone: String = "",
    val pricePerUnitIdr: Long,
    val unit: VendorPriceUnit,
    val quantityPcs: Int,
    val unitsPerPiece: Int = 1,
    val priceSource: VendorPriceSource,
    val expectedReturnAt: LocalDate? = null,
    val notes: String = "",
    val status: VendorAssignmentStatus = VendorAssignmentStatus.ASSIGNED,
    val assignedByUserId: String = "",
    val assignedAt: Instant,
    val cancelledAt: Instant? = null
) {
    init {
        require(subjectId.isNotBlank()) { "Order penugasan vendor tidak boleh kosong" }
        require(processCode.isNotBlank()) { "Proses penugasan vendor tidak boleh kosong" }
        require(pricePerUnitIdr >= 0L) { "Harga vendor tidak boleh negatif" }
        require(quantityPcs > 0) { "Jumlah pcs yang dikirim ke vendor harus lebih dari 0" }
        require(unitsPerPiece > 0) { "Jumlah titik per pcs harus lebih dari 0" }
    }

    val isActive: Boolean get() = status == VendorAssignmentStatus.ASSIGNED

    val totalIdr: Long get() = unit.totalFor(pricePerUnitIdr, quantityPcs, unitsPerPiece)

    fun covers(subjectId: String, processCode: String): Boolean =
        this.subjectId == subjectId && this.processCode.equals(processCode, ignoreCase = true)

    fun cancel(now: Instant): VendorAssignment {
        check(isActive) { "Penugasan vendor ini sudah dibatalkan" }
        return copy(status = VendorAssignmentStatus.CANCELLED, cancelledAt = now)
    }
}
