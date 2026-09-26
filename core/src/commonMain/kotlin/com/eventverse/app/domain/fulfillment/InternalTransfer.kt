package com.eventverse.app.domain.fulfillment

import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.domain.traceability.TraceCode
import com.eventverse.app.domain.traceability.TraceWorkOrderRef
import kotlinx.datetime.Instant

/**
 * Satu perjalanan karung antar divisi — dari titik transit sementara sampai diterima di tujuan.
 *
 * Entity ini **bukan** salinan [com.eventverse.app.domain.traceability.TraceContainer]: karung
 * menjalani daur hidup isi (dibuka, dituang, ditutup) di bounded context telusur, sementara di
 * sini ia menjalani daur hidup *perjalanan* (diajukan, di-ACC, diantar, diterima). Menyatukan
 * keduanya akan memaksa satu agregat menjawab dua pertanyaan yang berubah karena alasan berbeda.
 *
 * Disiplin bukti yang ditegakkan di sini, bukan di UI:
 * - Pengajuan wajib punya berat + foto timbangan dispatch — timbang dulu, foto, baru ajukan.
 * - Status diterima wajib punya [HandoverProof] lengkap — foto adalah bukti utama; angka
 *   ketikan hanya keterangan pelengkap untuk pencocokan otomatis.
 *
 * ## Pembagian tugas dengan [com.eventverse.app.domain.transfer.SuratJalanManifest]
 *
 * Keduanya mencatat barang berpindah, tapi menjawab pertanyaan yang berbeda dan **tidak boleh
 * disatukan**: satuan hitungnya berbeda (karung berkilogram vs bundel/karton/pcs), dan disiplin
 * buktinya bertentangan — di sini foto timbangan wajib sebelum boleh diajukan, sedangkan
 * `SuratJalanManifest.dispatch()` hanya menuntut item tidak kosong. Menyatukannya memaksa salah
 * satu invarian dilemahkan, dan yang dilemahkan selalu yang lebih ketat.
 *
 * Aturannya:
 * - **Surat Jalan diterbitkan per-leg** — satu dokumen sah untuk barang yang keluar gedung.
 * - **Perjalanan karung ini diterbitkan per-karung di dalam leg itu.**
 * - Untuk tenant multi-gedung, penerbitan Surat Jalan sebuah leg *mengumpulkan* karung yang
 *   sudah ber-ACC sebagai itemnya — operator tidak mengetik ulang kuantitas.
 * - Untuk tenant satu atap, perjalanan karung berdiri sendiri tanpa Surat Jalan.
 */
