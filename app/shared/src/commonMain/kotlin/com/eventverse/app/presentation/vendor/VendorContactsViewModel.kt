package com.eventverse.app.presentation.vendor

import com.eventverse.app.domain.vendor.VendorAssignment
import com.eventverse.app.domain.vendor.VendorId
import com.eventverse.app.domain.vendor.VendorServiceRate
import com.eventverse.app.infrastructure.api.VendorApiClient
import com.eventverse.app.infrastructure.api.VendorAssignmentInput
import com.eventverse.app.infrastructure.api.VendorProfileInput
import com.eventverse.app.infrastructure.api.VendorRemoteDataSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * State holder layar Kontak Vendor. Semua aturan (harga daftar vs nego, larangan ganti vendor
 * setelah Surat Jalan terbit, nama ganda) ada di use case server; ViewModel hanya meneruskan dan
 * menampilkan pesannya.
 */
class VendorContactsViewModel(
    private val tenantSlug: String,
    private val remote: VendorRemoteDataSource = VendorApiClient(),
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main)
) {
    private val _uiState = MutableStateFlow(VendorContactsUiState())
    val uiState: StateFlow<VendorContactsUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun onEvent(event: VendorContactsUiEvent) {
        when (event) {
            is VendorContactsUiEvent.Load -> load()
            is VendorContactsUiEvent.SelectTab -> _uiState.update { it.copy(tab = event.tab) }
            is VendorContactsUiEvent.UpdateSearch -> _uiState.update { it.copy(searchQuery = event.query) }
            is VendorContactsUiEvent.SetShowInactive -> {
                _uiState.update { it.copy(showInactive = event.show) }
                load()
            }
            is VendorContactsUiEvent.SelectVendor -> selectVendor(event.vendorId)
            is VendorContactsUiEvent.OpenDialog -> _uiState.update { it.copy(dialog = event.dialog, statusMessage = null) }
            is VendorContactsUiEvent.CloseDialog -> _uiState.update { it.copy(dialog = null) }
            is VendorContactsUiEvent.SaveVendor -> saveVendor(event.vendorId, event.input)
            is VendorContactsUiEvent.SaveRate -> saveRate(event.vendorId, event.rate)
            is VendorContactsUiEvent.Assign -> assign(event.input)
            is VendorContactsUiEvent.CancelAssignment -> cancel(event.assignment)
            is VendorContactsUiEvent.DismissMessage -> _uiState.update { it.copy(statusMessage = null) }
        }
    }

    private fun load() {
        scope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val vendors = async { remote.listVendors(tenantSlug, includeInactive = _uiState.value.showInactive) }
            val queue = async { remote.queue(tenantSlug) }

            val vendorResult = vendors.await()
            val queueResult = queue.await()
            _uiState.update { state ->
                state.copy(
                    vendors = vendorResult.getOrDefault(state.vendors),
                    queue = queueResult.getOrDefault(state.queue),
                    isLoading = false
                )
            }
            (vendorResult.exceptionOrNull() ?: queueResult.exceptionOrNull())?.let { showError(it) }
            _uiState.value.selectedVendor?.let { loadHistory(it.id) }
        }
    }

    private fun selectVendor(vendorId: VendorId) {
        _uiState.update { it.copy(selectedVendorId = vendorId, selectedVendorHistory = emptyList()) }
        loadHistory(vendorId)
    }

    private fun loadHistory(vendorId: VendorId) {
        scope.launch {
            remote.vendorAssignments(tenantSlug, vendorId.value)
                .onSuccess { history -> _uiState.update { it.copy(selectedVendorHistory = history) } }
        }
    }

    private fun saveVendor(vendorId: VendorId?, input: VendorProfileInput) {
        val resumeAssign = (_uiState.value.dialog as? VendorDialog.CreateVendor)?.resumeAssign
        submit(
            call = {
                if (vendorId == null) remote.registerVendor(tenantSlug, input)
                else remote.updateVendor(tenantSlug, vendorId.value, input)
            },
            onDone = { vendor ->
                _uiState.update {
                    if (resumeAssign != null) it.copy(selectedVendorId = vendor.id)
                    else it.copy(selectedVendorId = vendor.id, tab = VendorTab.CONTACTS)
                }
                "Kontak ${vendor.name.value} tersimpan"
            },
            nextDialog = resumeAssign?.let { VendorDialog.Assign(it) }
        )
    }

    private fun saveRate(vendorId: VendorId, rate: VendorServiceRate) = submit(
        call = { remote.setRate(tenantSlug, vendorId.value, rate) },
        onDone = { vendor -> "Harga ${rate.serviceName} untuk ${vendor.name.value} tersimpan" }
    )

    private fun assign(input: VendorAssignmentInput) = submit(
        call = { remote.assign(tenantSlug, input) },
        onDone = { a -> "${a.processName} ${a.subjectLabel} ditugaskan ke ${a.vendorName.value}. Surat Jalan ke vendor kini bisa diterbitkan." }
    )

    private fun cancel(assignment: VendorAssignment) = submit(
        call = { remote.cancelAssignment(tenantSlug, assignment.id.value) },
        onDone = { a -> "Penunjukan ${a.vendorName.value} untuk ${a.subjectLabel} dibatalkan" }
    )

    /**
     * Pola tunggal semua penulisan: kunci tombol, tutup dialog (atau pindah ke [nextDialog]) bila
     * sukses, lalu muat ulang.
     */
    private fun <T> submit(call: suspend () -> Result<T>, onDone: (T) -> String, nextDialog: VendorDialog? = null) {
        scope.launch {
            _uiState.update { it.copy(isSubmitting = true) }
            call()
                .onSuccess { result ->
                    val message = onDone(result)
                    _uiState.update {
                        it.copy(isSubmitting = false, dialog = nextDialog, statusMessage = message, isErrorMessage = false)
                    }
                    load()
                }
                .onFailure { error ->
                    _uiState.update { it.copy(isSubmitting = false) }
                    showError(error)
                }
        }
    }

    private fun showError(error: Throwable) {
        _uiState.update { it.copy(statusMessage = error.message ?: "Terjadi kesalahan", isErrorMessage = true) }
    }
}
