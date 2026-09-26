package com.eventverse.app.presentation.sampling

import com.eventverse.app.domain.sampling.SamplingOrder

/**
 * Metadata identitas desain dalam transaksi atau deal multi-desain.
 */
data class ResolvedDesignInfo(
    val code: String,
    val designNumber: Int,
    val totalDesigns: Int
)

/**
 * Menurunkan kode desain (`DSG-01`, `DSG-02`, …) dan indeks urutannya dari kesatuan deal.
 *
 * Bila sebuah deal CRM memiliki lebih dari satu desain sampling (misal 2 varian colorway atau model),
 * setiap desain memiliki nomor SPK tersendiri. Fungsi ini memberikan kode desain deterministik
 * agar lantai produksi, finishing, dan inspektor QC langsung mengenali perbedaan desain pada kartu.
 */
fun resolveDesignInfo(order: SamplingOrder, allOrders: List<SamplingOrder>): ResolvedDesignInfo? {
    if (!order.dealId.isNullOrBlank()) {
        val dealOrders = allOrders.filter { it.dealId == order.dealId }
            .sortedWith(compareBy<SamplingOrder> { it.createdAt }.thenBy { it.spkNumber.value })
        if (dealOrders.size > 1) {
            val idx = dealOrders.indexOfFirst { it.id == order.id }
            val num = if (idx >= 0) idx + 1 else 1
            return ResolvedDesignInfo(
                code = "DSG-" + num.toString().padStart(2, '0'),
                designNumber = num,
                totalDesigns = dealOrders.size
            )
        }
    }
    if (order.styleName.startsWith("DSG-")) {
        val candidate = order.styleName.substringBefore(' ').substringBefore(':')
        if (candidate.matches(Regex("^DSG-\\d+$"))) {
            val num = candidate.removePrefix("DSG-").toIntOrNull() ?: 1
            return ResolvedDesignInfo(
                code = candidate,
                designNumber = num,
                totalDesigns = 1
            )
        }
    }
    return null
}