data class InternalTransfer(
    val id: SackTransferId,
    val tenantId: TenantId,
    val sackCode: TraceCode,
    val workOrder: TraceWorkOrderRef? = null,
    val sizeLabel: String,
    val colorway: String = "",
    val declaredPcs: Int,
    val leg: SackRoute,
    /**
     * Pola serah terima yang berlaku saat perjalanan ini dibuat — **snapshot**, bukan dibaca
     * ulang dari konfigurasi.
     *
     * Mengikuti pola [com.eventverse.app.domain.workqueue.WorkDeposit.tariffSnapshotIdr]:
     * pabrik boleh mengubah mode sebuah rute kapan saja, dan perjalanan yang sudah telanjur
     * berjalan harus tetap sah dengan aturan yang berlaku saat ia berangkat. Tanpa snapshot,
     * mengubah rute dari DIRECT ke ADMIN_HUB akan membuat setiap record lama mendadak
     * melanggar invarian karena tidak punya tanda tangan admin.
     */
    val handoverMode: HandoverMode,
    val status: SackTransferStatus = SackTransferStatus.MENUNGGU_ACC,

    /**
     * Bukti dispatch pada [HandoverMode.ADMIN_HUB] — timbang dulu, foto, baru ajukan.
     *
     * Null pada [HandoverMode.DIRECT]: di sana tidak ada kustodi perantara yang perlu
     * dibatasi, dan hitungan operator sudah terekam saat bundel dihitung di modul telusur.
     */
    val dispatchWeightKg: WeightKg? = null,
    val dispatchScalePhotoKey: String? = null,
    val requestedBy: String,
    val requestedAt: Instant,

    val approvedBy: String? = null,
    val approvedAt: Instant? = null,
    val approvalSignatureKey: String? = null,
    val rejectedBy: String? = null,
    val rejectReason: String? = null,

    /** Bukti serah terima di ujung. Null berarti karung masih di jalan atau belum di-ACC. */
    val handover: HandoverProof? = null,
    /** Berat timbangan saat diterima — opsional; foto yang jadi bukti utama. */
    val receivedWeightKg: WeightKg? = null,
    val receivedPcs: Int? = null,
    val receivedAt: Instant? = null,

    val createdAt: Instant,
    val updatedAt: Instant,
    val notes: String = ""
) {
    init {
        require(sizeLabel.isNotBlank()) { "Label size wajib diisi — karung tanpa size tidak bisa diperiksa di tujuan" }
        require(declaredPcs > 0) { "Jumlah pcs karung minimal 1" }
        require(requestedBy.isNotBlank()) { "Nama pengirim wajib dicatat" }

        if (handoverMode == HandoverMode.ADMIN_HUB) {
            requireNotNull(dispatchWeightKg) { "Berat dispatch wajib — timbang dulu sebelum mengajukan ke meja admin" }
            require(dispatchWeightKg.value > 0.0) { "Berat dispatch wajib lebih dari 0 — timbang dulu sebelum mengajukan" }
            require(!dispatchScalePhotoKey.isNullOrBlank()) { "Foto timbangan dispatch wajib ada" }

            if (status.sedangBerjalan) {
                requireNotNull(approvedBy) { "Status ${status.displayName} wajib mencatat siapa yang menyetujui" }
                requireNotNull(approvalSignatureKey) { "ACC tanpa tanda tangan tidak sah" }
            }
        } else {
            // Bukan sekadar "tidak wajib": terisi berarti record ini dibuat dengan aturan yang
            // bertentangan dengan modenya, dan menyimpannya diam-diam membuat audit berbohong.
            require(approvedBy == null && approvalSignatureKey == null) {
                "Perjalanan ${HandoverMode.DIRECT.displayName} tidak mengenal ACC admin — " +
                    "hapus data persetujuan atau pakai mode ${HandoverMode.ADMIN_HUB.displayName}"
            }
            // Tanpa gerbang berangkat tidak ada yang bisa menolak keberangkatan. Penolakan di
            // tujuan bukan status ini — barang yang sudah sampai dicatat lewat selisih terima.
            require(status != SackTransferStatus.DITOLAK && status != SackTransferStatus.DIPERIKSA) {
                "Perjalanan ${HandoverMode.DIRECT.displayName} tidak melewati meja admin, " +
                    "jadi tidak bisa berstatus ${status.displayName}"
            }
        }
        if (status.isFinal) {
            requireNotNull(handover) { "Karung berstatus ${status.displayName} wajib punya bukti serah terima" }
            requireNotNull(receivedAt) { "Waktu diterima wajib dicatat" }
        }
        if (status == SackTransferStatus.DITOLAK) {
            require(!rejectReason.isNullOrBlank()) { "Penolakan tanpa alasan tidak bisa diaudit" }
            requireNotNull(rejectedBy) { "Penolakan wajib mencatat siapa yang menolak" }
        }
    }

    val humanCode: String get() = com.eventverse.app.domain.traceability.TraceCodec.grouped(sackCode)

    /**
     * Gerbang ACC admin produksi — karung boleh berangkat.
     *
     * Tanda tangan di sini bukan formalitas: ia yang membuat keputusan izin keluar bisa
     * dibedakan dari penerimaan barang saat selisih kuantitas diselidiki.
     */
    fun approve(approverName: String, signatureKey: String, now: Instant): InternalTransfer {
        require(handoverMode == HandoverMode.ADMIN_HUB) {
            "Karung $humanCode diantar langsung oleh operator — tidak ada ACC yang perlu diberikan"
        }
        require(status == SackTransferStatus.MENUNGGU_ACC) {
            "Karung $humanCode berstatus ${status.displayName} — hanya pengajuan baru yang bisa di-ACC"
        }
        require(approverName.isNotBlank()) { "Nama penyetuju wajib dicatat" }
        require(signatureKey.isNotBlank()) { "ACC wajib ditandatangani" }
        return copy(
            status = SackTransferStatus.DIANTAR,
            approvedBy = approverName,
            approvedAt = now,
            approvalSignatureKey = signatureKey,
            rejectReason = null,
            updatedAt = now
        )
    }

    /** Menolak pengajuan — alasan wajib, karena inilah yang dibaca saat karung diperiksa ulang. */
    fun reject(reason: String, approverName: String, now: Instant): InternalTransfer {
        require(handoverMode == HandoverMode.ADMIN_HUB) {
            "Karung $humanCode diantar langsung oleh operator — tidak melewati meja admin untuk ditolak"
        }
        require(status == SackTransferStatus.MENUNGGU_ACC) {
            "Karung $humanCode berstatus ${status.displayName} — hanya pengajuan baru yang bisa ditolak"
        }
        require(reason.isNotBlank()) { "Alasan penolakan wajib diisi" }
        require(approverName.isNotBlank()) { "Nama penolak wajib dicatat" }
        return copy(
            status = SackTransferStatus.DITOLAK,
            rejectedBy = approverName,
            rejectReason = reason,
            updatedAt = now
        )
    }

    /**
     * Mengajukan ulang karung yang ditolak — bukan record baru, supaya riwayat penolakannya
     * tetap menempel dan pola penolakan berulang terlihat.
     */
    fun resubmit(
        dispatchWeightKg: WeightKg,
        dispatchScalePhotoKey: String,
        requestedBy: String,
        now: Instant
    ): InternalTransfer {
        require(status == SackTransferStatus.DITOLAK) {
            "Hanya karung yang ditolak bisa diajukan ulang — $humanCode berstatus ${status.displayName}"
        }
        require(dispatchWeightKg.value > 0.0) { "Timbang ulang karung sebelum mengajukan lagi" }
        require(dispatchScalePhotoKey.isNotBlank()) { "Foto timbangan terbaru wajib ada" }
        require(requestedBy.isNotBlank()) { "Nama pengirim wajib dicatat" }
        return copy(
            status = SackTransferStatus.MENUNGGU_ACC,
            dispatchWeightKg = dispatchWeightKg,
            dispatchScalePhotoKey = dispatchScalePhotoKey,
            requestedBy = requestedBy,
            requestedAt = now,
            rejectedBy = null,
            rejectReason = null,
            updatedAt = now
        )
    }

    /**
     * Mencatat penerimaan di ujung tujuan.
     *
     * Selisih ditentukan dari dua sinyal: jumlah pcs fisik yang berbeda dari deklarasi, atau
     * berat yang turun melebihi toleransi. Tanpa angka yang diisi, karung tetap sah diterima —
     * foto timbangan yang menjadi bukti utamanya.
     */
    fun receive(
        proof: HandoverProof,
        receivedWeightKg: WeightKg?,
        receivedPcs: Int?,
        now: Instant
    ): InternalTransfer {
        require(status == SackTransferStatus.DIANTAR) {
            "Karung $humanCode berstatus ${status.displayName} — yang bisa diterima hanya karung yang sedang diantar"
        }
        when (proof) {
            is HandoverProof.ReceiverHandover -> {
                require(proof.receiverName.isNotBlank()) { "Nama penerima wajib dicatat — dia yang bertanggung jawab atas isinya" }
                if (handoverMode == HandoverMode.ADMIN_HUB) {
                    require(!proof.signatureKey.isNullOrBlank()) { "Penerima wajib menandatangani" }
                }
                require(proof.evidencePhotoKey.isNotBlank()) { "Foto timbangan saat diterima wajib ada" }
            }
            is HandoverProof.CourierShipment -> {
                require(proof.carrier.isNotBlank()) { "Nama ekspedisi/vendor wajib diisi" }
                require(proof.trackingNumber.isNotBlank()) { "Nomor resi wajib diisi" }
                require(proof.chargeableWeightKg.value > 0.0) { "Berat resi wajib diisi eksak seperti tercetak" }
                require(proof.evidencePhotoKey.isNotBlank()) { "Foto resi wajib ada" }
            }
        }

        val referenceWeight = when (proof) {
            is HandoverProof.ReceiverHandover -> receivedWeightKg
            is HandoverProof.CourierShipment -> proof.chargeableWeightKg
        }
        val pcsBeda = receivedPcs != null && receivedPcs != declaredPcs
        // Berat hanya bisa dibandingkan kalau ada berat berangkat untuk dibandingkan. Pada
        // HandoverMode.DIRECT tidak ada, dan di sanalah perbandingan pcs memikul seluruh
        // deteksi selisih — itulah sebabnya hitungan pcs di mode itu bukan pelengkap.
        val dispatched = dispatchWeightKg
        val beratBeda = dispatched != null && referenceWeight != null &&
            (dispatched.value - referenceWeight.value) > WEIGHT_TOLERANCE_KG

        return copy(
            status = if (pcsBeda || beratBeda) SackTransferStatus.DITERIMA_SELISIH else SackTransferStatus.DITERIMA,
            handover = proof,
            receivedWeightKg = when (proof) {
                is HandoverProof.ReceiverHandover -> receivedWeightKg
                is HandoverProof.CourierShipment -> proof.chargeableWeightKg
            },
            receivedPcs = receivedPcs,
            receivedAt = now,
            updatedAt = now
        )
    }

    companion object {
        /**
         * Toleransi selisih berat antar timbangan dispatch dan terima. Timbangan lantai tidak
         * identik; yang ditangkap adalah kehilangan bermakna (karung robek, pcs tercecer),
         * bukan kalibrasi yang berbeda sepersekian kilo.
         */
        const val WEIGHT_TOLERANCE_KG = 0.5
    }
}
