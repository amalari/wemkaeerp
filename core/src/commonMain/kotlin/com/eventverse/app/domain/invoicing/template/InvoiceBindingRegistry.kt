package com.eventverse.app.domain.invoicing.template

import kotlin.jvm.JvmInline

@JvmInline
value class BindingToken(val value: String) {
    init {
        require(value.isNotBlank()) { "Binding token tidak boleh kosong." }
    }
}

enum class BindingScope {
    DOCUMENT,
    LINE;

    companion object {
        fun fromCode(code: String?): BindingScope =
            entries.firstOrNull { it.name.equals(code, ignoreCase = true) } ?: DOCUMENT
    }
}

enum class BindingFormat {
    TEXT,
    MONEY,
    QUANTITY,
    DATE,
    PERCENT,
    NUMBER,
    IMAGE;

    companion object {
        fun fromCode(code: String?): BindingFormat =
            entries.firstOrNull { it.name.equals(code, ignoreCase = true) } ?: TEXT
    }
}

data class BindingDescriptor(
    val token: BindingToken,
    val displayName: String,
    val scope: BindingScope,
    val format: BindingFormat,
    val category: String
)

/**
 * Single source of truth untuk katalog token data binding invoice.
 * Digunakan bersama oleh palet desainer UI, preview Compose, dan PDFBox server.
 */
object InvoiceBindingRegistry {

    val DOCUMENT: List<BindingDescriptor> = listOf(
        // Dokumen
        BindingDescriptor(BindingToken("invoice.number"), "Nomor Faktur / Invoice", BindingScope.DOCUMENT, BindingFormat.TEXT, "Dokumen"),
        BindingDescriptor(BindingToken("invoice.kind"), "Jenis Invoice", BindingScope.DOCUMENT, BindingFormat.TEXT, "Dokumen"),
        BindingDescriptor(BindingToken("invoice.status"), "Status Invoice", BindingScope.DOCUMENT, BindingFormat.TEXT, "Dokumen"),
        BindingDescriptor(BindingToken("invoice.issueDate"), "Tanggal Terbit", BindingScope.DOCUMENT, BindingFormat.DATE, "Dokumen"),
        BindingDescriptor(BindingToken("invoice.dueDate"), "Tanggal Jatuh Tempo", BindingScope.DOCUMENT, BindingFormat.DATE, "Dokumen"),
        BindingDescriptor(BindingToken("invoice.terms"), "Syarat Pembayaran", BindingScope.DOCUMENT, BindingFormat.TEXT, "Dokumen"),
        BindingDescriptor(BindingToken("invoice.notes"), "Catatan Khusus", BindingScope.DOCUMENT, BindingFormat.TEXT, "Dokumen"),
        BindingDescriptor(BindingToken("invoice.sourceRef"), "Referensi SPK / PO", BindingScope.DOCUMENT, BindingFormat.TEXT, "Dokumen"),

        // Finansial
        BindingDescriptor(BindingToken("invoice.subtotal"), "Subtotal", BindingScope.DOCUMENT, BindingFormat.MONEY, "Finansial"),
        BindingDescriptor(BindingToken("invoice.discountAmount"), "Nominal Diskon Global", BindingScope.DOCUMENT, BindingFormat.MONEY, "Finansial"),
        BindingDescriptor(BindingToken("invoice.taxableBase"), "Dasar Pengenaan Pajak (DPP)", BindingScope.DOCUMENT, BindingFormat.MONEY, "Finansial"),
        BindingDescriptor(BindingToken("invoice.taxRate"), "Tarif Pajak (PPN %)", BindingScope.DOCUMENT, BindingFormat.PERCENT, "Finansial"),
        BindingDescriptor(BindingToken("invoice.taxAmount"), "Nominal Pajak (PPN)", BindingScope.DOCUMENT, BindingFormat.MONEY, "Finansial"),
        BindingDescriptor(BindingToken("invoice.total"), "Grand Total", BindingScope.DOCUMENT, BindingFormat.MONEY, "Finansial"),
        BindingDescriptor(BindingToken("invoice.totalInWords"), "Terbilang Rupiah", BindingScope.DOCUMENT, BindingFormat.TEXT, "Finansial"),
        BindingDescriptor(BindingToken("invoice.paidAmount"), "Total Terbayar", BindingScope.DOCUMENT, BindingFormat.MONEY, "Finansial"),
        BindingDescriptor(BindingToken("invoice.outstandingAmount"), "Sisa Tagihan", BindingScope.DOCUMENT, BindingFormat.MONEY, "Finansial"),
        BindingDescriptor(BindingToken("invoice.contractValue"), "Nilai Kontrak Penuh", BindingScope.DOCUMENT, BindingFormat.MONEY, "Finansial"),

        // Klien (Bill To)
        BindingDescriptor(BindingToken("billTo.name"), "Nama Klien / Pembeli", BindingScope.DOCUMENT, BindingFormat.TEXT, "Klien"),
        BindingDescriptor(BindingToken("billTo.contactPerson"), "Nama Kontak Person", BindingScope.DOCUMENT, BindingFormat.TEXT, "Klien"),
        BindingDescriptor(BindingToken("billTo.address"), "Alamat Klien", BindingScope.DOCUMENT, BindingFormat.TEXT, "Klien"),
        BindingDescriptor(BindingToken("billTo.phone"), "No. Telepon Klien", BindingScope.DOCUMENT, BindingFormat.TEXT, "Klien"),
        BindingDescriptor(BindingToken("billTo.email"), "Email Klien", BindingScope.DOCUMENT, BindingFormat.TEXT, "Klien"),
        BindingDescriptor(BindingToken("billTo.taxId"), "NPWP Klien", BindingScope.DOCUMENT, BindingFormat.TEXT, "Klien"),

        // Penerbit (Issuer / Tenant)
        BindingDescriptor(BindingToken("issuer.companyName"), "Nama Perusahaan Penerbit", BindingScope.DOCUMENT, BindingFormat.TEXT, "Penerbit"),
        BindingDescriptor(BindingToken("issuer.address"), "Alamat Penerbit", BindingScope.DOCUMENT, BindingFormat.TEXT, "Penerbit"),
        BindingDescriptor(BindingToken("issuer.taxId"), "NPWP Penerbit", BindingScope.DOCUMENT, BindingFormat.TEXT, "Penerbit"),
        BindingDescriptor(BindingToken("issuer.phone"), "No. Telepon Penerbit", BindingScope.DOCUMENT, BindingFormat.TEXT, "Penerbit"),
        BindingDescriptor(BindingToken("issuer.email"), "Email Penerbit", BindingScope.DOCUMENT, BindingFormat.TEXT, "Penerbit"),
        BindingDescriptor(BindingToken("issuer.bankName"), "Nama Bank", BindingScope.DOCUMENT, BindingFormat.TEXT, "Penerbit"),
        BindingDescriptor(BindingToken("issuer.bankAccountNumber"), "Nomor Rekening Bank", BindingScope.DOCUMENT, BindingFormat.TEXT, "Penerbit"),
        BindingDescriptor(BindingToken("issuer.bankAccountHolder"), "Atas Nama Rekening", BindingScope.DOCUMENT, BindingFormat.TEXT, "Penerbit"),
        BindingDescriptor(BindingToken("issuer.logoAssetUrl"), "Logo Perusahaan", BindingScope.DOCUMENT, BindingFormat.IMAGE, "Penerbit")
    )

