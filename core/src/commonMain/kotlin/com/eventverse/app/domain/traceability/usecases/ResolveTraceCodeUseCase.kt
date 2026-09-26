package com.eventverse.app.domain.traceability.usecases

import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.domain.traceability.*

/**
 * Hasil pemindaian satu kartu: apa yang diketahui dari kodenya sendiri, dan apa yang sudah tercatat.
 *
 * [container] null berarti kartu sah tapi belum pernah disentuh — bukan kesalahan, melainkan keadaan
 * normal sebuah kartu pra-cetak yang baru diambil dari tumpukan.
 */
data class TraceScanResult(
    val code: TraceCode,
    val parts: TraceCodeParts,
    val snapshot: TraceWorkOrderSnapshot?,
    val container: TraceContainer?,
    val sizeLabel: String
) {
    val isNewCard: Boolean get() = container == null
}

class ResolveTraceCodeUseCase(
    private val containers: TraceContainerRepository,
    private val workOrders: TraceWorkOrderProvider
) {
    suspend operator fun invoke(tenantId: TenantId, rawPayload: String): Result<TraceScanResult> = runCatching {
        val code = TraceCodec.fromScanPayload(rawPayload)
            ?: error("Kode tidak dikenali. Periksa lagi ketikannya, atau kartu ini bukan kartu telusur WeMade.")
        val parts = TraceCodec.parse(code.value) ?: error("Kode gagal diurai: ${TraceCodec.grouped(code)}")

        require(parts.version == TraceCodec.CURRENT_VERSION) {
            "Kartu ini memakai format kode versi ${parts.version}; aplikasi ini mengerti versi ${TraceCodec.CURRENT_VERSION}."
        }

        val tenantOrdinal = containers.tenantOrdinal(tenantId)
        // Ordinal tenant di dalam kode TIDAK dipakai untuk memilih tenant — tenant selalu datang dari
        // sesi. Ia hanya dicocokkan, supaya kartu milik pabrik lain ditolak alih-alih diam-diam
        // resolve ke SPK bernomor sama milik pabrik ini.
        require(parts.tenantOrdinal == tenantOrdinal) {
            "Kartu ${TraceCodec.grouped(code)} bukan milik pabrik ini."
        }

        val existing = containers.findByCode(tenantId, code)
        val ref = existing?.workOrder
            ?: containers.findWorkOrderByOrdinal(tenantId, parts.workOrderOrdinal, parts.workOrderKind)
            ?: error("SPK untuk kartu ${TraceCodec.grouped(code)} tidak ditemukan.")

        val snapshot = workOrders.snapshot(tenantId, ref)
        val sizeLabel = existing?.sizeLabel
            ?: snapshot?.sizeAt(parts.sizeIndex)?.sizeLabel
            ?: error("Size ke-${parts.sizeIndex + 1} tidak ada lagi pada SPK ini.")

        TraceScanResult(code, parts, snapshot, existing, sizeLabel)
    }
}
