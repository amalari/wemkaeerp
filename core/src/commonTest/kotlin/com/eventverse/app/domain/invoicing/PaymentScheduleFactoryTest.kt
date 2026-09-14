package com.eventverse.app.domain.invoicing

import com.eventverse.app.domain.common.CurrencyCode
import com.eventverse.app.domain.common.Money
import com.eventverse.app.domain.common.Ratio
import kotlin.test.Test
import kotlin.test.assertEquals

class PaymentScheduleFactoryTest {

    @Test
    fun splitOddContractValue_shouldPreserveExactContractValueWithoutLosingAPenny() {
        // Kasus uji krusial: Rp 10.000.001 (1.000.000.100 minor) dipecah DP 30% dan Pelunasan 70%
        val contractValue = Money.idrMinor(1_000_000_100L) // Rp 10.000.001,00

        val (dp, settlement) = PaymentScheduleFactory.downPaymentAndSettlement(
            contractValue = contractValue,
            downPaymentPercent = 30
        )

        // Penjumlahan kedua termin WAJIB persis sama dengan nilai kontrak
        assertEquals(
            contractValue,
            dp + settlement,
            "Penjumlahan DP dan Pelunasan harus persis sama dengan nilai kontrak (Zero-Loss Penny Allocation)"
        )

        // Verifikasi selisih pembulatan diberikan ke termin bobot terbesar (70%)
        // 30% = 300.000.030 minor
        // 70% = 700.000.070 minor
        assertEquals(300_000_030L, dp.minorUnits)
        assertEquals(700_000_070L, settlement.minorUnits)
    }

    @Test
    fun multiTermSplit_shouldAllocateExactShares() {
        // Kontrak Rp 100.000.003 dibagi 3 termin sama rata (masing-masing ~33.3333%)
        val contractValue = Money.idrMinor(10_000_000_300L) // Rp 100.000.003

        val terms = listOf(
            PaymentTerm("Termin 1 (DP)", Ratio.of(1, 3), InvoiceKind.DOWN_PAYMENT),
            PaymentTerm("Termin 2 (Progres)", Ratio.of(1, 3), InvoiceKind.SAMPLE),
            PaymentTerm("Termin 3 (Pelunasan)", Ratio.of(1, 3), InvoiceKind.SETTLEMENT)
        )

        val results = PaymentScheduleFactory.split(contractValue, terms)
        val sumAllocated = Money.sum(results.map { it.second }, CurrencyCode.IDR)

        assertEquals(contractValue, sumAllocated)
    }
}
