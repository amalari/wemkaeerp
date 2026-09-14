package com.eventverse.app.shared.common

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate

/**
 * Robust, Wasm-safe datetime parsing utilities for wire codecs.
 *
 * In Kotlin Multiplatform Wasm (`wasmJs`), inlining `runCatching { Instant.parse(it) }` produces
 * an inlined `Result<Instant>` where the boxed value class `Instant` creates an IR symbol
 * `kotlinx.datetime/Instant|null[0]`. Under incremental compilation or partial linkage across
 * klibs, reading a variable assigned from that expression throws:
 * `IrLinkageError: Can not read value from variable 'x': Variable uses unlinked class symbol 'kotlinx.datetime/Instant|null[0]'`.
 *
 * Using direct `try { ... } catch (_: Exception)` completely circumvents `Result<T>` boxing
 * and guarantees clean IR linkage in Kotlin/Wasm and Kotlin/JS.
 */
object DateTimeCodec {

    fun parseInstantOrNull(value: String?): Instant? {
        if (value.isNullOrBlank()) return null
        return try {
            Instant.parse(value)
        } catch (_: Exception) {
            null
        }
    }

    fun parseInstantOrFallback(value: String?, fallback: Instant): Instant {
        if (value.isNullOrBlank()) return fallback
        return try {
            Instant.parse(value)
        } catch (_: Exception) {
            fallback
        }
    }

    fun parseLocalDateOrNull(value: String?): LocalDate? {
        if (value.isNullOrBlank()) return null
        return try {
            LocalDate.parse(value)
        } catch (_: Exception) {
            null
        }
    }

    fun parseLocalDateOrFallback(value: String?, fallback: LocalDate): LocalDate {
        if (value.isNullOrBlank()) return fallback
        return try {
            LocalDate.parse(value)
        } catch (_: Exception) {
            fallback
        }
    }
}
