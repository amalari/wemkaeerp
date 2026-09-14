package com.eventverse.app.domain.rbac

/**
 * Business modules for WeMade Garment ERP.
 * Designed with human-friendly terminology for factory owners & management.
 */
/**
 * Membedakan modul yang mengerjakan **pekerjaan pabrik** dari modul yang mengatur **sistemnya**.
 *
 * Pembedaan ini bukan label dokumentasi: modul `GOVERNANCE` tidak mengisi slot kapabilitas
 * [com.eventverse.app.domain.pipeline.ModuleArchetype] mana pun, tidak pernah menjadi node di kanvas
 * Alur Pabrik, dan karenanya tidak boleh ikut menghabiskan kuota
 * `SubscriptionTier.maxActivePipelineModules`. Paket PRO tetap berarti sembilan modul produksi,
 * bukan sembilan dikurangi layar pengaturan.
 */
enum class ModuleKind { OPERATIONAL, GOVERNANCE, FOUNDATION }

enum class ModuleCategory(val displayName: String) {
    // Wajib entri pertama: NavMenu menyusun urutan seksi drawer dari ModuleCategory.entries, dan
    // seksi tata kelola selalu berada di puncak seperti sebelum ketiga layar ini menjadi modul.
    GOVERNANCE("Sistem & Struktur"),
    FOUNDATION("Data Induk & Referensi"),
    SALES("Penjualan & Relasi Pelanggan"),
    LOGISTICS("Gudang, Bahan Baku & Logistik"),
    TECHNICAL("Desain, Pola & Biaya HPP"),
    PRODUCTION("Lantai Produksi & Operator"),
    QUALITY("Kualitas & Pengawasan"),
    FINANCE("Keuangan & Penagihan");
}

enum class BusinessModule(
    val code: String,
    val displayName: String,
    val category: ModuleCategory,
    val description: String,
    val iconKey: String,
    val scopeCapability: ScopeCapability = ScopeCapability.GLOBAL_ONLY,
    val kind: ModuleKind = ModuleKind.OPERATIONAL,
    val supportedScopes: Set<DataScope> = if (scopeCapability == ScopeCapability.GLOBAL_ONLY) {
        setOf(DataScope.ALL_TENANT_DATA)
    } else {
        setOf(DataScope.OWN_DATA_ONLY, DataScope.SUBORDINATE_DATA, DataScope.ALL_TENANT_DATA)
    }
) {
    // ── Modul tata kelola ────────────────────────────────────────────────────────────────────
    // Ketiganya dulu berupa layar administrasi yang tidak dijaga matriks wewenang sama sekali.
    // Menjadikannya modul membuat dua hal mungkin sekaligus: superadmin menyambung/memutusnya per
    // tenant, dan admin pabrik mengatur siapa yang boleh membukanya.
    ORG_CHART(
        code = "org_chart",
        displayName = "Bagan Struktur Organisasi & Karyawan",
        category = ModuleCategory.GOVERNANCE,
        description = "Struktur divisi, jenjang jabatan, dan data karyawan pabrik.",
        iconKey = "users",
        // Global kolektif karena bagan organisasi dilihat utuh untuk seluruh struktur perusahaan;
        // pembedaan wewenang berada pada tingkat hak akses (VIEW hanya lihat full bagan, OPERATE input/edit, MANAGE kelola penuh).
        scopeCapability = ScopeCapability.GLOBAL_ONLY,
        kind = ModuleKind.GOVERNANCE
    ),
    DYNAMIC_RBAC(
        code = "dynamic_rbac",
        displayName = "Hak Akses & Jabatan (RBAC)",
        category = ModuleCategory.GOVERNANCE,
        description = "Matriks wewenang per jabatan, penugasan modul ke divisi, dan pengujian persona.",
        iconKey = "shield",
        scopeCapability = ScopeCapability.GLOBAL_ONLY,
        kind = ModuleKind.GOVERNANCE
    ),
    FACTORY_FLOW(
        code = "factory_flow",
        displayName = "Alur Pabrik (Pipeline)",
        category = ModuleCategory.GOVERNANCE,
        description = "Kanvas alur operasional tenant: urutan modul, penggantian nama, dan bypass.",
        iconKey = "flow_graph",
        scopeCapability = ScopeCapability.GLOBAL_ONLY,
        kind = ModuleKind.GOVERNANCE
    ),

    // ── Modul fondasi ────────────────────────────────────────────────────────────────────────
    // Modul permanen non-bypassable yang menyediakan data acuan global (katalog bahan, tarif harga acuan).
    MASTER_DATA(
        code = "master_data",
        displayName = "Master Data Bahan & Harga",
        category = ModuleCategory.FOUNDATION,
        description = "Katalog benang, kain, aksesoris, satuan kemasan, dan tarif acuan HPP point-in-time.",
        iconKey = "database",
        scopeCapability = ScopeCapability.GLOBAL_ONLY,
        kind = ModuleKind.FOUNDATION
    ),

    // ── Sembilan modul operasional konveksi ──────────────────────────────────────────────────
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
    ),
    INVOICING(
        code = "invoicing",
        displayName = "Invoice & Penagihan",
        category = ModuleCategory.FINANCE,
        description = "Penerbitan faktur tagihan sample, termin DP, dan pelunasan garmen berkanvas.",
        iconKey = "receipt",
        scopeCapability = ScopeCapability.HIERARCHICAL,
        kind = ModuleKind.FOUNDATION
    );

    val isGlobalOnly: Boolean get() = scopeCapability == ScopeCapability.GLOBAL_ONLY
    val isHierarchical: Boolean get() = scopeCapability == ScopeCapability.HIERARCHICAL

    val isGovernance: Boolean get() = kind == ModuleKind.GOVERNANCE
    val isOperational: Boolean get() = kind == ModuleKind.OPERATIONAL
    val isFoundation: Boolean get() = kind == ModuleKind.FOUNDATION

    fun isScopeSupported(scope: DataScope): Boolean = supportedScopes.contains(scope)

    companion object {
        /**
         * Modul yang boleh berdiri sebagai node di kanvas Alur Pabrik dan ikut dihitung kuota paket.
         *
         * Dipakai di mana pun "semua modul" sebelumnya berarti "semua modul produksi" — sebelum
         * modul tata kelola ada, dua pengertian itu kebetulan sama.
         */
        val operational: List<BusinessModule> get() = entries.filter { it.isOperational }

        /** Modul pengatur sistem: bagan organisasi, matriks wewenang, dan kanvas alur. */
        val governance: List<BusinessModule> get() = entries.filter { it.isGovernance }

        /** Modul fondasi non-bypassable: data induk bahan dan harga acuan. */
        val foundation: List<BusinessModule> get() = entries.filter { it.isFoundation }

        fun fromCode(code: String?): BusinessModule? =
            entries.firstOrNull { it.code.equals(code, ignoreCase = true) }
    }
}
