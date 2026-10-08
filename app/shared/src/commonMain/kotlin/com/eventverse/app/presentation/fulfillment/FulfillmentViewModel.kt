package com.eventverse.app.presentation.fulfillment

import com.eventverse.app.domain.fulfillment.FulfillmentRouteConfig
import com.eventverse.app.domain.fulfillment.HandoverMode
import com.eventverse.app.domain.fulfillment.HandoverProof
import com.eventverse.app.domain.fulfillment.HandoverRoute
import com.eventverse.app.domain.fulfillment.HandoverRouteCode
import com.eventverse.app.domain.fulfillment.HandoverRouteSetting
import com.eventverse.app.domain.fulfillment.HandoverRouteSettingsView
import com.eventverse.app.domain.fulfillment.InternalTransfer
import com.eventverse.app.domain.fulfillment.SackRoute
import com.eventverse.app.domain.fulfillment.SackTransferStatus
import com.eventverse.app.domain.fulfillment.toRouteCode
import com.eventverse.app.infrastructure.api.FulfillmentTransferApiClient
import com.eventverse.app.infrastructure.api.FulfillmentTransferRemoteDataSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FulfillmentUiState(
    val transfers: List<InternalTransfer> = emptyList(),
    val isLoading: Boolean = false,
    val isSubmitting: Boolean = false,
    val error: String? = null,
    /** Payload karung hasil scan/ketik yang sedang diproses di form pengajuan. */
    val scannedSack: String? = null,
    /** Konfigurasi rute lama (backward compatible). */
    val routeConfig: FulfillmentRouteConfig? = null,
    /** Tampilan konfigurasi rute serah terima berbasis data (TRD-FLOW-003). */
    val routeSettingsView: HandoverRouteSettingsView? = null
) {
    val pendingApproval get() = transfers.filter { it.status == SackTransferStatus.MENUNGGU_ACC }
    val inTransit get() = transfers.filter { it.status == SackTransferStatus.DIANTAR }
    val done get() = transfers.filter { it.status.isFinal }

    /** Daftar rute serah terima aktif milik tenant ini. Kosong bila tenant memang tidak memiliki rute. */
    val effectiveRoutes: List<HandoverRouteSetting>
        get() = routeSettingsView?.settings?.filter { it.route.active }?.sortedBy { it.route.sortOrder }
            ?: SackRoute.entries.map {
                HandoverRouteSetting(
                    route = HandoverRoute(it.toRouteCode(), it.displayName),
                    mode = modeFor(it),
                    isExplicit = false
                )
            }

    fun modeFor(routeCode: HandoverRouteCode): HandoverMode =
        routeSettingsView?.settings?.firstOrNull { it.route.code == routeCode }?.mode
            ?: (SackRoute.entries.firstOrNull { it.name == routeCode.value }?.let { modeFor(it) } ?: HandoverMode.ADMIN_HUB)

    fun modeFor(route: SackRoute): HandoverMode =
        routeConfig?.modeFor(route)
            ?: (routeSettingsView?.settings?.firstOrNull { it.route.code.value == route.name }?.mode ?: HandoverMode.ADMIN_HUB)

    /** Rute yang masih lewat meja admin; true selama konfigurasi belum dimuat. */
    val hasAdminHubRoute: Boolean
        get() = routeSettingsView?.hasAdminHubRoute
            ?: (routeConfig?.hasAdminHubRoute ?: true)

    /** Rute yang sah untuk wadah yang dipindai (berbasis data). */
    fun routesAccepting(isClosedSack: Boolean): List<HandoverRouteSetting> =
        effectiveRoutes.filter { isClosedSack || it.mode == HandoverMode.DIRECT }

    /** Rute yang sah untuk wadah yang dipindai (backward compatible enum). */
    fun routesAcceptingSackRoute(isClosedSack: Boolean): List<SackRoute> =
        routesAccepting(isClosedSack).mapNotNull { s ->
            SackRoute.entries.firstOrNull { it.name == s.route.code.value }
        }
}

sealed interface FulfillmentUiEvent {
    data object Load : FulfillmentUiEvent
    data class ScanSack(val payload: String) : FulfillmentUiEvent
    data object DismissScan : FulfillmentUiEvent
    data class SubmitTransfer(
        val sackPayload: String,
        val routeCode: HandoverRouteCode,
        val dispatchWeightKg: String?,
        val dispatchScalePhotoKey: String?,
        val requestedBy: String,
        val declaredPcs: Int? = null,
        val notes: String = "",
        val leg: SackRoute? = SackRoute.entries.firstOrNull { it.name == routeCode.value }
    ) : FulfillmentUiEvent {
        constructor(
            sackPayload: String,
            leg: SackRoute,
            dispatchWeightKg: String?,
            dispatchScalePhotoKey: String?,
            requestedBy: String,
            declaredPcs: Int? = null,
            notes: String = ""
        ) : this(
            sackPayload = sackPayload,
            routeCode = leg.toRouteCode(),
            dispatchWeightKg = dispatchWeightKg,
            dispatchScalePhotoKey = dispatchScalePhotoKey,
            requestedBy = requestedBy,
            declaredPcs = declaredPcs,
            notes = notes,
            leg = leg
        )
    }

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

    data class UploadEvidence(
        val fileName: String,
        val contentType: String,
        val bytes: ByteArray,
        val onUploaded: (Result<String>) -> Unit
    ) : FulfillmentUiEvent

    /** Mengubah mode rute serah terima (PUT /route-settings). */
    data class UpdateRouteModes(
        val modes: Map<HandoverRouteCode, HandoverMode>,
        val onDone: (Result<Unit>) -> Unit = {}
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
                    routeCode = event.routeCode,
                    dispatchWeightKg = event.dispatchWeightKg,
                    dispatchScalePhotoKey = event.dispatchScalePhotoKey,
                    requestedBy = event.requestedBy,
                    notes = event.notes,
                    declaredPcs = event.declaredPcs
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

            is FulfillmentUiEvent.UpdateRouteModes -> scope.launch {
                _uiState.update { it.copy(isSubmitting = true, error = null) }
                val result = remoteDataSource.updateRouteModes(tenantSlug, event.modes)
                result.onSuccess {
                    load()
                }.onFailure { err ->
                    _uiState.update { it.copy(isSubmitting = false, error = err.message) }
                }
                event.onDone(result)
            }
        }
    }

    private fun load() {
        _uiState.update { it.copy(isLoading = true, error = null) }
        scope.launch {
            remoteDataSource.routeSettingsView(tenantSlug)
                .onSuccess { view ->
                    _uiState.update { it.copy(routeSettingsView = view) }
                }
                .onFailure {
                    // Fallback bila server lama belum menyediakan routeSettingsView
                    remoteDataSource.routeSettings(tenantSlug)
                        .onSuccess { config -> _uiState.update { it.copy(routeConfig = config) } }
                }

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
