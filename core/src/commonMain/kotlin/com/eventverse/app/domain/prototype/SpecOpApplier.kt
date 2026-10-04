package com.eventverse.app.domain.prototype

import com.eventverse.app.domain.discovery.brief.CaptureEntry

/** Hasil satu giliran: layar akhir + log tiap operasi (sah maupun ditolak) untuk brief. */
data class AppliedOps(val screen: InteractiveScreen, val log: List<CaptureEntry>)

/**
 * Menerapkan [SpecOp] pada [InteractiveScreen] (kontrak v1). Murni, tanpa jam: waktu dikirim pemanggil.
 *
 * **KERANGKA (B0):** [apply] belum mengerjakan operasi apa pun dan selalu `Result.failure` berpesan;
 * butir B3 mengisinya. Yang sudah berlaku dan tidak boleh berubah: [applyAll] menerapkan operasi satu
 * per satu (yang gagal tidak membatalkan yang sah), membatasi [MAX_OPS_PER_TURN] per giliran, dan
 * mencatat semuanya.
 */
object SpecOpApplier {
    const val MAX_OPS_PER_TURN = 5

    fun apply(screen: InteractiveScreen, op: SpecOp): Result<InteractiveScreen> =
        Result.failure(IllegalStateException("Operasi '${op::class.simpleName}' belum didukung."))

    fun applyAll(screen: InteractiveScreen, ops: List<SpecOp>, at: String): AppliedOps {
        var current = screen
        val log = ops.mapIndexed { i, op ->
            if (i >= MAX_OPS_PER_TURN) {
                CaptureEntry(at, op, ok = false, message = "Dibatasi $MAX_OPS_PER_TURN perubahan per permintaan.")
            } else {
                apply(current, op).fold(
                    onSuccess = { next -> current = next; CaptureEntry(at, op, ok = true, message = null) },
                    onFailure = { e -> CaptureEntry(at, op, ok = false, message = e.message ?: "Operasi ditolak.") }
                )
            }
        }
        return AppliedOps(current, log)
    }
}
