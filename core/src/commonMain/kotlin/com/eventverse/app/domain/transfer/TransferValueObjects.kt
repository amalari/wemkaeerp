package com.eventverse.app.domain.transfer

import kotlin.jvm.JvmInline

@JvmInline
value class SuratJalanId(val value: String) {
    init {
        require(value.isNotBlank()) { "SuratJalanId cannot be blank" }
        require(value.length <= 64) { "SuratJalanId length cannot exceed 64 characters" }
    }
}

@JvmInline
value class SuratJalanNumber(val value: String) {
    init {
        require(value.isNotBlank()) { "SuratJalanNumber cannot be blank" }
        require(value.length <= 64) { "SuratJalanNumber length cannot exceed 64 characters" }
    }
}

@JvmInline
value class LocationId(val value: String) {
    init {
        require(value.isNotBlank()) { "LocationId cannot be blank" }
        require(value.length <= 64) { "LocationId length cannot exceed 64 characters" }
    }
}

@JvmInline
value class CartonId(val value: String) {
    init {
        require(value.isNotBlank()) { "CartonId cannot be blank" }
        require(value.length <= 64) { "CartonId length cannot exceed 64 characters" }
    }
}

/**
 * Tipe perpindahan / pengiriman barang fisik di pabrik.
 */
enum class TransferType(val displayName: String) {
    INTERNAL_SITE_TRANSFER("Mutasi Antar-Gedung (Internal)"),
    SUBCONTRACT_OUTBOUND("Pengiriman Makloon ke Vendor Luar"),
    SUBCONTRACT_INBOUND("Penerimaan Kembali dari Vendor Makloon"),
    CUSTOMER_DISPATCH("Pengiriman Resmi ke Buyer / Klien");
}

enum class TransferStatus(val displayName: String) {
    DRAFT("Draft"),
    DISPATCHED("Diberangkatkan"),
    IN_TRANSIT("Dalam Perjalanan"),
    RECEIVED("Diterima Lengkap"),
    PARTIAL_RECEIVED("Diterima Sebagian"),
    CANCELLED("Dibatalkan");

    val isTerminal: Boolean get() = this == RECEIVED || this == CANCELLED
}
