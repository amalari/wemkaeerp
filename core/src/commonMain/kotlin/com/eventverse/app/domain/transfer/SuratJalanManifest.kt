package com.eventverse.app.domain.transfer

import com.eventverse.app.domain.workqueue.WorkCardId
import com.eventverse.app.domain.workqueue.WorkSubjectRef
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate

data class SuratJalanItem(
    val id: String,
    val workCardId: WorkCardId? = null,
    val bundleNo: Int? = null,
    val cartonId: CartonId? = null,
    val sizeLabel: String,
    val colorway: String = "",
    val qtyPcs: Int,
    val notes: String = ""
) {
    init {
        require(qtyPcs > 0) { "qtyPcs must be strictly positive, was $qtyPcs" }
        require(sizeLabel.isNotBlank()) { "sizeLabel cannot be blank" }
    }
}

/**
 * Agregat Dokumen Surat Jalan (Manifest Pengiriman & Mutasi Barang).
 *
 * Menjamin integritas pemisahan alur:
 * - Pada [TransferType.INTERNAL_SITE_TRANSFER]: Mempertahankan rincian tiket bundle individual ([bundleNo]).
 * - Pada [TransferType.SUBCONTRACT_OUTBOUND]: Mengunci vendor rekanan, ongkos jasa makloon, dan SLA tanggal kembali.
 * - Pada [TransferType.CUSTOMER_DISPATCH]: Mendukung pengiriman bertahap berbasis nomor kardus/karung ([cartonId]).
 */
data class SuratJalanManifest(
    val id: SuratJalanId,
    val tenantId: String,
    val sjNumber: SuratJalanNumber,
    val transferType: TransferType,
    val subject: WorkSubjectRef,
    val originLocationId: LocationId? = null,
    val destinationLocationId: LocationId? = null,
    val vendorRef: String? = null,
    val customerName: String? = null,
    val customerAddress: String? = null,
    val carrierName: String? = null,
    val driverName: String? = null,
    val vehiclePlate: String? = null,
    val status: TransferStatus = TransferStatus.DRAFT,
    val items: List<SuratJalanItem> = emptyList(),
    val unitServiceFeeIdr: Long = 0L,
    val expectedReturnDate: LocalDate? = null,
    val dispatchedAt: Instant? = null,
    val receivedAt: Instant? = null,
    val notes: String = ""
) {
    init {
        require(tenantId.isNotBlank()) { "tenantId cannot be blank" }

        when (transferType) {
            TransferType.INTERNAL_SITE_TRANSFER -> {
                requireNotNull(originLocationId) { "Internal transfer requires originLocationId" }
                requireNotNull(destinationLocationId) { "Internal transfer requires destinationLocationId" }
                require(originLocationId != destinationLocationId) {
                    "Internal transfer origin and destination must be different physical locations"
                }
                require(items.all { it.bundleNo != null }) {
                    "Internal transfer must preserve individual bundle numbers for all items"
                }
            }
            TransferType.SUBCONTRACT_OUTBOUND -> {
                requireNotNull(vendorRef) { "Subcontract outbound transfer requires vendorRef" }
                require(vendorRef.isNotBlank()) { "vendorRef cannot be blank" }
            }
            TransferType.CUSTOMER_DISPATCH -> {
                requireNotNull(customerName) { "Customer dispatch requires customerName" }
                require(customerName.isNotBlank()) { "customerName cannot be blank" }
            }
            TransferType.SUBCONTRACT_INBOUND -> {
                requireNotNull(vendorRef) { "Subcontract inbound requires vendorRef" }
            }
        }
    }

    val totalPcs: Int get() = items.sumOf { it.qtyPcs }

    val totalCartons: Int get() = items.mapNotNull { it.cartonId }.distinct().size

    val isDispatched: Boolean get() = status == TransferStatus.DISPATCHED || status == TransferStatus.IN_TRANSIT

    fun dispatch(
        carrier: String? = carrierName,
        driver: String? = driverName,
        plate: String? = vehiclePlate,
        now: Instant
    ): SuratJalanManifest {
        require(status == TransferStatus.DRAFT) { "Can only dispatch a DRAFT manifest, current: $status" }
        require(items.isNotEmpty()) { "Cannot dispatch an empty Surat Jalan without items" }
        return copy(
            carrierName = carrier,
            driverName = driver,
            vehiclePlate = plate,
            status = TransferStatus.DISPATCHED,
            dispatchedAt = now
        )
    }

    fun markReceived(now: Instant): SuratJalanManifest {
        require(isDispatched) { "Can only mark as received from dispatched/in-transit manifest, current: $status" }
        return copy(
            status = TransferStatus.RECEIVED,
            receivedAt = now
        )
    }

    fun cancel(reason: String, now: Instant): SuratJalanManifest {
        require(status != TransferStatus.RECEIVED) { "Cannot cancel an already received manifest" }
        return copy(
            status = TransferStatus.CANCELLED,
            notes = if (notes.isBlank()) "Dibatalkan: $reason" else "$notes | Dibatalkan: $reason"
        )
    }
}
