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


/**
 * Modul bisnis (B6d, TRD-PLAT-001). Dulu `enum class` konveksi; kini [ModuleId] dari Domain Pack, sehingga pack lain
 * (e-learning) punya modulnya sendiri. Nama `BusinessModule` dipertahankan **sementara** agar tipe di ±100 file tetap
 * terkompilasi; anggota enum lama menjadi extension di bawah yang membaca definisi pack.
 *
 * - Konstanta konveksi: `GarmentModules.CRM_SALES` dsb.
 * - "Semua modul" milik **tenant**: `pack.moduleIds` (B7) — berurutan (urutan menu). Tidak ada daftar global.
 * - Kunci tersimpan: `name` = NAME lama (`CRM_SALES`), `code` = code (`crm_sales`); parser tunggal di `ModuleIdCodec`.
 */
typealias BusinessModule = ModuleId

private val ModuleId.definition: com.eventverse.app.domain.pack.ModuleDefinition
    get() = requireNotNull(com.eventverse.app.domain.pack.DomainPackRegistry.moduleDefinition(this)) {
        "Modul $value tidak ada di pack mana pun"
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

/** Seksi menu modul ini (dulu `enum ModuleCategory`), dari pack aktif. */
val ModuleId.section: com.eventverse.app.domain.pack.ModuleSection
    get() = requireNotNull(com.eventverse.app.domain.pack.DomainPackRegistry.ownerOf(this)?.sections?.firstOrNull { it.code == definition.section }) {
        "Seksi ${definition.section.value} modul $value tidak ada di pack"
    }

val ModuleId.isGlobalOnly: Boolean get() = scopeCapability == ScopeCapability.GLOBAL_ONLY
val ModuleId.isHierarchical: Boolean get() = scopeCapability == ScopeCapability.HIERARCHICAL
val ModuleId.isGovernance: Boolean get() = kind == ModuleKind.GOVERNANCE
val ModuleId.isOperational: Boolean get() = kind == ModuleKind.OPERATIONAL
val ModuleId.isFoundation: Boolean get() = kind == ModuleKind.FOUNDATION

fun ModuleId.isScopeSupported(scope: DataScope): Boolean = supportedScopes.contains(scope)

/** Semua modul pack, berurutan (urutan menu) — dulu `BusinessModule.entries`. Milik tenant: pack diresolusi per tenant (B7). */
val com.eventverse.app.domain.pack.DomainPack.moduleIds: List<BusinessModule> get() = modules.map { it.id }

/** Modul yang boleh berdiri sebagai node kanvas & dihitung kuota paket. */
val com.eventverse.app.domain.pack.DomainPack.operationalModules: List<BusinessModule> get() = moduleIds.filter { it.isOperational }

/** Modul pengatur sistem: bagan organisasi, matriks wewenang, dan kanvas alur. */
val com.eventverse.app.domain.pack.DomainPack.governanceModules: List<BusinessModule> get() = moduleIds.filter { it.isGovernance }

/** Modul fondasi non-bypassable: data induk bahan dan harga acuan. */
val com.eventverse.app.domain.pack.DomainPack.foundationModules: List<BusinessModule> get() = moduleIds.filter { it.isFoundation }

object BusinessModules {
    fun fromCode(code: String?): BusinessModule? = com.eventverse.app.domain.pack.ModuleIdCodec.standardOrNull(code)
}
