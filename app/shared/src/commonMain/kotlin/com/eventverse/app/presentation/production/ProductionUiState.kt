package com.eventverse.app.presentation.production

import com.eventverse.app.domain.production.BulkProductionStatus
import com.eventverse.app.domain.production.BulkWorkOrder
import com.eventverse.app.domain.production.BulkWorkOrderId
import com.eventverse.app.domain.production.MachineLineAllocation
import com.eventverse.app.domain.production.ProductionLineName
import com.eventverse.app.domain.production.ProductionStage

data class ProductionUiState(
    val workOrders: List<BulkWorkOrder> = emptyList(),
    val selectedWorkOrderId: BulkWorkOrderId? = null,
    val statusFilter: BulkProductionStatus? = null,
    val searchQuery: String = "",
    val isLoading: Boolean = false,
    val isSubmitting: Boolean = false,
    val statusMessage: String? = null,
    val isErrorMessage: Boolean = false,
    val isAllocateDialogOpen: Boolean = false,
    val isProgressDialogOpen: Boolean = false,
    val progressDialogStage: ProductionStage = ProductionStage.CUTTING
) {
    val selectedWorkOrder: BulkWorkOrder?
        get() = selectedWorkOrderId?.let { id -> workOrders.firstOrNull { it.id == id } }
            ?: workOrders.firstOrNull()

    val filteredWorkOrders: List<BulkWorkOrder>
        get() = workOrders.filter { order ->
            val matchStatus = statusFilter == null || order.status == statusFilter
            val matchSearch = searchQuery.isBlank() ||
                order.clientName.contains(searchQuery, ignoreCase = true) ||
                order.styleName.contains(searchQuery, ignoreCase = true) ||
                order.spkNumber.value.contains(searchQuery, ignoreCase = true)
            matchStatus && matchSearch
        }

    /** Ringkasan lantai produksi untuk baris KPI di puncak layar. */
    val activeOrders: List<BulkWorkOrder> get() = workOrders.filter { !it.status.isTerminal }
    val totalWip: Int get() = activeOrders.sumOf { it.wipPieces }
    val totalOrderedPcs: Int get() = activeOrders.sumOf { it.totalOrderedPcs }
    val totalCompletedPcs: Int get() = activeOrders.sumOf { it.completedPcs }
    val totalRejectPcs: Int get() = activeOrders.sumOf { it.totalRejectPcs }
}

sealed interface ProductionUiEvent {
    data object Load : ProductionUiEvent
    data class SelectWorkOrder(val id: BulkWorkOrderId) : ProductionUiEvent
    data class SetStatusFilter(val status: BulkProductionStatus?) : ProductionUiEvent
    data class UpdateSearchQuery(val query: String) : ProductionUiEvent

    data class OpenAllocateDialog(val id: BulkWorkOrderId) : ProductionUiEvent
    data object CloseAllocateDialog : ProductionUiEvent
    data class AllocateLine(
        val id: BulkWorkOrderId,
        val allocation: MachineLineAllocation
    ) : ProductionUiEvent
    data class RemoveLine(val id: BulkWorkOrderId, val lineName: ProductionLineName) : ProductionUiEvent

    data class OpenProgressDialog(val id: BulkWorkOrderId, val stage: ProductionStage) : ProductionUiEvent
    data object CloseProgressDialog : ProductionUiEvent
    data class RecordProgress(
        val id: BulkWorkOrderId,
        val stage: ProductionStage,
        val completedPcs: Int,
        val reworkPcs: Int,
        val rejectPcs: Int
    ) : ProductionUiEvent

    data object DismissStatusMessage : ProductionUiEvent
}
