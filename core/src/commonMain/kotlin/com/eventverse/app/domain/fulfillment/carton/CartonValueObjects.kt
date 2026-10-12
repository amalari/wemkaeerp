package com.eventverse.app.domain.fulfillment.carton

import kotlin.jvm.JvmInline
import kotlinx.datetime.Clock

@JvmInline
value class CartonNumber(val value: String) {
    init {
        require(value.isNotBlank()) { "CartonNumber tidak boleh kosong" }
        require(value.length <= 40) { "CartonNumber maksimal 40 karakter" }
        require(FORMAT_REGEX.matches(value)) {
            "CartonNumber wajib format CTN-TAHUN-urutan, contoh: CTN-2026-0001"
        }
    }

    companion object {
        private val FORMAT_REGEX = Regex("^CTN-\\d{4}-\\d{4}$")

        fun generate(year: Int, sequence: Int): CartonNumber {
            return CartonNumber("CTN-${year}-${sequence.toString().padStart(4, '0')}")
        }

        fun parse(value: String): CartonNumber? = runCatching { CartonNumber(value) }.getOrNull()
    }
}

@JvmInline
value class SealNumber(val value: String) {
    init {
        require(value.isNotBlank()) { "SealNumber tidak boleh kosong" }
        require(value.length <= 40) { "SealNumber maksimal 40 karakter" }
    }

    companion object {
        fun generate(): SealNumber {
            val timestamp = Clock.System.now().toEpochMilliseconds()
            val random = (1000..9999).random()
            return SealNumber("SL-$timestamp-$random")
        }
    }
}

enum class CartonStatus(val displayName: String) {
    OPEN("Karton Dibuka"),
    SEALED("Karton Disegel"),
    REOPENED("Karton Dibuka Kembali");

    val isSealed: Boolean get() = this == SEALED
    val canBeSealed: Boolean get() = this == OPEN
    val canBeReopened: Boolean get() = this == SEALED

    companion object {
        fun fromString(value: String): CartonStatus? = entries.find { it.name == value }
    }
}
