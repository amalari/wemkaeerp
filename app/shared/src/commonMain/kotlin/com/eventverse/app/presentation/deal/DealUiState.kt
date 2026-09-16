package com.eventverse.app.presentation.deal

import com.eventverse.app.domain.deal.Deal
import com.eventverse.app.domain.deal.DealStage
import com.eventverse.app.domain.deal.PurchaseOrder

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
    val canWrite: Boolean = true
) {
    val hasDeal: Boolean get() = deal != null
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
}
