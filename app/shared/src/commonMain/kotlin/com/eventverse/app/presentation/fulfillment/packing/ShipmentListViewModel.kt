package com.eventverse.app.presentation.fulfillment.packing

import com.eventverse.app.domain.fulfillment.shipment.CarrierInfo
import com.eventverse.app.domain.fulfillment.shipment.DestinationInfo
import com.eventverse.app.domain.fulfillment.shipment.DispatchShipment
import com.eventverse.app.domain.fulfillment.shipment.ShipmentId
import com.eventverse.app.domain.fulfillment.shipment.StockOwnership
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
 * UI State untuk layar Daftar Surat Jalan — shipments / pengiriman eksternal.
 */
data class ShipmentListUiState(
    val shipments: List<DispatchShipment> = emptyList(),
    val isLoading: Boolean = false,
    val isSubmitting: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null,
    /** Dialog buat SJ terbuka. */
    val showCreateDialog: Boolean = false,
    /** Dialog konfirmasi aksi (in-transit / delivered / cancel). */
    val showActionDialog: ActionDialogState? = null
) {
    val issuedShipments get() = shipments.filter { it.status.name == "ISSUED" }
    val inTransitShipments get() = shipments.filter { it.status.name == "IN_TRANSIT" }
    val deliveredShipments get() = shipments.filter { it.status.name == "DELIVERED" }
    val cancelledShipments get() = shipments.filter { it.status.name == "CANCELLED" }
}

/** State untuk dialog aksi shipment. */
data class ActionDialogState(
    val shipmentId: ShipmentId,
    val type: ActionType,
    val reason: String = ""
)

enum class ActionType {
    MARK_IN_TRANSIT,
    CONFIRM_DELIVERED,
    CANCEL
}

sealed interface ShipmentListUiEvent {
    data object Load : ShipmentListUiEvent
    data object DismissError : ShipmentListUiEvent
    data object DismissSuccess : ShipmentListUiEvent

    /** Buka dialog buat SJ. */
    data object ShowCreateDialog : ShipmentListUiEvent
    data object DismissCreateDialog : ShipmentListUiEvent

    /** Terbitkan SJ baru. */
    data class IssueShipment(
        val stockOwnership: StockOwnership,
        val carrier: CarrierInfo,
        val destination: DestinationInfo,
        val cartonIds: List<String>,
        val notes: String = ""
    ) : ShipmentListUiEvent

    /** Buka dialog tandai in-transit. */
    data class ShowInTransitDialog(val shipmentId: ShipmentId) : ShipmentListUiEvent

    /** Buka dialog konfirmasi delivered. */
    data class ShowDeliveredDialog(val shipmentId: ShipmentId) : ShipmentListUiEvent

    /** Buka dialog pembatalan. */
    data class ShowCancelDialog(val shipmentId: ShipmentId) : ShipmentListUiEvent

    data object DismissActionDialog : ShipmentListUiEvent

    /** Tandai SJ sedang dalam perjalanan. */
    data class MarkInTransit(val shipmentId: String, val actor: String) : ShipmentListUiEvent

    /** Konfirmasi SJ sudah terkirim. */
    data class ConfirmDelivered(val shipmentId: String, val actor: String) : ShipmentListUiEvent

    /** Batalkan SJ (hanya ISSUED). */
    data class CancelShipment(
        val shipmentId: String,
        val reason: String,
        val actor: String
    ) : ShipmentListUiEvent
}

