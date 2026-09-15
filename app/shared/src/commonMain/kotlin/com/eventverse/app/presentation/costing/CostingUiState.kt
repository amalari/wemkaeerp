package com.eventverse.app.presentation.costing

import com.eventverse.app.domain.costing.CostingDrift
import com.eventverse.app.domain.costing.CostingNodeTelemetry
import com.eventverse.app.domain.costing.CostingRateCard
import com.eventverse.app.domain.costing.CostingSheet
import com.eventverse.app.domain.costing.CostingSheetStatus
import com.eventverse.app.domain.pipeline.CostingBehavior

enum class CostingWorkbenchTab(val label: String) {
    DETAIL("Rincian HPP"),
    BUCKETS("Waterfall Biaya"),
    DRIFT("Deteksi Drift"),
    RATE_CARD("Rate Card Tenant"),
    SIMULATION("Simulasi Cepat")
}

enum class CostingMobileTab(val label: String) {
    LIST("Daftar"),
    DETAIL("Rincian"),
    RATE_CARD("Rate Card")
}

data class CostingUiState(
    val isLoading: Boolean = false,
    val statusMessage: String? = null,
    val isErrorMessage: Boolean = false,
    val sheets: List<CostingSheet> = emptyList(),
    val selectedSheet: CostingSheet? = null,
    val selectedStatusFilter: CostingSheetStatus? = null,
    val searchQuery: String = "",
    val activeWorkbenchTab: CostingWorkbenchTab = CostingWorkbenchTab.DETAIL,
    val activeMobileTab: CostingMobileTab = CostingMobileTab.LIST,
    val activeRateCard: CostingRateCard? = null,
    val rateCardBehavior: CostingBehavior = CostingBehavior.FULL_PACKAGE_COGS,
    val telemetry: CostingNodeTelemetry? = null,
    val drift: CostingDrift? = null,
    val canApprove: Boolean = false,
    val showMargin: Boolean = false,
    val canWrite: Boolean = false,

    // Dialog flags
    val isCreateDialogOpen: Boolean = false,
    val isOverrideParamsDialogOpen: Boolean = false,
    val isRejectDialogOpen: Boolean = false,
    val isRateCardDialogOpen: Boolean = false
) {
    val filteredSheets: List<CostingSheet>
        get() = sheets.filter { sheet ->
            val matchesFilter = selectedStatusFilter == null || sheet.status == selectedStatusFilter
            val matchesSearch = searchQuery.isBlank() ||
                sheet.number.value.contains(searchQuery, ignoreCase = true) ||
                sheet.techPackId.contains(searchQuery, ignoreCase = true)
            matchesFilter && matchesSearch
        }
}

sealed interface CostingUiEvent {
    data object Load : CostingUiEvent
    data class SelectSheet(val sheet: CostingSheet) : CostingUiEvent
    data class SetStatusFilter(val status: CostingSheetStatus?) : CostingUiEvent
    data class SetSearchQuery(val query: String) : CostingUiEvent
    data class SelectWorkbenchTab(val tab: CostingWorkbenchTab) : CostingUiEvent
    data class SelectMobileTab(val tab: CostingMobileTab) : CostingUiEvent
    data class SelectRateCardBehavior(val behavior: CostingBehavior) : CostingUiEvent
    data object DismissStatusMessage : CostingUiEvent

    // Actions
    data object OpenCreateDialog : CostingUiEvent
    data object CloseCreateDialog : CostingUiEvent
    data class CreateDraft(val techPackId: String, val orderQuantity: Long, val behavior: CostingBehavior) : CostingUiEvent

    data class Calculate(val sheetId: String) : CostingUiEvent
    data class Reprice(val sheetId: String) : CostingUiEvent

    data object OpenOverrideParamsDialog : CostingUiEvent
    data object CloseOverrideParamsDialog : CostingUiEvent
    data class SaveOverrides(val sheetId: String, val overrides: Map<String, String>) : CostingUiEvent

    data class SubmitForApproval(val sheetId: String) : CostingUiEvent
    data class Approve(val sheetId: String) : CostingUiEvent

    data object OpenRejectDialog : CostingUiEvent
    data object CloseRejectDialog : CostingUiEvent
    data class Reject(val sheetId: String, val reason: String) : CostingUiEvent

    data class Revise(val sheetId: String) : CostingUiEvent
    data class CheckDrift(val sheetId: String) : CostingUiEvent

    data object OpenRateCardDialog : CostingUiEvent
    data object CloseRateCardDialog : CostingUiEvent
    data class SaveRateCard(val rateCard: CostingRateCard) : CostingUiEvent
}
