package com.eventverse.app.domain.invoicing.template

import com.eventverse.app.domain.common.Ratio
import com.eventverse.app.domain.invoicing.InvoiceKind
import com.eventverse.app.domain.invoicing.InvoiceTemplateId
import com.eventverse.app.domain.tenant.TenantId
import kotlinx.datetime.Instant
import kotlin.test.*

class InvoiceTemplateTest {

    private val tenantId = TenantId("ten-demo-001")
    private val now = Instant.parse("2026-09-14T10:00:00Z")

    @Test
    fun standardSeedTemplate_shouldBeValid() {
        val template = InvoiceTemplateFactory.standardIndonesianInvoice(tenantId, now)
        assertEquals("Template Faktur Standar Indonesia", template.name)
        assertEquals(PaperSize.A4, template.paperSize)
        assertTrue(template.isDefault)
        assertNotNull(template.itemTable)
        assertEquals(5, template.itemTable?.columns?.size)
    }

    @Test
    fun template_shouldRejectMultipleItemTables() {
        val table1 = TemplateElement.ItemTable(
            elementId = "tbl-1",
            rect = TemplateRect(Mm10(100), Mm10(100), Mm10(1800), Mm10(500)),
            columns = listOf(TableColumn(BindingToken("line.description"), "Deskripsi", Ratio.of(1, 1)))
        )
        val table2 = TemplateElement.ItemTable(
            elementId = "tbl-2",
            rect = TemplateRect(Mm10(100), Mm10(700), Mm10(1800), Mm10(500)),
            columns = listOf(TableColumn(BindingToken("line.amount"), "Total", Ratio.of(1, 1)))
        )

        assertFailsWith<IllegalArgumentException>("Template hanya boleh memiliki maksimal 1 tabel item") {
            InvoiceTemplate(
                id = InvoiceTemplateId("tpl-test"),
                tenantId = tenantId,
                name = "Template Dobel Tabel",
                elements = listOf(table1, table2),
                createdAt = now,
                updatedAt = now
            )
        }
    }

    @Test
    fun template_shouldRejectDuplicateElementIds() {
        val el1 = TemplateElement.StaticText(
            elementId = "text-1",
            rect = TemplateRect(Mm10(100), Mm10(100), Mm10(500), Mm10(100)),
            text = "Halo"
        )
        val el2 = TemplateElement.StaticText(
            elementId = "text-1", // duplicate
            rect = TemplateRect(Mm10(100), Mm10(300), Mm10(500), Mm10(100)),
            text = "Dunia"
        )

        assertFailsWith<IllegalArgumentException> {
            InvoiceTemplate(
                id = InvoiceTemplateId("tpl-test"),
                tenantId = tenantId,
                name = "Template Duplikat ID",
                elements = listOf(el1, el2),
                createdAt = now,
                updatedAt = now
            )
        }
    }

    @Test
    fun moveElement_shouldReturnUpdatedCopy() {
        val seed = InvoiceTemplateFactory.standardIndonesianInvoice(tenantId, now)
        val targetId = "invoice-title"
        val newRect = TemplateRect(Mm10(1300), Mm10(180), Mm10(700), Mm10(120))

        val movedTemplate = seed.moveElement(targetId, newRect)
        val movedEl = movedTemplate.elements.single { it.elementId == targetId }

        assertEquals(newRect, movedEl.rect)
    }
}
