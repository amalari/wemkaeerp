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
    val modes: Map<SackRoute, HandoverMode> = emptyMap()
) {
    /** Rute tanpa entri memakai [HandoverMode.ADMIN_HUB] — perilaku tenant yang sudah jalan. */
    fun modeFor(route: SackRoute): HandoverMode = modes[route] ?: HandoverMode.ADMIN_HUB

    /**
     * Apakah ada satu pun rute yang masih melewati meja admin.
     *
     * Dipakai UI untuk memutuskan menampilkan antrean ACC atau tidak: di pabrik yang seluruh
     * rutenya langsung, penghitung "Menunggu ACC" selamanya nol dan hanya membuat orang
     * menduga ada yang rusak.
     */
    val hasAdminHubRoute: Boolean
        get() = SackRoute.entries.any { modeFor(it) == HandoverMode.ADMIN_HUB }

    /** Rute yang sah untuk wadah yang baru dipindai, dipakai UI untuk meredupkan sisanya. */
    fun routesAccepting(isClosedSack: Boolean): List<SackRoute> =
        SackRoute.entries.filter { isClosedSack || modeFor(it) == HandoverMode.DIRECT }
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
