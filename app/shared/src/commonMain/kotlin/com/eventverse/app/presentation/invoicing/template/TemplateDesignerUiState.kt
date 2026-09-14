package com.eventverse.app.presentation.invoicing.template

import com.eventverse.app.domain.common.CurrencyCode
import com.eventverse.app.domain.common.Money
import com.eventverse.app.domain.common.Quantity
import com.eventverse.app.domain.common.Ratio
import com.eventverse.app.domain.common.UnitOfMeasure
import com.eventverse.app.domain.invoicing.*
import com.eventverse.app.domain.invoicing.template.*
import com.eventverse.app.domain.tenant.TenantId
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate

data class TemplateDesignerUiState(
    val template: InvoiceTemplate,
    val selectedElementId: String? = null,
    val zoomPercent: Int = 100, // 50% to 200%
    val showGrid: Boolean = true,
    val snapGridMm: Int = 5, // 5mm snap
    val isSaving: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null,
    val previewInvoice: Invoice = createDummyPreviewInvoice()
) {
    val selectedElement: TemplateElement?
        get() = selectedElementId?.let { id -> template.elements.find { it.elementId == id } }

    companion object {
        fun createDummyPreviewInvoice(): Invoice {
            val now = Clock.System.now()
            val issueDate = LocalDate(2026, 3, 15)
            val dueDate = LocalDate(2026, 3, 29)
            val line1 = InvoiceLine(
                id = InvoiceLineId("line-01"),
                description = "Kemeja Seragam Lapangan Drill (Termin DP 50%)",
                quantity = Quantity(100_000_000L, UnitOfMeasure.PIECE),
                unitPrice = Money.idr(75_000),
                discount = Ratio.ZERO,
                sortOrder = 1
            )
            val line2 = InvoiceLine(
                id = InvoiceLineId("line-02"),
                description = "Bordir Komputer Logo Perusahaan & Nama Dada",
                quantity = Quantity(100_000_000L, UnitOfMeasure.PIECE),
                unitPrice = Money.idr(15_000),
                discount = Ratio.ZERO,
                sortOrder = 2
            )
            return Invoice(
                id = InvoiceId("inv-preview-001"),
                tenantId = TenantId("ten-demo-001"),
                number = InvoiceNumber("INV/2026/03/0042"),
                kind = InvoiceKind.DOWN_PAYMENT,
                status = InvoiceStatus.ISSUED,
                billTo = BillToParty(
                    name = "PT Mitra Usaha Mandiri",
                    contactPerson = "Bapak Hendra Gunawan",
                    address = "Kawasan Industri MM2100 Blok C-4, Cikarang",
                    phone = "0812-9876-5432",
                    email = "finance@mitrausaha.co.id",
                    taxId = "01.234.567.8-412.000"
                ),
                issuer = IssuerProfile(
                    companyName = "PT WeMade Garment Indonesia",
                    address = "Kawasan Industri Rancaekek Kav. 12, Bandung",
                    taxId = "02.345.678.9-429.000",
                    phone = "(022) 8765-4321",
                    email = "billing@wemade.co.id",
                    bankName = "Bank Central Asia (BCA)",
                    bankAccountNumber = "8420-123-999",
                    bankAccountHolder = "PT WEMADE GARMENT INDONESIA"
                ),
                lines = listOf(line1, line2),
                taxRatio = Ratio.percent(11.0),
                globalDiscount = Ratio.ZERO,
                currency = CurrencyCode.IDR,
                issueDate = issueDate,
                dueDate = dueDate,
                templateId = InvoiceTemplateId("tpl-std-id-001"),
                renderedTemplate = null,
                sourceKind = InvoiceSourceKind.MANUAL,
                sourceRef = "PO-MUM-2026/003",
                parentInvoiceId = null,
                contractValue = Money.idr(18_000_000),
                notes = "Termin 1: Down Payment 50% sebelum pengerjaan potong kain.",
                terms = "Pembayaran via transfer bank ke rekening tercantum. Faktur pelunasan diterbitkan setelah QC selesai.",
                createdBy = "Achmad Jamaludin",
                createdAt = now,
                updatedAt = now
            )
        }
    }
}

sealed interface TemplateDesignerUiEvent {
    data class SelectElement(val elementId: String?) : TemplateDesignerUiEvent
    data class UpdateElementRect(val elementId: String, val newBounds: TemplateRect) : TemplateDesignerUiEvent
    data class UpdateElement(val updatedElement: TemplateElement) : TemplateDesignerUiEvent
    data class AddElement(val element: TemplateElement) : TemplateDesignerUiEvent
    data class DeleteElement(val elementId: String) : TemplateDesignerUiEvent
    data class UpdateTemplateName(val name: String) : TemplateDesignerUiEvent
    data class SetZoom(val percent: Int) : TemplateDesignerUiEvent
    data class ToggleGrid(val show: Boolean) : TemplateDesignerUiEvent
    data class SetSnapGrid(val mm: Int) : TemplateDesignerUiEvent
    data object SaveTemplate : TemplateDesignerUiEvent
    data object DismissMessage : TemplateDesignerUiEvent
}
