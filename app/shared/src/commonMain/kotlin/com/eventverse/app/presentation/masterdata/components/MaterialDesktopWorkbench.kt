package com.eventverse.app.presentation.masterdata.components

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.eventverse.app.presentation.designsystem.ClaySpacing
import com.eventverse.app.presentation.masterdata.MasterDataUiEvent
import com.eventverse.app.presentation.masterdata.MasterDataUiState

@Composable
fun MaterialDesktopWorkbench(
    state: MasterDataUiState,
    canManage: Boolean,
    onEvent: (MasterDataUiEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Lg)
    ) {
        // Left Column: Catalog list (380.dp)
        MaterialCatalogList(
            materials = state.filteredMaterials,
            selectedMaterialId = state.selectedMaterialId,
            searchQuery = state.searchQuery,
            selectedCategory = state.selectedCategoryFilter,
            canManage = canManage,
            onSelectMaterial = { onEvent(MasterDataUiEvent.SelectMaterial(it)) },
            onSearchChange = { onEvent(MasterDataUiEvent.UpdateSearchQuery(it)) },
            onCategorySelect = { onEvent(MasterDataUiEvent.SetCategoryFilter(it)) },
            onOpenCreate = { onEvent(MasterDataUiEvent.OpenCreateDialog) },
            modifier = Modifier
                .width(380.dp)
                .fillMaxHeight()
        )

        // Right Column: Detail & Price Management
        MaterialDetailPanel(
            material = state.selectedMaterial,
            prices = state.priceHistory,
            isPriceLoading = state.isPriceLoading,
            canManage = canManage,
            onOpenEdit = { onEvent(MasterDataUiEvent.OpenEditDialog) },
            onArchive = {
                state.selectedMaterialId?.let { onEvent(MasterDataUiEvent.ArchiveMaterial(it)) }
            },
            onOpenSetPrice = { onEvent(MasterDataUiEvent.OpenSetPriceDialog) },
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        )
    }
}
