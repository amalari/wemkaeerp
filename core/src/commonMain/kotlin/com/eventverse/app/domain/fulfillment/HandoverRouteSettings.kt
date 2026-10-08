package com.eventverse.app.domain.fulfillment

import com.eventverse.app.domain.tenant.TenantId

/** Satu baris layar konfigurasi: rute, mode efektifnya, dan apakah mode itu **sengaja disetel**. */
data class HandoverRouteSetting(
    val route: HandoverRoute,
    val mode: HandoverMode,
    /** Membedakan "sengaja disetel ADMIN_HUB" dari "belum pernah disentuh"; keduanya berperilaku sama. */
    val isExplicit: Boolean
)

/**
 * Tampilan konfigurasi rute yang dipakai server dan klien (kontrak JSON TRD-FLOW-003 §4.4).
 *
 * Seluruh rute tenant selalu dipancarkan beserta mode efektifnya, supaya UI tidak mengulang aturan
 * *fallback*: rute dikenal tanpa entri = [HandoverMode.ADMIN_HUB] (perilaku tenant yang sudah jalan).
 * Kode di [modes] yang **tidak** dikenal tenant ditolak oleh [of], bukan diabaikan.
 */
data class HandoverRouteSettingsView(
    val tenantId: TenantId,
    val settings: List<HandoverRouteSetting>
) {
    /** Apakah ada rute **aktif** yang masih lewat meja admin; menentukan tampilnya antrean ACC. */
    val hasAdminHubRoute: Boolean
        get() = settings.any { it.route.active && it.mode == HandoverMode.ADMIN_HUB }

    companion object {
        fun of(routes: TenantHandoverRoutes, modes: Map<HandoverRouteCode, HandoverMode>): HandoverRouteSettingsView {
            modes.keys.firstOrNull { routes.find(it) == null }?.let {
                throw IllegalArgumentException("Mode disetel untuk rute '${it.value}' yang tidak dikenal tenant ${routes.tenantId.value}")
            }
            return HandoverRouteSettingsView(
                tenantId = routes.tenantId,
                settings = routes.routes.sortedBy { it.sortOrder }.map { r ->
                    HandoverRouteSetting(r, modes[r.code] ?: HandoverMode.ADMIN_HUB, isExplicit = r.code in modes)
                }
            )
        }
    }
}
