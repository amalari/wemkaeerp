package com.eventverse.app.domain.prototype

/** Aksi pengguna terhadap prototype. Kosakata tertutup sistem. */
sealed interface PrototypeAction {
    data class SetField(val entityId: String, val rowId: String, val field: String, val value: String) : PrototypeAction
    data class Create(val entityId: String, val row: PrototypeRow) : PrototypeAction
    /** Kontrak v1: hapus satu baris. Baris lain tidak tersentuh. */
    data class Delete(val entityId: String, val rowId: String) : PrototypeAction
}

/**
 * Reducer murni: (spec, store, aksi) → store baru, atau kegagalan ber-pesan yang layak ditampilkan
 * ke pengguna. Pelanggaran transisi/skema **ditolak**, tidak dibulatkan diam-diam.
 */
object PrototypeReducer {

    fun reduce(spec: PrototypeSpec, store: PrototypeStore, action: PrototypeAction): Result<PrototypeStore> =
        runCatching {
            when (action) {
                is PrototypeAction.SetField -> setField(spec, store, action)
                is PrototypeAction.Create -> create(spec, store, action)
                is PrototypeAction.Delete -> delete(spec, store, action)
            }
        }

    /** Pintasan kanban: pindah kartu = ubah field kelompoknya. */
    fun moveCard(spec: PrototypeSpec, store: PrototypeStore, entityId: String, rowId: String, field: String, to: String) =
        reduce(spec, store, PrototypeAction.SetField(entityId, rowId, field, to))

    private fun setField(spec: PrototypeSpec, store: PrototypeStore, a: PrototypeAction.SetField): PrototypeStore {
        val entity = requireNotNull(spec.entity(a.entityId)) { "Entitas '${a.entityId}' tidak dikenal" }
        val field = requireNotNull(entity.field(a.field)) { "Field '${a.field}' tidak ada di '${entity.label}'" }
        require(field.accepts(a.value)) { "Nilai '${a.value}' tidak sah untuk '${field.label}'" }
        require(!field.required || a.value.isNotBlank()) { "'${field.label}' wajib diisi" }
        val row = requireNotNull(store.rowsOf(a.entityId).firstOrNull { it.id == a.rowId }) { "Baris '${a.rowId}' tidak ada" }
        val machine = entity.stateMachine?.takeIf { it.field == a.field }
        if (machine != null) {
            val from = row[a.field]
            require(machine.allows(from, a.value)) { "'${field.label}' tidak boleh pindah dari $from ke ${a.value}" }
        }
        val updated = row.copy(values = row.values + (a.field to a.value))
        return store.copy(rows = store.rows + (a.entityId to store.rowsOf(a.entityId).map { if (it.id == a.rowId) updated else it }))
    }

    private fun create(spec: PrototypeSpec, store: PrototypeStore, a: PrototypeAction.Create): PrototypeStore {
        val entity = requireNotNull(spec.entity(a.entityId)) { "Entitas '${a.entityId}' tidak dikenal" }
        require(store.rowsOf(a.entityId).none { it.id == a.row.id }) { "Id '${a.row.id}' sudah dipakai" }
        a.row.values.forEach { (k, v) ->
            val field = requireNotNull(entity.field(k)) { "Field '$k' tidak ada di '${entity.label}'" }
            require(field.accepts(v)) { "Nilai '$v' tidak sah untuk '${field.label}'" }
        }
        entity.fields.filter { it.required }.forEach { f ->
            require(a.row[f.key].isNotBlank()) { "'${f.label}' wajib diisi" }
        }
        return store.copy(rows = store.rows + (a.entityId to store.rowsOf(a.entityId) + a.row))
    }

    private fun delete(spec: PrototypeSpec, store: PrototypeStore, a: PrototypeAction.Delete): PrototypeStore {
        val entity = requireNotNull(spec.entity(a.entityId)) { "Entitas '${a.entityId}' tidak dikenal" }
        require(store.rowsOf(a.entityId).any { it.id == a.rowId }) { "Baris '${a.rowId}' tidak ada di '${entity.label}'" }
        return store.copy(rows = store.rows + (a.entityId to store.rowsOf(a.entityId).filterNot { it.id == a.rowId }))
    }
}
