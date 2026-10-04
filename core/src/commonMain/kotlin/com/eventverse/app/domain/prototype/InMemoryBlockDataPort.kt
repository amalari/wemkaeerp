package com.eventverse.app.domain.prototype

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * [BlockDataPort] di atas [PrototypeStore] memori (demo garment; kontrak plan induk §3.1). Lahir
 * dari seed yang **divalidasi skema** ([PrototypeStore.seeded] — seed tak sah menggagalkan
 * pembuatan, bukan dilewati). Semua operasi lewat [PrototypeReducer], sehingga aturan field wajib,
 * nilai ENUM, dan mesin status identik dengan validasi layar — satu sumber kebenaran. `update`
 * atomik karena store immutable dan hanya dikomit bila seluruh perubahan sah. Operasi diserialkan
 * lewat [Mutex] — tidak ada balapan antar-call.
 *
 * Id baris baru berprefiks stabil `"<entityId>-<n>"` dengan `n` terkecil yang belum terpakai —
 * deterministik, unik, tanpa jam.
 */
class InMemoryBlockDataPort(
    private val spec: PrototypeSpec,
    private val entityId: String,
    seed: List<PrototypeRow> = emptyList(),
) : BlockDataPort {

    init { requireNotNull(spec.entity(entityId)) { "Entitas '$entityId' tidak ada di spec port ini" } }

    private val mutex = Mutex()
    private var store: PrototypeStore = PrototypeStore.seeded(spec, mapOf(entityId to seed))

    override suspend fun load(): Result<List<PrototypeRow>> = guarded { store.rowsOf(entityId) }

    override suspend fun create(values: Map<String, String>): Result<PrototypeRow> = guarded {
        val row = PrototypeRow(nextId(), values)
        store = apply(PrototypeAction.Create(entityId, row), store)
        row
    }

    override suspend fun update(rowId: String, changes: Map<String, String>): Result<PrototypeRow> = guarded {
        require(store.rowsOf(entityId).any { it.id == rowId }) { "Baris '$rowId' tidak ada" }
        var working = store
        changes.forEach { (field, value) ->
            working = apply(PrototypeAction.SetField(entityId, rowId, field, value), working)
        }
        store = working
        working.rowsOf(entityId).first { it.id == rowId }
    }

    override suspend fun delete(rowId: String): Result<Unit> = guarded {
        store = apply(PrototypeAction.Delete(entityId, rowId), store)
        Unit
    }

    /** Penolakan reducer dibungkus [PortException] ber-[PortError.Validation] berpesan pengguna. */
    private fun apply(action: PrototypeAction, from: PrototypeStore): PrototypeStore =
        PrototypeReducer.reduce(spec, from, action).getOrElse { e ->
            throw PortException(PortError.Validation(e.message ?: "Perubahan ditolak."))
        }

    private suspend fun <T> guarded(block: () -> T): Result<T> = try {
        mutex.withLock { Result.success(block()) }
    } catch (e: PortException) {
        Result.failure(e)
    } catch (e: IllegalArgumentException) {
        Result.failure(PortException(PortError.Validation(e.message ?: "Perubahan ditolak.")))
    }

    private fun nextId(): String {
        val prefix = "$entityId-"
        val used = store.rowsOf(entityId).mapTo(mutableSetOf()) { it.id }
        var n = 1
        while (prefix + n in used) n++
        return prefix + n
    }
}
