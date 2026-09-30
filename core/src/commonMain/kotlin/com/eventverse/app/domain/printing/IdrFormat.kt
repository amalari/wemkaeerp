package com.eventverse.app.domain.printing

/**
 * Label rupiah untuk dokumen cetak.
 *
 * Diangkat ke satu tempat begitu pemakai ketiganya muncul. Sebelumnya pengelompokan ribuan ditulis
 * ulang di empat tempat (papan deal, wizard discovery, kalkulator biaya, pane tagihan) — dan di
 * dokumen cetak, format yang berbeda bukan sekadar tidak konsisten: angka di kertas yang tidak sama
 * dengan angka di layar membuat orang mempertanyakan **angkanya**, bukan formatnya.
 *
 * Sengaja tanpa desimal (rupiah tidak dipakai sampai sen di dokumen bisnis) dan tanpa API locale,
 * karena `NumberFormat` tidak tersedia seragam di lima target KMP: kelompok ribuan dihitung sendiri.
 */
object IdrFormat {

    /** `3400000` → `"Rp 3.400.000"`. Nilai negatif ditulis `"-Rp 1.500"`, bukan `"Rp -1.500"`. */
    fun format(amount: Long): String =
        if (amount < 0) "-Rp ${group(-amount)}" else "Rp ${group(amount)}"

    /** Digit dikelompokkan tiga dengan titik, tanpa awalan apa pun: `3400000` → `"3.400.000"`. */
    fun group(amount: Long): String {
        val digits = amount.toString().removePrefix("-")
        return digits.reversed().chunked(3).joinToString(".").reversed()
    }
}
