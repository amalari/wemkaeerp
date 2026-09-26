package com.eventverse.app.presentation.techpack

import com.eventverse.app.domain.contracts.BomLine
import com.eventverse.app.domain.contracts.LaborOperation
import com.eventverse.app.domain.contracts.SizeYieldFactor
import com.eventverse.app.domain.masterdata.MaterialItem
import com.eventverse.app.domain.pipeline.StockOwnershipSemantics
import com.eventverse.app.domain.techpack.BomCostPreview
import com.eventverse.app.domain.techpack.TechPack
import com.eventverse.app.domain.techpack.TechPackId
import com.eventverse.app.domain.techpack.TechPackStatus

enum class TechPackMobileTab(val displayName: String) {
    LIST("Daftar Style"),
    BOM_AND_OPS("BOM & Operasi"),
    COST_PREVIEW("Estimasi Biaya");
}

enum class TechPackWorkbenchTab(val displayName: String) {
    BOM_LINES("BOM Bahan"),
    LABOR_OPERATIONS("Operasi Kerja (SAM)"),
    SIZE_YIELD("Yield Skala Ukuran"),
    COST_PREVIEW("Estimasi Biaya Bahan");
}

data class TechPackUiState(
    val techPacks: List<TechPack> = emptyList(),
    val selectedTechPackId: TechPackId? = null,
    val activeWorkbenchTab: TechPackWorkbenchTab = TechPackWorkbenchTab.BOM_LINES,
    val activeMobileTab: TechPackMobileTab = TechPackMobileTab.LIST,
    val selectedStatusFilter: TechPackStatus? = null,
    val searchQuery: String = "",
    val orderQuantity: Long = 100L,
    val costPreview: BomCostPreview? = null,
    val versions: List<TechPack> = emptyList(),
    val availableMaterials: List<MaterialItem> = emptyList(),
    val isLoading: Boolean = false,
    val isCostLoading: Boolean = false,
    val isSubmitting: Boolean = false,
    val statusMessage: String? = null,
    val isErrorMessage: Boolean = false,
    val isCreateBlankDialogOpen: Boolean = false,
    val isCreateFromSamplingDialogOpen: Boolean = false,
    val isBomLineDialogOpen: Boolean = false,
    val editingBomLine: BomLine? = null,
    val isLaborOpDialogOpen: Boolean = false,
    val editingLaborOp: LaborOperation? = null,
    val isSizeYieldDialogOpen: Boolean = false
) {
    val selectedTechPack: TechPack?
        get() = (selectedTechPackId?.let { id -> techPacks.firstOrNull { it.id == id } } ?: techPacks.firstOrNull())

    val filteredTechPacks: List<TechPack>
        get() = techPacks.filter { tp ->
            val matchStatus = selectedStatusFilter == null || tp.status == selectedStatusFilter
            val matchSearch = searchQuery.isBlank() ||
                tp.styleCode.value.contains(searchQuery, ignoreCase = true) ||
                tp.styleName.contains(searchQuery, ignoreCase = true) ||
                tp.clientName.contains(searchQuery, ignoreCase = true) ||
                tp.sourceSpkNumber.contains(searchQuery, ignoreCase = true)
            matchStatus && matchSearch
        }

    val isEditable: Boolean
        get() = selectedTechPack?.isEditable == true

    val unresolvedCount: Int
        get() = selectedTechPack?.unresolvedLines?.size ?: 0

    val blockingUnresolvedCount: Int
        get() = selectedTechPack?.blockingUnresolvedLines?.size ?: 0
}

sealed interface TechPackUiEvent {
    data object Load : TechPackUiEvent
    data class SelectTechPack(val id: TechPackId) : TechPackUiEvent
    data class SelectWorkbenchTab(val tab: TechPackWorkbenchTab) : TechPackUiEvent
    data class SelectMobileTab(val tab: TechPackMobileTab) : TechPackUiEvent
    data class SetStatusFilter(val status: TechPackStatus?) : TechPackUiEvent
    data class UpdateSearchQuery(val query: String) : TechPackUiEvent
    data class UpdateOrderQuantity(val quantity: Long) : TechPackUiEvent
    data object LoadCostPreview : TechPackUiEvent
    data object OpenCreateBlankDialog : TechPackUiEvent
    data object CloseCreateBlankDialog : TechPackUiEvent
    data class CreateBlankTechPack(val styleName: String, val styleCode: String?, val clientName: String) : TechPackUiEvent
    data object OpenCreateFromSamplingDialog : TechPackUiEvent
    data object CloseCreateFromSamplingDialog : TechPackUiEvent
    data class CreateFromSampling(
        val samplingOrderId: String,
        val styleCode: String?,
        val defaultOwnership: StockOwnershipSemantics?,
        val defaultWastePercent: Double?
    ) : TechPackUiEvent
    data object OpenAddBomLineDialog : TechPackUiEvent
    data class OpenEditBomLineDialog(val line: BomLine) : TechPackUiEvent
    data object CloseBomLineDialog : TechPackUiEvent
    data class SaveBomLine(val line: BomLine) : TechPackUiEvent
    data class DeleteBomLine(val lineId: String) : TechPackUiEvent
    data object OpenAddLaborOpDialog : TechPackUiEvent
    data class OpenEditLaborOpDialog(val op: LaborOperation) : TechPackUiEvent
    data object CloseLaborOpDialog : TechPackUiEvent
    data class SaveLaborOp(val op: LaborOperation) : TechPackUiEvent
    data class DeleteLaborOp(val operationId: String) : TechPackUiEvent
    data object OpenSizeYieldDialog : TechPackUiEvent
    data object CloseSizeYieldDialog : TechPackUiEvent
    data class UpdateSizeYieldFactors(val factors: List<SizeYieldFactor>) : TechPackUiEvent
    data object ResolveMaterials : TechPackUiEvent
    data object ReleaseTechPack : TechPackUiEvent
    data object ReviseTechPack : TechPackUiEvent
    data class ArchiveTechPack(val id: TechPackId) : TechPackUiEvent
    data object DismissStatusMessage : TechPackUiEvent
}

fun com.eventverse.app.domain.common.Money.format(): String = formatted()
fun com.eventverse.app.domain.common.Quantity.format(): String = formatted()
fun com.eventverse.app.domain.common.Ratio.toPercentageString(): String = "${(toDouble() * 100).toInt()}%"
fun com.eventverse.app.domain.common.Ratio.toFormattedString(): String = ((toDouble() * 100).toInt() / 100.0).toString()
