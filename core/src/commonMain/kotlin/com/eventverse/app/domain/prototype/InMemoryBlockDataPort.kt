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
 * Id baris baru berprefiks stabil `"<entityId>-<n>"`, `n` **monoton** — tidak pernah dipakai ulang
 * selama umur port (bahkan setelah barisnya dihapus) dan tidak bentrok dengan id seed berprefiks
 * sama. Klasifikasi galat: baris tak dikenal = [PortError.NotFound] (padanan 404 kontrak);
 * pelanggaran aturan isi/transisi = [PortError.Validation]. Semuanya berpesan pengguna.
 */
class InMemoryBlockDataPort(
    private val spec: PrototypeSpec,
    private val entityId: String,
    seed: List<PrototypeRow> = emptyList(),
) : BlockDataPort {

    init { requireNotNull(spec.entity(entityId)) { "Entitas '$entityId' tidak ada di spec port ini" } }

    private val mutex = Mutex()
    private var store: PrototypeStore = PrototypeStore.seeded(spec, mapOf(entityId to seed))

    /** Penghitung id monoton; id seed berprefiks sama ikut dihitung supaya tak pernah bentrok. */
    private var lastIdNumber: Int = store.rowsOf(entityId)
        .mapNotNull { it.id.removePrefix("$entityId-").toIntOrNull() }
        .maxOrNull() ?: 0

    override suspend fun load(): Result<List<PrototypeRow>> = guarded { store.rowsOf(entityId) }

    override suspend fun create(values: Map<String, String>): Result<PrototypeRow> = guarded {
        val row = PrototypeRow(nextId(), values)
        store = apply(PrototypeAction.Create(entityId, row), store)
        row
    }

    override suspend fun update(rowId: String, changes: Map<String, String>): Result<PrototypeRow> = guarded {
        rowOrNotFound(rowId)
        var working = store
        changes.forEach { (field, value) ->
            working = apply(PrototypeAction.SetField(entityId, rowId, field, value), working)
        }
        store = working
        working.rowsOf(entityId).first { it.id == rowId }
    }

    override suspend fun delete(rowId: String): Result<Unit> = guarded {
        rowOrNotFound(rowId)
        store = apply(PrototypeAction.Delete(entityId, rowId), store)
        Unit
    }

    /** Baris wajib ada; tidak ada = [PortError.NotFound] (padanan 404 kontrak), bukan Validation. */
    private fun rowOrNotFound(rowId: String): PrototypeRow =
        store.rowsOf(entityId).firstOrNull { it.id == rowId }
            ?: throw PortException(PortError.NotFound("Baris '$rowId' tidak ada di '${spec.entity(entityId)?.label ?: entityId}'."))

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

    private fun nextId(): String = "$entityId-${++lastIdNumber}"
}
