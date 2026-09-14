package com.eventverse.app.presentation.masterdata

import com.eventverse.app.domain.common.UnitOfMeasure
import com.eventverse.app.domain.common.UnitPrice
import com.eventverse.app.domain.masterdata.*
import com.eventverse.app.domain.pipeline.StockOwnershipSemantics
import kotlinx.datetime.Instant

enum class MasterDataMobileTab(val displayName: String) {
    CATALOG("Katalog Bahan"),
    DETAIL("Spesifikasi & UoM"),
    PRICE_HISTORY("Riwayat Harga");
}

data class MasterDataUiState(
    val materials: List<MaterialItem> = emptyList(),
    val selectedMaterialId: MaterialId? = null,
    val activeMobileTab: MasterDataMobileTab = MasterDataMobileTab.CATALOG,
    val selectedCategoryFilter: MaterialCategory? = null,
    val selectedOwnershipFilter: StockOwnershipSemantics? = null,
    val searchQuery: String = "",
    val priceHistory: List<MaterialPrice> = emptyList(),
    val isLoading: Boolean = false,
    val isPriceLoading: Boolean = false,
    val isSubmitting: Boolean = false,
    val statusMessage: String? = null,
    val isErrorMessage: Boolean = false,
    val isCreateDialogOpen: Boolean = false,
    val isEditDialogOpen: Boolean = false,
    val isSetPriceDialogOpen: Boolean = false
) {
    val selectedMaterial: MaterialItem?
        get() = (selectedMaterialId?.let { id -> materials.firstOrNull { it.id == id } } ?: materials.firstOrNull())

    val filteredMaterials: List<MaterialItem>
        get() = materials.filter { item ->
            val matchCategory = selectedCategoryFilter == null || item.category == selectedCategoryFilter
            val matchOwnership = selectedOwnershipFilter == null || item.defaultOwnership == selectedOwnershipFilter
            val matchSearch = searchQuery.isBlank() ||
                item.name.contains(searchQuery, ignoreCase = true) ||
                item.code.value.contains(searchQuery, ignoreCase = true) ||
                item.description.contains(searchQuery, ignoreCase = true)
            matchCategory && matchOwnership && matchSearch
        }
}

sealed interface MasterDataUiEvent {
    data object Load : MasterDataUiEvent
    data class SelectMaterial(val materialId: MaterialId) : MasterDataUiEvent
    data class SelectMobileTab(val tab: MasterDataMobileTab) : MasterDataUiEvent
    data class SetCategoryFilter(val category: MaterialCategory?) : MasterDataUiEvent
    data class SetOwnershipFilter(val ownership: StockOwnershipSemantics?) : MasterDataUiEvent
    data class UpdateSearchQuery(val query: String) : MasterDataUiEvent
    data object OpenCreateDialog : MasterDataUiEvent
    data object CloseCreateDialog : MasterDataUiEvent
    data object OpenEditDialog : MasterDataUiEvent
    data object CloseEditDialog : MasterDataUiEvent
    data object OpenSetPriceDialog : MasterDataUiEvent
    data object CloseSetPriceDialog : MasterDataUiEvent
    data class CreateMaterial(
        val name: String,
        val code: String? = null,
        val category: MaterialCategory,
        val baseUom: UnitOfMeasure? = null,
        val alternateUoms: List<UomConversion> = emptyList(),
        val defaultOwnership: StockOwnershipSemantics = StockOwnershipSemantics.OWNED_RAW_MATERIAL,
        val description: String = ""
    ) : MasterDataUiEvent
    data class UpdateMaterial(
        val name: String? = null,
        val category: MaterialCategory? = null,
        val defaultOwnership: StockOwnershipSemantics? = null,
        val alternateUoms: List<UomConversion>? = null,
        val description: String? = null
    ) : MasterDataUiEvent
    data class ArchiveMaterial(val materialId: MaterialId) : MasterDataUiEvent
    data class SetStandardPrice(
        val materialId: MaterialId,
        val unitPrice: UnitPrice,
        val effectiveFrom: Instant,
        val note: String
    ) : MasterDataUiEvent
    data object DismissStatusMessage : MasterDataUiEvent
}
