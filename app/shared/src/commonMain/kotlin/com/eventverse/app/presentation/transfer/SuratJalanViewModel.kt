package com.eventverse.app.presentation.transfer

import com.eventverse.app.domain.transfer.LocationId
import com.eventverse.app.domain.transfer.SuratJalanManifest
import com.eventverse.app.domain.transfer.TransferType
import com.eventverse.app.domain.transfer.usecases.CustomerDispatchCartonInput
import com.eventverse.app.domain.workqueue.WorkCardId
import com.eventverse.app.domain.workqueue.WorkSubjectRef
import com.eventverse.app.infrastructure.api.SuratJalanApiClient
import com.eventverse.app.infrastructure.api.SuratJalanRemoteDataSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class SuratJalanTab(val title: String) {
    INTERNAL_MUTASI("Mutasi Antar-Gedung"),
    MAKLOON_VENDOR("Makloon Vendor Luar"),
    PENGIRIMAN_KLIEN("Pengiriman ke Buyer")
}

data class SuratJalanUiState(
    val manifests: List<SuratJalanManifest> = emptyList(),
    val selectedTab: SuratJalanTab = SuratJalanTab.INTERNAL_MUTASI,
    val isLoading: Boolean = false,
    val isSubmitting: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null
) {
    val internalManifests: List<SuratJalanManifest>
        get() = manifests.filter { it.transferType == TransferType.INTERNAL_SITE_TRANSFER }

    val makloonManifests: List<SuratJalanManifest>
        get() = manifests.filter { it.transferType == TransferType.SUBCONTRACT_OUTBOUND || it.transferType == TransferType.SUBCONTRACT_INBOUND }

    val customerManifests: List<SuratJalanManifest>
        get() = manifests.filter { it.transferType == TransferType.CUSTOMER_DISPATCH }
}

sealed interface SuratJalanUiEvent {
    data object Load : SuratJalanUiEvent
    data class SelectTab(val tab: SuratJalanTab) : SuratJalanUiEvent
    data class CreateInternal(
        val sjNumber: String,
        val subject: WorkSubjectRef,
        val originLocationId: LocationId,
        val destinationLocationId: LocationId,
        val cardIds: List<WorkCardId>,
        val carrierName: String? = null,
        val driverName: String? = null,
        val vehiclePlate: String? = null,
        val notes: String = ""
    ) : SuratJalanUiEvent
    data class CreateMakloon(
        val sjNumber: String,
        val subject: WorkSubjectRef,
        val vendorRef: String,
        val cardIds: List<WorkCardId>,
        val unitServiceFeeIdr: Long,
        val expectedReturnDate: String,
        val carrierName: String? = null,
        val driverName: String? = null,
        val vehiclePlate: String? = null,
        val notes: String = ""
    ) : SuratJalanUiEvent
    data class CreateCustomerDispatch(
        val sjNumber: String,
        val subject: WorkSubjectRef,
        val customerName: String,
        val customerAddress: String,
        val totalOrderedPcs: Int,
        val previouslyShippedPcs: Int,
        val cartons: List<CustomerDispatchCartonInput>,
        val carrierName: String? = null,
        val driverName: String? = null,
        val vehiclePlate: String? = null,
        val notes: String = ""
    ) : SuratJalanUiEvent
    data class Receive(val manifestId: String, val receiverName: String, val notes: String = "") : SuratJalanUiEvent
    data object DismissMessage : SuratJalanUiEvent
}

