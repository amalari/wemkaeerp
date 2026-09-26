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

/**
 * Modul WeMade ERP yang **memproduksi** nilai sebuah token.
 *
 * ## Mengapa ini ada di lapisan domain, bukan sebagai pengelompokan di UI
 *
 * Desainer faktur menawarkan isian dinamis sebagai "Modul": pengguna memilih grup `CRM — Klien &
 * Prospek` lalu mengambil `Nama Klien`, `Telepon`, `Email`. Pengelompokan itu bukan hiasan tata
 * letak — ia menyatakan fakta domain: nilai tersebut adalah **keluaran (output port)** modul lain,
 * sesuai kontrak modul di `AGENTS.md` §3 Kontrak 2.
 *
 * Kalau pengelompokan ini ditulis di UI, modul kesepuluh yang lahir ikut memaksa UI diubah, dan
 * hubungan "nilai ini datang dari mana" hilang dari tempat yang seharusnya mendokumentasikannya.
 *
 * [ITEM_LINES] berbeda sifatnya dari tiga lainnya: token `line.*` hanya masuk akal **di dalam** tabel
 * item, karena resolver menerimanya bersama satu baris faktur. Di luar tabel ia resolve ke string
 * kosong, jadi palet wajib menolak token dari modul ini sebagai elemen bebas.
 */
