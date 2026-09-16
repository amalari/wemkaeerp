package com.eventverse.app.presentation.deal

import com.eventverse.app.domain.deal.Deal
import com.eventverse.app.domain.deal.DealStage
import com.eventverse.app.domain.deal.PurchaseOrder
import com.eventverse.app.domain.sampling.SamplingOrder

/** Tab di dalam dialog detail deal: siklus sampling vs produksi massal & PO. */
enum class DealDetailTab(val label: String) {
    SAMPLING("Siklus Sampling"),
    MASS_PRODUCTION("Produksi Massal & PO")
}

data class DealUiState(
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val error: String? = null,
    val statusMessage: String? = null,
    val deal: Deal? = null,
    val contactName: String? = null,
    val contactPhone: String? = null,
    val contactEmail: String? = null,
    val purchaseOrders: List<PurchaseOrder> = emptyList(),
    val samplingOrders: List<SamplingOrder> = emptyList(),
    val activeTab: DealDetailTab = DealDetailTab.SAMPLING,
    val canWrite: Boolean = true
) {
    val hasDeal: Boolean get() = deal != null

    /** Desain aktif = belum ACC dan tidak dibatalkan — inilah yang mengunci Tab Produksi. */
    val activeDesigns: List<SamplingOrder>
        get() = samplingOrders.filter { it.isActiveDesign }

    /** Desain yang sudah di-ACC buyer — acuan Golden Sample Tab Produksi. */
    val approvedDesigns: List<SamplingOrder>
        get() = samplingOrders.filter { it.isAccApproved }

    /** Gerbang Tab Produksi Massal: terbuka hanya jika tidak ada desain aktif tersisa. */
    val productionUnlocked: Boolean
        get() = samplingOrders.isNotEmpty() && activeDesigns.isEmpty()
}

sealed interface DealUiEvent {
    data object Load : DealUiEvent
    data object DismissError : DealUiEvent
    data object DismissStatusMessage : DealUiEvent
    data class ChangeStage(val stage: DealStage) : DealUiEvent
    data class AttachManualPo(
        val poNumber: String,
        val description: String,
        val quantity: Double,
        val unitPriceIdr: Long
    ) : DealUiEvent
    data object UploadPoFile : DealUiEvent
    data class OpenPoDownload(val poId: String) : DealUiEvent

    // ── Siklus sampling (Tab 1) ──────────────────────────────────────────────────────────────
    data class SelectDealTab(val tab: DealDetailTab) : DealUiEvent

    /** [samplingOrderId] null = tambah desain baru; terisi = perbarui quantity/kurir/biaya. */
    data class SaveSamplingOrder(
        val samplingOrderId: String?,
        val styleName: String,
        val sampleQuantity: Int,
        val courierTracking: String?,
        val samplingFeeIdr: Long,
        val notes: String
    ) : DealUiEvent

    data class ToggleSampleAcc(
        val samplingId: String,
        val isApproved: Boolean,
        val notes: String
    ) : DealUiEvent

    /** Membuka picker foto platform lalu mengunggahnya sebagai mockup desain ini. */
    data class UploadSamplingMockup(val samplingId: String) : DealUiEvent
}
