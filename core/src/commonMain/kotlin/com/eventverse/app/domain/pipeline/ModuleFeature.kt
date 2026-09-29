package com.eventverse.app.domain.pipeline

import com.eventverse.app.domain.rbac.isScopeSupported

import com.eventverse.app.domain.rbac.isFoundation

import com.eventverse.app.domain.rbac.isOperational

import com.eventverse.app.domain.rbac.isGovernance

import com.eventverse.app.domain.rbac.isHierarchical

import com.eventverse.app.domain.rbac.isGlobalOnly


import com.eventverse.app.domain.rbac.supportedScopes

import com.eventverse.app.domain.rbac.kind

import com.eventverse.app.domain.rbac.scopeCapability

import com.eventverse.app.domain.rbac.iconKey

import com.eventverse.app.domain.rbac.description

import com.eventverse.app.domain.rbac.displayName

import com.eventverse.app.domain.rbac.name

import com.eventverse.app.domain.rbac.code

import com.eventverse.app.domain.pack.GarmentModules

import com.eventverse.app.domain.rbac.BusinessModule

/**
 * Bentuk fitur di dalam modul — menentukan cara ia digambar di kanvas level 2.
 *
 * Uji Variabilitas (tenant-variability-rules Kontrak 1): lolos sebagai enum karena ini **cara
 * render milik sistem**, bukan konsep tenant. Isi tiap bentuk (tahap, proses, stasiun) tetap data.
 */
enum class ModuleFeatureKind(val displayName: String) {
    /** Kerangka tahap per tenant (`TenantStageFlow`) — tahap digambar berurutan. */
    STAGE_FRAME("Kerangka Tahap"),

    /** Proses sisipan per desain (`TenantProcessCatalog`). */
    OPTIONAL_PROCESS("Proses Sisipan"),

    /** Stasiun lini produksi massal (`WorkStationCatalog`). */
    STATION("Stasiun Lini"),

    /** Alat kerja / layar pendukung (penyimpanan, cetak, surat jalan). */
    TOOL("Alat Kerja")
}

/**
 * Fitur yang hidup **di dalam** modul induk (module-integration-rules §5.4): ia tidak punya
 * `BusinessModule` sendiri, mewarisi RBAC & entitlement induknya, dan tampil di kanvas Factory Flow
 * level 2 di bawah node induk.
 *
 * @param routePrefixes prefix route server yang dimiliki fitur ini. `RouteOwnershipTest` (server)
 *   gagal bila ada route `/api/tenant/…` yang tidak dimiliki modul maupun fitur mana pun.
 */
data class ModuleFeature(
    val code: String,
    val displayName: String,
    val hostModule: BusinessModule,
    val kind: ModuleFeatureKind,
    val description: String,
    val routePrefixes: List<String> = emptyList()
) {
    init {
        require(hostModule.isOperational) { "Fitur $code harus berinduk modul operasional, bukan ${hostModule.kind}" }
    }
}

/**
 * Daftar fitur dalam modul. **Fitur baru wajib didaftarkan di sini** — itulah yang membuatnya
 * tampil di kanvas level 2 dan lolos `RouteOwnershipTest`.
 */
object ModuleFeatureRegistry {

    val all: List<ModuleFeature> = listOf(
        ModuleFeature(
            code = "stage_frame",
            displayName = "Kerangka Tahap Sampling",
            hostModule = GarmentModules.SAMPLING_ORDER,
            kind = ModuleFeatureKind.STAGE_FRAME,
            description = "Urutan tahap kerja SPK sampling milik pabrik, dari template industri.",
            routePrefixes = listOf("/api/tenant/stage-flow", "/api/tenant/stage-templates")
        ),
        ModuleFeature(
            code = "optional_process",
            displayName = "Proses Sisipan & Tag Fase",
            hostModule = GarmentModules.SAMPLING_ORDER,
            kind = ModuleFeatureKind.OPTIONAL_PROCESS,
            description = "Bordir, sablon, laundry yang disisipkan per desain; tag fase Cuci/Setrika.",
            routePrefixes = listOf("/api/tenant/process-catalog")
        ),
        ModuleFeature(
            code = "sample_storage",
            displayName = "Penyimpanan Sampel",
            hostModule = GarmentModules.SAMPLING_ORDER,
            kind = ModuleFeatureKind.TOOL,
            description = "Kustodi sampel selesai kemas: lokasi rak, penerima simpan, rilis kirim."
        ),
        ModuleFeature(
            code = "production_line",
            displayName = "Stasiun Lini Produksi",
            hostModule = GarmentModules.PRODUCTION_MRP,
            kind = ModuleFeatureKind.STATION,
            description = "Urutan stasiun lini massal (potong, jahit, cuci, steam, QC, kemas).",
            routePrefixes = listOf("/api/tenant/production")
        ),
        ModuleFeature(
            code = "work_queue",
            displayName = "Antrian Kerja & Washing Batch",
            hostModule = GarmentModules.OPERATOR_EXEC,
            kind = ModuleFeatureKind.TOOL,
            description = "Kartu kerja per stasiun dan batch cuci bundle berfoto.",
            routePrefixes = listOf("/api/tenant/work-queue")
        ),
        ModuleFeature(
            code = "traceability",
            displayName = "Telusur & Cetak Kartu",
            hostModule = GarmentModules.OPERATOR_EXEC,
            kind = ModuleFeatureKind.TOOL,
            description = "Pindai QR, kartu SPK A6, jejak kontainer.",
            routePrefixes = listOf("/api/tenant/traceability")
        ),
        ModuleFeature(
            code = "surat_jalan",
            displayName = "Surat Jalan & Transfer",
            hostModule = GarmentModules.FULFILLMENT,
            kind = ModuleFeatureKind.TOOL,
            description = "Manifest perpindahan barang antar gedung dan ke buyer.",
            routePrefixes = listOf("/api/tenant/transfers")
        )
    )

    fun forHost(module: BusinessModule): List<ModuleFeature> = all.filter { it.hostModule == module }

    fun ownerOf(path: String): ModuleFeature? = all.firstOrNull { f -> f.routePrefixes.any { path.startsWith(it) } }
}