    val LINE: List<BindingDescriptor> = listOf(
        BindingDescriptor(BindingToken("line.no"), "Nomor Baris", BindingScope.LINE, BindingFormat.NUMBER, "Baris Item"),
        BindingDescriptor(BindingToken("line.description"), "Deskripsi Barang / Jasa", BindingScope.LINE, BindingFormat.TEXT, "Baris Item"),
        BindingDescriptor(BindingToken("line.quantity"), "Jumlah / Kuantitas", BindingScope.LINE, BindingFormat.QUANTITY, "Baris Item"),
        BindingDescriptor(BindingToken("line.uom"), "Satuan (UOM)", BindingScope.LINE, BindingFormat.TEXT, "Baris Item"),
        BindingDescriptor(BindingToken("line.unitPrice"), "Harga Satuan", BindingScope.LINE, BindingFormat.MONEY, "Baris Item"),
        BindingDescriptor(BindingToken("line.discount"), "Diskon (%)", BindingScope.LINE, BindingFormat.PERCENT, "Baris Item"),
        BindingDescriptor(BindingToken("line.grossAmount"), "Jumlah Kotor", BindingScope.LINE, BindingFormat.MONEY, "Baris Item"),
        BindingDescriptor(BindingToken("line.amount"), "Jumlah Bersih (Subtotal)", BindingScope.LINE, BindingFormat.MONEY, "Baris Item")
    )

    private val allByToken: Map<String, BindingDescriptor> =
        (DOCUMENT + LINE).associateBy { it.token.value }

    fun descriptorFor(token: BindingToken): BindingDescriptor? =
        allByToken[token.value]

    fun descriptorFor(tokenValue: String): BindingDescriptor? =
        allByToken[tokenValue]
}
