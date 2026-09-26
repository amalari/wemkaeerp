package com.eventverse.app.domain.invoicing

import com.eventverse.app.domain.common.Money
import com.eventverse.app.domain.common.Ratio

data class PaymentTerm(
    val label: String,
    val ratio: Ratio,
    val kind: InvoiceKind
) {
    init {
        require(label.isNotBlank()) { "Label termin tidak boleh kosong." }
        require(ratio > Ratio.ZERO) { "Rasio termin harus lebih besar dari 0." }
    }
}

/**
 * Pabrik kalkulasi termin pembayaran (DP / Termin Bertahap / Pelunasan).
 * Memanfaatkan [Money.allocate] untuk menjamin bahwa jumlah dari seluruh termin
 * persis sama dengan nilai kontrak awal tanpa kehilangan satu sen pun (zero-loss penny allocation).
 */
object PaymentScheduleFactory {

    /**
     * Memecah [contractValue] menjadi daftar termin pembayaran dengan bobot rasio masing-masing.
     */
    fun split(contractValue: Money, terms: List<PaymentTerm>): List<Pair<PaymentTerm, Money>> {
        require(terms.isNotEmpty()) { "Daftar termin tidak boleh kosong." }

        // Cari penyebut persekutuan terkecil atau gunakan skala 1_000_000 (micros)
        val weights = terms.map { it.ratio.applyTo(1_000_000L) }
        val allocatedAmounts = contractValue.allocate(weights)

        return terms.zip(allocatedAmounts)
    }

    /**
     * Memecah kontrak menjadi sepasang termin Uang Muka (DP) dan Pelunasan (Settlement).
     * Sisa pembulatan sen dialokasikan secara adil ke porsi termin terbesar.
     */
    fun downPaymentAndSettlement(
        contractValue: Money,
        downPaymentPercent: Int
    ): Pair<Money, Money> {
        require(downPaymentPercent in 1..99) {
            "Persentase DP harus berada di antara 1% s/d 99%: $downPaymentPercent."
        }
        val dpRatio = Ratio.percent(downPaymentPercent.toDouble())
        val settlementRatio = Ratio.percent((100 - downPaymentPercent).toDouble())

        val terms = listOf(
            PaymentTerm("Uang Muka (DP $downPaymentPercent%)", dpRatio, InvoiceKind.DOWN_PAYMENT),
            PaymentTerm("Pelunasan (${100 - downPaymentPercent}%)", settlementRatio, InvoiceKind.SETTLEMENT)
        )

        val splitResults = split(contractValue, terms)
        return splitResults[0].second to splitResults[1].second
    }
}
