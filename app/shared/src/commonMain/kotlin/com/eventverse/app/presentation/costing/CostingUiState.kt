package com.eventverse.app.presentation.costing

import com.eventverse.app.domain.costing.CostingDrift
import com.eventverse.app.domain.costing.CostingNodeTelemetry
import com.eventverse.app.domain.costing.CostingRateCard
import com.eventverse.app.domain.costing.CostingSheet
import com.eventverse.app.domain.costing.CostingSheetStatus
import com.eventverse.app.domain.costing.CostingProductBenchmark
import com.eventverse.app.domain.costing.DesignVisionHints
import com.eventverse.app.domain.costing.GarmentSilhouette
import com.eventverse.app.domain.costing.KnitThickness
import com.eventverse.app.domain.costing.MaterialCharacter
import com.eventverse.app.domain.costing.QuickEstimateInput
import com.eventverse.app.domain.costing.QuickQuotationEstimateResult
import com.eventverse.app.domain.costing.TrimSpec
import com.eventverse.app.domain.pipeline.CostingBehavior

enum class CostingWorkbenchTab(val label: String) {
    DETAIL("Rincian HPP"),
    BUCKETS("Waterfall Biaya"),
    DRIFT("Deteksi Drift"),
    RATE_CARD("Rate Card Tenant"),
    SIMULATION("Simulasi Cepat"),
    QUICK_ESTIMATOR("Estimator Cepat (CS)"),
    HISTORICAL_BENCHMARKS("Knowledge Base");

    /**
     * Tab yang berdiri sendiri, tidak bergantung pada lembar HPP yang sedang dipilih.
     *
     * Tanpa penanda ini, CS yang membuka Estimator Cepat di tenant yang belum punya satu pun
     * lembar HPP hanya melihat "Pilih atau buat lembar HPP terlebih dahulu" — padahal justru
     * estimator-lah pintu masuk sebelum lembar HPP ada.
     */
    val isSheetIndependent: Boolean
        get() = this == QUICK_ESTIMATOR || this == HISTORICAL_BENCHMARKS
}

/**
 * Isian formulir estimator, dipisah dari [CostingUiState] supaya perubahan satu dropdown
 * tidak menyalin ulang seluruh daftar lembar HPP di setiap ketukan.
 */
data class QuickEstimatorFormState(
    val quantityText: String = "100",
    val materialCharacter: MaterialCharacter = MaterialCharacter.HANGAT_AKRILIK,
    val thickness: KnitThickness = KnitThickness.SEDANG,
    val silhouette: GarmentSilhouette = GarmentSilhouette.CARDIGAN_BUKAAN,
    val buttonCountText: String = "0",
    val hasWovenLabel: Boolean = true,
    val hasHangtag: Boolean = false,
    val notes: String = ""
) {
    val quantity: Long? get() = quantityText.trim().toLongOrNull()?.takeIf { it > 0L }
    val buttonCount: Int get() = buttonCountText.trim().toIntOrNull()?.coerceAtLeast(0) ?: 0
    val isValid: Boolean get() = quantity != null

    fun toInput(): QuickEstimateInput? = quantity?.let { qty ->
        QuickEstimateInput(
            orderQuantity = qty,
            materialCharacter = materialCharacter,
            thickness = thickness,
            silhouette = silhouette,
            trims = TrimSpec(
                buttonCount = buttonCount,
                hasWovenLabel = hasWovenLabel,
                hasHangtag = hasHangtag
            ),
            notes = notes.trim()
        )
    }
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

    // Estimator cepat (CS)
    val estimatorForm: QuickEstimatorFormState = QuickEstimatorFormState(),
    val estimatorResult: QuickQuotationEstimateResult? = null,
    val isEstimating: Boolean = false,
    val mockupImageUrl: String? = null,
    val mockupHints: DesignVisionHints? = null,
    val isAnalyzingMockup: Boolean = false,

    // Knowledge Base arsip historis
    val benchmarks: List<CostingProductBenchmark> = emptyList(),
    val isLoadingBenchmarks: Boolean = false,
    val benchmarkSearchQuery: String = "",
    val isImportingWorkbook: Boolean = false,

    // Dialog flags
    val isCreateDialogOpen: Boolean = false,
    val isOverrideParamsDialogOpen: Boolean = false,
    val isRejectDialogOpen: Boolean = false,
    val isRateCardDialogOpen: Boolean = false
) {
    val filteredBenchmarks: List<CostingProductBenchmark>
        get() = benchmarks.filter { benchmark ->
            benchmarkSearchQuery.isBlank() ||
                benchmark.styleName.contains(benchmarkSearchQuery, ignoreCase = true) ||
                benchmark.clientName.contains(benchmarkSearchQuery, ignoreCase = true) ||
                benchmark.structure.yarnType.contains(benchmarkSearchQuery, ignoreCase = true)
        }

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

    // Estimator cepat (CS)
    data class UpdateEstimatorForm(val form: QuickEstimatorFormState) : CostingUiEvent
    data object RunQuickEstimate : CostingUiEvent
    data object ResetQuickEstimate : CostingUiEvent
    data object PickAndAnalyzeMockup : CostingUiEvent

    // Knowledge Base arsip historis
    data object LoadBenchmarks : CostingUiEvent
    data class SetBenchmarkSearchQuery(val query: String) : CostingUiEvent
    data object PickAndImportWorkbook : CostingUiEvent

    data object OpenRateCardDialog : CostingUiEvent
    data object CloseRateCardDialog : CostingUiEvent
    data class SaveRateCard(val rateCard: CostingRateCard) : CostingUiEvent
}
