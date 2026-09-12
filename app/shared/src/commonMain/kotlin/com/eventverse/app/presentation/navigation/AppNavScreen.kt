package com.eventverse.app.presentation.navigation

import com.eventverse.app.domain.rbac.BusinessModule

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
     * Null berarti layar tidak dijaga matriks RBAC (login, dan layar administrasi yang justru
     * dipakai untuk memperbaiki matriksnya — mengunci keduanya berarti tidak ada jalan kembali).
     */
    val businessModule: BusinessModule? = null
) {
    ORG_CHART(
        route = "/org-chart",
        title = "Bagan Organisasi",
        aliases = listOf("/orgchart", "/organization", "/bagan-organisasi")
    ),
    DYNAMIC_RBAC(
        route = "/rbac",
        title = "Hak Akses (RBAC)",
        aliases = listOf("/roles", "/hak-akses", "/permissions")
    ),
    FACTORY_FLOW(
        route = "/factory-flow",
        title = "Alur Pabrik (Pipeline)",
        aliases = listOf("/pipeline", "/alur-pabrik", "/flow")
    ),
    // ── Sembilan modul operasional konveksi ──────────────────────────────────────────────────
    CRM_SALES(
        route = "/crm-sales",
        title = "Penjualan & Pelanggan",
        aliases = listOf("/sales", "/crm"),
        businessModule = BusinessModule.CRM_SALES
    ),
    SAMPLING_ORDER(
        route = "/sampling-order",
        title = "Order Sampling",
        aliases = listOf("/sampling", "/sample"),
        businessModule = BusinessModule.SAMPLING_ORDER
    ),
    INVENTORY(
        route = "/inventory",
        title = "Gudang & Bahan Baku",
        aliases = listOf("/gudang", "/stok"),
        businessModule = BusinessModule.INVENTORY
    ),
    TECH_PACK_BOM(
        route = "/tech-pack",
        title = "Tech Pack & BOM",
        aliases = listOf("/techpack", "/bom"),
        businessModule = BusinessModule.TECH_PACK_BOM
    ),
    COSTING_HPP(
        route = "/costing-hpp",
        title = "Kalkulasi HPP",
        aliases = listOf("/hpp", "/costing"),
        businessModule = BusinessModule.COSTING_HPP
    ),
    PRODUCTION_MRP(
        route = "/production-mrp",
        title = "Jadwal Produksi (MRP)",
        aliases = listOf("/produksi", "/mrp"),
        businessModule = BusinessModule.PRODUCTION_MRP
    ),
    OPERATOR_EXEC(
        route = "/operator-exec",
        title = "Lantai Produksi",
        aliases = listOf("/operator", "/shopfloor"),
        businessModule = BusinessModule.OPERATOR_EXEC
    ),
    QUALITY_CONTROL(
        route = "/quality-control",
        title = "Quality Control",
        aliases = listOf("/qc", "/kualitas"),
        businessModule = BusinessModule.QUALITY_CONTROL
    ),
    FULFILLMENT(
        route = "/fulfillment",
        title = "Packing & Pengiriman",
        aliases = listOf("/packing", "/pengiriman"),
        businessModule = BusinessModule.FULFILLMENT
    ),

    LOGIN(
        route = "/login",
        title = "Login Akun",
        aliases = listOf("/masuk")
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

            return entries.firstOrNull { screen ->
                screen.route.equals(normalized, ignoreCase = true) ||
                    screen.aliases.any { alias -> alias.equals(normalized, ignoreCase = true) }
            }
        }
    }
}
