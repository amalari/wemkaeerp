package com.eventverse.app.domain.costing

import com.eventverse.app.domain.common.Ratio
import com.eventverse.app.domain.common.Rounding
import com.eventverse.app.domain.common.divideWithRounding
import com.eventverse.app.domain.contracts.LaborOperation

/**
 * Menghitung total SAM (Standard Allowable Minutes) dari daftar operasi kerja.
 *
 * ## Mengapa TIDAK menggunakan `Ratio.plus` untuk menjumlah SAM?
 *
 * `Ratio.percent(x)` menghasilkan penyebut 1_000_000. `Ratio.plus` mengalikan penyebut:
 * `1_000_000 × 1_000_000 = 10^12`, dan operasi selanjutnya: `10^18` — melampaui Long.MAX_VALUE
 * pada operasi keempat, secara diam-diam. Tech pack garmen nyata bisa punya belasan operasi,
 * jadi nilai `TechPack.totalSamMinutes` (yang memakai `Ratio.plus`) praktis dijamin sampah
 * untuk produk nyata.
 *
 * Kalkulator ini menjumlahkan ke akumulator `Long` mikro-detik. Tidak ada `Ratio.plus`,
 * tidak ada overflow.
 *
 * @see [com.eventverse.app.domain.techpack.TechPack.totalSamMinutes] yang masih memakai
 *   `Ratio.plus` dan diakui rusak di Risiko #1 planning — modul costing adalah konsumen
 *   pertama SAM, sehingga kalkulator ini harus ada SEBELUM modul dipakai.
 */
object SamMinutesCalculator {

    private const val MICROS_PER_MINUTE = 1_000_000L

    /**
     * Data class pemisah antara SAM operasi langsung dan subkon.
     *
     * @param directMicros SAM menit × 10^6 dari operasi yang dikerjakan in-house.
     * @param subcontractMicros SAM menit × 10^6 dari operasi yang disubkonkan ke vendor luar.
     */
    data class SamMinutesBreakdown(
        val directMicros: Long,
        val subcontractMicros: Long
    ) {
        val totalMicros: Long get() = directMicros + subcontractMicros

        /** SAM langsung sebagai menit rasional, presisi hingga 6 desimal. */
        fun directMinutes(): Ratio = Ratio(directMicros, MICROS_PER_MINUTE)

        /** SAM subkon sebagai menit rasional. */
        fun subcontractMinutes(): Ratio = Ratio(subcontractMicros, MICROS_PER_MINUTE)

        /** Total SAM sebagai menit rasional. */
        fun totalMinutes(): Ratio = Ratio(totalMicros, MICROS_PER_MINUTE)
    }

    /**
     * Menghitung SAM breakdown dari daftar [LaborOperation].
     *
     * Tiap operasi di-resolve ke mikro-detik terlebih dahulu:
     * `micros = round(samMinutes.numerator * 1_000_000 / samMinutes.denominator)`
     * lalu dijumlahkan ke akumulator Long — tidak pernah memanggil `Ratio.plus`.
     */
    fun calculate(operations: List<LaborOperation>): SamMinutesBreakdown {
        var directMicros = 0L
        var subcontractMicros = 0L

        for (op in operations) {
            val opMicros = divideWithRounding(
                dividend = op.samMinutes.numerator * MICROS_PER_MINUTE,
                divisor = op.samMinutes.denominator,
                rounding = Rounding.HALF_UP
            )
            if (op.isSubcontracted) {
                subcontractMicros += opMicros
            } else {
                directMicros += opMicros
            }
        }

        return SamMinutesBreakdown(
            directMicros = directMicros,
            subcontractMicros = subcontractMicros
        )
    }
}
