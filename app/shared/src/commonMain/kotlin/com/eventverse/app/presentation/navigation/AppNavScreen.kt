package com.eventverse.app.presentation.navigation

/**
 * Typed application navigation routes with canonical paths, titles, and URI aliases.
 * Supports direct deep-linking and browser reload on each route.
 */
enum class AppNavScreen(
    val route: String,
    val title: String,
    val aliases: List<String> = emptyList()
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
