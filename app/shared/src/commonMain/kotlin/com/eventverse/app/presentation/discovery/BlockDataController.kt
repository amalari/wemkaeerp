package com.eventverse.app.presentation.discovery

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.eventverse.app.domain.prototype.BlockDataPort
import com.eventverse.app.domain.prototype.PortError
import com.eventverse.app.domain.prototype.PortException
import com.eventverse.app.domain.prototype.PrototypeAction
import com.eventverse.app.domain.prototype.PrototypeReducer
import com.eventverse.app.domain.prototype.PrototypeRow
import com.eventverse.app.domain.prototype.PrototypeSpec
import com.eventverse.app.domain.prototype.PrototypeStore
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Fase siklus hidup pemuatan/penyimpanan port data (kebijakan plan induk §2.1).
 */
sealed interface BlockDataPhase {
    data object Loading : BlockDataPhase
    data object Idle : BlockDataPhase
    data object Saving : BlockDataPhase
    data class Error(val message: String) : BlockDataPhase
}

/**
 * Pengontrol data per blok (TRD-PLAT-003, butir A1):
 * - Menjembatani UI blok ke [BlockDataPort] (asinkron, bisa memori atau API).
 * - Menerapkan **kebijakan §2.1**:
 *   - Optimistik + rollback: [move] (pindah kartu kanban / ubah status tabel / centang).
 *   - Pesimistik: [create], [update], [delete].
 * - Pra-validasi di klien menggunakan [PrototypeReducer] untuk pesan instan sebelum memanggil port.
 * - Menjaga pesan galat [PortError.userMessage] selalu berbahasa pengguna.
 * - Diserialkan dengan [Mutex] untuk mencegah balapan / state ganda pada aksi berurutan.
 */
