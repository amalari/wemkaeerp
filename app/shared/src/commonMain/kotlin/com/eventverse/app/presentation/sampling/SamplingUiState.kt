package com.eventverse.app.presentation.sampling

import com.eventverse.app.domain.sampling.*

enum class SamplingMobileTab(val displayName: String) {
    INFO("Info & Desain"),
    SIZE("Ukuran Ganda"),
    MACHINE("Mesin & Feeder"),
    STATUS("Status Alur");
}

data class SamplingUiState(
    val orders: List<SamplingOrder> = emptyList(),
    val selectedOrderId: SamplingOrderId? = null,
    val activeMobileTab: SamplingMobileTab = SamplingMobileTab.INFO,
    val selectedStatusFilter: SamplingStatus? = null,
    val searchQuery: String = "",
    val isLoading: Boolean = false,
    val isSubmitting: Boolean = false,
    val statusMessage: String? = null,
    val isErrorMessage: Boolean = false,
    val isCreateDialogOpen: Boolean = false
) {
    val selectedOrder: SamplingOrder?
        get() = (selectedOrderId?.let { id -> orders.firstOrNull { it.id == id } } ?: orders.firstOrNull())

    val filteredOrders: List<SamplingOrder>
        get() = orders.filter { order ->
            val matchStatus = selectedStatusFilter == null || order.status == selectedStatusFilter
            val matchSearch = searchQuery.isBlank() ||
                order.clientName.contains(searchQuery, ignoreCase = true) ||
                order.styleName.contains(searchQuery, ignoreCase = true) ||
                order.spkNumber.value.contains(searchQuery, ignoreCase = true)
            matchStatus && matchSearch
        }
}

sealed interface SamplingUiEvent {
    data object Load : SamplingUiEvent
    data class SelectOrder(val orderId: SamplingOrderId) : SamplingUiEvent
    data class SelectMobileTab(val tab: SamplingMobileTab) : SamplingUiEvent
    data class SetFilter(val status: SamplingStatus?) : SamplingUiEvent
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
    data object DismissStatusMessage : SamplingUiEvent
}
