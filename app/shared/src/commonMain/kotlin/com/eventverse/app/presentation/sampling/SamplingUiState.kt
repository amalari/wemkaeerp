package com.eventverse.app.presentation.sampling

import com.eventverse.app.domain.masterdata.MaterialItem
import com.eventverse.app.domain.sampling.*
import com.eventverse.app.domain.sampling.SamplingRoute
import com.eventverse.app.domain.stageflow.StageCode
import com.eventverse.app.domain.stageflow.StageDefinition
import kotlinx.datetime.LocalDate

data class SamplingUiState(
    val orders: List<SamplingOrder> = emptyList(),
    val availableMaterials: List<MaterialItem> = emptyList(),
    val selectedOrderId: SamplingOrderId? = null,
    /**
     * SPK yang sedang membuka dialog detail — dibuka saat kartu di kolom "SPK Baru"
     * diklik. Dialog ini sekaligus meja persiapan Program CAM tim sampling.
     * Null = tidak ada dialog detail terbuka.
     */
    val spkDetailTarget: SamplingOrder? = null,
    val spkDetailFocusFlow: Boolean = false,
    val spkDetailFocusCam: Boolean = false,
    val selectedStatusFilter: SamplingStatus? = null,
    val selectedStageFilter: StageCode? = null,
    /** Kerangka tahap pabrik — sumber kolom papan (TRD-FLOW-001); rajut sampai berhasil dimuat. */
    val stageFlow: List<StageDefinition> = SamplingRoute.DEFAULT_STAGES,
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
    val stageAdvanceTargetStage: StageCode? = null,
    /** SPK yang sedang membuka dialog kirim rework di meja operator. */
    val reworkTarget: SamplingOrder? = null,
    /** Status autosave draft lembar kerja di dialog Detail SPK. */
    val draftSave: DraftSaveState = DraftSaveState(),
    /**
     * SPK yang kartu A6-nya harus dibuka — diisi hanya setelah pindah tahap sukses, supaya kartu
     * tidak pernah keluar untuk SPK yang gagal maju tahap. Dikonsumsi layar lalu di-reset.
     */
    val spkCardToPrint: SamplingOrderId? = null,
    /** Kustodi penyimpanan: dialog simpan (dari Pengemasan) dan dialog rilis kirim. */
    val storage: SamplingStorageUiState = SamplingStorageUiState()
) {
    val selectedOrder: SamplingOrder?
        get() = (selectedOrderId?.let { id -> orders.firstOrNull { it.id == id } } ?: orders.firstOrNull())

    val filteredOrders: List<SamplingOrder>
        get() = orders.filter { order ->
            val matchStatus = selectedStatusFilter == null || order.status == selectedStatusFilter
            val matchStage = selectedStageFilter == null || order.stageCode == selectedStageFilter
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
    data class OpenSpkDetailDialog(
        val order: SamplingOrder,
        val focusFlow: Boolean = false,
        val focusCam: Boolean = false
    ) : SamplingUiEvent
    data object CloseSpkDetailDialog : SamplingUiEvent
    data class SetFilter(val status: SamplingStatus?) : SamplingUiEvent
    data class SetStageFilter(val stage: StageCode?) : SamplingUiEvent
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
    data class AdvanceStage(val orderId: SamplingOrderId, val targetStage: StageCode) : SamplingUiEvent
    data class OpenStageAdvanceDialog(val order: SamplingOrder, val targetStage: StageCode) : SamplingUiEvent
    data object CloseStageAdvanceDialog : SamplingUiEvent
    data class ConfirmStageAdvance(
        val orderId: SamplingOrderId,
        val targetStage: StageCode,
        val sections: List<StageInputSection>,
        /** Tahap pemilik lembar; lembar Program CAM disimpan di CAM walau tujuannya Mesin Rajut. */
        val inputStage: StageCode = targetStage,
        /** Buka Kartu SPK A6 setelah server mengonfirmasi pindah tahap (CAM → lantai produksi). */
        val openSpkCardOnSuccess: Boolean = false
    ) : SamplingUiEvent
    /** Kartu SPK A6 yang diminta sudah dibuka oleh layar. */
    data object SpkCardPrintHandled : SamplingUiEvent
    data class AddFinishingDeposit(val orderId: SamplingOrderId, val deposit: FinishingDeposit) : SamplingUiEvent
    data class AssignMakloonVendor(val orderId: SamplingOrderId, val info: MakloonVendorInfo) : SamplingUiEvent
    data class ConfirmVendorReturn(val orderId: SamplingOrderId, val returnedAt: LocalDate? = null) : SamplingUiEvent
    data class SubmitQcInspection(val orderId: SamplingOrderId, val report: QcInspectionReport) : SamplingUiEvent
    data class RequestRevision(val orderId: SamplingOrderId, val notes: String) : SamplingUiEvent
    data class SaveFullOrder(val order: SamplingOrder) : SamplingUiEvent
    /** Draft lembar kerja satu tahap berubah — di-autosave (debounce) tanpa memindahkan tahap. */
    data class SaveStageInput(
        val orderId: SamplingOrderId,
        val stage: StageCode,
        val sections: List<StageInputSection>
    ) : SamplingUiEvent

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
        val target: StageCode,
        val reason: String,
        val liability: com.eventverse.app.domain.pipeline.DefectLiability
    ) : SamplingUiEvent

    // Kustodi penyimpanan (Pengemasan -> Penyimpanan -> Terkirim)
    data class OpenStoreDialog(val order: SamplingOrder) : SamplingUiEvent
    data class ConfirmStore(val orderId: SamplingOrderId, val locationLabel: String, val qtyPcs: Int) : SamplingUiEvent
    data class OpenReleaseDialog(val order: SamplingOrder) : SamplingUiEvent
    data class ConfirmRelease(val orderId: SamplingOrderId, val partialReason: String?) : SamplingUiEvent
    data object CloseStorageDialog : SamplingUiEvent

    data object DismissStatusMessage : SamplingUiEvent
    /** Kerangka pabrik disunting di editor — papan langsung memakai kolom barunya. */
    data class StageFlowUpdated(val stages: List<StageDefinition>) : SamplingUiEvent
}
