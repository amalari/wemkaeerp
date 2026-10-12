package com.eventverse.app.domain.fulfillment.shipment

import kotlin.jvm.JvmInline

@JvmInline
value class ShipmentNumber(val value: String) {
    init {
        require(value.isNotBlank()) { "ShipmentNumber tidak boleh kosong" }
        require(value.length <= 40) { "ShipmentNumber maksimal 40 karakter" }
        require(FORMAT_REGEX.matches(value)) {
            "ShipmentNumber wajib format SJ-TAHUN-urutan, contoh: SJ-2026-0001"
        }
    }

    companion object {
        private val FORMAT_REGEX = Regex("^SJ-\\d{4}-\\d{4}$")

        fun generate(year: Int, sequence: Int): ShipmentNumber {
            return ShipmentNumber("SJ-${year}-${sequence.toString().padStart(4, '0')}")
        }

        fun parse(value: String): ShipmentNumber? = runCatching { ShipmentNumber(value) }.getOrNull()
    }
}

enum class ShipmentStatus(val displayName: String) {
    ISSUED("Surat Jalan Diterbitkan"),
    IN_TRANSIT("Sedang Diantar"),
    DELIVERED("Terkirim"),
    CANCELLED("Dibatalkan");

    val isActive: Boolean get() = this == ISSUED || this == IN_TRANSIT
    val canMarkInTransit: Boolean get() = this == ISSUED
    val canConfirmDelivered: Boolean get() = this == IN_TRANSIT
    val canBeCancelled: Boolean get() = this == ISSUED
    val isFinal: Boolean get() = this == DELIVERED || this == CANCELLED

    companion object {
        fun fromString(value: String): ShipmentStatus? = entries.find { it.name == value }
    }
}

enum class StockOwnership(val displayName: String) {
    OWNED("Milik Pabrik"),
    CONSIGNED("Titipan Client")
}
