package com.eventverse.app.presentation.production

import com.eventverse.app.domain.production.BulkWorkOrder
import com.eventverse.app.domain.production.BulkWorkOrderId
import com.eventverse.app.domain.production.MachineLineAllocation
import com.eventverse.app.domain.production.ProductionLineName
import com.eventverse.app.domain.production.ProductionStage
import com.eventverse.app.infrastructure.api.ProductionApiClient
import com.eventverse.app.infrastructure.api.ProductionRemoteDataSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ProductionViewModel(
    private val tenantSlug: String,
    private val remoteDataSource: ProductionRemoteDataSource = ProductionApiClient(),
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main)
) {
    private val _uiState = MutableStateFlow(ProductionUiState())
    val uiState: StateFlow<ProductionUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun onEvent(event: ProductionUiEvent) {
        when (event) {
            is ProductionUiEvent.Load -> load()
            is ProductionUiEvent.SelectWorkOrder ->
                _uiState.update { it.copy(selectedWorkOrderId = event.id) }
            is ProductionUiEvent.SetStatusFilter ->
                _uiState.update { it.copy(statusFilter = event.status) }
            is ProductionUiEvent.UpdateSearchQuery ->
                _uiState.update { it.copy(searchQuery = event.query) }

            is ProductionUiEvent.OpenAllocateDialog ->
                _uiState.update { it.copy(isAllocateDialogOpen = true, selectedWorkOrderId = event.id) }
            is ProductionUiEvent.CloseAllocateDialog ->
                _uiState.update { it.copy(isAllocateDialogOpen = false) }
            is ProductionUiEvent.AllocateLine -> allocateLine(event.id, event.allocation)
            is ProductionUiEvent.RemoveLine -> removeLine(event.id, event.lineName)

            is ProductionUiEvent.OpenProgressDialog ->
                _uiState.update {
                    it.copy(
                        isProgressDialogOpen = true,
                        selectedWorkOrderId = event.id,
                        progressDialogStage = event.stage
                    )
                }
            is ProductionUiEvent.CloseProgressDialog ->
                _uiState.update { it.copy(isProgressDialogOpen = false) }
            is ProductionUiEvent.RecordProgress ->
                recordProgress(event.id, event.stage, event.completedPcs, event.reworkPcs, event.rejectPcs)

            is ProductionUiEvent.DismissStatusMessage ->
                _uiState.update { it.copy(statusMessage = null) }
        }
    }

    private fun load() {
        scope.launch {
            _uiState.update { it.copy(isLoading = true) }
            remoteDataSource.getWorkOrders(tenantSlug)
                .onSuccess { list ->
                    _uiState.update { current ->
                        current.copy(
                            workOrders = list,
                            selectedWorkOrderId = current.selectedWorkOrderId ?: list.firstOrNull()?.id,
                            isLoading = false
                        )
                    }
                }
                .onFailure { err -> fail("Gagal memuat SPK massal", err, loading = true) }
        }
    }

    private fun allocateLine(id: BulkWorkOrderId, allocation: MachineLineAllocation) {
        scope.launch {
            _uiState.update { it.copy(isSubmitting = true) }
            remoteDataSource.allocateLine(tenantSlug, id.value, allocation)
                .onSuccess { updated ->
                    replaceOrder(updated, "Lini ${allocation.lineName.value} dialokasikan ${allocation.assignedPcs} pcs")
                    _uiState.update { it.copy(isAllocateDialogOpen = false) }
                }
                .onFailure { err -> fail("Gagal mengalokasikan lini", err) }
        }
    }

    private fun removeLine(id: BulkWorkOrderId, lineName: ProductionLineName) {
        scope.launch {
            _uiState.update { it.copy(isSubmitting = true) }
            remoteDataSource.removeLine(tenantSlug, id.value, lineName)
                .onSuccess { replaceOrder(it, "Alokasi lini ${lineName.value} dihapus") }
                .onFailure { err -> fail("Gagal menghapus alokasi lini", err) }
        }
    }

    private fun recordProgress(
        id: BulkWorkOrderId,
        stage: ProductionStage,
        completedPcs: Int,
        reworkPcs: Int,
        rejectPcs: Int
    ) {
        scope.launch {
            _uiState.update { it.copy(isSubmitting = true) }
            remoteDataSource.recordProgress(tenantSlug, id.value, stage, completedPcs, reworkPcs, rejectPcs)
                .onSuccess { updated ->
                    replaceOrder(updated, "Hasil ${stage.displayName} tercatat $completedPcs pcs")
                    _uiState.update { it.copy(isProgressDialogOpen = false) }
                }
                .onFailure { err -> fail("Gagal mencatat hasil produksi", err) }
        }
    }

    private fun replaceOrder(updated: BulkWorkOrder, message: String) {
        _uiState.update { current ->
            current.copy(
                workOrders = current.workOrders.map { if (it.id == updated.id) updated else it },
                isSubmitting = false,
                statusMessage = message,
                isErrorMessage = false
            )
        }
    }

    /**
     * Pesan gagal selalu membawa sebab aslinya.
     *
     * Aturan domain di sini berbunyi kalimat lengkap ("Hasil Jahit 500 pcs melebihi hasil Potong
     * yang lolos (300 pcs)"), dan kalimat itulah yang perlu dibaca operator — bukan
     * "terjadi kesalahan" yang memaksa dia menebak angka mana yang salah.
     */
    private fun fail(prefix: String, error: Throwable, loading: Boolean = false) {
        _uiState.update {
            it.copy(
                isLoading = if (loading) false else it.isLoading,
                isSubmitting = false,
                statusMessage = "$prefix: ${error.message ?: "penyebab tidak diketahui"}",
                isErrorMessage = true
            )
        }
    }
}
