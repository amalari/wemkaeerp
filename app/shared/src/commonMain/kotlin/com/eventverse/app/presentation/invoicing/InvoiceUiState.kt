package com.eventverse.app.presentation.invoicing

import com.eventverse.app.domain.common.Money
import com.eventverse.app.domain.invoicing.*
import com.eventverse.app.domain.invoicing.template.InvoiceTemplate
import com.eventverse.app.domain.invoicing.usecases.CreateInvoiceCommand

enum class InvoicingTab(val title: String) {
    INVOICES("Daftar Tagihan"),
    TEMPLATES("Template Faktur"),
    ISSUER_PROFILE("Profil Penerbit")
}

data class InvoiceUiState(
    val selectedTab: InvoicingTab = InvoicingTab.INVOICES,
    val invoices: List<Invoice> = emptyList(),
    val totalInvoicesCount: Long = 0,
    val filterStatus: InvoiceStatus? = null,
    val filterKind: InvoiceKind? = null,
    val searchQuery: String = "",
    val currentPage: Int = 1,
    val selectedInvoice: Invoice? = null,
    val selectedInvoicePayments: List<InvoicePayment> = emptyList(),
    val templates: List<InvoiceTemplate> = emptyList(),
    val defaultTemplate: InvoiceTemplate? = null,
    val issuerProfile: IssuerProfile? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null,
    // Dialog states
    val isCreateInvoiceDialogOpen: Boolean = false,
    val isRecordPaymentDialogOpen: Boolean = false,
    val isVoidDialogOpen: Boolean = false,
    val isPdfPreviewOpen: Boolean = false,
    val isDesignerOpen: Boolean = false,
    val editingTemplateId: String? = null
) {
    val totalReceivable: Money
        get() = invoices.filter { it.status == InvoiceStatus.ISSUED || it.status == InvoiceStatus.PARTIALLY_PAID }
            .map { it.total }
            .fold(Money.idr(0)) { acc, m -> acc + m }

    val totalPaid: Money
        get() = invoices.filter { it.status == InvoiceStatus.PAID }
            .map { it.total }
            .fold(Money.idr(0)) { acc, m -> acc + m }

    val totalDraftCount: Int
        get() = invoices.count { it.status == InvoiceStatus.DRAFT }

    val totalActiveCount: Int
        get() = invoices.count { it.status == InvoiceStatus.ISSUED || it.status == InvoiceStatus.PARTIALLY_PAID }
}

sealed interface InvoiceUiEvent {
    data object Load : InvoiceUiEvent
    data class SelectTab(val tab: InvoicingTab) : InvoiceUiEvent
    data class FilterByStatus(val status: InvoiceStatus?) : InvoiceUiEvent
    data class FilterByKind(val kind: InvoiceKind?) : InvoiceUiEvent
    data class Search(val query: String) : InvoiceUiEvent
    data class SelectInvoice(val invoice: Invoice?) : InvoiceUiEvent
    data class OpenCreateInvoiceDialog(val prefillSamplingId: String? = null) : InvoiceUiEvent
    data object CloseCreateInvoiceDialog : InvoiceUiEvent
    data class SubmitCreateInvoice(val command: CreateInvoiceCommand) : InvoiceUiEvent
    data class IssueInvoice(val invoiceId: InvoiceId) : InvoiceUiEvent
    data class OpenRecordPaymentDialog(val invoice: Invoice) : InvoiceUiEvent
    data object CloseRecordPaymentDialog : InvoiceUiEvent
    data class SubmitRecordPayment(val amount: Money, val method: String, val reference: String, val note: String) : InvoiceUiEvent
    data class OpenVoidDialog(val invoice: Invoice) : InvoiceUiEvent
    data object CloseVoidDialog : InvoiceUiEvent
    data class SubmitVoid(val reason: String) : InvoiceUiEvent
    data class CreateSettlement(val downPaymentInvoiceId: InvoiceId) : InvoiceUiEvent
    data class OpenDesigner(val templateId: String?) : InvoiceUiEvent
    data object CloseDesigner : InvoiceUiEvent
    data class SetDefaultTemplate(val templateId: InvoiceTemplateId) : InvoiceUiEvent
    data class ArchiveTemplate(val templateId: InvoiceTemplateId) : InvoiceUiEvent
    data class SaveIssuerProfile(val profile: IssuerProfile) : InvoiceUiEvent
    data class OpenPdfPreview(val invoice: Invoice) : InvoiceUiEvent
    data object ClosePdfPreview : InvoiceUiEvent
    data object DismissMessage : InvoiceUiEvent
}
