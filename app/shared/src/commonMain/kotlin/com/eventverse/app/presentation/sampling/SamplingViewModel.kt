package com.eventverse.app.presentation.sampling

import com.eventverse.app.domain.sampling.*
import com.eventverse.app.infrastructure.api.SamplingApiClient
import com.eventverse.app.infrastructure.api.SamplingRemoteDataSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SamplingViewModel(
    private val tenantSlug: String,
    private val remoteDataSource: SamplingRemoteDataSource = SamplingApiClient(),
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main)
) {
    private val _uiState = MutableStateFlow(SamplingUiState())
    val uiState: StateFlow<SamplingUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun onEvent(event: SamplingUiEvent) {
        when (event) {
            is SamplingUiEvent.Load -> load()
            is SamplingUiEvent.SelectOrder -> _uiState.update { it.copy(selectedOrderId = event.orderId) }
            is SamplingUiEvent.SelectMobileTab -> _uiState.update { it.copy(activeMobileTab = event.tab) }
            is SamplingUiEvent.SetFilter -> _uiState.update { it.copy(selectedStatusFilter = event.status) }
            is SamplingUiEvent.UpdateSearchQuery -> _uiState.update { it.copy(searchQuery = event.query) }
            is SamplingUiEvent.OpenCreateDialog -> _uiState.update { it.copy(isCreateDialogOpen = true) }
            is SamplingUiEvent.CloseCreateDialog -> _uiState.update { it.copy(isCreateDialogOpen = false) }
            is SamplingUiEvent.CreateOrder -> createOrder(event)
            is SamplingUiEvent.ToggleMilestone -> toggleMilestone(event.orderId, event.step, event.isCompleted)
            is SamplingUiEvent.ApproveOrder -> approveOrder(event.orderId, event.isApproved, event.notes)
            is SamplingUiEvent.SaveTechnicalSpec -> saveTechnicalSpec(event.updatedOrder)
            is SamplingUiEvent.DismissStatusMessage -> _uiState.update { it.copy(statusMessage = null) }
        }
    }

    private fun load() {
        scope.launch {
            _uiState.update { it.copy(isLoading = true) }
            remoteDataSource.getOrders(tenantSlug)
                .onSuccess { list ->
                    _uiState.update { current ->
                        current.copy(
                            orders = list,
                            selectedOrderId = current.selectedOrderId ?: list.firstOrNull()?.id,
                            isLoading = false
                        )
                    }
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            statusMessage = "Gagal memuat SPK sample: ${err.message}",
                            isErrorMessage = true
                        )
                    }
                }
        }
    }

    private fun createOrder(event: SamplingUiEvent.CreateOrder) {
        scope.launch {
            _uiState.update { it.copy(isSubmitting = true) }
            remoteDataSource.createOrder(
                tenantSlug = tenantSlug,
                clientName = event.clientName,
                styleName = event.styleName,
                sizeMode = event.sizeMode,
                useFactoryPreset = event.useFactoryPreset
            ).onSuccess { created ->
                _uiState.update { current ->
                    current.copy(
                        orders = listOf(created) + current.orders,
                        selectedOrderId = created.id,
                        isCreateDialogOpen = false,
                        isSubmitting = false,
                        statusMessage = "Berhasil membuat SPK ${created.spkNumber.value}",
                        isErrorMessage = false
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isSubmitting = false,
                        statusMessage = "Gagal membuat SPK: ${err.message}",
                        isErrorMessage = true
                    )
                }
            }
        }
    }

    private fun toggleMilestone(orderId: SamplingOrderId, step: MilestoneStep, isCompleted: Boolean) {
        scope.launch {
            remoteDataSource.toggleMilestone(tenantSlug, orderId.value, step, isCompleted)
                .onSuccess { updated ->
                    _uiState.update { current ->
                        val newOrders = current.orders.map { if (it.id == updated.id) updated else it }
                        current.copy(orders = newOrders)
                    }
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(
                            statusMessage = "Gagal mengubah milestone: ${err.message}",
                            isErrorMessage = true
                        )
                    }
                }
        }
    }

    private fun approveOrder(orderId: SamplingOrderId, isApproved: Boolean, notes: String) {
        scope.launch {
            _uiState.update { it.copy(isSubmitting = true) }
            remoteDataSource.approveOrder(tenantSlug, orderId.value, isApproved, notes)
                .onSuccess { updated ->
                    _uiState.update { current ->
                        val newOrders = current.orders.map { if (it.id == updated.id) updated else it }
                        current.copy(
                            orders = newOrders,
                            isSubmitting = false,
                            statusMessage = if (isApproved) "SPK ${updated.spkNumber.value} berhasil di-ACC Produksi!" else "Catatan revisi berhasil disimpan",
                            isErrorMessage = false
                        )
                    }
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(
                            isSubmitting = false,
                            statusMessage = "Gagal memproses ACC: ${err.message}",
                            isErrorMessage = true
                        )
                    }
                }
        }
    }

    private fun saveTechnicalSpec(order: SamplingOrder) {
        scope.launch {
            _uiState.update { it.copy(isSubmitting = true) }
            remoteDataSource.updateTechnicalSpec(tenantSlug, order)
                .onSuccess { updated ->
                    _uiState.update { current ->
                        val newOrders = current.orders.map { if (it.id == updated.id) updated else it }
                        current.copy(
                            orders = newOrders,
                            isSubmitting = false,
                            statusMessage = "Spesifikasi teknis berhasil disimpan",
                            isErrorMessage = false
                        )
                    }
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(
                            isSubmitting = false,
                            statusMessage = "Gagal menyimpan spesifikasi teknis: ${err.message}",
                            isErrorMessage = true
                        )
                    }
                }
        }
    }
}
