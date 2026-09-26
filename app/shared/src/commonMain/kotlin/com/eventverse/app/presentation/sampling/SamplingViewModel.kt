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
import kotlinx.datetime.Clock

class SamplingViewModel(
    private val tenantSlug: String,
    private val remoteDataSource: SamplingRemoteDataSource = SamplingApiClient(),
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main)
) {
    private val _uiState = MutableStateFlow(SamplingUiState())
    val uiState: StateFlow<SamplingUiState> = _uiState.asStateFlow()
    private val stageWork = SamplingStageWorkActions(tenantSlug, remoteDataSource, scope, _uiState)

    init {
        load()
    }

    fun onEvent(event: SamplingUiEvent) {
        when (event) {
            is SamplingUiEvent.Load -> load()
            is SamplingUiEvent.SelectOrder -> _uiState.update { it.copy(selectedOrderId = event.orderId) }
            is SamplingUiEvent.OpenSpkDetailDialog -> _uiState.update {
                it.copy(spkDetailTarget = event.order, spkDetailFocusFlow = event.focusFlow)
            }
            SamplingUiEvent.CloseSpkDetailDialog -> _uiState.update {
                it.copy(spkDetailTarget = null, spkDetailFocusFlow = false)
            }
            is SamplingUiEvent.SetFilter -> _uiState.update { it.copy(selectedStatusFilter = event.status) }
            is SamplingUiEvent.SetStageFilter -> _uiState.update { it.copy(selectedStageFilter = event.stage) }
            is SamplingUiEvent.UpdateSearchQuery -> _uiState.update { it.copy(searchQuery = event.query) }
            is SamplingUiEvent.OpenCreateDialog -> _uiState.update { it.copy(isCreateDialogOpen = true) }
            is SamplingUiEvent.CloseCreateDialog -> _uiState.update { it.copy(isCreateDialogOpen = false) }
            is SamplingUiEvent.CreateOrder -> createOrder(event)
            is SamplingUiEvent.ToggleMilestone -> toggleMilestone(event.orderId, event.step, event.isCompleted)
            is SamplingUiEvent.ApproveOrder -> approveOrder(event.orderId, event.isApproved, event.notes)
            is SamplingUiEvent.SaveTechnicalSpec -> saveTechnicalSpec(event.updatedOrder)
            is SamplingUiEvent.SaveFullOrder -> saveFullOrder(event.order)
            is SamplingUiEvent.SaveStageInput -> _uiState.value.orders.firstOrNull { it.id == event.orderId }?.let {
                saveFullOrder(it.fillStageInput(event.stage, event.sections, Clock.System.now()))
            }
            is SamplingUiEvent.DetermineFlow -> determineFlow(event.orderId)
            is SamplingUiEvent.AdvanceStage -> advanceStage(event.orderId, event.targetStage)
            is SamplingUiEvent.OpenStageAdvanceDialog -> _uiState.update {
                it.copy(stageAdvanceTarget = event.order, stageAdvanceTargetStage = event.targetStage)
            }
            SamplingUiEvent.CloseStageAdvanceDialog -> _uiState.update {
                it.copy(stageAdvanceTarget = null, stageAdvanceTargetStage = null)
            }
            is SamplingUiEvent.ConfirmStageAdvance -> confirmStageAdvance(
                event.orderId, event.targetStage, event.sections, event.inputStage
            )
            is SamplingUiEvent.AddFinishingDeposit -> addFinishingDeposit(event.orderId, event.deposit)
            is SamplingUiEvent.AssignMakloonVendor -> assignMakloonVendor(event.orderId, event.info)
            is SamplingUiEvent.ConfirmVendorReturn -> confirmVendorReturn(event.orderId, event.returnedAt)
            is SamplingUiEvent.SubmitQcInspection -> submitQcInspection(event.orderId, event.report)
            is SamplingUiEvent.RequestRevision -> requestRevision(event.orderId, event.notes)

            is SamplingUiEvent.OpenVendorDialog -> _uiState.update { it.copy(isVendorDialogOpen = true, targetOrderForAction = event.order) }
            is SamplingUiEvent.CloseVendorDialog -> _uiState.update { it.copy(isVendorDialogOpen = false, targetOrderForAction = null) }
            is SamplingUiEvent.OpenRevisionDialog -> _uiState.update { it.copy(isRevisionDialogOpen = true, targetOrderForAction = event.order) }
            is SamplingUiEvent.CloseRevisionDialog -> _uiState.update { it.copy(isRevisionDialogOpen = false, targetOrderForAction = null) }

            is SamplingUiEvent.StartStageWork -> stageWork.start(event.orderId, event.operatorName)
            is SamplingUiEvent.ReleaseStageWork -> stageWork.release(event.orderId)
            is SamplingUiEvent.OpenReworkDialog -> _uiState.update { it.copy(reworkTarget = event.order) }
            SamplingUiEvent.CloseReworkDialog -> _uiState.update { it.copy(reworkTarget = null) }
            is SamplingUiEvent.ConfirmRework ->
                stageWork.sendBackForRework(event.orderId, event.target, event.reason, event.liability)

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

    private fun saveFullOrder(order: SamplingOrder) {
        scope.launch {
            _uiState.update { it.copy(isSubmitting = true) }
            remoteDataSource.saveOrder(tenantSlug, order)
                .onSuccess { updated ->
                    _uiState.update { current ->
                        val newOrders = current.orders.map { if (it.id == updated.id) updated else it }
                        current.copy(
                            orders = newOrders,
                            isSubmitting = false,
                            statusMessage = "Perubahan SPK ${updated.spkNumber.value} berhasil disimpan",
                            isErrorMessage = false
                        )
                    }
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(
                            isSubmitting = false,
                            statusMessage = "Gagal menyimpan SPK: ${err.message}",
                            isErrorMessage = true
                        )
                    }
                }
        }
    }

    private fun determineFlow(orderId: SamplingOrderId) {
        val order = _uiState.value.orders.firstOrNull { it.id == orderId } ?: return
        if (order.pipelineStage != SamplingPipelineStage.NEW_INTAKE) return
        advanceStage(orderId, SamplingPipelineStage.FLOW_REVIEW)
        // Dialog memegang snapshot order; ikut dimajukan agar gerbangnya konsisten dengan kolom.
        _uiState.update { state ->
            state.copy(
                spkDetailTarget = state.spkDetailTarget
                    ?.takeIf { it.id == orderId }
                    ?.copy(pipelineStage = SamplingPipelineStage.FLOW_REVIEW)
                    ?: state.spkDetailTarget
            )
        }
    }

    private fun advanceStage(orderId: SamplingOrderId, targetStage: SamplingPipelineStage) {
        scope.launch {
            remoteDataSource.advanceStage(tenantSlug, orderId.value, targetStage)
                .onSuccess { updated ->
                    _uiState.update { current ->
                        val newOrders = current.orders.map { if (it.id == updated.id) updated else it }
                        current.copy(
                            orders = newOrders,
                            // Dialog Detail SPK yang terbuka ikut berganti tahap — "Mulai CAM"
                            // langsung mengunci alur dan membuka section Program di tempat.
                            spkDetailTarget = current.spkDetailTarget?.let { if (it.id == updated.id) updated else it },
                            statusMessage = "Tahapan SPK diperbarui ke ${targetStage.displayName}",
                            isErrorMessage = false
                        )
                    }
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(
                            statusMessage = "Gagal memperbarui tahapan: ${err.message}",
                            isErrorMessage = true
                        )
                    }
                }
        }
    }

    private fun confirmStageAdvance(
        orderId: SamplingOrderId,
        targetStage: SamplingPipelineStage,
        sections: List<StageInputSection>,
        inputStage: SamplingPipelineStage
    ) {
        scope.launch {
            _uiState.update { it.copy(isSubmitting = true) }
            remoteDataSource.advanceStage(
                tenantSlug = tenantSlug,
                orderId = orderId.value,
                targetStage = targetStage,
                stageInputs = listOf(StageWorkInput(stage = inputStage, sections = sections))
            ).onSuccess { updated ->
                _uiState.update { current ->
                    val newOrders = current.orders.map { if (it.id == updated.id) updated else it }
                    current.copy(
                        orders = newOrders,
                        isSubmitting = false,
                        stageAdvanceTarget = null,
                        stageAdvanceTargetStage = null,
                        spkDetailTarget = null,
                        spkDetailFocusFlow = false,
                        statusMessage = "Lembar kerja tersimpan — SPK masuk tahap ${targetStage.displayName}",
                        isErrorMessage = false
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isSubmitting = false,
                        statusMessage = "Gagal pindah tahap: ${err.message}",
                        isErrorMessage = true
                    )
                }
            }
        }
    }

    private fun addFinishingDeposit(orderId: SamplingOrderId, deposit: FinishingDeposit) {
        scope.launch {
            _uiState.update { it.copy(isSubmitting = true) }
            remoteDataSource.addFinishingDeposit(tenantSlug, orderId.value, deposit)
                .onSuccess { updated ->
                    _uiState.update { current ->
                        val newOrders = current.orders.map { if (it.id == updated.id) updated else it }
                        current.copy(
                            orders = newOrders,
                            isSubmitting = false,
                            targetOrderForAction = null,
                            statusMessage = "Setoran ${deposit.qtyPcs} pcs (${deposit.weightKg} kg) berhasil dicatat",
                            isErrorMessage = false
                        )
                    }
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(
                            isSubmitting = false,
                            statusMessage = "Gagal mencatat setoran: ${err.message}",
                            isErrorMessage = true
                        )
                    }
                }
        }
    }

    private fun assignMakloonVendor(orderId: SamplingOrderId, info: MakloonVendorInfo) {
        scope.launch {
            _uiState.update { it.copy(isSubmitting = true) }
            remoteDataSource.assignMakloonVendor(tenantSlug, orderId.value, info)
                .onSuccess { updated ->
                    _uiState.update { current ->
                        val newOrders = current.orders.map { if (it.id == updated.id) updated else it }
                        current.copy(
                            orders = newOrders,
                            isSubmitting = false,
                            isVendorDialogOpen = false,
                            targetOrderForAction = null,
                            statusMessage = "SPK berhasil dialihkan ke vendor ${info.vendorName}",
                            isErrorMessage = false
                        )
                    }
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(
                            isSubmitting = false,
                            statusMessage = "Gagal menugaskan vendor: ${err.message}",
                            isErrorMessage = true
                        )
                    }
                }
        }
    }

    private fun confirmVendorReturn(orderId: SamplingOrderId, returnedAt: kotlinx.datetime.LocalDate?) {
        scope.launch {
            _uiState.update { it.copy(isSubmitting = true) }
            remoteDataSource.confirmVendorReturn(tenantSlug, orderId.value, returnedAt)
                .onSuccess { updated ->
                    _uiState.update { current ->
                        val newOrders = current.orders.map { if (it.id == updated.id) updated else it }
                        current.copy(
                            orders = newOrders,
                            isSubmitting = false,
                            statusMessage = "Konfirmasi barang kembali dari vendor diterima. Masuk ke Finishing & QC.",
                            isErrorMessage = false
                        )
                    }
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(
                            isSubmitting = false,
                            statusMessage = "Gagal konfirmasi dari vendor: ${err.message}",
                            isErrorMessage = true
                        )
                    }
                }
        }
    }

    private fun submitQcInspection(orderId: SamplingOrderId, report: QcInspectionReport) {
        scope.launch {
            _uiState.update { it.copy(isSubmitting = true) }
            remoteDataSource.submitQcInspection(tenantSlug, orderId.value, report)
                .onSuccess { updated ->
                    _uiState.update { current ->
                        val newOrders = current.orders.map { if (it.id == updated.id) updated else it }
                        current.copy(
                            orders = newOrders,
                            isSubmitting = false,
                            targetOrderForAction = null,
                            statusMessage = "Laporan inspeksi QC tersimpan (${report.qcResult.displayName})",
                            isErrorMessage = false
                        )
                    }
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(
                            isSubmitting = false,
                            statusMessage = "Gagal menyimpan laporan QC: ${err.message}",
                            isErrorMessage = true
                        )
                    }
                }
        }
    }

    private fun requestRevision(orderId: SamplingOrderId, notes: String) {
        scope.launch {
            _uiState.update { it.copy(isSubmitting = true) }
            remoteDataSource.requestRevision(tenantSlug, orderId.value, notes)
                .onSuccess { updated ->
                    _uiState.update { current ->
                        val newOrders = current.orders.map { if (it.id == updated.id) updated else it }
                        current.copy(
                            orders = newOrders,
                            isSubmitting = false,
                            isRevisionDialogOpen = false,
                            targetOrderForAction = null,
                            statusMessage = "Revisi berhasil diajukan. SPK kembali ke tahap Program CAM (Rev ${updated.revisionCount})",
                            isErrorMessage = false
                        )
                    }
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(
                            isSubmitting = false,
                            statusMessage = "Gagal mengajukan revisi: ${err.message}",
                            isErrorMessage = true
                        )
                    }
                }
        }
    }
}
