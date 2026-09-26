package com.eventverse.app.presentation.masterdata.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eventverse.app.presentation.designsystem.*
import com.eventverse.app.presentation.masterdata.MasterDataMobileTab
import com.eventverse.app.presentation.masterdata.MasterDataUiEvent
import com.eventverse.app.presentation.masterdata.MasterDataUiState
import com.eventverse.app.presentation.theme.WeMadeColors

@Composable
fun MaterialMobileWorkbench(
    state: MasterDataUiState,
    canManage: Boolean,
    onEvent: (MasterDataUiEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize()) {
        // Tab Navigation Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)
        ) {
            MasterDataMobileTab.entries.forEach { tab ->
                val isSelected = tab == state.activeMobileTab
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clayFlat(
                            shape = ClayShapes.Pill,
                            background = if (isSelected) WeMadeColors.Primary else WeMadeColors.Surface,
                            outline = if (isSelected) WeMadeColors.Outline else WeMadeColors.Border,
                            borderWidth = ClayBorder.Medium
                        )
                        .clickable { onEvent(MasterDataUiEvent.SelectMobileTab(tab)) }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = tab.displayName,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) WeMadeColors.Surface else WeMadeColors.OnSurface
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(ClaySpacing.Md))

        when (state.activeMobileTab) {
            MasterDataMobileTab.CATALOG -> {
                MaterialCatalogList(
                    materials = state.filteredMaterials,
                    selectedMaterialId = state.selectedMaterialId,
                    searchQuery = state.searchQuery,
                    selectedCategory = state.selectedCategoryFilter,
                    canManage = canManage,
                    onSelectMaterial = {
                        onEvent(MasterDataUiEvent.SelectMaterial(it))
                        onEvent(MasterDataUiEvent.SelectMobileTab(MasterDataMobileTab.DETAIL))
                    },
                    onSearchChange = { onEvent(MasterDataUiEvent.UpdateSearchQuery(it)) },
                    onCategorySelect = { onEvent(MasterDataUiEvent.SetCategoryFilter(it)) },
                    onOpenCreate = { onEvent(MasterDataUiEvent.OpenCreateDialog) },
                    modifier = Modifier.fillMaxSize()
                )
            }
            MasterDataMobileTab.DETAIL -> {
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
                    modifier = Modifier.fillMaxSize()
                )
            }
            MasterDataMobileTab.PRICE_HISTORY -> {
                MaterialPriceHistoryCard(
                    prices = state.priceHistory,
                    isLoading = state.isPriceLoading,
                    canManage = canManage,
                    onOpenSetPrice = { onEvent(MasterDataUiEvent.OpenSetPriceDialog) },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}
