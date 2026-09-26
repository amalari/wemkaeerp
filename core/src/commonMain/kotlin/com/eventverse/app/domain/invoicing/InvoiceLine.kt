package com.eventverse.app.domain.invoicing

import com.eventverse.app.domain.common.Money
import com.eventverse.app.domain.common.Quantity
import com.eventverse.app.domain.common.Ratio
import com.eventverse.app.domain.common.UnitPrice

/**
 * Baris item pada faktur/invoice.
 * Perhitungan finansial presisi: mengalikan sebelum membagi untuk mencegah kehilangan presisi sen.
 */
data class InvoiceLine(
    val id: InvoiceLineId,
    val description: String,
    val quantity: Quantity,
    val unitPrice: Money,
    val discount: Ratio = Ratio.ZERO,
    val sortOrder: Int = 0
) {
    init {
        require(description.isNotBlank()) { "Deskripsi baris invoice tidak boleh kosong." }
        require(quantity.isPositive) { "Kuantitas baris invoice harus positif: ${quantity.formatted()}." }
        require(!discount.isNegative && discount <= Ratio.ONE) {
            "Diskon baris harus berada di rentang 0% s/d 100%: ${discount.asPercentageString()}."
        }
    }

    val grossAmount: Money
        get() {
            val up = UnitPrice(unitPrice, Quantity.of(1.0, quantity.uom))
            return up.costOf(quantity)
        }

    val discountAmount: Money
        get() = grossAmount * discount

    val amount: Money
        get() = grossAmount - discountAmount
}
