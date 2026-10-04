package com.eventverse.app.domain.prototype

/**
 * Kegagalan operasi port data (kontrak plan induk §3.1). Kosakata tertutup milik sistem:
 * [Validation] = isi/aturan ditolak (reducer atau HTTP 400/422), [Forbidden] = tidak berwenang
 * (401/403), [NotFound] = baris/basis tidak ada (404), [Unavailable] = jaringan/server padam (5xx).
 * [PortError.userMessage]-nya selalu berbahasa pengguna (kebijakan plan induk §2.1); pesan teknis
 * tetap tersimpan di field `message` tiap varian untuk log.
 */
sealed interface PortError {
    data class Validation(val message: String) : PortError
    data class Forbidden(val message: String) : PortError
    data class NotFound(val message: String) : PortError
    data class Unavailable(val message: String) : PortError

    /** Pesan siap-tampil ke pengguna. */
    val userMessage: String
        get() = when (this) {
            is Validation -> message
            is Forbidden -> if (message.isBlank()) "Anda tidak berwenang melakukan perubahan ini." else "Anda tidak berwenang: $message"
            is NotFound -> if (message.isBlank()) "Data tidak ditemukan." else message
            is Unavailable -> if (message.isBlank()) "Gagal terhubung ke server. Coba lagi sebentar." else "Gagal terhubung ke server: $message"
        }
}

/** [Exception] pembawa [PortError]; [message]-nya sudah siap tampil ke pengguna. */
class PortException(val error: PortError) : Exception(error.userMessage)

/**
 * Port data satu blok: **satu port = satu entitas** (satu kumpulan baris). Semua operasi `suspend`
 * dan mengembalikan [Result]; kegagalan selalu `Result.failure(PortException)` — tidak ada
 * pengecualian mentah yang bocor ke pemanggil. Implementasi: [InMemoryBlockDataPort] (demo memori)
 * dan kelak `ApiBlockDataPort` (klien HTTP, milik jalur C).
 *
 * Kontrak `update`: beberapa field diterapkan **atomik** — satu field melanggar aturan berarti
 * tidak ada yang berubah — dengan aturan yang sama persis dengan [PrototypeReducer] (satu sumber
 * kebenaran validasi, kebijakan plan induk §2.1).
 */
interface BlockDataPort {
    suspend fun load(): Result<List<PrototypeRow>>

    /** Baris baru; **id dibuat implementasi** (server/port), bukan pemanggil. */
    suspend fun create(values: Map<String, String>): Result<PrototypeRow>

    /** Ubah beberapa field sekaligus, atomik. */
    suspend fun update(rowId: String, changes: Map<String, String>): Result<PrototypeRow>

    suspend fun delete(rowId: String): Result<Unit>
}
