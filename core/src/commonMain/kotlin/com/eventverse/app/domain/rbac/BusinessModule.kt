package com.eventverse.app.domain.rbac

/**
 * Business modules for WeMade Garment ERP.
 * Designed with human-friendly terminology for factory owners & management.
 */
enum class ModuleCategory(val displayName: String) {
    SALES("Penjualan & Relasi Pelanggan"),
    LOGISTICS("Gudang, Bahan Baku & Logistik"),
    TECHNICAL("Desain, Pola & Biaya HPP"),
    PRODUCTION("Lantai Produksi & Operator"),
    QUALITY("Kualitas & Pengawasan");
}

enum class BusinessModule(
    val code: String,
    val displayName: String,
    val category: ModuleCategory,
    val description: String,
    val iconKey: String,
    val scopeCapability: ScopeCapability = ScopeCapability.GLOBAL_ONLY,
    val supportedScopes: Set<DataScope> = if (scopeCapability == ScopeCapability.GLOBAL_ONLY) {
        setOf(DataScope.ALL_TENANT_DATA)
    } else {
        setOf(DataScope.OWN_DATA_ONLY, DataScope.SUBORDINATE_DATA, DataScope.ALL_TENANT_DATA)
    }
) {
    CRM_SALES(
        code = "crm_sales",
        displayName = "Pelanggan & Prospek Sales",
        category = ModuleCategory.SALES,
        description = "Pencatatan prospek, riwayat follow-up negosiasi, dan kontak pelanggan konveksi.",
        iconKey = "handshake",
        scopeCapability = ScopeCapability.HIERARCHICAL
    ),
    SAMPLING_ORDER(
        code = "sampling_order",
        displayName = "Pola & Sampling Order",
        category = ModuleCategory.SALES,
        description = "Pembuatan SPK sampling prototipe baju, pola potong awal, dan persetujuan sample.",
        iconKey = "ruler",
        scopeCapability = ScopeCapability.HIERARCHICAL
    ),
    INVENTORY(
        code = "inventory",
        displayName = "Bahan Baku & Stok Kain",
        category = ModuleCategory.LOGISTICS,
        description = "Penerimaan kain rol, stok benang, kancing, zipper, dan multi-satuan (Yard/Kg/Pcs).",
        iconKey = "package",
        scopeCapability = ScopeCapability.GLOBAL_ONLY
    ),
    TECH_PACK_BOM(
        code = "tech_pack_bom",
        displayName = "Spesifikasi BOM & Tech Pack",
        category = ModuleCategory.TECHNICAL,
        description = "Lembar kerja spesifikasi jahitan, Bill of Materials (BOM), dan panduan ukuran.",
        iconKey = "clipboard",
        scopeCapability = ScopeCapability.GLOBAL_ONLY
    ),
    COSTING_HPP(
        code = "costing_hpp",
        displayName = "Kalkulasi HPP & Biaya",
        category = ModuleCategory.TECHNICAL,
        description = "Perhitungan HPP otomatis: bahan baku + ongkos jahit per menit + finishing & margin laba rahasia.",
        iconKey = "calculator",
        scopeCapability = ScopeCapability.GLOBAL_ONLY
    ),
    PRODUCTION_MRP(
        code = "production_mrp",
        displayName = "Jadwal Mesin & SPK Massal",
        category = ModuleCategory.PRODUCTION,
        description = "Alokasi antrean 10 mesin jahit, target jam kerja harian, dan penerbitan SPK potong/jahit.",
        iconKey = "calendar",
        scopeCapability = ScopeCapability.GLOBAL_ONLY
    ),
    OPERATOR_EXEC(
        code = "operator_exec",
        displayName = "Catatan Kerja Operator",
        category = ModuleCategory.PRODUCTION,
        description = "Antarmuka ringkas operator jahit untuk input output potong, jahit, dan progres harian.",
        iconKey = "activity",
        scopeCapability = ScopeCapability.HIERARCHICAL
    ),
    QUALITY_CONTROL(
        code = "quality_control",
        displayName = "Inspeksi QC & Defect",
        category = ModuleCategory.QUALITY,
        description = "Pencatatan baju cacat (reject/scrap), cetak label barcode lolos inspeksi, dan grading.",
        iconKey = "check_circle",
        scopeCapability = ScopeCapability.GLOBAL_ONLY
    ),
    FULFILLMENT(
        code = "fulfillment",
        displayName = "Packing & Surat Jalan",
        category = ModuleCategory.LOGISTICS,
        description = "Finishing setrika uap, verifikasi kuantitas per karton, dan cetak Surat Jalan ekspedisi.",
        iconKey = "truck",
        scopeCapability = ScopeCapability.GLOBAL_ONLY
    );

    val isGlobalOnly: Boolean get() = scopeCapability == ScopeCapability.GLOBAL_ONLY
    val isHierarchical: Boolean get() = scopeCapability == ScopeCapability.HIERARCHICAL

    fun isScopeSupported(scope: DataScope): Boolean = supportedScopes.contains(scope)
}
