package com.eventverse.app.domain.deal

import com.eventverse.app.domain.moduledev.MoneyIdr
import kotlin.jvm.JvmInline

@JvmInline
value class DealId(val value: String) {
    init {
        require(value.isNotBlank()) { "DealId cannot be blank" }
        require(value.length <= 64) { "DealId must be at most 64 characters" }
    }
}

@JvmInline
value class DealTitle(val value: String) {
    init {
        require(value.isNotBlank()) { "DealTitle cannot be blank" }
        require(value.length <= 150) { "DealTitle must be at most 150 characters" }
    }
}

@JvmInline
value class PurchaseOrderId(val value: String) {
    init {
        require(value.isNotBlank()) { "PurchaseOrderId cannot be blank" }
        require(value.length <= 64) { "PurchaseOrderId must be at most 64 characters" }
    }
}

@JvmInline
value class PoNumber(val value: String) {
    init {
        require(value.isNotBlank()) { "Nomor PO tidak boleh kosong" }
        require(value.length <= 64) { "Nomor PO maksimal 64 karakter" }
    }
}

/**
 * Business-defined stage of a sales deal. A deal is born the moment a CRM lead is qualified;
 * [PO_RECEIVED] is set automatically when the first purchase order lands on the deal.
 */
enum class DealStage(val displayName: String) {
    OPEN("Deal Terbuka"),
    PO_RECEIVED("PO Diterima"),
    IN_PRODUCTION("Dalam Produksi"),
    WON("Dimenangkan"),
    LOST("Hilang");

    /** Free movement between stages, same spirit as [com.eventverse.app.domain.crm.LeadStage]. */
    fun canTransitionTo(target: DealStage): Boolean = this != target

    companion object {
        fun fromCode(code: String?): DealStage? =
            entries.firstOrNull { it.name.equals(code, ignoreCase = true) }
    }
}

/** How a purchase order entered the system: a scanned/photographed document or typed by an admin. */
enum class PoOrigin(val displayName: String) {
    UPLOADED("PO Upload"),
    MANUAL("Input Manual");

    companion object {
        fun fromCode(code: String?): PoOrigin? =
            entries.firstOrNull { it.name.equals(code, ignoreCase = true) }
    }
}

/** One typed line of a manually-entered purchase order. */
data class PurchaseOrderLine(
    val description: String,
    val quantity: Double,
    val unitPriceIdr: Long
) {
    init {
        require(description.isNotBlank()) { "Deskripsi baris PO tidak boleh kosong" }
        require(description.length <= 250) { "Deskripsi baris PO maksimal 250 karakter" }
        require(quantity > 0) { "Kuantitas baris PO harus lebih dari 0" }
        require(unitPriceIdr >= 0) { "Harga satuan baris PO tidak boleh negatif" }
    }

    val amountIdr: Long get() = (quantity * unitPriceIdr).toLong()
}

/**
 * Purchase order (PO) milik klien yang mengikat satu [Deal].
 *
 * Dua asal-usul:
 * - [PoOrigin.UPLOADED]: berkas hasil scan/foto klien — metadata file + storage key (S3/MinIO).
 * - [PoOrigin.MANUAL]: admin mengetik nomor PO dan rinciannya langsung ke sistem.
 */
data class PurchaseOrder(
    val id: PurchaseOrderId,
    val tenantId: com.eventverse.app.domain.tenant.TenantId,
    val dealId: DealId,
    val poNumber: PoNumber,
    val poDate: kotlinx.datetime.LocalDate,
    val origin: PoOrigin,
    val fileName: String? = null,
    val mimeType: String? = null,
    val fileSizeBytes: Long? = null,
    val storageKey: String? = null,
    val lines: List<PurchaseOrderLine> = emptyList(),
    val notes: String = "",
    val recordedBy: String,
    val createdAt: kotlinx.datetime.Instant
) {
    init {
        require(origin != PoOrigin.UPLOADED || !storageKey.isNullOrBlank()) {
            "PO upload wajib memiliki storage key berkasnya"
        }
        require(origin != PoOrigin.MANUAL || lines.isNotEmpty()) {
            "PO input manual wajib memiliki minimal satu baris item"
        }
    }

    val totalValueIdr: Long get() = lines.sumOf { it.amountIdr }

    val hasFile: Boolean get() = origin == PoOrigin.UPLOADED && !storageKey.isNullOrBlank()
}
