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
