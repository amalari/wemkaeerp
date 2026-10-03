package com.eventverse.app.domain.prototype

/** Satu baris data prototype. [id] stabil — kunci tarik-lepas kartu. */
data class PrototypeRow(val id: String, val values: Map<String, String>) {
    init { require(id.isNotBlank()) { "PrototypeRow.id kosong" } }
    operator fun get(key: String): String = values[key].orEmpty()
}

/**
 * Isi prototype di memori sesi: koleksi baris per entitas. Immutable — mutasi lewat
 * [PrototypeReducer] yang menghasilkan salinan baru. Tidak pernah disimpan ke server.
 */
data class PrototypeStore(val rows: Map<String, List<PrototypeRow>> = emptyMap()) {
    fun rowsOf(entityId: String): List<PrototypeRow> = rows[entityId].orEmpty()

    companion object {
        /** Seed harus lolos skema: baris tak sah menggagalkan pembuatan, bukan diam-diam dilewati. */
        fun seeded(spec: PrototypeSpec, seed: Map<String, List<PrototypeRow>>): PrototypeStore {
            seed.forEach { (entityId, rows) ->
                val entity = requireNotNull(spec.entity(entityId)) { "Seed untuk entitas '$entityId' yang tidak ada" }
                require(rows.map { it.id }.distinct().size == rows.size) { "Seed '$entityId' punya id kembar" }
                rows.forEach { row ->
                    row.values.forEach { (k, v) ->
                        val field = requireNotNull(entity.field(k)) { "Seed '${row.id}': field '$k' tidak ada di '$entityId'" }
                        require(field.accepts(v)) { "Seed '${row.id}': nilai '$v' tidak sah untuk field '$k'" }
                    }
                }
            }
            return PrototypeStore(seed)
        }
    }
}
