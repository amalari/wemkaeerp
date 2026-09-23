package com.eventverse.app.domain.fulfillment

import kotlin.jvm.JvmInline

@JvmInline
value class SackTransferId(val value: String) {
    init {
        require(value.isNotBlank()) { "SackTransferId tidak boleh kosong" }
        require(value.length <= 64) { "SackTransferId maksimal 64 karakter" }
    }
}

/**
 * Rute perjalanan karung antar divisi.
 *
 * Sengaja hanya dua rute awal yang benar-benar ada di lantai hari ini — bukan katalog divisi
 * yang harus dijaga sinkron dengan Org Chart. Rute baru (mis. ke vendor rekanan) tinggal
 * ditambah di sini tanpa menyentuh mesin transfernya.
 */
enum class SackRoute(val displayName: String) {
    QC_RAJUT_TO_FINISHING("QC Rajut ke Finishing"),
    FINISHING_TO_QC_FINISHING("Finishing ke QC Finishing")
}

/**
 * Siapa yang memegang barang di antara dua divisi, dan karena itu bukti apa yang masuk akal
 * dituntut di titik berangkat.
 *
 * [ADMIN_HUB] adalah pola konveksi menengah ke bawah: operator membawa bundel selesai ke meja
 * admin, admin menyortir dan menuangkannya ke karung, menutup karung, lalu mengirimkannya ke
 * divisi lain. Karung berpindah kustodi di meja itu, jadi timbangan, foto, dan tanda tangan
 * ACC punya alasan — merekalah yang membedakan "hilang sebelum meja admin" dari "hilang
 * sesudahnya" saat selisih diselidiki.
 *
 * [DIRECT] adalah pabrik yang operatornya mengantar sendiri dari meja A ke meja B. Tidak ada
 * kustodi perantara yang perlu dibatasi, jadi menuntut timbang + foto + ACC tiap serah terima
 * hanya menambah friksi tanpa menambah informasi: hitungan dan identitas operator sudah
 * terekam saat bundel dihitung di modul telusur.
 */
enum class HandoverMode(val displayName: String) {
    DIRECT("Operator Antar Langsung"),
    ADMIN_HUB("Lewat Meja Admin")
}

/**
 * Daur hidup satu perjalanan karung.
 *
 * [MENUNGGU_ACC] adalah gerbang admin produksi — dan hanya ada pada [HandoverMode.ADMIN_HUB];
 * [DITOLAK] mengembalikan karung ke pemeriksaan dan lewat [resubmit] naik lagi — bukan
 * hapus-buat-ulang, karena riwayat penolakan adalah bagian dari pengawasan.
 */
enum class SackTransferStatus(val displayName: String) {
    MENUNGGU_ACC("Menunggu ACC Admin"),
    DIANTAR("Diantar"),
    DITERIMA("Diterima"),
    DITERIMA_SELISIH("Diterima dengan Selisih"),
    DITOLAK("Ditolak"),
    DIPERIKSA("Diperiksa Ulang");

    val isFinal: Boolean get() = this == DITERIMA || this == DITERIMA_SELISIH

    /**
     * Barang sudah lepas dari titik berangkat — sedang di jalan atau sudah sampai.
     *
     * Sengaja dipisah dari [butuhAccAdmin]: dua hal ini dulu satu properti (`sudahDisetujui`),
     * dan penggabungan itulah yang membuat setiap karung berjalan menuntut tanda tangan admin
     * — syarat yang mustahil dipenuhi pada [HandoverMode.DIRECT] karena di sana tidak ada
     * penyetuju sama sekali.
     */
    val sedangBerjalan: Boolean get() = this == DIANTAR || isFinal

    /** Masih menunggu keputusan admin. Hanya bisa bernilai true pada [HandoverMode.ADMIN_HUB]. */
    val butuhAccAdmin: Boolean get() = this == MENUNGGU_ACC
}

/**
 * Berat dalam kilogram dengan dua desimal — kecocokan dengan resi kurir dan layar timbangan.
 *
 * Dibungkus value class, bukan Double telanjang, supaya aturan "eksak sampai koma" punya satu
 * rumah: [parse] menerima `8,35` maupun `8.35` (tombol koma di keypad pabrik), dan [formatted]
 * selalu menampilkan dua desimal tanpa bergantung pada locale platform.
 */
@JvmInline
value class WeightKg(val value: Double) {
    init {
        require(value >= 0.0) { "Berat timbangan tidak boleh negatif" }
        require(value <= MAX_KG) { "Berat di luar jangkauan wajar (maksimal ${MAX_KG.toLong()} kg)" }
    }

    /** Dua desimal yang dijamin — `8.5` tampil `8.50 kg`, bukan `8.5 kg`. */
    fun formatted(): String {
        val scaled = kotlin.math.round(value * 100).toLong()
        val whole = scaled / 100
        val frac = (scaled % 100).toString().padStart(2, '0')
        return "$whole.$frac kg"
    }

    companion object {
        const val MAX_KG = 5_000.0

        /** Menerima input operator apa adanya: koma atau titik, spasi di dua samping. */
        fun parse(raw: String): WeightKg? =
            raw.trim().replace(',', '.').toDoubleOrNull()?.let { candidate ->
                if (candidate in 0.0..MAX_KG) WeightKg(kotlin.math.round(candidate * 100) / 100) else null
            }
    }
}

/**
 * Bukti serah terima — tepat dua jalur, sesuai praktik lapangan.
 *
 * Penerima **tidak pernah butuh akun**: siapa pun dari pihak kita yang memegang aplikasi
 * (kurir, admin) yang merekam. Jalur [ReceiverHandover] menggantikan tanda tangan kertas;
 * jalur [CourierShipment] menyalin angka resmi dari resi kurir — beratnya **eksak sampai
 * koma** sebagaimana tercetak, bukan hasil pembulatan pabrik.
 */
sealed interface HandoverProof {

    /** Foto bukti — timbangan terima, atau resi. Wajib; tanpa foto, serah terima tidak sah. */
    val evidencePhotoKey: String

    /**
     * Diserahkan langsung ke orang di tujuan; TTD digambar sekali di perangkat internal.
     *
     * [signatureKey] null hanya sah pada [HandoverMode.DIRECT]: menggambar tanda tangan tiap
     * kali operator mengantar bundel ke meja sebelah adalah friksi yang tidak dibayar dengan
     * informasi apa pun — foto dan nama penerima sudah cukup menunjuk siapa yang memegang.
     * Pada [HandoverMode.ADMIN_HUB] ia tetap wajib; di situlah kustodi benar-benar berpindah.
     */
    data class ReceiverHandover(
        val receiverName: String,
        val signatureKey: String? = null,
        override val evidencePhotoKey: String
    ) : HandoverProof

    /** Dikirim via ekspedisi/vendor; resi + berat tertagih menjadi bukti resminya. */
    data class CourierShipment(
        val carrier: String,
        val trackingNumber: String,
        val chargeableWeightKg: WeightKg,
        override val evidencePhotoKey: String
    ) : HandoverProof
}
