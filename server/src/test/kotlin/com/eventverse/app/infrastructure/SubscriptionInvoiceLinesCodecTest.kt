package com.eventverse.app.infrastructure

import com.eventverse.app.domain.builder.SubscriptionInvoiceLine
import com.eventverse.app.domain.moduledev.MoneyIdr
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Codec baris invoice (`builder.subscription_invoices.lines_json`).
 *
 * Dua hal yang dijaga: harga tidak berubah bentuk setelah perjalanan pulang-pergi, dan data rusak
 * **menggagalkan pembacaan** alih-alih menghasilkan tagihan yang diam-diam lebih murah.
 */
class SubscriptionInvoiceLinesCodecTest {

    @Test
    fun `round trip keeps module, price and kind intact`() {
        val lines = listOf(
            SubscriptionInvoiceLine("sampling_order", "Order Sampling", MoneyIdr(150_000), "SUBSCRIPTION"),
            SubscriptionInvoiceLine("custom_bordir", "Bordir \"khusus\"", MoneyIdr(75_000), "CUSTOMIZATION")
        )

        val decoded = SubscriptionInvoiceLinesCodec.fromJson(SubscriptionInvoiceLinesCodec.toJson(lines))

        assertEquals(2, decoded.size)
        assertEquals(150_000, decoded[0].monthlyPrice.amount)
        assertEquals("sampling_order", decoded[0].moduleId)
        assertEquals("custom_bordir", decoded[1].moduleId)
        assertEquals("CUSTOMIZATION", decoded[1].kind)
        assertTrue(
            decoded[1].displayName.contains("khusus"),
            "kutip ganda tidak boleh memutus JSON: ${decoded[1].displayName}"
        )
    }

    @Test
    fun `line without a readable price fails loudly instead of billing zero`() {
        val broken = SubscriptionInvoiceLinesCodec.runCatching {
            fromJson("[{\"moduleId\":\"sampling_order\",\"displayName\":\"Order Sampling\"}]")
        }

        assertTrue(broken.isFailure, "baris tanpa harga = data rusak, bukan harga nol")
    }
}
