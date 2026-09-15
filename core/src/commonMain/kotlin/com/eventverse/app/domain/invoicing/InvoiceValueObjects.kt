package com.eventverse.app.domain.invoicing

import kotlin.jvm.JvmInline

@JvmInline
value class InvoiceId(val value: String) {
    init {
        require(value.isNotBlank()) { "InvoiceId tidak boleh kosong." }
    }
}

@JvmInline
value class InvoiceNumber(val value: String) {
    init {
        require(value.isNotBlank()) { "Nomor invoice tidak boleh kosong." }
    }
}

@JvmInline
value class InvoiceLineId(val value: String) {
    init {
        require(value.isNotBlank()) { "InvoiceLineId tidak boleh kosong." }
    }
}

@JvmInline
value class InvoicePaymentId(val value: String) {
    init {
        require(value.isNotBlank()) { "InvoicePaymentId tidak boleh kosong." }
    }
}

@JvmInline
value class InvoiceTemplateId(val value: String) {
    init {
        require(value.isNotBlank()) { "InvoiceTemplateId tidak boleh kosong." }
    }
}

enum class InvoiceKind(val displayName: String) {
    SAMPLE("Invoice Sample"),
    DOWN_PAYMENT("Invoice DP / Uang Muka"),
    SETTLEMENT("Invoice Pelunasan"),
    FULL("Invoice Penuh");

    companion object {
        fun fromCode(code: String?): InvoiceKind =
            entries.firstOrNull { it.name.equals(code, ignoreCase = true) } ?: FULL
    }
}

enum class InvoiceStatus(val displayName: String, val isMutable: Boolean) {
    DRAFT("Draft", isMutable = true),
    ISSUED("Terbit", isMutable = false),
    PARTIALLY_PAID("Dibayar Sebagian", isMutable = false),
    PAID("Lunas", isMutable = false),
    VOID("Dibatalkan", isMutable = false);

    companion object {
        fun fromCode(code: String?): InvoiceStatus =
            entries.firstOrNull { it.name.equals(code, ignoreCase = true) } ?: DRAFT
    }
}

enum class InvoiceSourceKind(val displayName: String) {
    MANUAL("Manual"),
    SAMPLING("Order Sampling"),
    FULFILLMENT("Surat Jalan / Shipment"),
    COSTING("Kalkulasi Costing"),
    CRM_LEAD("Prospek CRM");

    companion object {
        fun fromCode(code: String?): InvoiceSourceKind =
            entries.firstOrNull { it.name.equals(code, ignoreCase = true) } ?: MANUAL
    }
}

/**
 * Snapshot pihak pembeli/klien saat invoice diterbitkan.
 * Dokumen legal: perubahan nama/alamat klien di masa depan tidak boleh mengubah faktur masa lalu.
 */
data class BillToParty(
    val name: String,
    val contactPerson: String = "",
    val address: String = "",
    val phone: String = "",
    val email: String = "",
    val taxId: String = ""
) {
    init {
        require(name.isNotBlank()) { "Nama klien (Bill To) tidak boleh kosong." }
    }
}

/**
 * Snapshot profil perusahaan penerbit (tenant) saat invoice diterbitkan.
 */
data class IssuerProfile(
    val companyName: String,
    val address: String = "",
    val taxId: String = "",
    val phone: String = "",
    val email: String = "",
    val bankName: String = "",
    val bankAccountNumber: String = "",
    val bankAccountHolder: String = "",
    val logoAssetUrl: String? = null
) {
    init {
        require(companyName.isNotBlank()) { "Nama perusahaan penerbit tidak boleh kosong." }
    }
}