class BlockDataController(
    val port: BlockDataPort,
    initialSpec: PrototypeSpec,
    val entityId: String,
    initialRows: List<PrototypeRow> = emptyList(),
) {
    var spec: PrototypeSpec by mutableStateOf(initialSpec)
        private set

    var rows: List<PrototypeRow> by mutableStateOf(initialRows)
        private set

    var phase: BlockDataPhase by mutableStateOf(if (initialRows.isNotEmpty()) BlockDataPhase.Idle else BlockDataPhase.Loading)
        private set

    var errorMessage: String? by mutableStateOf(null)
        private set

    private val mutex = Mutex()

    /** Memperbarui spesifikasi layar (mis. setelah chat edit) tanpa membuat ulang port. */
    fun updateSpec(newSpec: PrototypeSpec) {
        spec = newSpec
    }

    /** Memuat data dari port. */
    suspend fun load(): Result<List<PrototypeRow>> = mutex.withLock {
        phase = BlockDataPhase.Loading
        errorMessage = null
        val result = port.load()
        result.fold(
            onSuccess = { loaded ->
                rows = loaded
                phase = BlockDataPhase.Idle
                errorMessage = null
                Result.success(loaded)
            },
            onFailure = { err ->
                val msg = formatError(err)
                phase = BlockDataPhase.Error(msg)
                errorMessage = msg
                Result.failure(err)
            }
        )
    }

    /**
     * Tambah baris baru — **pesimistik**: tunggu balasan port/server karena ID dibuat oleh server.
     * Pra-validasi klien dengan reducer untuk feedback instan jika field wajib kosong.
     */
    suspend fun create(values: Map<String, String>): Result<PrototypeRow> = mutex.withLock {
        // Pra-validasi klien (reducer)
        val dummyRow = PrototypeRow("temp-preval", values)
        val dummyStore = PrototypeStore.seeded(spec, mapOf(entityId to rows))
        val prevalResult = PrototypeReducer.reduce(spec, dummyStore, PrototypeAction.Create(entityId, dummyRow))
        if (prevalResult.isFailure) {
            val msg = prevalResult.exceptionOrNull()?.message ?: "Data tidak valid."
            phase = BlockDataPhase.Error(msg)
            errorMessage = msg
            return Result.failure(PortException(PortError.Validation(msg)))
        }

        phase = BlockDataPhase.Saving
        val result = port.create(values)
        return result.fold(
            onSuccess = { created ->
                rows = rows + created
                phase = BlockDataPhase.Idle
                errorMessage = null
                Result.success(created)
            },
            onFailure = { err ->
                val msg = formatError(err)
                phase = BlockDataPhase.Error(msg)
                errorMessage = msg
                Result.failure(err)
            }
        )
    }

    /**
     * Ubah beberapa field sekaligus — **pesimistik**: indikator Saving ditampilkan, baris diperbarui
     * hanya bila port mengonfirmasi keberhasilan.
     */
    suspend fun update(rowId: String, changes: Map<String, String>): Result<PrototypeRow> = mutex.withLock {
        // Pra-validasi klien
        val currentStore = PrototypeStore.seeded(spec, mapOf(entityId to rows))
        var workingStore = currentStore
        for ((field, value) in changes) {
            val step = PrototypeReducer.reduce(spec, workingStore, PrototypeAction.SetField(entityId, rowId, field, value))
            if (step.isFailure) {
                val msg = step.exceptionOrNull()?.message ?: "Perubahan tidak valid."
                phase = BlockDataPhase.Error(msg)
                errorMessage = msg
                return Result.failure(PortException(PortError.Validation(msg)))
            }
            workingStore = step.getOrThrow()
        }

        phase = BlockDataPhase.Saving
        val result = port.update(rowId, changes)
        return result.fold(
            onSuccess = { updated ->
                rows = rows.map { if (it.id == rowId) updated else it }
                phase = BlockDataPhase.Idle
                errorMessage = null
                Result.success(updated)
            },
            onFailure = { err ->
                val msg = formatError(err)
                phase = BlockDataPhase.Error(msg)
                errorMessage = msg
                Result.failure(err)
            }
        )
    }

    /**
     * Pindah status / kolom — **optimistik**: UI langsung berpindah, bila port gagal dilakukan **rollback**.
     */
    suspend fun move(rowId: String, field: String, toValue: String): Result<PrototypeRow> = mutex.withLock {
        // Pra-validasi aturan transisi di klien
        val currentStore = PrototypeStore.seeded(spec, mapOf(entityId to rows))
        val prevalResult = PrototypeReducer.moveCard(spec, currentStore, entityId, rowId, field, toValue)
        if (prevalResult.isFailure) {
            val msg = prevalResult.exceptionOrNull()?.message ?: "Perpindahan tidak diizinkan."
            phase = BlockDataPhase.Error(msg)
            errorMessage = msg
            return Result.failure(PortException(PortError.Validation(msg)))
        }

        // Optimistik: mutasi state Compose langsung
        val previousRows = rows
        val targetRow = rows.firstOrNull { it.id == rowId }
            ?: return Result.failure(PortException(PortError.NotFound("Baris '$rowId' tidak ditemukan.")))

        val optimisticRow = targetRow.copy(values = targetRow.values + (field to toValue))
        rows = rows.map { if (it.id == rowId) optimisticRow else it }
        phase = BlockDataPhase.Saving

        val result = port.update(rowId, mapOf(field to toValue))
        return result.fold(
            onSuccess = { confirmed ->
                rows = rows.map { if (it.id == rowId) confirmed else it }
                phase = BlockDataPhase.Idle
                errorMessage = null
                Result.success(confirmed)
            },
            onFailure = { err ->
                // ROLLBACK ke baris sebelum mutasi
                rows = previousRows
                val msg = formatError(err)
                phase = BlockDataPhase.Error(msg)
                errorMessage = msg
                Result.failure(err)
            }
        )
    }

    /**
     * Hapus baris — **pesimistik**: dipanggil setelah konfirmasi dialog pengguna.
     */
    suspend fun delete(rowId: String): Result<Unit> = mutex.withLock {
        phase = BlockDataPhase.Saving
        val result = port.delete(rowId)
        return result.fold(
            onSuccess = {
                rows = rows.filterNot { it.id == rowId }
                phase = BlockDataPhase.Idle
                errorMessage = null
                Result.success(Unit)
            },
            onFailure = { err ->
                val msg = formatError(err)
                phase = BlockDataPhase.Error(msg)
                errorMessage = msg
                Result.failure(err)
            }
        )
    }

    /** Memasukkan baris langsung secara lokal (mis. hasil siaran form lintas-blok) tanpa memicu create baru. */
    fun insertRowLocally(row: PrototypeRow) {
        if (rows.none { it.id == row.id }) {
            rows = rows + row
        }
    }

    /** Menghapus baris langsung secara lokal (mis. hasil siaran hapus lintas-blok). */
    fun deleteRowLocally(rowId: String) {
        rows = rows.filterNot { it.id == rowId }
    }

    private fun formatError(throwable: Throwable): String {
        return when (throwable) {
            is PortException -> throwable.error.userMessage
            else -> throwable.message ?: "Terjadi kesalahan saat memproses data."
        }
    }
}
