package com.eventverse.app.domain.costing

import kotlin.jvm.JvmInline

@JvmInline
value class CostingSheetId(val value: String) {
    init {
        require(value.isNotBlank()) { "CostingSheetId tidak boleh kosong" }
    }
}

@JvmInline
value class CostingRateCardId(val value: String) {
    init {
        require(value.isNotBlank()) { "CostingRateCardId tidak boleh kosong" }
    }
}

/**
 * Nomor lembar HPP yang dapat dibaca manusia, mis. "HPP-2024-0042".
 * Dibuat oleh server (sequence per tenant), bukan oleh domain.
 */
@JvmInline
value class CostingNumber(val value: String) {
    init {
        require(value.isNotBlank()) { "CostingNumber tidak boleh kosong" }
    }
}

/**
 * Status siklus hidup lembar HPP.
 *
 * Transisi yang sah:
 * ```
 * DRAFT → CALCULATED → PENDING_APPROVAL → APPROVED (terminal komersial)
 *                    ↳ REJECTED → DRAFT (revisi)
 * APPROVED → SUPERSEDED (ketika ada revisi yang disetujui)
 * * → ARCHIVED (cleanup)
 * ```
 */
enum class CostingSheetStatus(
    val displayName: String,
    val isEditable: Boolean,
    val isCommitted: Boolean
) {
    DRAFT(
        displayName = "Draf Perhitungan",
        isEditable = true,
        isCommitted = false
    ),
    CALCULATED(
        displayName = "Sudah Dihitung",
        isEditable = true,
        isCommitted = false
    ),
    PENDING_APPROVAL(
        displayName = "Menunggu Persetujuan",
        isEditable = false,
        isCommitted = false
    ),
    APPROVED(
        displayName = "Disetujui (Komitmen Komersial)",
        isEditable = false,
        isCommitted = true
    ),
    REJECTED(
        displayName = "Ditolak / Revisi",
        isEditable = true,
        isCommitted = false
    ),
    SUPERSEDED(
        displayName = "Digantikan Versi Baru",
        isEditable = false,
        isCommitted = true
    ),
    ARCHIVED(
        displayName = "Diarsipkan",
        isEditable = false,
        isCommitted = false
    );
}

/** Sumber parameter dalam [ResolvedCostingParameters] — untuk audit provenance. */
enum class CostingParameterSource(val displayName: String) {
    HARDCODED_DEFAULT("Default Sistem"),
    PIPELINE_NODE("Konfigurasi Node Pipeline"),
    RATE_CARD("Rate Card Tenant"),
    SHEET_OVERRIDE("Override Per Lembar")
}
