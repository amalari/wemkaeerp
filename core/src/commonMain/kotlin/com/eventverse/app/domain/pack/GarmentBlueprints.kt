package com.eventverse.app.domain.pack

import com.eventverse.app.domain.blueprint.Blueprint
import com.eventverse.app.domain.blueprint.BlueprintCode
import com.eventverse.app.domain.blueprint.BlueprintModule

/** Kunci parameter modul garment (TRD-PLAT-001 FR-3). */
object GarmentBlueprintParams {
    const val STOCK_OWNERSHIP = "stockOwnership"
    const val COSTING_BEHAVIOR = "costingBehavior"
    const val DEFECT_LIABILITY = "defectLiability"
}

private const val STOCK = GarmentBlueprintParams.STOCK_OWNERSHIP
private const val COSTING = GarmentBlueprintParams.COSTING_BEHAVIOR
private const val DEFECT = GarmentBlueprintParams.DEFECT_LIABILITY

private fun m(module: String, active: Boolean, vararg params: Pair<String, String>) =
    BlueprintModule(module, active, params.toMap())

/**
 * Tiga starter konveksi — **data literal** sejak B4b (sumber kebenaran; spec modul tidak lagi menyebut
 * preset). Memuat **semua** modul katalog, termasuk yang non-aktif beserta parameternya (TRD-PLAT-001 FR-2).
 * Modul katalog baru wajib ditambahkan ke setiap starter — `GarmentBlueprintParityTest` gagal bila lupa.
 */
object GarmentBlueprints {

    val FOB_FULL_PACKAGE = Blueprint(
        code = BlueprintCode("fob_full_package"),
        pack = GarmentDomainPack.CODE,
        displayName = "FOB (Full Order / Buy) — Paket Lengkap",
        shortBadge = "FOB Full Package",
        description = "Pengerjaan hulu-ke-hilir: Dari pengadaan bahan baku kain, aksesoris, pembuatan pola/sample, produksi massal, hingga ekspedisi ekspor/retail.",
        targetClientProfile = "Pabrik OEM, Ekspor Garmen, atau Konveksi Skala Menengah ke Atas",
        modules = listOf(
            m("crm_sales", true, STOCK to "NON_STOCK_SERVICE", COSTING to "INDIRECT_OVERHEAD"),
            m("sampling_order", true, STOCK to "NON_STOCK_SERVICE", COSTING to "SERVICE_FEE_ONLY"),
            m("tech_pack_bom", true, STOCK to "NON_STOCK_SERVICE", COSTING to "FULL_PACKAGE_COGS"),
            m("inventory", true, STOCK to "OWNED_RAW_MATERIAL", COSTING to "FULL_PACKAGE_COGS"),
            m("costing_hpp", true, STOCK to "NON_STOCK_SERVICE", COSTING to "FULL_PACKAGE_COGS"),
            m("production_mrp", true, STOCK to "OWNED_RAW_MATERIAL", COSTING to "INDIRECT_OVERHEAD"),
            m("operator_exec", true, STOCK to "OWNED_RAW_MATERIAL", COSTING to "SERVICE_FEE_ONLY"),
            m("quality_control", true, STOCK to "OWNED_RAW_MATERIAL", COSTING to "INDIRECT_OVERHEAD", DEFECT to "SUPPLIER_VENDOR_DEFECT"),
            m("fulfillment", true, STOCK to "INTERNAL_FINISHED_GOODS", COSTING to "RETAIL_VALUATION_WITH_FEES")
        )
    )

