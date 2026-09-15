package com.eventverse.app.domain.invoicing.template

import com.eventverse.app.domain.tenant.TenantId
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Penempatan dan pembuatan elemen baru dari perpustakaan elemen.
 *
 * Nilai bawaan hidup di domain, bukan di UI: palet kiri dan panel properti memakai jalur yang sama,
 * sehingga tombol yang sama di dua tempat tidak bisa menghasilkan elemen yang berbeda.
 */
class TemplateElementPlacementTest {

    private val tenantId = TenantId("ten-demo-001")
    private val now = Instant.parse("2026-09-14T10:00:00Z")
    private val template = InvoiceTemplateFactory.standardIndonesianInvoice(tenantId, now)

    @Test
    fun `new element is placed below the lowest existing element`() {
        val lowestBottom = template.elements.maxOf { it.rect.y.value + it.rect.height.value }

        val rect = template.nextFreeRect(
            widthMm10 = 1000,
            heightMm10 = 60,
            snapMm10 = 50
        )

        assertTrue(rect.y.value >= lowestBottom, "Elemen baru harus muncul di bawah isi yang sudah ada")
        assertTrue(rect.bottom <= template.paperSize.height, "Elemen baru tetap harus di dalam kertas")
        assertEquals(0, rect.y.value % 50, "Penempatan menghormati snap grid")
    }

    @Test
    fun `new element on an empty template starts at the top margin`() {
        val empty = InvoiceTemplate(
            id = template.id,
            tenantId = tenantId,
            name = "Template Kosong",
            marginMm10 = 150,
            createdAt = now,
            updatedAt = now
        )

        val rect = empty.nextFreeRect(widthMm10 = 1000, heightMm10 = 60, snapMm10 = 50)

        assertEquals(150, rect.y.value)
        assertEquals(150, rect.x.value)
    }

    @Test
    fun `requested width below the readable minimum is widened, not rejected`() {
        val rect = template.nextFreeRect(widthMm10 = 10, heightMm10 = 60, snapMm10 = 50)

        assertEquals(InvoiceTemplateDefaults.MIN_TEXT_WIDTH_MM10, rect.width.value)
    }

    @Test
    fun `placement never escapes the paper even when the template is already full`() {
        val full = template.copy(
            elements = template.elements + TemplateElement.StaticText(
                elementId = "bottom-note",
                rect = TemplateRect(Mm10(150), Mm10(2900), Mm10(1800), Mm10(60)),
                text = "Catatan paling bawah"
            )
        )

        val rect = full.nextFreeRect(widthMm10 = 1000, heightMm10 = 60, snapMm10 = 50)

        assertTrue(rect.bottom <= full.paperSize.height, "Elemen baru tidak boleh lahir di luar kertas")
    }

    @Test
    fun `module field preset carries the label and font size from the registry`() {
        val descriptor = InvoiceBindingRegistry.descriptorFor("billTo.phone")!!
        val rect = TemplateRect(Mm10(150), Mm10(150), Mm10(descriptor.defaultWidthMm10), Mm10(60))

        val element = TemplateElementFactory.create(
            preset = TemplateElementPreset.ModuleField(descriptor),
            elementId = "el-1",
            rect = rect,
            zOrder = 1.0
        )

        val bound = element as TemplateElement.BoundField
        assertEquals("billTo.phone", bound.binding.value)
        assertEquals("Telp: ", bound.prefix)
        assertEquals(descriptor.defaultFontSizePt, bound.style.fontSizePt)
    }

    @Test
    fun `item table preset uses tokens that exist in the registry`() {
        val element = TemplateElementFactory.create(
            preset = TemplateElementPreset.ItemTable,
            elementId = "el-table",
            rect = TemplateRect(Mm10(150), Mm10(800), Mm10(1800), Mm10(360)),
            zOrder = 1.0
        )

        val table = element as TemplateElement.ItemTable
        assertTrue(table.columns.isNotEmpty())
        table.columns.forEach { column ->
            assertTrue(
                InvoiceBindingRegistry.descriptorFor(column.binding) != null,
                "Kolom tabel bawaan tidak boleh menunjuk token yang tidak ada: ${column.binding.value}"
            )
        }

        val numeratorSum = table.columns.sumOf { it.widthRatio.numerator }
        val denominator = table.columns.first().widthRatio.denominator
        assertTrue(
            table.columns.all { it.widthRatio.denominator == denominator },
            "Kolom bawaan memakai penyebut rasio yang seragam"
        )
        assertEquals(
            denominator,
            numeratorSum,
            "Total rasio kolom bawaan harus pas satu lebar penuh, bukan kurang atau lebih"
        )
    }

    @Test
    fun `line-scoped tokens are never offered as standalone palette entries`() {
        // Token `line.*` hanya bisa diresolusi bersama satu baris faktur; menempelkannya di luar tabel
        // menghasilkan elemen kosong yang membingungkan.
        InvoiceBindingRegistry.standaloneTokens.forEach { descriptor ->
            assertTrue(
                !descriptor.moduleSource.isLineScopedOnly,
                "Token ${descriptor.token.value} tidak boleh ditawarkan sebagai elemen bebas"
            )
        }
        assertEquals(
            InvoiceBindingRegistry.LINE.size,
            InvoiceBindingRegistry.tableColumnTokens.size
        )
    }
}
