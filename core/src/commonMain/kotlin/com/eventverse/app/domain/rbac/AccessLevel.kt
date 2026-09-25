package com.eventverse.app.domain.rbac

/**
 * 4-Tier user-friendly access level.
 * Replaces complex IAM policy syntax with intuitive factory roles.
 */
enum class AccessLevel(
    val displayName: String,
    val shortDescription: String,
    val weight: Int
) {
    NONE(
        displayName = "Tutup Akses",
        shortDescription = "Menu tidak terlihat dan akses diblokir total.",
        weight = 0
    ),
    VIEW(
        displayName = "Hanya Lihat",
        shortDescription = "Dapat melihat data dan laporan tanpa hak mengubah.",
        weight = 1
    ),
    OPERATE(
        displayName = "Input & Kerja",
        shortDescription = "Dapat membuat, menginput, dan mengubah tugas harian.",
        weight = 2
    ),
    MANAGE(
        displayName = "Akses Penuh",
        shortDescription = "Hak penuh termasuk approval, hapus data, dan akses data rahasia.",
        weight = 3
    );

    fun isAtLeast(required: AccessLevel): Boolean = weight >= required.weight
}

/**
 * Data boundary scope for a role within a tenant.
 */
enum class DataScope(
    val code: String,
    val displayName: String,
    val shortLabel: String,
    val description: String
) {
    OWN_DATA_ONLY(
        code = "own_data",
        displayName = "Hanya Data Sendiri",
        shortLabel = "Data Sendiri",
        description = "Pengguna hanya melihat dokumen/transaksi yang dibuat oleh dirinya sendiri."
    ),
    SUBORDINATE_DATA(
        code = "subordinate_data",
        displayName = "Data Tim & Bawahan",
        shortLabel = "Data Bawahan",
        description = "Melihat data miliknya dan seluruh staf bawahannya di divisi yang sama."
    ),
    ALL_TENANT_DATA(
        code = "all_data",
        displayName = "Seluruh Data Pabrik",
        shortLabel = "Semua Data",
        description = "Pengguna dapat melihat seluruh data di seluruh divisi pabrik."
    );
}

/**
 * Dynamic Scope Capability defining whether a module's data boundary
 * can be scoped per individual/hierarchy or must remain enterprise-wide (global).
 */
enum class ScopeCapability(
    val displayName: String,
    val shortLabel: String,
    val description: String
) {
    GLOBAL_ONLY(
        displayName = "Seluruh Pabrik (Data Kolektif)",
        shortLabel = "Seluruh Pabrik",
        description = "Data inventaris, kalkulasi HPP, dan mesin dikelola kolektif untuk seluruh pabrik tanpa partisi kepemilikan."
    ),
    HIERARCHICAL(
        displayName = "Hirarkis (Sendiri & Bawahan)",
        shortLabel = "Hirarkis",
        description = "Mendukung isolasi data dokumen per pembuat (Data Sendiri) dan atasan komando (Data Bawahan)."
    );
}

/**
 * Value object configuring access for a single module.
 */
data class ModuleAccessConfig(
    val level: AccessLevel = AccessLevel.NONE,
    val scope: DataScope = DataScope.ALL_TENANT_DATA,
    /**
     * Meja (tahap lantai produksi) yang boleh diakses, sebagai nama [SamplingPipelineStage].
     * `null` berarti tanpa batasan — seluruh meja. Hanya bermakna untuk `OPERATOR_EXEC`;
     * modul lain mengabaikannya. Dibawa di sini — bukan di entity penugasan saja — supaya
     * [AccessDecision] memuatnya dan layar bisa memfilter tanpa query kedua.
     */
    val allowedDesks: Set<String>? = null
) {
    val isAccessible: Boolean get() = level != AccessLevel.NONE
    val canWrite: Boolean get() = level.isAtLeast(AccessLevel.OPERATE)
    val canManage: Boolean get() = level.isAtLeast(AccessLevel.MANAGE)

    /**
     * Sanitizes data scope according to the module's capability.
     * If the module is GLOBAL_ONLY, force scope to ALL_TENANT_DATA.
     */
    fun sanitizeFor(module: BusinessModule): ModuleAccessConfig {
        return if (module.isGlobalOnly && scope != DataScope.ALL_TENANT_DATA) {
            copy(scope = DataScope.ALL_TENANT_DATA)
        } else {
            this
        }
    }
}
