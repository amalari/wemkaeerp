package com.eventverse.app.domain.costing

import com.eventverse.app.domain.common.Money
import com.eventverse.app.domain.contracts.CostingCalculationResult

/**
 * Hasil perbandingan antara snapshot HPP yang sudah disetujui dan kalkulasi terkini.
 *
 * ## Mengapa sealed interface, bukan Boolean?
 * Boolean hanya menjawab "ada pergeseran atau tidak". Sealed interface membawa data
 * lengkap: berapa besar pergeseran (delta) dan apa yang berubah — kunci untuk
 * memutuskan apakah lembar perlu direvisi atau bisa diabaikan.
 *
 * ## Kapan drift terdeteksi?
 * [DetectCostingSheetDriftUseCase] menjalankan kalkulasi ulang dengan harga material
 * terkini dan membandingkan `billablePerUnit` hasil kalkulasi dengan snapshot.
 * Jika delta > threshold (default: lebih dari 2%), status berubah dari APPROVED ke CALCULATED
 * dan workflow persetujuan perlu diulang.
 */
sealed interface CostingDrift {
    /** Tidak ada pergeseran signifikan — snapshot masih valid sebagai komitmen komersial. */
    data object None : CostingDrift

    /**
     * Pergeseran terdeteksi — snapshot perlu direvisi.
     *
     * @param deltaPerUnit Selisih `billablePerUnit` saat ini vs snapshot (bisa negatif jika harga turun).
     * @param deltaPercent Persentase perubahan relatif terhadap snapshot.
     * @param changedInputs Daftar input yang berubah (mis. harga kain naik, SAM berubah).
     * @param currentResult Kalkulasi terkini yang menjadi dasar deteksi.
     */
    data class Detected(
        val deltaPerUnit: Money,
        val deltaPercent: Double,
        val changedInputs: List<DriftedInput>,
        val currentResult: CostingCalculationResult
    ) : CostingDrift {
        val isSignificant: Boolean get() = kotlin.math.abs(deltaPercent) > 2.0
    }
}

/**
 * Satu item yang berubah dalam kalkulasi HPP, dibandingkan dengan snapshot terakhir.
 *
 * @param inputKey Identifikasi input yang berubah (mis. `"MATERIAL:kain_drill"`, `"laborRate"`).
 * @param previousValue Nilai sebelumnya (teks yang bisa dibaca manusia).
 * @param currentValue Nilai terkini.
 */
data class DriftedInput(
    val inputKey: String,
    val previousValue: String,
    val currentValue: String
)
