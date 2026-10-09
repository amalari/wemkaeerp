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

    /**
     * Baris yang cocok [query] (cocok nilai sel mana pun atau [PrototypeRow.id]), maksimum [limit].
     * Dipakai pencarian opsi `RELATION` (TRD-FIELD-001 FR-4) supaya jalur ini tidak perlu menarik
     * seluruh tabel. Implementasi default memfilter [list] di memori agar setiap implementasi tetap
     * sah; implementasi Postgres **dianjurkan menekan** predicate + `LIMIT` ke SQL (jalur rujukan
     * tidak boleh memuat seluruh tabel tiap ketikan).
     */
    suspend fun search(tenantId: TenantId, query: String, limit: Int): List<PrototypeRow> =
        list(tenantId).asSequence()
            .filter { row ->
                query.isBlank() ||
                    row.id.contains(query, ignoreCase = true) ||
                    row.values.values.any { it.contains(query, ignoreCase = true) }
            }
            .take(limit)
            .toList()
}

/** Implementasi memori (test). Urutan penyisipan terjaga; data tiap tenant terpisah. */
class InMemoryPrototypeRowRepository : PrototypeRowRepository {
    private val data = LinkedHashMap<TenantId, LinkedHashMap<String, PrototypeRow>>()

    override suspend fun list(tenantId: TenantId) = data[tenantId]?.values?.toList().orEmpty()
    override suspend fun find(tenantId: TenantId, id: String) = data[tenantId]?.get(id)
    override suspend fun save(tenantId: TenantId, row: PrototypeRow) { data.getOrPut(tenantId) { LinkedHashMap() }[row.id] = row }
    override suspend fun delete(tenantId: TenantId, id: String) = data[tenantId]?.remove(id) != null
}
