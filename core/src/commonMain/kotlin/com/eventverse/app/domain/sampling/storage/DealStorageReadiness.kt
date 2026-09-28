package com.eventverse.app.domain.sampling.storage

import com.eventverse.app.domain.sampling.ExitStages
import com.eventverse.app.domain.sampling.SamplingOrder
import com.eventverse.app.domain.sampling.SamplingStatus
import com.eventverse.app.domain.sampling.positionOf

/**
 * Seberapa lengkap barang satu deal di penyimpanan — dasar keputusan "kirim sekarang atau
 * tunggu". Deal yang PO-nya terpecah (per ukuran, per desain) dikirim sekaligus; mengirim
 * yang sudah jadi duluan adalah keputusan sadar yang wajib beralasan, bukan kebetulan.
 *
 * SPK yang sudah lewat penyimpanan (dikirim, di-ACC) dihitung siap: barangnya memang tidak
 * perlu ditunggu lagi. SPK yang dibatalkan tidak dihitung sama sekali.
 */
data class DealStorageReadiness(
    val ready: List<SamplingOrder>,
    val pending: List<SamplingOrder>
) {
    val isComplete: Boolean get() = pending.isEmpty()
    val total: Int get() = ready.size + pending.size
}

fun dealStorageReadiness(siblings: List<SamplingOrder>): DealStorageReadiness {
    val active = siblings.filter { it.status != SamplingStatus.CANCELLED && !it.isArchived }
    val (ready, pending) = active.partition { it.hasReachedStorage }
    return DealStorageReadiness(ready = ready, pending = pending)
}

val SamplingOrder.hasReachedStorage: Boolean
    get() = positionOf(stageCode) >= positionOf(ExitStages.STORAGE)

/**
 * Gambaran penyimpanan satu SPK untuk layar: catatan kustodinya (bila sudah disimpan) dan
 * kelengkapan deal-nya. Dirakit server supaya klien tidak perlu memuat seluruh SPK sedeal.
 */
data class SampleStorageStatus(
    val record: SampleStorageRecord?,
    val readyCount: Int,
    val total: Int,
    val pendingSpkNumbers: List<String>
) {
    val isDealComplete: Boolean get() = pendingSpkNumbers.isEmpty()

    companion object {
        fun of(record: SampleStorageRecord?, readiness: DealStorageReadiness) = SampleStorageStatus(
            record = record,
            readyCount = readiness.ready.size,
            total = readiness.total,
            pendingSpkNumbers = readiness.pending.map { it.spkNumber.value }
        )
    }
}