    val CMT_MAKLOON = Blueprint(
        code = BlueprintCode("cmt_makloon"),
        pack = GarmentDomainPack.CODE,
        displayName = "CMT (Cut, Make, Trim) — Jasa Jahit Makloon",
        shortBadge = "CMT Jasa Jahit",
        description = "Pengerjaan jasa jahit murni. Pola potong & kain rol utama disediakan sepenuhnya oleh Buyer/Brand. Pengadaan bahan baku di-bypass.",
        targetClientProfile = "Vendor Makloon, Sub-kontraktor Jahit, Mitra Konveksi Rumahan/Sentra",
        modules = listOf(
            m("crm_sales", true, STOCK to "NON_STOCK_SERVICE", COSTING to "INDIRECT_OVERHEAD"),
            m("sampling_order", true, STOCK to "NON_STOCK_SERVICE", COSTING to "SERVICE_FEE_ONLY"),
            m("tech_pack_bom", false, STOCK to "NON_STOCK_SERVICE", COSTING to "FULL_PACKAGE_COGS"),
            m("inventory", false, STOCK to "CONSIGNED_CLIENT_MATERIAL", COSTING to "FULL_PACKAGE_COGS"),
            m("costing_hpp", true, STOCK to "NON_STOCK_SERVICE", COSTING to "SERVICE_FEE_ONLY"),
            m("production_mrp", true, STOCK to "CONSIGNED_CLIENT_MATERIAL", COSTING to "INDIRECT_OVERHEAD"),
            m("operator_exec", true, STOCK to "OWNED_RAW_MATERIAL", COSTING to "SERVICE_FEE_ONLY"),
            m("quality_control", true, STOCK to "OWNED_RAW_MATERIAL", COSTING to "INDIRECT_OVERHEAD", DEFECT to "CLIENT_SUPPLIED_DEFECT"),
            m("fulfillment", true, STOCK to "CONSIGNED_CLIENT_MATERIAL", COSTING to "RETAIL_VALUATION_WITH_FEES")
        )
    )

    val BRAND_D2C = Blueprint(
        code = BlueprintCode("brand_d2c"),
        pack = GarmentDomainPack.CODE,
        displayName = "Brand Konveksi Sendiri (Direct to Consumer)",
        shortBadge = "Brand D2C Internal",
        description = "Model bisnis terintegrasi brand sendiri. Menghubungkan peluncuran katalog baru, sample approval cepat, stok jadi, dan pesanan multichannel.",
        targetClientProfile = "Clothing Line Lokal, Distro Brand, Pabrik Seragam Custom Mandiri",
        modules = listOf(
            m("crm_sales", true, STOCK to "NON_STOCK_SERVICE", COSTING to "INDIRECT_OVERHEAD"),
            m("sampling_order", true, STOCK to "NON_STOCK_SERVICE", COSTING to "SERVICE_FEE_ONLY"),
            m("tech_pack_bom", true, STOCK to "NON_STOCK_SERVICE", COSTING to "FULL_PACKAGE_COGS"),
            m("inventory", true, STOCK to "OWNED_RAW_MATERIAL", COSTING to "FULL_PACKAGE_COGS"),
            m("costing_hpp", true, STOCK to "NON_STOCK_SERVICE", COSTING to "RETAIL_VALUATION_WITH_FEES"),
            m("production_mrp", true, STOCK to "OWNED_RAW_MATERIAL", COSTING to "INDIRECT_OVERHEAD"),
            m("operator_exec", true, STOCK to "OWNED_RAW_MATERIAL", COSTING to "SERVICE_FEE_ONLY"),
            m("quality_control", true, STOCK to "OWNED_RAW_MATERIAL", COSTING to "INDIRECT_OVERHEAD", DEFECT to "FACTORY_WORKMANSHIP"),
            m("fulfillment", true, STOCK to "INTERNAL_FINISHED_GOODS", COSTING to "RETAIL_VALUATION_WITH_FEES")
        )
    )

    val all: List<Blueprint> = listOf(FOB_FULL_PACKAGE, CMT_MAKLOON, BRAND_D2C)

    /** Kode tak dikenal → null. Pemanggil yang menolak (B4d). */
    fun find(code: BlueprintCode): Blueprint? = all.firstOrNull { it.code == code }

    /** Starter untuk tenant baru tanpa pilihan eksplisit (kolom `tenants.business_preset` default). */
    val DEFAULT: Blueprint get() = FOB_FULL_PACKAGE

    /**
     * Parser **ketat** kode Blueprint tersimpan (B4d, tenant-variability-rules Kontrak 4). Menggantikan
     * `GarmentBusinessPreset.fromCode` lama yang diam-diam jatuh ke FOB: kode tak dikenal kini gagal keras,
     * karena menebaknya berarti mengubah model bisnis tenant tanpa jejak. Tidak peka huruf besar (perilaku lama).
     */
    fun parse(code: String): Blueprint =
        requireNotNull(findByCode(code)) { "Blueprint tidak dikenal: '$code'" }

    /** Versi lunak untuk masukan pengguna: null bila tak dikenal, pemanggil yang menjawab 400. */
    fun findByCode(code: String): Blueprint? = all.firstOrNull { it.code.value.equals(code, ignoreCase = true) }
}