enum class BindingModuleSource(
    val displayName: String,
    val description: String,
    val iconKey: String
) {
    CRM_SALES(
        displayName = "CRM — Klien & Prospek",
        description = "Identitas pembeli yang dihasilkan pipeline penjualan.",
        iconKey = "user"
    ),
    INVOICING_DOCUMENT(
        displayName = "Invoicing — Dokumen & Tagihan",
        description = "Nomor, tanggal, dan nominal yang dihitung mesin faktur.",
        iconKey = "receipt"
    ),
    ISSUER_TENANT(
        displayName = "Penerbit — Profil Perusahaan",
        description = "Identitas dan rekening penerbit faktur (profil tenant).",
        iconKey = "database"
    ),
    ITEM_LINES(
        displayName = "Baris Item — Tabel Faktur",
        description = "Kolom per baris pekerjaan. Hanya berlaku di dalam tabel item.",
        iconKey = "layers"
    );

    /** True bila token dari modul ini hanya boleh dipakai di dalam tabel item. */
    val isLineScopedOnly: Boolean get() = this == ITEM_LINES
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

/**
 * Satu token data dinamis yang bisa ditempel ke kanvas.
 *
 * @param defaultPrefix Teks label yang langsung dipasang saat token disisipkan dari palet, mis.
 *   `"Telp: "`. Disimpan di domain supaya UI tidak perlu tahu konvensi label per token — menambah
 *   token baru cukup dengan mengisi satu baris di registry ini.
 * @param defaultSuffix Teks penutup, dipakai token seperti terbilang (`" #"`).
 * @param defaultFontSizePt Ukuran font awal saat token disisipkan, mengikuti kebiasaan dokumen
 *   (nama klien lebih besar dari nomor telepon).
 * @param defaultWidthMm10 Lebar kotak awal saat disisipkan, dalam 1/10 mm.
 */
data class BindingDescriptor(
    val token: BindingToken,
    val displayName: String,
    val scope: BindingScope,
    val format: BindingFormat,
    val category: String,
    val moduleSource: BindingModuleSource,
    val defaultPrefix: String = "",
    val defaultSuffix: String = "",
    val defaultFontSizePt: Int = 10,
    val defaultWidthMm10: Int = 800
)

/**
 * Single source of truth untuk katalog token data binding invoice.
 * Digunakan bersama oleh palet desainer UI, preview Compose, dan PDFBox server.
 */
object InvoiceBindingRegistry {

    val DOCUMENT: List<BindingDescriptor> = listOf(
        // ── Dokumen (diproduksi Invoicing) ──
        document("invoice.number", "Nomor Faktur / Invoice", BindingFormat.TEXT, "Dokumen", prefix = "No: ", widthMm10 = 750),
        document("invoice.kind", "Jenis Invoice", BindingFormat.TEXT, "Dokumen", fontSizePt = 16, widthMm10 = 750),
        document("invoice.status", "Status Invoice", BindingFormat.TEXT, "Dokumen"),
        document("invoice.issueDate", "Tanggal Terbit", BindingFormat.DATE, "Dokumen", prefix = "Tgl Terbit: ", fontSizePt = 9),
        document("invoice.dueDate", "Tanggal Jatuh Tempo", BindingFormat.DATE, "Dokumen", prefix = "Jatuh Tempo: ", fontSizePt = 9),
        document("invoice.terms", "Syarat Pembayaran", BindingFormat.TEXT, "Dokumen", fontSizePt = 9, widthMm10 = 1000),
        document("invoice.notes", "Catatan Khusus", BindingFormat.TEXT, "Dokumen", fontSizePt = 9, widthMm10 = 1000),
        document("invoice.sourceRef", "Referensi SPK / PO", BindingFormat.TEXT, "Dokumen", prefix = "Ref: ", fontSizePt = 9),

        // ── Finansial (diproduksi Invoicing) ──
        document("invoice.subtotal", "Subtotal", BindingFormat.MONEY, "Finansial", prefix = "Subtotal: ", widthMm10 = 700),
        document("invoice.discountAmount", "Nominal Diskon Global", BindingFormat.MONEY, "Finansial", prefix = "Diskon: ", widthMm10 = 700),
        document("invoice.taxableBase", "Dasar Pengenaan Pajak (DPP)", BindingFormat.MONEY, "Finansial", prefix = "DPP: ", widthMm10 = 700),
        document("invoice.taxRate", "Tarif Pajak (PPN %)", BindingFormat.PERCENT, "Finansial", fontSizePt = 9),
        document("invoice.taxAmount", "Nominal Pajak (PPN)", BindingFormat.MONEY, "Finansial", prefix = "PPN: ", widthMm10 = 700),
        document("invoice.total", "Grand Total", BindingFormat.MONEY, "Finansial", prefix = "TOTAL: ", fontSizePt = 13, widthMm10 = 700),
        document("invoice.totalInWords", "Terbilang Rupiah", BindingFormat.TEXT, "Finansial", prefix = "Terbilang: # ", suffix = " #", fontSizePt = 9, widthMm10 = 1050),
        document("invoice.paidAmount", "Total Terbayar", BindingFormat.MONEY, "Finansial", prefix = "Terbayar: ", widthMm10 = 700),
        document("invoice.outstandingAmount", "Sisa Tagihan", BindingFormat.MONEY, "Finansial", prefix = "Sisa: ", widthMm10 = 700),
        document("invoice.contractValue", "Nilai Kontrak Penuh", BindingFormat.MONEY, "Finansial", fontSizePt = 9, widthMm10 = 700),

        // ── Klien (diproduksi modul CRM) ──
        document("billTo.name", "Nama Klien / Pembeli", BindingFormat.TEXT, "Klien", module = BindingModuleSource.CRM_SALES, fontSizePt = 11, widthMm10 = 800),
        document("billTo.contactPerson", "Nama Kontak Person", BindingFormat.TEXT, "Klien", module = BindingModuleSource.CRM_SALES, prefix = "Kontak: ", fontSizePt = 9),
        document("billTo.address", "Alamat Klien", BindingFormat.TEXT, "Klien", module = BindingModuleSource.CRM_SALES, fontSizePt = 9, widthMm10 = 1000),
        document("billTo.phone", "No. Telepon Klien", BindingFormat.TEXT, "Klien", module = BindingModuleSource.CRM_SALES, prefix = "Telp: ", fontSizePt = 9),
        document("billTo.email", "Email Klien", BindingFormat.TEXT, "Klien", module = BindingModuleSource.CRM_SALES, prefix = "Email: ", fontSizePt = 9),
        document("billTo.taxId", "NPWP Klien", BindingFormat.TEXT, "Klien", module = BindingModuleSource.CRM_SALES, prefix = "NPWP: ", fontSizePt = 9),

        // ── Penerbit (diproduksi profil tenant) ──
        document("issuer.companyName", "Nama Perusahaan Penerbit", BindingFormat.TEXT, "Penerbit", module = BindingModuleSource.ISSUER_TENANT, fontSizePt = 14, widthMm10 = 1000),
        document("issuer.address", "Alamat Penerbit", BindingFormat.TEXT, "Penerbit", module = BindingModuleSource.ISSUER_TENANT, fontSizePt = 9, widthMm10 = 1000),
        document("issuer.taxId", "NPWP Penerbit", BindingFormat.TEXT, "Penerbit", module = BindingModuleSource.ISSUER_TENANT, prefix = "NPWP: ", fontSizePt = 9),
        document("issuer.phone", "No. Telepon Penerbit", BindingFormat.TEXT, "Penerbit", module = BindingModuleSource.ISSUER_TENANT, prefix = "Telp: ", fontSizePt = 9),
        document("issuer.email", "Email Penerbit", BindingFormat.TEXT, "Penerbit", module = BindingModuleSource.ISSUER_TENANT, prefix = "Email: ", fontSizePt = 9),
        document("issuer.bankName", "Nama Bank", BindingFormat.TEXT, "Penerbit", module = BindingModuleSource.ISSUER_TENANT, fontSizePt = 9),
        document("issuer.bankAccountNumber", "Nomor Rekening Bank", BindingFormat.TEXT, "Penerbit", module = BindingModuleSource.ISSUER_TENANT, prefix = "No. Rekening: ", fontSizePt = 9),
        document("issuer.bankAccountHolder", "Atas Nama Rekening", BindingFormat.TEXT, "Penerbit", module = BindingModuleSource.ISSUER_TENANT, prefix = "A/N: ", fontSizePt = 9),
        document("issuer.logoAssetUrl", "Logo Perusahaan", BindingFormat.IMAGE, "Penerbit", module = BindingModuleSource.ISSUER_TENANT, widthMm10 = 600)
    )

    val LINE: List<BindingDescriptor> = listOf(
        line("line.no", "Nomor Baris", BindingFormat.NUMBER),
        line("line.description", "Deskripsi Barang / Jasa", BindingFormat.TEXT),
        line("line.quantity", "Jumlah / Kuantitas", BindingFormat.QUANTITY),
        line("line.uom", "Satuan (UOM)", BindingFormat.TEXT),
        line("line.unitPrice", "Harga Satuan", BindingFormat.MONEY),
        line("line.discount", "Diskon (%)", BindingFormat.PERCENT),
        line("line.grossAmount", "Jumlah Kotor", BindingFormat.MONEY),
        line("line.amount", "Jumlah Bersih (Subtotal)", BindingFormat.MONEY)
    )

    private val allByToken: Map<String, BindingDescriptor> =
        (DOCUMENT + LINE).associateBy { it.token.value }

    /**
     * Modul yang saat ini ditawarkan sebagai sumber isian bebas di Perpustakaan Elemen.
     *
     * Keputusan produk (sementara): **hanya CRM** yang dibuka. Grup "Invoicing — Dokumen &
     * Tagihan" dan "Penerbit — Profil Perusahaan" tetap hidup di [DOCUMENT] karena masih dipakai
     * resolver kanvas/PDF serta template seed; mereka hanya tidak lagi **ditawarkan** sebagai
     * penyisipan baru. Membuka ulang sebuah grup cukup dengan menambahkan modulnya di sini.
     */
    val paletteModules: Set<BindingModuleSource> = setOf(BindingModuleSource.CRM_SALES)

    /**
     * Token yang boleh disisipkan sebagai elemen bebas di kanvas.
     *
     * Token baris (`line.*`) sengaja tidak termasuk: resolver menerimanya bersama satu baris faktur,
     * sehingga di luar tabel ia selalu bernilai kosong. Menawarkannya di palet akan menghasilkan
     * elemen kosong yang membingungkan — "sudah saya tempel, kok isinya tidak muncul".
     */
    val standaloneTokens: List<BindingDescriptor> =
        DOCUMENT.filter { it.moduleSource in paletteModules }

    /** Token yang valid sebagai kolom tabel item. */
    val tableColumnTokens: List<BindingDescriptor> = LINE

    fun descriptorsOf(module: BindingModuleSource): List<BindingDescriptor> =
        (DOCUMENT + LINE).filter { it.moduleSource == module }

    /** Modul yang isiannya boleh disisipkan sebagai elemen bebas, terurut sesuai enum. */
    fun standaloneModules(): List<BindingModuleSource> =
        BindingModuleSource.entries.filter { it in paletteModules }

    fun descriptorFor(token: BindingToken): BindingDescriptor? =
        allByToken[token.value]

    fun descriptorFor(tokenValue: String): BindingDescriptor? =
        allByToken[tokenValue]

    private fun document(
        token: String,
        displayName: String,
        format: BindingFormat,
        category: String,
        module: BindingModuleSource = BindingModuleSource.INVOICING_DOCUMENT,
        prefix: String = "",
        suffix: String = "",
        fontSizePt: Int = 10,
        widthMm10: Int = 800
    ) = BindingDescriptor(
        token = BindingToken(token),
        displayName = displayName,
        scope = BindingScope.DOCUMENT,
        format = format,
        category = category,
        moduleSource = module,
        defaultPrefix = prefix,
        defaultSuffix = suffix,
        defaultFontSizePt = fontSizePt,
        defaultWidthMm10 = widthMm10
    )

    private fun line(token: String, displayName: String, format: BindingFormat) = BindingDescriptor(
        token = BindingToken(token),
        displayName = displayName,
        scope = BindingScope.LINE,
        format = format,
        category = "Baris Item",
        moduleSource = BindingModuleSource.ITEM_LINES,
        defaultFontSizePt = 9,
        defaultWidthMm10 = 400
    )
}
