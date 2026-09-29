package com.eventverse.app.domain.pack

import com.eventverse.app.domain.rbac.DataScope
import com.eventverse.app.domain.rbac.ModuleKind
import com.eventverse.app.domain.rbac.ScopeCapability

/**
 * Modul & seksi menu pack konveksi. Sejak **B6d** ini **sumber kebenaran**: `enum BusinessModule` dihapus, nilainya
 * disalin persis dan dikunci `GarmentModulesParityTest` (termasuk urutan = urutan menu). Konstanta di bawah adalah
 * kosakata konveksi; kode mesin memakai `BusinessModules` (modul pack aktif), bukan konstanta ini.
 *
 * Urutan deklarasi penting: konstanta dulu, daftar definisi `lazy` — menghindari siklus inisialisasi dengan
 * [GarmentSlots] yang juga merujuk konstanta ini.
 */
object GarmentModules {
    val ORG_CHART = ModuleId("org_chart")
    val DYNAMIC_RBAC = ModuleId("dynamic_rbac")
    val FACTORY_FLOW = ModuleId("factory_flow")
    val MASTER_DATA = ModuleId("master_data")
    val VENDOR_CONTACTS = ModuleId("vendor_contacts")
    val CRM_SALES = ModuleId("crm_sales")
    val SAMPLING_ORDER = ModuleId("sampling_order")
    val INVENTORY = ModuleId("inventory")
    val TECH_PACK_BOM = ModuleId("tech_pack_bom")
    val COSTING_HPP = ModuleId("costing_hpp")
    val PRODUCTION_MRP = ModuleId("production_mrp")
    val OPERATOR_EXEC = ModuleId("operator_exec")
    val QUALITY_CONTROL = ModuleId("quality_control")
    val FULFILLMENT = ModuleId("fulfillment")
    val INVOICING = ModuleId("invoicing")

    val sections: List<ModuleSection> = listOf(
        ModuleSection(ModuleSectionCode("GOVERNANCE"), "Sistem & Struktur", 1),
        ModuleSection(ModuleSectionCode("FOUNDATION"), "Data Induk & Referensi", 2),
        ModuleSection(ModuleSectionCode("SALES"), "Penjualan & Relasi Pelanggan", 3),
        ModuleSection(ModuleSectionCode("LOGISTICS"), "Gudang, Bahan Baku & Logistik", 4),
        ModuleSection(ModuleSectionCode("TECHNICAL"), "Desain, Pola & Biaya HPP", 5),
        ModuleSection(ModuleSectionCode("PRODUCTION"), "Lantai Produksi & Operator", 6),
        ModuleSection(ModuleSectionCode("QUALITY"), "Kualitas & Pengawasan", 7),
        ModuleSection(ModuleSectionCode("FINANCE"), "Keuangan & Penagihan", 8)
    )

