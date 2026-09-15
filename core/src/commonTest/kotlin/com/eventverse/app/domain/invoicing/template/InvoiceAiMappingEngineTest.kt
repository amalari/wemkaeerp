package com.eventverse.app.domain.invoicing.template

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InvoiceAiMappingEngineTest {

    @Test
    fun analyzeElement_identifiesClientNameProperly() {
        val element = TemplateElement.StaticText(
            elementId = "txt-1",
            rect = TemplateRect(Mm10(200), Mm10(500), Mm10(600), Mm10(100)),
            text = "Kepada Yth. PT Mitra Usaha Mandiri"
        )
        val suggestion = InvoiceAiMappingEngine.analyzeElement(element)
        assertNotNull(suggestion)
        assertEquals("billTo.name", suggestion.token.value)
        assertTrue(suggestion.confidence >= 0.90)
        assertFalse(suggestion.isStaticLabelOnly)
        assertEquals("Kepada Yth. ", suggestion.suggestedPrefix)
    }

    @Test
    fun analyzeElement_identifiesInvoiceNumberAndTotal() {
        val invNoElement = TemplateElement.StaticText(
            elementId = "txt-2",
            rect = TemplateRect(Mm10(1400), Mm10(500), Mm10(500), Mm10(80)),
            text = "Nomor Faktur: INV/2026/03/001"
        )
        val invNoSuggestion = InvoiceAiMappingEngine.analyzeElement(invNoElement)
        assertNotNull(invNoSuggestion)
        assertEquals("invoice.number", invNoSuggestion.token.value)
        assertEquals("Nomor Faktur: ", invNoSuggestion.suggestedPrefix)

        val totalElement = TemplateElement.StaticText(
            elementId = "txt-3",
            rect = TemplateRect(Mm10(1400), Mm10(2200), Mm10(500), Mm10(100)),
            text = "Total Tagihan (Grand Total)"
        )
        val totalSuggestion = InvoiceAiMappingEngine.analyzeElement(totalElement)
        assertNotNull(totalSuggestion)
        assertEquals("invoice.total", totalSuggestion.token.value)
    }

    @Test
    fun autoMapAll_convertsEligibleStaticTextToBoundFields() {
        val elements = listOf(
            TemplateElement.StaticText(
                elementId = "el-client",
                rect = TemplateRect(Mm10(200), Mm10(500), Mm10(600), Mm10(100)),
                text = "PT Adhi Garmen Sejahtera"
            ),
            TemplateElement.StaticText(
                elementId = "el-phone",
                rect = TemplateRect(Mm10(200), Mm10(650), Mm10(600), Mm10(100)),
                text = "Telp / WA: 08123456789"
            ),
            TemplateElement.StaticText(
                elementId = "el-random",
                rect = TemplateRect(Mm10(200), Mm10(1500), Mm10(300), Mm10(50)),
                text = "Dekorasi Border"
            )
        )

        val summary = InvoiceAiMappingEngine.autoMapAll(elements)
        assertEquals(3, summary.totalProcessed)
        assertEquals(2, summary.newlyMappedCount)

        val mappedClient = summary.mappedElements.first { it.elementId == "el-client" }
        assertTrue(mappedClient is TemplateElement.BoundField)
        assertEquals("billTo.name", mappedClient.binding.value)

        val mappedPhone = summary.mappedElements.first { it.elementId == "el-phone" }
        assertTrue(mappedPhone is TemplateElement.BoundField)
        assertEquals("billTo.phone", mappedPhone.binding.value)
        assertEquals("Telp / WA: ", mappedPhone.prefix)

        val remainingRandom = summary.mappedElements.first { it.elementId == "el-random" }
        assertTrue(remainingRandom is TemplateElement.StaticText)
    }

    @Test
    fun autoMapAll_keepsPureLabelsStaticWhileSummarizingThem() {
        val elements = listOf(
            TemplateElement.StaticText(
                elementId = "lbl-bill-to",
                rect = TemplateRect(Mm10(150), Mm10(560), Mm10(600), Mm10(60)),
                text = "DITAGIHKAN KEPADA:"
            ),
            TemplateElement.StaticText(
                elementId = "val-note",
                rect = TemplateRect(Mm10(150), Mm10(1700), Mm10(1000), Mm10(80)),
                text = "Catatan: Bahan Katun Combed 30s"
            )
        )

        val summary = InvoiceAiMappingEngine.autoMapAll(elements)

        assertEquals(1, summary.newlyMappedCount)
        assertEquals(1, summary.skippedLabelCount)

        val labelElement = summary.mappedElements.first { it.elementId == "lbl-bill-to" }
        assertTrue(labelElement is TemplateElement.StaticText, "Label statis tidak boleh dikonversi otomatis")

        val noteElement = summary.mappedElements.first { it.elementId == "val-note" }
        assertTrue(noteElement is TemplateElement.BoundField)
        assertEquals("invoice.notes", noteElement.binding.value)
        assertEquals("Catatan: ", noteElement.prefix)
    }

    @Test
    fun analyzeElement_treatsPureLabelAsStaticLabelOnlyWithLowConfidence() {
        val label = TemplateElement.StaticText(
            elementId = "txt-label",
            rect = TemplateRect(Mm10(150), Mm10(1600), Mm10(700), Mm10(60)),
            text = "Subtotal"
        )
        val suggestion = InvoiceAiMappingEngine.analyzeElement(label)
        assertNotNull(suggestion)
        assertTrue(suggestion.isStaticLabelOnly)
        assertTrue(
            suggestion.confidence < InvoiceAiMappingEngine.DEFAULT_MIN_CONFIDENCE,
            "Label statis harus berada di bawah ambang pemetaan otomatis"
        )
        assertEquals("Subtotal: ", suggestion.suggestedPrefix)
    }

    @Test
    fun analyzeElement_mapsBankAccountNumberBeforeBankName() {
        val element = TemplateElement.StaticText(
            elementId = "txt-bank",
            rect = TemplateRect(Mm10(150), Mm10(1800), Mm10(800), Mm10(60)),
            text = "Bank BCA - No. Rek: 8420-123-999"
        )
        val suggestion = InvoiceAiMappingEngine.analyzeElement(element)
        assertNotNull(suggestion)
        assertEquals("issuer.bankAccountNumber", suggestion.token.value)
    }

    @Test
    fun analyzeElement_mapsTerbilangToTotalInWords() {
        val element = TemplateElement.StaticText(
            elementId = "txt-words",
            rect = TemplateRect(Mm10(150), Mm10(1620), Mm10(1000), Mm10(80)),
            text = "Terbilang: Lima belas juta rupiah"
        )
        val suggestion = InvoiceAiMappingEngine.analyzeElement(element)
        assertNotNull(suggestion)
        assertEquals("invoice.totalInWords", suggestion.token.value)
    }

    @Test
    fun analyzeElement_returnsNullForDecorativeText() {
        val element = TemplateElement.StaticText(
            elementId = "txt-decor",
            rect = TemplateRect(Mm10(200), Mm10(1500), Mm10(300), Mm10(50)),
            text = "Dekorasi Border"
        )
        assertNull(InvoiceAiMappingEngine.analyzeElement(element))
    }

    @Test
    fun analyzeElement_reportsFullConfidenceForAlreadyBoundField() {
        val bound = TemplateElement.BoundField(
            elementId = "bound-1",
            rect = TemplateRect(Mm10(150), Mm10(630), Mm10(800), Mm10(80)),
            binding = BindingToken("billTo.name")
        )
        val suggestion = InvoiceAiMappingEngine.analyzeElement(bound)
        assertNotNull(suggestion)
        assertEquals(1.0, suggestion.confidence)
        assertEquals("billTo.name", suggestion.token.value)
    }

    @Test
    fun toBoundField_preservesGeometryStyleAndSuggestedPrefix() {
        val element = TemplateElement.StaticText(
            elementId = "txt-phone",
            rect = TemplateRect(Mm10(150), Mm10(850), Mm10(800), Mm10(60)),
            zOrder = 12.0,
            anchorBelowTable = true,
            text = "Telp / WA: 08123456789",
            style = TextStyleSpec(fontSizePt = 11, isBold = true, align = TextAlign.RIGHT)
        )
        val suggestion = InvoiceAiMappingEngine.analyzeElement(element)
        assertNotNull(suggestion)

        val converted = InvoiceAiMappingEngine.toBoundField(element, suggestion)
        assertEquals(element.elementId, converted.elementId)
        assertEquals(element.rect, converted.rect)
        assertEquals(element.zOrder, converted.zOrder)
        assertTrue(converted.anchorBelowTable)
        assertEquals("Telp / WA: ", converted.prefix)
        assertEquals(11, converted.style.fontSizePt)
        assertTrue(converted.style.isBold)
    }

    @Test
    fun isStaticLabelOnly_distinguishesLabelFromValue() {
        assertTrue(InvoiceAiMappingEngine.isStaticLabelOnly("DITAGIHKAN KEPADA:"))
        assertTrue(InvoiceAiMappingEngine.isStaticLabelOnly("Total Tagihan (Grand Total)"))
        assertFalse(InvoiceAiMappingEngine.isStaticLabelOnly("Telp / WA: 08123456789"))
        assertFalse(InvoiceAiMappingEngine.isStaticLabelOnly("Jl. Kawasan Industri MM2100 Blok C-4"))
        assertFalse(InvoiceAiMappingEngine.isStaticLabelOnly("finance@mitrausaha.co.id"))
    }

    @Test
    fun aiMappingSuggestion_rejectsConfidenceOutsideRange() {
        assertFailsWith<IllegalArgumentException> {
            AiMappingSuggestion(
                token = BindingToken("billTo.name"),
                confidence = 1.5,
                explanation = "Skor tidak valid"
            )
        }
    }
}
