package com.eventverse.app.presentation.invoicing

import com.eventverse.app.domain.common.Money
import com.eventverse.app.domain.invoicing.*
import com.eventverse.app.domain.invoicing.template.InvoiceTemplate
import com.eventverse.app.domain.invoicing.usecases.CreateInvoiceCommand
import com.eventverse.app.domain.invoicing.usecases.RecordInvoicePaymentCommand
import com.eventverse.app.domain.rbac.ModuleAccessConfig
import com.eventverse.app.infrastructure.api.InvoicingApiClient
import com.eventverse.app.infrastructure.api.InvoicingRemoteDataSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class InvoiceViewModel(
    private val tenantSlug: String,
    private val remoteDataSource: InvoicingRemoteDataSource = InvoicingApiClient(),
    val access: ModuleAccessConfig = ModuleAccessConfig(),
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main)
) {
    private val _uiState = MutableStateFlow(InvoiceUiState())
    val uiState: StateFlow<InvoiceUiState> = _uiState.asStateFlow()

    fun onEvent(event: InvoiceUiEvent) {
        when (event) {
            is InvoiceUiEvent.Load -> loadAll()
            is InvoiceUiEvent.SelectTab -> _uiState.update { it.copy(selectedTab = event.tab) }
            is InvoiceUiEvent.FilterByStatus -> {
                _uiState.update { it.copy(filterStatus = event.status, currentPage = 1) }
                loadInvoices()
            }
            is InvoiceUiEvent.FilterByKind -> {
                _uiState.update { it.copy(filterKind = event.kind, currentPage = 1) }
                loadInvoices()
            }
            is InvoiceUiEvent.Search -> {
                _uiState.update { it.copy(searchQuery = event.query, currentPage = 1) }
                loadInvoices()
            }
            is InvoiceUiEvent.SelectInvoice -> {
                _uiState.update { it.copy(selectedInvoice = event.invoice) }
                if (event.invoice != null) {
                    loadPayments(event.invoice.id)
                }
            }
            is InvoiceUiEvent.OpenCreateInvoiceDialog -> _uiState.update { it.copy(isCreateInvoiceDialogOpen = true) }
            is InvoiceUiEvent.CloseCreateInvoiceDialog -> _uiState.update { it.copy(isCreateInvoiceDialogOpen = false) }
            is InvoiceUiEvent.SubmitCreateInvoice -> createInvoice(event.command)
            is InvoiceUiEvent.IssueInvoice -> issueInvoice(event.invoiceId)
            is InvoiceUiEvent.OpenRecordPaymentDialog -> _uiState.update {
                it.copy(isRecordPaymentDialogOpen = true, selectedInvoice = event.invoice)
            }
            is InvoiceUiEvent.CloseRecordPaymentDialog -> _uiState.update { it.copy(isRecordPaymentDialogOpen = false) }
            is InvoiceUiEvent.SubmitRecordPayment -> recordPayment(event.amount, event.method, event.reference, event.note)
            is InvoiceUiEvent.OpenVoidDialog -> _uiState.update {
                it.copy(isVoidDialogOpen = true, selectedInvoice = event.invoice)
            }
            is InvoiceUiEvent.CloseVoidDialog -> _uiState.update { it.copy(isVoidDialogOpen = false) }
            is InvoiceUiEvent.SubmitVoid -> voidInvoice(event.reason)
            is InvoiceUiEvent.CreateSettlement -> createSettlement(event.downPaymentInvoiceId)
            is InvoiceUiEvent.OpenDesigner -> _uiState.update {
                it.copy(isDesignerOpen = true, editingTemplateId = event.templateId)
            }
            is InvoiceUiEvent.CloseDesigner -> {
                _uiState.update { it.copy(isDesignerOpen = false, editingTemplateId = null) }
                loadTemplates()
            }
            is InvoiceUiEvent.SetDefaultTemplate -> setDefaultTemplate(event.templateId)
            is InvoiceUiEvent.ArchiveTemplate -> archiveTemplate(event.templateId)
            is InvoiceUiEvent.SaveIssuerProfile -> saveIssuerProfile(event.profile)
            is InvoiceUiEvent.OpenPdfPreview -> _uiState.update {
                it.copy(isPdfPreviewOpen = true, selectedInvoice = event.invoice)
            }
            is InvoiceUiEvent.ClosePdfPreview -> _uiState.update { it.copy(isPdfPreviewOpen = false) }
            is InvoiceUiEvent.DismissMessage -> _uiState.update { it.copy(error = null, successMessage = null) }
        }
    }

    private fun loadAll() {
        _uiState.update { it.copy(isLoading = true, error = null) }
        scope.launch {
            loadInvoicesInternal()
            loadTemplatesInternal()
            loadIssuerProfileInternal()
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    private fun loadInvoices() {
        scope.launch {
            loadInvoicesInternal()
        }
    }

    private suspend fun loadInvoicesInternal() {
        val s = _uiState.value
        remoteDataSource.getInvoices(
            tenantSlug = tenantSlug,
            status = s.filterStatus,
            kind = s.filterKind,
            searchQuery = s.searchQuery,
            page = s.currentPage,
            pageSize = 50
        ).onSuccess { page ->
            _uiState.update { current ->
                current.copy(
                    invoices = page.items,
                    totalInvoicesCount = page.totalCount,
                    selectedInvoice = current.selectedInvoice?.let { sel -> page.items.find { it.id == sel.id } ?: sel }
                )
            }
        }.onFailure { err ->
            _uiState.update { it.copy(error = "Gagal memuat invoice: ${err.message}") }
        }
    }

    private fun loadTemplates() {
        scope.launch {
            loadTemplatesInternal()
        }
    }

    private suspend fun loadTemplatesInternal() {
        remoteDataSource.getTemplates(tenantSlug, includeArchived = false).onSuccess { list ->
            val defaultTpl = list.firstOrNull { it.isDefault }
            _uiState.update { it.copy(templates = list, defaultTemplate = defaultTpl) }
        }
    }

    private suspend fun loadIssuerProfileInternal() {
        remoteDataSource.getIssuerProfile(tenantSlug).onSuccess { profile ->
            _uiState.update { it.copy(issuerProfile = profile) }
        }
    }

    private fun loadPayments(invoiceId: InvoiceId) {
        scope.launch {
            remoteDataSource.getPayments(tenantSlug, invoiceId).onSuccess { payments ->
                _uiState.update { it.copy(selectedInvoicePayments = payments) }
            }
        }
    }

    private fun createInvoice(command: CreateInvoiceCommand) {
        scope.launch {
            _uiState.update { it.copy(isLoading = true) }
            remoteDataSource.createInvoice(tenantSlug, command).onSuccess { created ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isCreateInvoiceDialogOpen = false,
                        selectedInvoice = created,
                        successMessage = "Invoice draft ${created.number.value} berhasil dibuat"
                    )
                }
                loadInvoicesInternal()
            }.onFailure { err ->
                _uiState.update { it.copy(isLoading = false, error = "Gagal membuat invoice: ${err.message}") }
            }
        }
    }

    private fun issueInvoice(invoiceId: InvoiceId) {
        scope.launch {
            _uiState.update { it.copy(isLoading = true) }
            remoteDataSource.issueInvoice(tenantSlug, invoiceId).onSuccess { issued ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        selectedInvoice = issued,
                        successMessage = "Invoice ${issued.number.value} resmi diterbitkan (snapshot template terkunci)"
                    )
                }
                loadInvoicesInternal()
            }.onFailure { err ->
                _uiState.update { it.copy(isLoading = false, error = "Gagal menerbitkan invoice: ${err.message}") }
            }
        }
    }

    private fun recordPayment(amount: Money, method: String, reference: String, note: String) {
        val invoice = _uiState.value.selectedInvoice ?: return
        scope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val command = RecordInvoicePaymentCommand(
                invoiceId = invoice.id,
                amount = amount,
                method = method,
                reference = reference,
                note = note,
                recordedBy = "finance"
            )
            remoteDataSource.recordPayment(tenantSlug, command).onSuccess { payment ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isRecordPaymentDialogOpen = false,
                        successMessage = "Pembayaran ${payment.amount.formatted()} berhasil dicatat"
                    )
                }
                // reload detail and list
                remoteDataSource.getInvoice(tenantSlug, invoice.id).onSuccess { updated ->
                    _uiState.update { it.copy(selectedInvoice = updated) }
                }
                loadPayments(invoice.id)
                loadInvoicesInternal()
            }.onFailure { err ->
                _uiState.update { it.copy(isLoading = false, error = "Gagal mencatat pembayaran: ${err.message}") }
            }
        }
    }

    private fun voidInvoice(reason: String) {
        val invoice = _uiState.value.selectedInvoice ?: return
        scope.launch {
            _uiState.update { it.copy(isLoading = true) }
            remoteDataSource.voidInvoice(tenantSlug, invoice.id, reason).onSuccess { voided ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isVoidDialogOpen = false,
                        selectedInvoice = voided,
                        successMessage = "Invoice ${voided.number.value} dibatalkan (VOID)"
                    )
                }
                loadInvoicesInternal()
            }.onFailure { err ->
                _uiState.update { it.copy(isLoading = false, error = "Gagal membatalkan invoice: ${err.message}") }
            }
        }
    }

    private fun createSettlement(downPaymentInvoiceId: InvoiceId) {
        scope.launch {
            _uiState.update { it.copy(isLoading = true) }
            remoteDataSource.createSettlement(tenantSlug, downPaymentInvoiceId).onSuccess { settlement ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        selectedInvoice = settlement,
                        successMessage = "Draft invoice pelunasan ${settlement.number.value} berhasil dibuat"
                    )
                }
                loadInvoicesInternal()
            }.onFailure { err ->
                _uiState.update { it.copy(isLoading = false, error = "Gagal membuat pelunasan: ${err.message}") }
            }
        }
    }

    private fun setDefaultTemplate(templateId: InvoiceTemplateId) {
        scope.launch {
            remoteDataSource.setDefaultTemplate(tenantSlug, templateId).onSuccess {
                loadTemplatesInternal()
            }
        }
    }

    private fun archiveTemplate(templateId: InvoiceTemplateId) {
        scope.launch {
            remoteDataSource.archiveTemplate(tenantSlug, templateId).onSuccess {
                loadTemplatesInternal()
            }
        }
    }

    private fun saveIssuerProfile(profile: IssuerProfile) {
        scope.launch {
            remoteDataSource.saveIssuerProfile(tenantSlug, profile).onSuccess { saved ->
                _uiState.update {
                    it.copy(issuerProfile = saved, successMessage = "Profil penerbit berhasil disimpan")
                }
            }.onFailure { err ->
                _uiState.update { it.copy(error = "Gagal menyimpan profil: ${err.message}") }
            }
        }
    }

    fun getPdfUrl(invoiceId: InvoiceId): String =
        remoteDataSource.getPdfUrl(tenantSlug, invoiceId)
}
