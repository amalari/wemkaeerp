package com.eventverse.app.domain.transfer

import com.eventverse.app.domain.workqueue.WorkCardId
import com.eventverse.app.domain.workqueue.WorkSubjectKind
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
 *
 * ## Pembagian tugas dengan [com.eventverse.app.domain.fulfillment.InternalTransfer]
 *
 * Agregat ini adalah **dokumen lintas batas**: bukti sah yang menyertai barang keluar gedung
 * atau keluar pabrik, dihitung per bundel/karton/pcs. `InternalTransfer` adalah **perjalanan
 * satu karung** di dalamnya, dihitung per kilogram dengan foto timbangan dan tanda tangan.
 * Keduanya sengaja tidak disatukan — lihat KDoc di sana untuk alasannya.
 *
 * Aturannya: **Surat Jalan per-leg, perjalanan karung per-karung di dalam leg.** Untuk tenant
 * multi-gedung, penerbitan Surat Jalan mengumpulkan karung yang sudah ber-ACC sebagai itemnya
 * alih-alih meminta operator mengetik ulang kuantitas.
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
    /**
     * Leg alur yang dokumen ini layani, bila diterbitkan dari panel alur.
     *
     * Ini jembatan ke [FlowTransferLeg.legKey]. Tanpanya, status sebuah leg hanya bisa dicari
     * dengan mencocokkan tuple asal/tujuan — yang langsung ambigu begitu satu SPK melewati
     * gedung yang sama dua kali, atau memakai vendor yang sama untuk dua proses.
     *
     * `null` untuk dokumen yang diterbitkan manual dari workspace Surat Jalan, dan untuk
     * seluruh dokumen yang terbit sebelum kolom ini ada.
     */
    val legKey: String? = null,
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
                // Identitas bundle wajib diteruskan pada produksi masal: tanpa nomor bundle,
                // operator di gedung tujuan tidak tahu ikatan mana milik siapa dan upah
                // borongannya tidak bisa dihitung.
                //
                // SPK sampling dikecualikan karena di sana bundle memang tidak pernah ada.
                // Sampel berjumlah beberapa potong dan tidak melewati meja potong yang
                // menerbitkan WorkCard, jadi menuntut nomor bundle berarti menuntut sesuatu
                // yang secara struktural mustahil dipenuhi — dan efeknya bukan data yang lebih
                // rapi, melainkan mutasi antar-gedung untuk sampel tidak bisa dicatat sama
                // sekali.
                if (subject.kind != WorkSubjectKind.SAMPLING_ORDER) {
                    require(items.all { it.bundleNo != null }) {
                        "Internal transfer must preserve individual bundle numbers for all items"
                    }
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
