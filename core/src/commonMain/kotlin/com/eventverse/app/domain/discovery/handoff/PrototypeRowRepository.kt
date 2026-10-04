package com.eventverse.app.domain.discovery.handoff

import com.eventverse.app.domain.prototype.PrototypeRow
import com.eventverse.app.domain.tenant.TenantId

/**
 * Port penyimpanan baris modul hasil handoff: satu implementasi Postgres per tabel (digenerate
 * [SpecScaffoldGenerator]), satu implementasi memori untuk test. Selalu **per tenant** — tidak ada
 * operasi lintas tenant, jadi isolasi tidak bergantung pada ingatan pemanggil (RLS tetap lapis kedua).
 */
interface PrototypeRowRepository {
    suspend fun list(tenantId: TenantId): List<PrototypeRow>
    suspend fun find(tenantId: TenantId, id: String): PrototypeRow?
    /** Tambah atau ganti baris berkunci [PrototypeRow.id]. */
    suspend fun save(tenantId: TenantId, row: PrototypeRow)
    /** `true` bila baris ada dan terhapus. */
    suspend fun delete(tenantId: TenantId, id: String): Boolean
}

/** Implementasi memori (test). Urutan penyisipan terjaga; data tiap tenant terpisah. */
class InMemoryPrototypeRowRepository : PrototypeRowRepository {
    private val data = LinkedHashMap<TenantId, LinkedHashMap<String, PrototypeRow>>()

    override suspend fun list(tenantId: TenantId) = data[tenantId]?.values?.toList().orEmpty()
    override suspend fun find(tenantId: TenantId, id: String) = data[tenantId]?.get(id)
    override suspend fun save(tenantId: TenantId, row: PrototypeRow) { data.getOrPut(tenantId) { LinkedHashMap() }[row.id] = row }
    override suspend fun delete(tenantId: TenantId, id: String) = data[tenantId]?.remove(id) != null
}
