package com.eventverse.app.presentation.sampling

import com.eventverse.app.domain.sampling.*
import kotlinx.datetime.LocalDate

data class SamplingUiState(
    val orders: List<SamplingOrder> = emptyList(),
    val selectedOrderId: SamplingOrderId? = null,
    /**
     * SPK yang sedang membuka dialog detail — dibuka saat kartu di kolom "SPK Baru"
     * diklik. Dialog ini sekaligus meja persiapan Program CAM tim sampling.
     * Null = tidak ada dialog detail terbuka.
     */
    val spkDetailTarget: SamplingOrder? = null,
    val spkDetailFocusFlow: Boolean = false,
    val selectedStatusFilter: SamplingStatus? = null,
    val selectedStageFilter: SamplingPipelineStage? = null,
    val searchQuery: String = "",
    val isLoading: Boolean = false,
    val isSubmitting: Boolean = false,
    val statusMessage: String? = null,
    val isErrorMessage: Boolean = false,
    val isCreateDialogOpen: Boolean = false,
    val isVendorDialogOpen: Boolean = false,
    val isRevisionDialogOpen: Boolean = false,
    val targetOrderForAction: SamplingOrder? = null,
    /**
     * Tahap tujuan yang sedang membuka dialog input dinamis (CAM -> Rajut, Rajut -> Finishing).
     * Null = tidak ada dialog tahap terbuka.
     */
    val stageAdvanceTarget: SamplingOrder? = null,
    val stageAdvanceTargetStage: SamplingPipelineStage? = null,
    /** SPK yang sedang membuka dialog kirim rework di meja operator. */
    val reworkTarget: SamplingOrder? = null
) {
    val selectedOrder: SamplingOrder?
        get() = (selectedOrderId?.let { id -> orders.firstOrNull { it.id == id } } ?: orders.firstOrNull())

    val filteredOrders: List<SamplingOrder>
        get() = orders.filter { order ->
            val matchStatus = selectedStatusFilter == null || order.status == selectedStatusFilter
            val matchStage = selectedStageFilter == null || order.pipelineStage == selectedStageFilter
            val matchSearch = searchQuery.isBlank() ||
                order.clientName.contains(searchQuery, ignoreCase = true) ||
                order.styleName.contains(searchQuery, ignoreCase = true) ||
                order.spkNumber.value.contains(searchQuery, ignoreCase = true) ||
                order.vendorInfo.vendorName.contains(searchQuery, ignoreCase = true)
            matchStatus && matchStage && matchSearch
        }

    val ordersWithVendor: List<SamplingOrder>
        get() = orders.filter { it.finishingPath == FinishingPath.MAKLOON_VENDOR && it.vendorInfo.status != VendorFollowUpStatus.NONE }
}

sealed interface SamplingUiEvent {
    data object Load : SamplingUiEvent
    data class SelectOrder(val orderId: SamplingOrderId) : SamplingUiEvent
    data class OpenSpkDetailDialog(val order: SamplingOrder, val focusFlow: Boolean = false) : SamplingUiEvent
    data object CloseSpkDetailDialog : SamplingUiEvent
    data class SetFilter(val status: SamplingStatus?) : SamplingUiEvent
    data class SetStageFilter(val stage: SamplingPipelineStage?) : SamplingUiEvent
    data class UpdateSearchQuery(val query: String) : SamplingUiEvent
    data object OpenCreateDialog : SamplingUiEvent
    data object CloseCreateDialog : SamplingUiEvent
    data class CreateOrder(
        val clientName: String,
        val styleName: String,
        val sizeMode: SizeMode = SizeMode.ALL_SIZE,
        val useFactoryPreset: Boolean = true
    ) : SamplingUiEvent
    data class ToggleMilestone(
        val orderId: SamplingOrderId,
        val step: MilestoneStep,
        val isCompleted: Boolean
    ) : SamplingUiEvent
    data class ApproveOrder(
        val orderId: SamplingOrderId,
        val isApproved: Boolean,
        val notes: String
    ) : SamplingUiEvent
    data class SaveTechnicalSpec(val updatedOrder: SamplingOrder) : SamplingUiEvent
    /** "Tentukan Alur Desain": SPK Masuk pindah ke kolom Penentuan Alur; tahap lain tidak disentuh. */
    data class DetermineFlow(val orderId: SamplingOrderId) : SamplingUiEvent
    data class AdvanceStage(val orderId: SamplingOrderId, val targetStage: SamplingPipelineStage) : SamplingUiEvent
    data class OpenStageAdvanceDialog(val order: SamplingOrder, val targetStage: SamplingPipelineStage) : SamplingUiEvent
    data object CloseStageAdvanceDialog : SamplingUiEvent
    data class ConfirmStageAdvance(
        val orderId: SamplingOrderId,
        val targetStage: SamplingPipelineStage,
        val sections: List<StageInputSection>,
        /** Tahap pemilik lembar; lembar Program CAM disimpan di CAM walau tujuannya Mesin Rajut. */
        val inputStage: SamplingPipelineStage = targetStage
    ) : SamplingUiEvent
    data class AddFinishingDeposit(val orderId: SamplingOrderId, val deposit: FinishingDeposit) : SamplingUiEvent
    data class AssignMakloonVendor(val orderId: SamplingOrderId, val info: MakloonVendorInfo) : SamplingUiEvent
    data class ConfirmVendorReturn(val orderId: SamplingOrderId, val returnedAt: LocalDate? = null) : SamplingUiEvent
    data class SubmitQcInspection(val orderId: SamplingOrderId, val report: QcInspectionReport) : SamplingUiEvent
    data class RequestRevision(val orderId: SamplingOrderId, val notes: String) : SamplingUiEvent
    data class SaveFullOrder(val order: SamplingOrder) : SamplingUiEvent

    data class OpenVendorDialog(val order: SamplingOrder) : SamplingUiEvent
    data object CloseVendorDialog : SamplingUiEvent
    data class OpenRevisionDialog(val order: SamplingOrder) : SamplingUiEvent
    data object CloseRevisionDialog : SamplingUiEvent

    // Meja operator lantai produksi (Antrian / Sedang Dikerjakan / Selesai)
    data class StartStageWork(val orderId: SamplingOrderId, val operatorName: String) : SamplingUiEvent
    data class ReleaseStageWork(val orderId: SamplingOrderId) : SamplingUiEvent
    data class OpenReworkDialog(val order: SamplingOrder) : SamplingUiEvent
    data object CloseReworkDialog : SamplingUiEvent
    data class ConfirmRework(
        val orderId: SamplingOrderId,
        val target: SamplingPipelineStage,
        val reason: String,
        val liability: com.eventverse.app.domain.pipeline.DefectLiability
    ) : SamplingUiEvent

    data object DismissStatusMessage : SamplingUiEvent
}
