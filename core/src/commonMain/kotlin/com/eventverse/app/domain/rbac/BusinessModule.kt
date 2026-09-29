package com.eventverse.app.domain.rbac

import com.eventverse.app.domain.pack.ModuleId

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

/**
 * Modul bisnis (B6d, TRD-PLAT-001). Dulu `enum class` konveksi; kini [ModuleId] dari Domain Pack, sehingga pack lain
 * (e-learning) punya modulnya sendiri. Nama `BusinessModule` dipertahankan **sementara** agar tipe di ±100 file tetap
 * terkompilasi; anggota enum lama menjadi extension di bawah yang membaca definisi pack.
 *
 * - Konstanta konveksi: `GarmentModules.CRM_SALES` dsb.
 * - "Semua modul": [BusinessModules.entries] — modul pack aktif, **berurutan** (urutan menu).
 * - Kunci tersimpan: `name` = NAME lama (`CRM_SALES`), `code` = code (`crm_sales`); parser tunggal di `ModuleIdCodec`.
 */
typealias BusinessModule = ModuleId

private val ModuleId.definition: com.eventverse.app.domain.pack.ModuleDefinition
    get() = requireNotNull(com.eventverse.app.domain.pack.DomainPackRegistry.soleActivePack.module(this)) {
        "Modul $value tidak ada di pack ${com.eventverse.app.domain.pack.DomainPackRegistry.soleActivePack.code.value}"
    }

/** Kunci code tersimpan (`crm_sales`) — katalog modul, node pipeline. */
val ModuleId.code: String get() = value

/** Kunci NAME tersimpan (`CRM_SALES`) — RBAC, entitlement, `/me/access`. Dulu `Enum.name`. */
val ModuleId.name: String get() = storedName

val ModuleId.displayName: String get() = definition.displayName
val ModuleId.description: String get() = definition.description
val ModuleId.iconKey: String get() = definition.iconKey
val ModuleId.scopeCapability: ScopeCapability get() = definition.scopeCapability
val ModuleId.kind: ModuleKind get() = definition.kind
val ModuleId.supportedScopes: Set<DataScope> get() = definition.supportedScopes

/** Seksi menu lama — tetap `ModuleCategory` sampai klien membaca `ModuleSection` pack (B6f). */
val ModuleId.category: ModuleCategory get() = ModuleCategory.valueOf(definition.section.value)

val ModuleId.isGlobalOnly: Boolean get() = scopeCapability == ScopeCapability.GLOBAL_ONLY
val ModuleId.isHierarchical: Boolean get() = scopeCapability == ScopeCapability.HIERARCHICAL
val ModuleId.isGovernance: Boolean get() = kind == ModuleKind.GOVERNANCE
val ModuleId.isOperational: Boolean get() = kind == ModuleKind.OPERATIONAL
val ModuleId.isFoundation: Boolean get() = kind == ModuleKind.FOUNDATION

fun ModuleId.isScopeSupported(scope: DataScope): Boolean = supportedScopes.contains(scope)

/** Pengganti companion enum lama: modul **pack aktif** (bukan konstanta konveksi). */
object BusinessModules {
    /** Semua modul, berurutan — dulu `BusinessModule.entries`. */
    val entries: List<BusinessModule>
        get() = com.eventverse.app.domain.pack.DomainPackRegistry.soleActivePack.modules.map { it.id }

    /** Modul yang boleh berdiri sebagai node kanvas & dihitung kuota paket. */
    val operational: List<BusinessModule> get() = entries.filter { it.isOperational }

    /** Modul pengatur sistem: bagan organisasi, matriks wewenang, dan kanvas alur. */
    val governance: List<BusinessModule> get() = entries.filter { it.isGovernance }

    /** Modul fondasi non-bypassable: data induk bahan dan harga acuan. */
    val foundation: List<BusinessModule> get() = entries.filter { it.isFoundation }

    fun fromCode(code: String?): BusinessModule? = com.eventverse.app.domain.pack.ModuleIdCodec.standardOrNull(code)
}
