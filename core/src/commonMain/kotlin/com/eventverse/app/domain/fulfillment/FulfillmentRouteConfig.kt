package com.eventverse.app.domain.fulfillment

import com.eventverse.app.domain.tenant.TenantId

/**
 * Pola serah terima yang berlaku di tiap rute antar divisi milik satu tenant.
 *
 * Dipilih **per rute**, bukan per tenant, karena satu pabrik bisa memakai dua pola sekaligus:
 * Rajut ke Finishing lewat meja admin karena volumenya besar dan perlu disortir per size,
 * sementara Finishing ke QC cukup diantar operatornya sendiri karena mejanya bersebelahan.
 *
 * ## Kenapa yang tidak terdaftar jatuh ke ADMIN_HUB
 *
 * Bukan karena ADMIN_HUB "lebih aman" secara umum, tapi karena itulah satu-satunya pola yang
 * ada sebelum konfigurasi ini lahir. Tenant yang tidak menyentuh apa pun harus melihat
 * perilaku yang persis sama seperti kemarin — perubahan diam-diam pada disiplin bukti adalah
 * hal terakhir yang boleh terjadi pada modul yang mengawasi kehilangan barang.
 */
data class FulfillmentRouteConfig(
    val tenantId: TenantId,
    /** Mode yang **sengaja disetel**, per kode rute (TRD-FLOW-003 A3). Rute tanpa entri tidak ada di sini. */
    val modes: Map<HandoverRouteCode, HandoverMode> = emptyMap()
) {
    /**
     * Rute dikenal tanpa entri memakai [HandoverMode.ADMIN_HUB] — perilaku tenant yang sudah jalan.
     *
     * Config ini **tidak tahu rute apa saja milik tenant**, jadi kode yang salah ketik pun jatuh ke
     * ADMIN_HUB di sini. Kekakuan "kode tak dikenal = galat" ditegakkan di [validatedAgainst] dan
     * [HandoverRouteSettingsView.of], yang membawa daftar rute tenant.
     */
    fun modeFor(code: HandoverRouteCode): HandoverMode = modes[code] ?: HandoverMode.ADMIN_HUB

    /** Menolak mode yang disetel untuk kode yang tidak dikenal [routes]; bukan mengabaikannya. */
    fun validatedAgainst(routes: TenantHandoverRoutes): FulfillmentRouteConfig = also {
        modes.keys.firstOrNull { routes.find(it) == null }?.let {
            throw IllegalArgumentException("Mode disetel untuk rute '${it.value}' yang tidak dikenal tenant ${tenantId.value}")
        }
    }

    /** Apakah ada rute **aktif** milik tenant yang masih lewat meja admin (menentukan antrean ACC tampil). */
    fun hasAdminHubRoute(routes: TenantHandoverRoutes): Boolean =
        routes.active.any { modeFor(it.code) == HandoverMode.ADMIN_HUB }

    /** Rute aktif yang sah untuk wadah yang baru dipindai, dipakai UI untuk meredupkan sisanya. */
    fun routesAccepting(routes: TenantHandoverRoutes, isClosedSack: Boolean): List<HandoverRoute> =
        routes.active.filter { isClosedSack || modeFor(it.code) == HandoverMode.DIRECT }

    // ── Jembatan Strangler Fig (S0–S2), dihapus di S3 bersama SackRoute ─────────────────────────────

    fun modeFor(route: SackRoute): HandoverMode = modeFor(route.toRouteCode())

    val hasAdminHubRoute: Boolean
        get() = SackRoute.entries.any { modeFor(it) == HandoverMode.ADMIN_HUB }

    fun routesAccepting(isClosedSack: Boolean): List<SackRoute> =
        SackRoute.entries.filter { isClosedSack || modeFor(it) == HandoverMode.DIRECT }

    companion object {
        /** Pemanggil lama yang masih memegang `Map<SackRoute, HandoverMode>`. */
        operator fun invoke(tenantId: TenantId, modes: Map<SackRoute, HandoverMode>): FulfillmentRouteConfig =
            FulfillmentRouteConfig(tenantId, modes.mapKeys { it.key.toRouteCode() })
    }
}

/**
 * Port konfigurasi rute. Mengikuti kontrak [com.eventverse.app.domain.transfer.TenantLocationConfigRepository]:
 * `null` berarti tenant belum pernah menyetel apa pun, bukan kesalahan — pemanggil memakai
 * [FulfillmentRouteConfig] kosong yang seluruh rutenya jatuh ke [HandoverMode.ADMIN_HUB].
 */
interface FulfillmentRouteConfigRepository {
    suspend fun findByTenantId(tenantId: TenantId): FulfillmentRouteConfig?
    suspend fun save(config: FulfillmentRouteConfig)
}
