package com.eventverse.app.domain.pipeline

/**
 * Garment business model presets for WeMade ERP.
 * Dictates standard module sequencing, bypassed nodes, and data handoffs.
 */
enum class GarmentBusinessPreset(
    val code: String,
    val displayName: String,
    val shortBadge: String,
    val description: String,
    val targetClientProfile: String
) {
    FOB_FULL_PACKAGE(
        code = "fob_full_package",
        displayName = "FOB (Full Order / Buy) — Paket Lengkap",
        shortBadge = "FOB Full Package",
        description = "Pengerjaan hulu-ke-hilir: Dari pengadaan bahan baku kain, aksesoris, pembuatan pola/sample, produksi massal, hingga ekspedisi ekspor/retail.",
        targetClientProfile = "Pabrik OEM, Ekspor Garmen, atau Konveksi Skala Menengah ke Atas"
    ),
    CMT_MAKLOON(
        code = "cmt_makloon",
        displayName = "CMT (Cut, Make, Trim) — Jasa Jahit Makloon",
        shortBadge = "CMT Jasa Jahit",
        description = "Pengerjaan jasa jahit murni. Pola potong & kain rol utama disediakan sepenuhnya oleh Buyer/Brand. Pengadaan bahan baku di-bypass.",
        targetClientProfile = "Vendor Makloon, Sub-kontraktor Jahit, Mitra Konveksi Rumahan/Sentra"
    ),
    BRAND_D2C(
        code = "brand_d2c",
        displayName = "Brand Konveksi Sendiri (Direct to Consumer)",
        shortBadge = "Brand D2C Internal",
        description = "Model bisnis terintegrasi brand sendiri. Menghubungkan peluncuran katalog baru, sample approval cepat, stok jadi, dan pesanan multichannel.",
        targetClientProfile = "Clothing Line Lokal, Distro Brand, Pabrik Seragam Custom Mandiri"
    );

    companion object {
        val DEFAULT = FOB_FULL_PACKAGE

        fun fromCode(code: String?): GarmentBusinessPreset {
            return entries.firstOrNull { it.code.equals(code, ignoreCase = true) } ?: DEFAULT
        }
    }
}
