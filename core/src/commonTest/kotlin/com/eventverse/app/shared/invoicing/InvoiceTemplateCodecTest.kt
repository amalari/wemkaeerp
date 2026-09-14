package com.eventverse.app.shared.invoicing

import com.eventverse.app.domain.invoicing.template.InvoiceTemplateFactory
import com.eventverse.app.domain.tenant.TenantId
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class InvoiceTemplateCodecTest {

    private val tenantId = TenantId("ten-demo-001")
    private val now = Instant.parse("2026-09-14T12:00:00Z")

    @Test
    fun standardSeedTemplate_roundTrip_shouldBeLossless() {
        val original = InvoiceTemplateFactory.standardIndonesianInvoice(tenantId, now)

        val encoded = InvoiceTemplateCodec.encode(original)
        val decoded = InvoiceTemplateCodec.decode(encoded)

        assertEquals(original.id, decoded.id)
        assertEquals(original.tenantId, decoded.tenantId)
        assertEquals(original.name, decoded.name)
        assertEquals(original.paperSize, decoded.paperSize)
        assertEquals(original.marginMm10, decoded.marginMm10)
        assertEquals(original.applicableKinds, decoded.applicableKinds)
        assertEquals(original.isDefault, decoded.isDefault)
        assertEquals(original.elements.size, decoded.elements.size)

        for (i in original.elements.indices) {
            val origEl = original.elements[i]
            val decEl = decoded.elements[i]
            assertEquals(origEl.elementId, decEl.elementId)
            assertEquals(origEl.rect, decEl.rect)
            assertEquals(origEl.zOrder, decEl.zOrder)
            assertEquals(origEl.anchorBelowTable, decEl.anchorBelowTable)
        }
    }
}
