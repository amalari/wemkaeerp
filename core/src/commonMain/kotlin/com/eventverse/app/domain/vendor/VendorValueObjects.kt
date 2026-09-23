package com.eventverse.app.domain.vendor

import kotlin.jvm.JvmInline

@JvmInline
value class VendorId(val value: String) {
    init {
        require(value.isNotBlank()) { "VendorId cannot be blank" }
        require(value.length <= 64) { "VendorId must be at most 64 characters" }
    }
}

/**
 * Nama vendor rekanan.
 *
 * Batas 150 karakter bukan angka acak: nama ini disalin apa adanya ke `vendorRef` proses alur,
 * Surat Jalan, dan kartu kerja — ketiganya berkolom `varchar(150)`.
 */
@JvmInline
value class VendorName(val value: String) {
    init {
        require(value.isNotBlank()) { "Nama vendor tidak boleh kosong" }
        require(value.length <= 150) { "Nama vendor maksimal 150 karakter" }
    }
}

@JvmInline
value class VendorAssignmentId(val value: String) {
    init {
        require(value.isNotBlank()) { "VendorAssignmentId cannot be blank" }
        require(value.length <= 64) { "VendorAssignmentId must be at most 64 characters" }
    }
}

/**
 * Cara vendor menghitung tagihannya. Sablon menagih per titik, penjahit per pcs, sebagian
 * vendor per lusin, dan ada yang borongan — memaksa semuanya ke "per pcs" membuat admin
 * mengonversi di kepala dan salah hitung di tagihan.
 */
enum class VendorPriceUnit(val displayName: String, val shortLabel: String) {
    PER_PIECE("Per pcs", "pcs"),
    PER_PRINT_POINT("Per titik", "titik"),
    PER_DOZEN("Per lusin", "lusin"),
    PER_ORDER("Borongan per order", "order");

    /**
     * Total tagihan untuk [quantityPcs] potong baju.
     *
     * [unitsPerPiece] hanya bermakna untuk [PER_PRINT_POINT] (jumlah titik sablon per baju);
     * satuan lain mengabaikannya. Lusin dibulatkan ke atas karena vendor tidak menagih
     * setengah lusin.
     */
    fun totalFor(pricePerUnitIdr: Long, quantityPcs: Int, unitsPerPiece: Int = 1): Long = when (this) {
        PER_PIECE -> pricePerUnitIdr * quantityPcs
        PER_PRINT_POINT -> pricePerUnitIdr * unitsPerPiece * quantityPcs
        PER_DOZEN -> pricePerUnitIdr * ((quantityPcs + 11) / 12)
        PER_ORDER -> pricePerUnitIdr
    }
}

/** Asal harga pada penugasan — supaya laporan bisa membedakan harga daftar dari hasil nego. */
enum class VendorPriceSource(val displayName: String) {
    PRICE_LIST("Sesuai daftar harga"),
    NEGOTIATED("Harga nego");
}

enum class VendorAssignmentStatus(val displayName: String) {
    ASSIGNED("Ditugaskan"),
    CANCELLED("Dibatalkan");
}
