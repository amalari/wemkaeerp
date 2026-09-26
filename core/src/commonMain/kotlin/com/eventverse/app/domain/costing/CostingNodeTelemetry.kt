package com.eventverse.app.domain.costing

import com.eventverse.app.domain.pipeline.FlowHealthStatus

/**
 * Data telemetri node `costing_hpp` di kanvas Factory Flow.
 *
 * Memenuhi **Kontrak 6** (Operational Telemetry) dari module-integration-rules.md:
 * node wajib menyediakan `wipPieces`, `cycleTimeHours`, dan `healthStatus` agar bisa
 * berkedip hijau/kuning/merah di visualisasi kanvas.
 *
 * @param pendingSheetCount Jumlah lembar HPP berstatus DRAFT yang belum dihitung.
 *   Ekuivalen "WIP" — lembar yang masuk antrian tapi belum selesai diproses.
 * @param overdueApprovalCount Jumlah lembar PENDING_APPROVAL yang sudah menunggu > 48 jam.
 *   Indikator bottleneck persetujuan.
 * @param avgApprovalCycleHours Rata-rata jam dari DRAFT → APPROVED dalam 30 hari terakhir.
 *   Ekuivalen `cycleTimeHours` dalam kontrak telemetri.
 * @param healthStatus Status kesehatan node yang ditampilkan sebagai warna di kanvas.
 *   Diturunkan dari: `overdueApprovalCount` dan `pendingSheetCount`.
 */
data class CostingNodeTelemetry(
    val pendingSheetCount: Int,
    val overdueApprovalCount: Int,
    val avgApprovalCycleHours: Double,
    val healthStatus: FlowHealthStatus
) {
    companion object {
        /**
         * Menurunkan [FlowHealthStatus] dari metrik telemetri.
         *
         * Aturan:
         * - `overdueApprovalCount > 0` → CRITICAL (ada keputusan yang "tersandera")
         * - `pendingSheetCount > 5` → BOTTLENECK (antrian perhitungan menumpuk)
         * - Selain itu → HEALTHY
         */
        fun deriveHealthStatus(
            pendingSheetCount: Int,
            overdueApprovalCount: Int
        ): FlowHealthStatus = when {
            overdueApprovalCount > 0 -> FlowHealthStatus.CRITICAL
            pendingSheetCount > 5 -> FlowHealthStatus.BOTTLENECK
            else -> FlowHealthStatus.HEALTHY
        }

        /** Telemetri saat tidak ada data (modul baru, belum ada lembar). */
        val EMPTY = CostingNodeTelemetry(
            pendingSheetCount = 0,
            overdueApprovalCount = 0,
            avgApprovalCycleHours = 0.0,
            healthStatus = FlowHealthStatus.HEALTHY
        )
    }
}