class ShipmentListViewModel(
    private val tenantSlug: String,
    private val remoteDataSource: FulfillmentPackingRemoteDataSource = FulfillmentPackingApiClient(),
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main)
) {
    private val _uiState = MutableStateFlow(ShipmentListUiState())
    val uiState: StateFlow<ShipmentListUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun onEvent(event: ShipmentListUiEvent) {
        when (event) {
            ShipmentListUiEvent.Load -> load()
            ShipmentListUiEvent.DismissError -> _uiState.update { it.copy(error = null) }
            ShipmentListUiEvent.DismissSuccess -> _uiState.update { it.copy(successMessage = null) }
            ShipmentListUiEvent.ShowCreateDialog -> _uiState.update { it.copy(showCreateDialog = true) }
            ShipmentListUiEvent.DismissCreateDialog -> _uiState.update { it.copy(showCreateDialog = false) }

            is ShipmentListUiEvent.IssueShipment -> issueShipment(
                event.stockOwnership,
                event.carrier,
                event.destination,
                event.cartonIds,
                event.notes
            )

            is ShipmentListUiEvent.ShowInTransitDialog -> _uiState.update {
                it.copy(showActionDialog = ActionDialogState(event.shipmentId, ActionType.MARK_IN_TRANSIT))
            }

            is ShipmentListUiEvent.ShowDeliveredDialog -> _uiState.update {
                it.copy(showActionDialog = ActionDialogState(event.shipmentId, ActionType.CONFIRM_DELIVERED))
            }

            is ShipmentListUiEvent.ShowCancelDialog -> _uiState.update {
                it.copy(showActionDialog = ActionDialogState(event.shipmentId, ActionType.CANCEL))
            }

            ShipmentListUiEvent.DismissActionDialog -> _uiState.update { it.copy(showActionDialog = null) }

            is ShipmentListUiEvent.MarkInTransit -> markInTransit(event.shipmentId, event.actor)
            is ShipmentListUiEvent.ConfirmDelivered -> confirmDelivered(event.shipmentId, event.actor)
            is ShipmentListUiEvent.CancelShipment -> cancelShipment(event.shipmentId, event.reason, event.actor)
        }
    }

    private fun load() {
        _uiState.update { it.copy(isLoading = true, error = null) }
        scope.launch {
            remoteDataSource.packingQueue(tenantSlug)
                .onSuccess { queue ->
                    _uiState.update { it.copy(shipments = queue.shipments, isLoading = false) }
                }
                .onFailure { failure ->
                    _uiState.update {
                        it.copy(isLoading = false, error = failure.message ?: "Gagal memuat daftar pengiriman")
                    }
                }
        }
    }

    private fun issueShipment(
        stockOwnership: StockOwnership,
        carrier: CarrierInfo,
        destination: DestinationInfo,
        cartonIds: List<String>,
        notes: String
    ) {
        _uiState.update { it.copy(isSubmitting = true, error = null, showCreateDialog = false) }
        scope.launch {
            remoteDataSource.issueShipment(tenantSlug, stockOwnership, carrier, destination, cartonIds, notes)
                .onSuccess {
                    _uiState.update {
                        it.copy(isSubmitting = false, successMessage = "Surat Jalan berhasil diterbitkan")
                    }
                    load()
                }
                .onFailure { failure ->
                    _uiState.update {
                        it.copy(isSubmitting = false, error = failure.message ?: "Gagal menerbitkan SJ")
                    }
                }
        }
    }

    private fun markInTransit(shipmentId: String, actor: String) {
        _uiState.update { it.copy(isSubmitting = true, error = null, showActionDialog = null) }
        scope.launch {
            remoteDataSource.markInTransit(tenantSlug, shipmentId, actor)
                .onSuccess {
                    _uiState.update {
                        it.copy(isSubmitting = false, successMessage = "SJ ditandai sedang dalam perjalanan")
                    }
                    load()
                }
                .onFailure { failure ->
                    _uiState.update {
                        it.copy(isSubmitting = false, error = failure.message ?: "Gagal menandai SJ")
                    }
                }
        }
    }

    private fun confirmDelivered(shipmentId: String, actor: String) {
        _uiState.update { it.copy(isSubmitting = true, error = null, showActionDialog = null) }
        scope.launch {
            remoteDataSource.confirmDelivered(tenantSlug, shipmentId, actor)
                .onSuccess {
                    _uiState.update {
                        it.copy(isSubmitting = false, successMessage = "Pengiriman dikonfirmasi terkirim")
                    }
                    load()
                }
                .onFailure { failure ->
                    _uiState.update {
                        it.copy(isSubmitting = false, error = failure.message ?: "Gagal mengonfirmasi SJ")
                    }
                }
        }
    }

    private fun cancelShipment(shipmentId: String, reason: String, actor: String) {
        _uiState.update { it.copy(isSubmitting = true, error = null, showActionDialog = null) }
        scope.launch {
            remoteDataSource.cancelShipment(tenantSlug, shipmentId, reason, actor)
                .onSuccess {
                    _uiState.update {
                        it.copy(isSubmitting = false, successMessage = "Surat Jalan dibatalkan")
                    }
                    load()
                }
                .onFailure { failure ->
                    _uiState.update {
                        it.copy(isSubmitting = false, error = failure.message ?: "Gagal membatalkan SJ")
                    }
                }
        }
    }
}
