package com.eventverse.app.domain.fulfillment

import com.eventverse.app.domain.tenant.TenantId

/**
 * Port daftar rute per tenant. Mengikuti kontrak [FulfillmentRouteConfigRepository]: `null` berarti
 * tenant belum pernah menyimpan apa pun, bukan kesalahan — pemanggil memakai
 * [TenantHandoverRoutes.resolve] dengan template pack.
 */
interface HandoverRouteRepository {
    suspend fun findByTenantId(tenantId: TenantId): TenantHandoverRoutes?

    /** Mengganti seluruh daftar tenant. Penghapusan rute yang pernah dipakai ditolak (gunakan nonaktif). */
    suspend fun save(routes: TenantHandoverRoutes)

    /** Kode rute yang dipakai paling sedikit satu perjalanan karung; tidak boleh dihapus, hanya dinonaktifkan. */
    suspend fun codesInUse(tenantId: TenantId): Set<HandoverRouteCode>
}