    val modules: List<ModuleDefinition> by lazy {
        listOf(
            ModuleDefinition(
                id = ORG_CHART,
                displayName = "Bagan Struktur Organisasi & Karyawan",
                description = "Struktur divisi, jenjang jabatan, dan data karyawan pabrik.",
                section = ModuleSectionCode("GOVERNANCE"),
                kind = ModuleKind.GOVERNANCE,
                iconKey = "users",
                scopeCapability = ScopeCapability.GLOBAL_ONLY,
                supportedScopes = setOf(DataScope.ALL_TENANT_DATA),
                slot = null
            ),
            ModuleDefinition(
                id = DYNAMIC_RBAC,
                displayName = "Hak Akses & Jabatan (RBAC)",
                description = "Matriks wewenang per jabatan, penugasan modul ke divisi, dan pengujian persona.",
                section = ModuleSectionCode("GOVERNANCE"),
                kind = ModuleKind.GOVERNANCE,
                iconKey = "shield",
                scopeCapability = ScopeCapability.GLOBAL_ONLY,
                supportedScopes = setOf(DataScope.ALL_TENANT_DATA),
                slot = null
            ),
            ModuleDefinition(
                id = FACTORY_FLOW,
                displayName = "Alur Pabrik (Pipeline)",
                description = "Kanvas alur operasional tenant: urutan modul, penggantian nama, dan bypass.",
                section = ModuleSectionCode("GOVERNANCE"),
                kind = ModuleKind.GOVERNANCE,
                iconKey = "flow_graph",
                scopeCapability = ScopeCapability.GLOBAL_ONLY,
                supportedScopes = setOf(DataScope.ALL_TENANT_DATA),
                slot = null
            ),
            ModuleDefinition(
                id = MASTER_DATA,
                displayName = "Master Data Bahan & Harga",
                description = "Katalog benang, kain, aksesoris, satuan kemasan, dan tarif acuan HPP point-in-time.",
                section = ModuleSectionCode("FOUNDATION"),
                kind = ModuleKind.FOUNDATION,
                iconKey = "database",
                scopeCapability = ScopeCapability.GLOBAL_ONLY,
                supportedScopes = setOf(DataScope.ALL_TENANT_DATA),
                slot = null
            ),
            ModuleDefinition(
                id = VENDOR_CONTACTS,
                displayName = "Kontak Vendor & Makloon",
                description = "Buku kontak vendor subkon, daftar harga layanan per vendor, dan penunjukan vendor ke proses Vendor Luar.",
                section = ModuleSectionCode("FOUNDATION"),
                kind = ModuleKind.FOUNDATION,
                iconKey = "truck",
                scopeCapability = ScopeCapability.GLOBAL_ONLY,
                supportedScopes = setOf(DataScope.ALL_TENANT_DATA),
                slot = null
            ),
            ModuleDefinition(
                id = CRM_SALES,
                displayName = "Pelanggan & Prospek Sales",
                description = "Pencatatan prospek, riwayat follow-up negosiasi, dan kontak pelanggan konveksi.",
                section = ModuleSectionCode("SALES"),
                kind = ModuleKind.OPERATIONAL,
                iconKey = "handshake",
                scopeCapability = ScopeCapability.HIERARCHICAL,
                supportedScopes = setOf(DataScope.ALL_TENANT_DATA, DataScope.OWN_DATA_ONLY, DataScope.SUBORDINATE_DATA),
                slot = GarmentSlots.ORDER_INGESTION
            ),
            ModuleDefinition(
                id = SAMPLING_ORDER,
                displayName = "Pola & Sampling Order",
                description = "Pembuatan SPK sampling prototipe baju, pola potong awal, dan persetujuan sample.",
                section = ModuleSectionCode("SALES"),
                kind = ModuleKind.OPERATIONAL,
                iconKey = "ruler",
                scopeCapability = ScopeCapability.HIERARCHICAL,
                supportedScopes = setOf(DataScope.ALL_TENANT_DATA, DataScope.OWN_DATA_ONLY, DataScope.SUBORDINATE_DATA),
                slot = GarmentSlots.ORDER_INGESTION
            ),
            ModuleDefinition(
                id = INVENTORY,
                displayName = "Bahan Baku & Stok Kain",
                description = "Penerimaan kain rol, stok benang, kancing, zipper, dan multi-satuan (Yard/Kg/Pcs).",
                section = ModuleSectionCode("LOGISTICS"),
                kind = ModuleKind.OPERATIONAL,
                iconKey = "package",
                scopeCapability = ScopeCapability.GLOBAL_ONLY,
                supportedScopes = setOf(DataScope.ALL_TENANT_DATA),
                slot = GarmentSlots.RAW_MATERIAL
            ),
            ModuleDefinition(
                id = TECH_PACK_BOM,
                displayName = "Spesifikasi BOM & Tech Pack",
                description = "Lembar kerja spesifikasi jahitan, Bill of Materials (BOM), dan panduan ukuran.",
                section = ModuleSectionCode("TECHNICAL"),
                kind = ModuleKind.OPERATIONAL,
                iconKey = "clipboard",
                scopeCapability = ScopeCapability.GLOBAL_ONLY,
                supportedScopes = setOf(DataScope.ALL_TENANT_DATA),
                slot = GarmentSlots.PRODUCT_ENGINEERING
            ),
            ModuleDefinition(
                id = COSTING_HPP,
                displayName = "Kalkulasi HPP & Biaya",
                description = "Perhitungan HPP otomatis: bahan baku + ongkos jahit per menit + finishing & margin laba rahasia.",
                section = ModuleSectionCode("TECHNICAL"),
                kind = ModuleKind.OPERATIONAL,
                iconKey = "calculator",
                scopeCapability = ScopeCapability.GLOBAL_ONLY,
                supportedScopes = setOf(DataScope.ALL_TENANT_DATA),
                slot = GarmentSlots.COSTING_HPP
            ),
            ModuleDefinition(
                id = PRODUCTION_MRP,
                displayName = "Jadwal Mesin & SPK Massal",
                description = "Alokasi antrean 10 mesin jahit, target jam kerja harian, dan penerbitan SPK potong/jahit.",
                section = ModuleSectionCode("PRODUCTION"),
                kind = ModuleKind.OPERATIONAL,
                iconKey = "calendar",
                scopeCapability = ScopeCapability.GLOBAL_ONLY,
                supportedScopes = setOf(DataScope.ALL_TENANT_DATA),
                slot = GarmentSlots.CUTTING
            ),
            ModuleDefinition(
                id = OPERATOR_EXEC,
                displayName = "Catatan Kerja Operator",
                description = "Antarmuka ringkas operator jahit untuk input output potong, jahit, dan progres harian.",
                section = ModuleSectionCode("PRODUCTION"),
                kind = ModuleKind.OPERATIONAL,
                iconKey = "activity",
                scopeCapability = ScopeCapability.HIERARCHICAL,
                supportedScopes = setOf(DataScope.ALL_TENANT_DATA, DataScope.OWN_DATA_ONLY, DataScope.SUBORDINATE_DATA),
                slot = GarmentSlots.SEWING
            ),
            ModuleDefinition(
                id = QUALITY_CONTROL,
                displayName = "Inspeksi QC & Defect",
                description = "Pencatatan baju cacat (reject/scrap), cetak label barcode lolos inspeksi, dan grading.",
                section = ModuleSectionCode("QUALITY"),
                kind = ModuleKind.OPERATIONAL,
                iconKey = "check_circle",
                scopeCapability = ScopeCapability.GLOBAL_ONLY,
                supportedScopes = setOf(DataScope.ALL_TENANT_DATA),
                slot = GarmentSlots.QUALITY_CONTROL
            ),
            ModuleDefinition(
                id = FULFILLMENT,
                displayName = "Packing & Surat Jalan",
                description = "Finishing setrika uap, verifikasi kuantitas per karton, dan cetak Surat Jalan ekspedisi.",
                section = ModuleSectionCode("LOGISTICS"),
                kind = ModuleKind.OPERATIONAL,
                iconKey = "truck",
                scopeCapability = ScopeCapability.GLOBAL_ONLY,
                supportedScopes = setOf(DataScope.ALL_TENANT_DATA),
                slot = GarmentSlots.FULFILLMENT
            ),
            ModuleDefinition(
                id = INVOICING,
                displayName = "Invoice & Penagihan",
                description = "Penerbitan faktur tagihan sample, termin DP, dan pelunasan garmen berkanvas.",
                section = ModuleSectionCode("FINANCE"),
                kind = ModuleKind.FOUNDATION,
                iconKey = "receipt",
                scopeCapability = ScopeCapability.HIERARCHICAL,
                supportedScopes = setOf(DataScope.ALL_TENANT_DATA, DataScope.OWN_DATA_ONLY, DataScope.SUBORDINATE_DATA),
                slot = null
            )
        )
    }
}
