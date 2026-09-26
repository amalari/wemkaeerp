package com.eventverse.app.presentation.costing

import com.eventverse.app.domain.common.Money

/**
 * Pemisah ribuan gaya Indonesia untuk nilai minor units (sen).
 *
 * Diangkat dari `CostingWorkspaceScreen` ketika pemakaiannya menyentuh tiga berkas (layar
 * workbench, pane estimator, pane Knowledge Base) — Aturan Tiga Kali, design-system rules §3.
 */
internal fun Long.toFormattedIdr(): String {
    val negative = this < 0L
    val digits = kotlin.math.abs(this).toString()
    val builder = StringBuilder()
    var count = 0
    for (i in digits.length - 1 downTo 0) {
        builder.append(digits[i])
        count++
        if (count % 3 == 0 && i > 0) builder.append('.')
    }
    val formatted = builder.reverse().toString()
    return if (negative) "-$formatted" else formatted
}

/**
 * Rupiah utuh tanpa sen, cara angka uang ditulis di lantai produksi.
 *
 * Sen dibuang di lapisan tampilan saja — [Money] tetap menyimpan presisi penuh supaya
 * penjumlahan rincian biaya tidak kehilangan rupiah di pembulatan.
 */
internal fun Money.formatRupiah(): String = "Rp ${(minorUnits / 100L).toFormattedIdr()}"
