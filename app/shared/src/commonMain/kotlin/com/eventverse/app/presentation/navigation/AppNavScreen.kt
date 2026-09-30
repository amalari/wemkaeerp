package com.eventverse.app.presentation.navigation

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

import androidx.compose.runtime.compositionLocalOf
import com.eventverse.app.domain.rbac.BusinessModule

/**
 * CompositionLocal untuk aksi navigasi aplikasi antar modul.
 */
val LocalAppNavigator = compositionLocalOf<(AppNavScreen) -> Unit> { {} }

/**
 * Typed application navigation routes with canonical paths, titles, and URI aliases.
 * Supports direct deep-linking and browser reload on each route.
 */
enum class AppNavScreen(
    val route: String,
    val title: String,
    val aliases: List<String> = emptyList(),
    /**
     * Modul bisnis yang menjadi gerbang wewenang layar ini.
     *
     * Null kini hanya untuk layar yang memang berada di luar tenant (login). Ketiga layar tata kelola
     * dulu juga null dengan alasan "dipakai untuk memperbaiki matriksnya sendiri"; alasan itu kini
     * ditangani dua lapis proteksi anti-lockout di lapisan domain — bypass Owner pada
     * `AccessDecisionEngine` dan penguncian jabatan Owner pada `CustomRole.updateModuleAccess` —
     * sehingga layarnya tidak perlu lagi dikecualikan dari matriks.
     */
    val businessModule: BusinessModule? = null,
    val isNavMenuItem: Boolean = true
) {
    ORG_CHART(
        route = "/org-chart",
        title = "Bagan Organisasi",
        aliases = listOf("/orgchart", "/organization", "/bagan-organisasi"),
        businessModule = GarmentModules.ORG_CHART
    ),
    DYNAMIC_RBAC(
        route = "/rbac",
        title = "Hak Akses (RBAC)",
        aliases = listOf("/roles", "/hak-akses", "/permissions"),
        businessModule = GarmentModules.DYNAMIC_RBAC
    ),
    FACTORY_FLOW(
        route = "/factory-flow",
        title = "Alur Pabrik (Pipeline)",
        aliases = listOf("/pipeline", "/alur-pabrik", "/flow"),
        businessModule = GarmentModules.FACTORY_FLOW
    ),
    // ── Sembilan modul operasional konveksi ──────────────────────────────────────────────────
    CRM_SALES(
        route = "/crm-sales",
        title = "Penjualan & Pelanggan",
        aliases = listOf("/sales", "/crm"),
        businessModule = GarmentModules.CRM_SALES
    ),
    SAMPLING_ORDER(
        route = "/sampling-order",
        title = "Order Sampling",
        aliases = listOf("/sampling", "/sample"),
        businessModule = GarmentModules.SAMPLING_ORDER
    ),
    INVENTORY(
        route = "/inventory",
        title = "Gudang & Bahan Baku",
        aliases = listOf("/gudang", "/stok"),
        businessModule = GarmentModules.INVENTORY
    ),
    TECH_PACK_BOM(
        route = "/tech-pack",
        title = "Tech Pack & BOM",
        aliases = listOf("/techpack", "/bom"),
        businessModule = GarmentModules.TECH_PACK_BOM
    ),
    COSTING_HPP(
        route = "/costing-hpp",
        title = "Kalkulasi HPP",
        aliases = listOf("/hpp", "/costing"),
        businessModule = GarmentModules.COSTING_HPP
    ),
    PRODUCTION_MRP(
        route = "/production-mrp",
        title = "Jadwal Produksi (MRP)",
        aliases = listOf("/produksi", "/mrp"),
        businessModule = GarmentModules.PRODUCTION_MRP
    ),
    OPERATOR_EXEC(
        route = "/operator-exec",
        title = "Lantai Produksi",
        aliases = listOf("/operator", "/shopfloor"),
        businessModule = GarmentModules.OPERATOR_EXEC
    ),
    TRACEABILITY(
        route = "/telusur",
        title = "Telusur Bundel & Karung",
        aliases = listOf("/trace", "/telusur-qr"),
        // Bergerbang pada modul lantai produksi yang sudah ada, bukan BusinessModule baru:
        // menambah nilai enum merembet ke matriks RBAC, penugasan divisi, entitlement, dan seed
        // tiap tenant — biaya besar untuk satu layar.
        businessModule = GarmentModules.OPERATOR_EXEC
    ),
    QUALITY_CONTROL(
        route = "/quality-control",
        title = "Quality Control",
        aliases = listOf("/qc", "/kualitas"),
        businessModule = GarmentModules.QUALITY_CONTROL
    ),
    FULFILLMENT(
        route = "/fulfillment",
        title = "Packing & Pengiriman",
        aliases = listOf("/packing", "/pengiriman"),
        businessModule = GarmentModules.FULFILLMENT
    ),
    SURAT_JALAN(
        route = "/surat-jalan",
        title = "Surat Jalan & Transfer",
        aliases = listOf("/transfer", "/makloon-transfer", "/sj"),
        businessModule = GarmentModules.FULFILLMENT,
        isNavMenuItem = false
    ),
    MASTER_DATA(
        route = "/master-data",
        title = "Master Data Bahan & Harga",
        aliases = listOf("/materials", "/bahan", "/masterdata"),
        businessModule = GarmentModules.MASTER_DATA
    ),
    VENDOR_CONTACTS(
        route = "/vendors",
        title = "Kontak Vendor & Makloon",
        aliases = listOf("/vendor", "/kontak-vendor", "/makloon-vendor"),
        businessModule = GarmentModules.VENDOR_CONTACTS
    ),
    INVOICING(
        route = "/invoicing",
        title = "Invoice & Penagihan",
        aliases = listOf("/invoice", "/tagihan", "/faktur"),
        businessModule = GarmentModules.INVOICING
    ),
    INVOICING_TEMPLATES(
        route = "/invoicing/templates",
        title = "Template & Desain Faktur",
        aliases = listOf("/invoicing/design", "/invoicing/template", "/invoicing/layouts"),
        businessModule = GarmentModules.INVOICING,
        isNavMenuItem = false
    ),

    LOGIN(
        route = "/login",
        title = "Login Akun",
        aliases = listOf("/masuk")
    ),

    /**
     * R16/Fase D: funnel discovery ("Studio Discovery"). Bukan `BusinessModule` — datanya draf
     * prospek per pengguna, bukan aset tenant yang di-RBAC per jabatan; gerbang layarnya sesi +
     * kehadiran keputusan wewenang (diperiksa di cabang `App.kt`). `isNavMenuItem = false` karena
     * tidak masuk matriks modul; baris drawer-nya ditambahkan terpisah di `App.kt`.
     */
    DISCOVERY(
        route = "/discovery",
        title = "Studio Discovery",
        aliases = listOf("/studio-discovery", "/discovery-studio"),
        isNavMenuItem = false
    ),

    /**
     * Fase C: Studio pola prototype — alat **internal**, bukan funnel prospek. Gerbang sesinya sama
     * dengan [DISCOVERY] (keduanya platform, bukan modul); yang membedakan hanya wewenang menulis,
     * dan itu ditentukan server (`POST /api/discovery/patterns` menolak non-superadmin).
     *
     * Rutenya sengaja di bawah `/discovery/` supaya `fromPath` memilihnya lewat pencocokan prefiks
     * terpanjang, bukan lewat daftar alias.
     */
    DISCOVERY_STUDIO(
        route = "/discovery/studio",
        title = "Studio Pola Prototipe",
        aliases = listOf("/studio-pola", "/discovery/patterns"),
        isNavMenuItem = false
    ),

    /**
     * Fase E: buku demand — antrean review platform (superadmin saja di server). Gerbang sesinya
     * sama dengan [DISCOVERY]; gerbang baca superadmin dijelaskan layar, bukan disembunyikan.
     */
    DISCOVERY_DEMANDS(
        route = "/discovery/demands",
        title = "Buku Demand",
        aliases = listOf(),
        isNavMenuItem = false
    ),

    /**
     * Rute generik `/m/{code}` (B6f) untuk modul pack yang belum punya layar khusus. Gerbangnya modul dari path,
     * bukan field ini — satu entri melayani semua modul data-only.
     */
    MODULE(
        route = "/m",
        title = "Modul",
        isNavMenuItem = false
    );

    val isProtected: Boolean
        get() = this != LOGIN

    companion object {
        /**
         * Resolves the matching [AppNavScreen] from a browser URL path or hash string.
         * Returns null if path is root ("/") or unrecognized.
         */
        fun fromPath(rawPath: String): AppNavScreen? {
            val trimmed = rawPath.trim()
            // Strip hash prefix if routing with hash (#/rbac or #rbac)
            val withoutHash = if (trimmed.startsWith("#")) {
                trimmed.removePrefix("#").removePrefix("/")
            } else {
                trimmed
            }

            // Strip query parameters (?foo=bar) and trailing slashes
            val normalized = withoutHash
                .substringBefore("?")
                .substringBefore("#")
                .removeSuffix("/")
                .let { if (it.isEmpty() || it == "/") "/" else if (it.startsWith("/")) it else "/$it" }

            if (normalized == "/") return null

            val exactMatch = entries.firstOrNull { screen ->
                screen.route.equals(normalized, ignoreCase = true) ||
                    screen.aliases.any { alias -> alias.equals(normalized, ignoreCase = true) }
            }
            if (exactMatch != null) return exactMatch

            // Match sub-routes by prefix (e.g. /invoicing/templates/tpl-123 -> INVOICING_TEMPLATES)
            // Urutkan berdasarkan panjang rute menurun agar sub-rute lebih spesifik menang lebih dulu.
            return entries
                .sortedByDescending { it.route.length }
                .firstOrNull { screen ->
                    screen.route != "/" && (
                        normalized.startsWith("${screen.route}/", ignoreCase = true) ||
                            screen.aliases.any { alias -> normalized.startsWith("$alias/", ignoreCase = true) }
                    )
                }
        }

        /**
         * Mengambil ID template dari URL path (baik dari subpath /invoicing/templates/{id}
         * ataupun query param ?templateId={id} atau ?id={id}).
         */
        fun extractTemplateId(rawPath: String): String? {
            val trimmed = rawPath.trim().removePrefix("#").removePrefix("/")
            val queryPart = trimmed.substringAfter("?", "")
            if (queryPart.isNotEmpty()) {
                val params = queryPart.split("&").associate {
                    val parts = it.split("=", limit = 2)
                    parts[0].lowercase() to (parts.getOrNull(1) ?: "")
                }
                params["templateid"]?.takeIf { it.isNotBlank() }?.let { return it }
                params["id"]?.takeIf { it.isNotBlank() }?.let { return it }
            }

            val pathOnly = trimmed.substringBefore("?").removeSuffix("/")
            val prefixes = listOf("invoicing/templates/", "invoicing/design/", "invoicing/template/", "invoicing/layouts/")
            for (prefix in prefixes) {
                if (pathOnly.startsWith(prefix, ignoreCase = true)) {
                    val candidate = pathOnly.substring(prefix.length).trim()
                    if (candidate.isNotBlank() && !candidate.contains("/")) {
                        return candidate
                    }
                }
            }
            return null
        }
    }
}
