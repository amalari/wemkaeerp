package com.eventverse.app.domain.prototype

/**
 * Port pengusul operasi dari bahasa biasa (kontrak v1). Implementasi **diasumsikan kadang salah**
 * (terutama yang berbasis LLM): keluarannya hanya usulan dan wajib melewati [SpecOpApplier];
 * port ini sendiri tidak pernah mengubah spec. Murni domain — tanpa HTTP/framework.
 */
interface SpecOpProposer {
    /** Maksimal [SpecOpApplier.MAX_OPS_PER_TURN] operasi; kalimat tak dikenal = `Result.failure` berpesan, bukan tebakan. */
    suspend fun propose(message: String, screen: InteractiveScreen): Result<List<SpecOp>>
}

/**
 * Pengusul berbasis kata kunci Indonesia, tanpa LLM/kunci API.
 *
 * **KERANGKA (B0):** belum mengenali kalimat apa pun; butir B5 mengisinya. Pesan galat sudah memuat
 * contoh kalimat yang akan didukung, supaya UI/endpoint bisa menampilkannya sejak sekarang.
 */
class DeterministicSpecOpProposer : SpecOpProposer {
    override suspend fun propose(message: String, screen: InteractiveScreen): Result<List<SpecOp>> =
        Result.failure(IllegalArgumentException("Belum bisa memahami permintaan itu. Contoh: \"tambah status Revisi setelah Dikerjakan\"."))
}