class SuratJalanViewModel(
    private val client: SuratJalanRemoteDataSource = SuratJalanApiClient(),
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main)
) {
    private val _uiState = MutableStateFlow(SuratJalanUiState())
    val uiState: StateFlow<SuratJalanUiState> = _uiState.asStateFlow()

    init {
        onEvent(SuratJalanUiEvent.Load)
    }

    fun onEvent(event: SuratJalanUiEvent) {
        when (event) {
            is SuratJalanUiEvent.Load -> loadManifests()
            is SuratJalanUiEvent.SelectTab -> _uiState.update { it.copy(selectedTab = event.tab) }
            is SuratJalanUiEvent.DismissMessage -> _uiState.update { it.copy(error = null, successMessage = null) }
            is SuratJalanUiEvent.CreateInternal -> createInternal(event)
            is SuratJalanUiEvent.CreateMakloon -> createMakloon(event)
            is SuratJalanUiEvent.CreateCustomerDispatch -> createCustomerDispatch(event)
            is SuratJalanUiEvent.Receive -> receive(event)
        }
    }

    private fun loadManifests() {
        scope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            client.fetchManifests(null)
                .onSuccess { manifests ->
                    _uiState.update { it.copy(manifests = manifests, isLoading = false) }
                }
                .onFailure { err ->
                    _uiState.update { it.copy(error = err.message ?: "Gagal memuat Surat Jalan", isLoading = false) }
                }
        }
    }

    private fun createInternal(event: SuratJalanUiEvent.CreateInternal) {
        scope.launch {
            _uiState.update { it.copy(isSubmitting = true, error = null) }
            client.createInternalTransfer(
                sjNumber = event.sjNumber,
                subject = event.subject,
                originLocationId = event.originLocationId,
                destinationLocationId = event.destinationLocationId,
                cardIds = event.cardIds,
                carrierName = event.carrierName,
                driverName = event.driverName,
                vehiclePlate = event.vehiclePlate,
                notes = event.notes
            ).onSuccess { manifest ->
                _uiState.update {
                    it.copy(
                        manifests = listOf(manifest) + it.manifests,
                        isSubmitting = false,
                        successMessage = "Surat Jalan Mutasi Internal #${manifest.sjNumber.value} berhasil diterbitkan!"
                    )
                }
            }.onFailure { err ->
                _uiState.update { it.copy(isSubmitting = false, error = err.message ?: "Gagal membuat mutasi internal") }
            }
        }
    }

    private fun createMakloon(event: SuratJalanUiEvent.CreateMakloon) {
        scope.launch {
            _uiState.update { it.copy(isSubmitting = true, error = null) }
            client.createMakloonOutbound(
                sjNumber = event.sjNumber,
                subject = event.subject,
                vendorRef = event.vendorRef,
                cardIds = event.cardIds,
                unitServiceFeeIdr = event.unitServiceFeeIdr,
                expectedReturnDate = event.expectedReturnDate,
                carrierName = event.carrierName,
                driverName = event.driverName,
                vehiclePlate = event.vehiclePlate,
                notes = event.notes
            ).onSuccess { manifest ->
                _uiState.update {
                    it.copy(
                        manifests = listOf(manifest) + it.manifests,
                        isSubmitting = false,
                        successMessage = "Surat Jalan Makloon #${manifest.sjNumber.value} ke vendor ${manifest.vendorRef} berhasil diterbitkan!"
                    )
                }
            }.onFailure { err ->
                _uiState.update { it.copy(isSubmitting = false, error = err.message ?: "Gagal membuat makloon outbound") }
            }
        }
    }

    private fun createCustomerDispatch(event: SuratJalanUiEvent.CreateCustomerDispatch) {
        scope.launch {
            _uiState.update { it.copy(isSubmitting = true, error = null) }
            client.createCustomerDispatch(
                sjNumber = event.sjNumber,
                subject = event.subject,
                customerName = event.customerName,
                customerAddress = event.customerAddress,
                totalOrderedPcs = event.totalOrderedPcs,
                previouslyShippedPcs = event.previouslyShippedPcs,
                cartons = event.cartons,
                carrierName = event.carrierName,
                driverName = event.driverName,
                vehiclePlate = event.vehiclePlate,
                notes = event.notes
            ).onSuccess { result ->
                val statusText = if (result.isFullyShipped) "Semua pesanan tuntas terkirim!" else "Sisa backlog: ${result.remainingBacklogPcs} pcs"
                _uiState.update {
                    it.copy(
                        manifests = listOf(result.manifest) + it.manifests,
                        isSubmitting = false,
                        successMessage = "Surat Jalan Pengiriman #${result.manifest.sjNumber.value} berhasil diterbitkan! $statusText"
                    )
                }
            }.onFailure { err ->
                _uiState.update { it.copy(isSubmitting = false, error = err.message ?: "Gagal membuat pengiriman customer") }
            }
        }
    }

    private fun receive(event: SuratJalanUiEvent.Receive) {
        scope.launch {
            _uiState.update { it.copy(isSubmitting = true, error = null) }
            client.receiveManifest(event.manifestId, event.receiverName, event.notes)
                .onSuccess { updated ->
                    _uiState.update { state ->
                        state.copy(
                            manifests = state.manifests.map { if (it.id == updated.id) updated else it },
                            isSubmitting = false,
                            successMessage = "Serah terima Surat Jalan #${updated.sjNumber.value} berhasil diselesaikan!"
                        )
                    }
                }
                .onFailure { err ->
                    _uiState.update { it.copy(isSubmitting = false, error = err.message ?: "Gagal serah terima") }
                }
        }
    }
}
