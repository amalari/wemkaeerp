package com.eventverse.app.presentation.fulfillment

import com.eventverse.app.infrastructure.api.FulfillmentTransferApiClient
import com.eventverse.app.infrastructure.api.FulfillmentTransferRemoteDataSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

import com.eventverse.app.domain.fulfillment.HandoverProof
import com.eventverse.app.domain.fulfillment.InternalTransfer
import com.eventverse.app.domain.fulfillment.TransferLeg

data class FulfillmentUiState(
    val transfers: List<InternalTransfer> = emptyList(),
    val isLoading: Boolean = false,
    val isSubmitting: Boolean = false,
    val error: String? = null,
    /** Payload karung hasil scan/ketik yang sedang diproses di form pengajuan. */
    val scannedSack: String? = null
) {
    val pendingApproval get() = transfers.filter { it.status.name == "MENUNGGU_ACC" }
    val inTransit get() = transfers.filter { it.status.name == "DIANTAR" }
    val done get() = transfers.filter { it.status.isFinal }
}

sealed interface FulfillmentUiEvent {
    data object Load : FulfillmentUiEvent
    data class ScanSack(val payload: String) : FulfillmentUiEvent
    data object DismissScan : FulfillmentUiEvent
    data class SubmitTransfer(
        val sackPayload: String,
        val leg: TransferLeg,
        val dispatchWeightKg: String,
        val dispatchScalePhotoKey: String,
        val requestedBy: String,
        val notes: String = ""
    ) : FulfillmentUiEvent

    data class Approve(val transferId: String, val approverName: String, val signatureKey: String) : FulfillmentUiEvent
    data class Reject(val transferId: String, val reason: String, val approverName: String) : FulfillmentUiEvent
    data class Resubmit(
        val transferId: String,
        val dispatchWeightKg: String,
        val dispatchScalePhotoKey: String,
        val requestedBy: String
    ) : FulfillmentUiEvent

    data class Receive(
        val transferId: String,
        val proof: HandoverProof,
        val receivedWeightKg: String?,
        val receivedPcs: Int?,
        val recordedBy: String
    ) : FulfillmentUiEvent

    /** Unggah bukti; hasilnya dikembalikan lewat callback supaya form tetap satu tempat. */
    data class UploadEvidence(
        val fileName: String,
        val contentType: String,
        val bytes: ByteArray,
        val onUploaded: (Result<String>) -> Unit
    ) : FulfillmentUiEvent
}

class FulfillmentViewModel(
    private val tenantSlug: String,
    private val remoteDataSource: FulfillmentTransferRemoteDataSource = FulfillmentTransferApiClient(),
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main)
) {
    private val _uiState = MutableStateFlow(FulfillmentUiState())
    val uiState: StateFlow<FulfillmentUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun onEvent(event: FulfillmentUiEvent) {
        when (event) {
            FulfillmentUiEvent.Load -> load()
            FulfillmentUiEvent.DismissScan -> _uiState.update { it.copy(scannedSack = null) }
            is FulfillmentUiEvent.ScanSack -> _uiState.update { it.copy(scannedSack = event.payload, error = null) }
            is FulfillmentUiEvent.SubmitTransfer -> launchAction {
                remoteDataSource.submit(
                    tenantSlug = tenantSlug,
                    sackPayload = event.sackPayload,
                    leg = event.leg,
                    dispatchWeightKg = event.dispatchWeightKg,
                    dispatchScalePhotoKey = event.dispatchScalePhotoKey,
                    requestedBy = event.requestedBy,
                    notes = event.notes
                )
            }

            is FulfillmentUiEvent.Approve -> launchAction {
                remoteDataSource.approve(tenantSlug, event.transferId, event.approverName, event.signatureKey)
            }

            is FulfillmentUiEvent.Reject -> launchAction {
                remoteDataSource.reject(tenantSlug, event.transferId, event.reason, event.approverName)
            }

            is FulfillmentUiEvent.Resubmit -> launchAction {
                remoteDataSource.resubmit(
                    tenantSlug, event.transferId,
                    event.dispatchWeightKg, event.dispatchScalePhotoKey, event.requestedBy
                )
            }

            is FulfillmentUiEvent.Receive -> launchAction {
                remoteDataSource.receive(
                    tenantSlug, event.transferId, event.proof,
                    event.receivedWeightKg, event.receivedPcs, event.recordedBy
                )
            }

            is FulfillmentUiEvent.UploadEvidence -> scope.launch {
                val result = remoteDataSource.uploadEvidence(
                    tenantSlug, event.fileName, event.contentType, event.bytes
                )
                event.onUploaded(result)
            }
        }
    }

    private fun load() {
        _uiState.update { it.copy(isLoading = true, error = null) }
        scope.launch {
            remoteDataSource.transfers(tenantSlug)
                .onSuccess { rows ->
                    _uiState.update { it.copy(transfers = rows, isLoading = false) }
                }
                .onFailure { failure ->
                    _uiState.update { it.copy(isLoading = false, error = failure.message) }
                }
        }
    }

    private fun launchAction(action: suspend () -> Result<InternalTransfer>) {
        _uiState.update { it.copy(isSubmitting = true, error = null) }
        scope.launch {
            action()
                .onSuccess {
                    _uiState.update { it.copy(isSubmitting = false, scannedSack = null) }
                    load()
                }
                .onFailure { failure ->
                    _uiState.update { it.copy(isSubmitting = false, error = failure.message) }
                }
        }
    }
}
