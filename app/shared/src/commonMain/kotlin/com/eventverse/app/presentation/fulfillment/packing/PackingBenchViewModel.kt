package com.eventverse.app.presentation.fulfillment.packing

import com.eventverse.app.domain.fulfillment.carton.CartonId
import com.eventverse.app.domain.fulfillment.carton.PackingCarton
import com.eventverse.app.infrastructure.api.FulfillmentPackingApiClient
import com.eventverse.app.infrastructure.api.FulfillmentPackingRemoteDataSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * UI State untuk layar Packing Bench — meja kerja packing karton.
 */
data class PackingBenchUiState(
    val cartons: List<PackingCarton> = emptyList(),
    val isLoading: Boolean = false,
    val isSubmitting: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null,
    /** Dialog pembuatan karton terbuka. */
    val showCreateDialog: Boolean = false,
    /** Dialog seal karton terbuka. */
    val showSealDialog: CartonId? = null,
    /** Dialog reopen karton terbuka. */
    val showReopenDialog: CartonId? = null
) {
    val openCartons get() = cartons.filter { it.status.name == "OPEN" }
    val sealedCartons get() = cartons.filter { it.status.name == "SEALED" }
    val reopenedCartons get() = cartons.filter { it.status.name == "REOPENED" }
}

sealed interface PackingBenchUiEvent {
    data object Load : PackingBenchUiEvent
    data object DismissError : PackingBenchUiEvent
    data object DismissSuccess : PackingBenchUiEvent

    /** Buka dialog buat karton. */
    data object ShowCreateDialog : PackingBenchUiEvent
    data object DismissCreateDialog : PackingBenchUiEvent

    /** Buat karton baru. */
    data class CreateCarton(
        val productionRef: String,
        val notes: String = ""
    ) : PackingBenchUiEvent

    /** Buka dialog seal karton. */
    data class ShowSealDialog(val cartonId: CartonId) : PackingBenchUiEvent
    data object DismissSealDialog : PackingBenchUiEvent

    /** Segel karton. */
    data class SealCarton(
        val cartonId: String,
        val sealNumber: String,
        val grossWeightKg: Double,
        val actor: String
    ) : PackingBenchUiEvent

    /** Buka dialog reopen karton. */
    data class ShowReopenDialog(val cartonId: CartonId) : PackingBenchUiEvent
    data object DismissReopenDialog : PackingBenchUiEvent

    /** Buka kembali karton (MANAGE). */
    data class ReopenCarton(
        val cartonId: String,
        val reason: String,
        val actor: String
    ) : PackingBenchUiEvent
}

class PackingBenchViewModel(
    private val tenantSlug: String,
    private val remoteDataSource: FulfillmentPackingRemoteDataSource = FulfillmentPackingApiClient(),
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main)
) {
    private val _uiState = MutableStateFlow(PackingBenchUiState())
    val uiState: StateFlow<PackingBenchUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun onEvent(event: PackingBenchUiEvent) {
        when (event) {
            PackingBenchUiEvent.Load -> load()
            PackingBenchUiEvent.DismissError -> _uiState.update { it.copy(error = null) }
            PackingBenchUiEvent.DismissSuccess -> _uiState.update { it.copy(successMessage = null) }
            PackingBenchUiEvent.ShowCreateDialog -> _uiState.update { it.copy(showCreateDialog = true) }
            PackingBenchUiEvent.DismissCreateDialog -> _uiState.update { it.copy(showCreateDialog = false) }

            is PackingBenchUiEvent.CreateCarton -> createCarton(event.productionRef, event.notes)

            is PackingBenchUiEvent.ShowSealDialog -> _uiState.update { it.copy(showSealDialog = event.cartonId) }
            PackingBenchUiEvent.DismissSealDialog -> _uiState.update { it.copy(showSealDialog = null) }

            is PackingBenchUiEvent.SealCarton -> sealCarton(
                event.cartonId,
                event.sealNumber,
                event.grossWeightKg,
                event.actor
            )

            is PackingBenchUiEvent.ShowReopenDialog -> _uiState.update { it.copy(showReopenDialog = event.cartonId) }
            PackingBenchUiEvent.DismissReopenDialog -> _uiState.update { it.copy(showReopenDialog = null) }

            is PackingBenchUiEvent.ReopenCarton -> reopenCarton(
                event.cartonId,
                event.reason,
                event.actor
            )
        }
    }

    private fun load() {
        _uiState.update { it.copy(isLoading = true, error = null) }
        scope.launch {
            remoteDataSource.packingQueue(tenantSlug)
                .onSuccess { queue ->
                    _uiState.update { it.copy(cartons = queue.cartons, isLoading = false) }
                }
                .onFailure { failure ->
                    _uiState.update {
                        it.copy(isLoading = false, error = failure.message ?: "Gagal memuat antrian packing")
                    }
                }
        }
    }

    private fun createCarton(productionRef: String, notes: String) {
        _uiState.update { it.copy(isSubmitting = true, error = null, showCreateDialog = false) }
        scope.launch {
            remoteDataSource.createCarton(tenantSlug, productionRef, notes)
                .onSuccess {
                    _uiState.update {
                        it.copy(isSubmitting = false, successMessage = "Karton berhasil dibuat")
                    }
                    load()
                }
                .onFailure { failure ->
                    _uiState.update {
                        it.copy(isSubmitting = false, error = failure.message ?: "Gagal membuat karton")
                    }
                }
        }
    }

    private fun sealCarton(cartonId: String, sealNumber: String, grossWeightKg: Double, actor: String) {
        _uiState.update { it.copy(isSubmitting = true, error = null, showSealDialog = null) }
        scope.launch {
            remoteDataSource.sealCarton(tenantSlug, cartonId, sealNumber, grossWeightKg, actor)
                .onSuccess {
                    _uiState.update {
                        it.copy(isSubmitting = false, successMessage = "Karton berhasil disegel")
                    }
                    load()
                }
                .onFailure { failure ->
                    _uiState.update {
                        it.copy(isSubmitting = false, error = failure.message ?: "Gagal menyegel karton")
                    }
                }
        }
    }

    private fun reopenCarton(cartonId: String, reason: String, actor: String) {
        _uiState.update { it.copy(isSubmitting = true, error = null, showReopenDialog = null) }
        scope.launch {
            remoteDataSource.reopenCarton(tenantSlug, cartonId, reason, actor)
                .onSuccess {
                    _uiState.update {
                        it.copy(isSubmitting = false, successMessage = "Karton berhasil dibuka kembali")
                    }
                    load()
                }
                .onFailure { failure ->
                    _uiState.update {
                        it.copy(isSubmitting = false, error = failure.message ?: "Gagal membuka karton")
                    }
                }
        }
    }
}
