package com.eventverse.app.domain.transfer

/**
 * Port penyimpanan konfigurasi lokasi tenant.
 *
 * [findByTenantId] mengembalikan `null` untuk tenant yang belum pernah menyetel lokasinya sama
 * sekali. Pemanggil memperlakukan itu sebagai "tidak ada perpindahan", bukan sebagai kesalahan
 * — gagal terbuka, supaya pabrik yang belum sempat mengisi pemetaan tidak terkunci.
 */
interface TenantLocationConfigRepository {
    suspend fun findByTenantId(tenantId: String): TenantLocationConfig?
    suspend fun save(config: TenantLocationConfig)
}
