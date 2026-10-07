package com.eventverse.app.domain.pack

/**
 * Kamus peran → modul **pack garment** (B2). Data khas konveksi — sengaja bukan di mesin wawancara. Setiap modul
 * garment yang lazim dipegang satu peran punya padanan di sini; paritasnya dikunci `GarmentRoleHintsTest`.
 */
object GarmentRoleHints {
    private fun h(word: String, label: String, module: ModuleId) = RoleHint(word, label, module)

    val all: List<RoleHint> by lazy {
        val m = GarmentModules
        listOf(
            h("pemilik", "Pemilik", m.ORG_CHART),
            h("admin penjualan", "Admin Penjualan", m.CRM_SALES),
            h("sales", "Sales", m.CRM_SALES),
            h("sampling", "Staf Sampling", m.SAMPLING_ORDER),
            h("admin gudang", "Admin Gudang", m.INVENTORY),
            h("gudang", "Staf Gudang", m.INVENTORY),
            h("pola", "Staf Pola", m.TECH_PACK_BOM),
            h("costing", "Staf Costing", m.COSTING_HPP),
            h("kepala potong", "Kepala Potong", m.PRODUCTION_MRP),
            h("ppic", "Staf PPIC", m.PRODUCTION_MRP),
            h("operator jahit", "Operator Jahit", m.OPERATOR_EXEC),
            h("operator rajut", "Operator Rajut", m.OPERATOR_EXEC),
            h("penjahit", "Penjahit", m.OPERATOR_EXEC),
            h("qc", "Petugas QC", m.QUALITY_CONTROL),
            h("admin pengiriman", "Admin Pengiriman", m.FULFILLMENT),
            h("packing", "Staf Packing", m.FULFILLMENT),
            h("keuangan", "Staf Keuangan", m.INVOICING)
        )
    }
}
