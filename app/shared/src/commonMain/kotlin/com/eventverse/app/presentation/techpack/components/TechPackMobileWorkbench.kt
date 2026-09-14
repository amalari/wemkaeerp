package com.eventverse.app.presentation.techpack.components

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
import com.eventverse.app.presentation.techpack.TechPackMobileTab
import com.eventverse.app.presentation.techpack.TechPackUiEvent
import com.eventverse.app.presentation.techpack.TechPackUiState
import com.eventverse.app.presentation.techpack.TechPackWorkbenchTab
import com.eventverse.app.presentation.theme.WeMadeColors

@Composable
fun TechPackMobileWorkbench(
    state: TechPackUiState,
    canManage: Boolean,
    onEvent: (TechPackUiEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)
    ) {
        // Mobile Tab Switcher
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Xs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TechPackMobileTab.entries.forEach { tab ->
                val isSelected = state.activeMobileTab == tab
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onEvent(TechPackUiEvent.SelectMobileTab(tab)) }
                        .claySurface(
                            shape = ClayShapes.Pill,
                            background = if (isSelected) WeMadeColors.Primary else WeMadeColors.Surface,
                            outline = if (isSelected) WeMadeColors.Outline else WeMadeColors.OutlineSoft,
                            offset = if (isSelected) ClayOffset.Small else ClayOffset.Flat,
                            borderWidth = ClayBorder.Hairline
                        )
                        .padding(vertical = ClaySpacing.Sm),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = tab.displayName,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) androidx.compose.ui.graphics.Color.White else WeMadeColors.OnSurface
                    )
                }
            }
        }

        val selected = state.selectedTechPack

        when (state.activeMobileTab) {
            TechPackMobileTab.LIST -> {
                TechPackListPanel(
                    state = state,
                    onEvent = { event ->
                        onEvent(event)
                        if (event is TechPackUiEvent.SelectTechPack) {
                            onEvent(TechPackUiEvent.SelectMobileTab(TechPackMobileTab.BOM_AND_OPS))
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
            TechPackMobileTab.BOM_AND_OPS -> {
                if (selected == null) {
                    Box(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Pilih Tech Pack terlebih dahulu dari tab Daftar Style",
                            fontSize = 13.sp,
                            color = WeMadeColors.OnSurfaceMuted
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)
                    ) {
                        TechPackDetailHeader(
                            techPack = selected,
                            state = state,
                            canManage = canManage,
                            onEvent = onEvent
                        )

                        // Sub tabs for mobile: BOM vs Labor vs Size Yield
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Xs)
                        ) {
                            val subTabs = listOf(
                                TechPackWorkbenchTab.BOM_LINES,
                                TechPackWorkbenchTab.LABOR_OPERATIONS,
                                TechPackWorkbenchTab.SIZE_YIELD
                            )
                            subTabs.forEach { tab ->
                                val isSelected = state.activeWorkbenchTab == tab
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { onEvent(TechPackUiEvent.SelectWorkbenchTab(tab)) }
                                        .claySurface(
                                            shape = ClayShapes.Pill,
                                            background = if (isSelected) WeMadeColors.Primary else WeMadeColors.SurfaceMuted,
                                            outline = if (isSelected) WeMadeColors.Outline else WeMadeColors.OutlineSoft,
                                            offset = if (isSelected) ClayOffset.Small else ClayOffset.Flat,
                                            borderWidth = ClayBorder.Hairline
                                        )
                                        .padding(vertical = ClaySpacing.Xs),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = when (tab) {
                                            TechPackWorkbenchTab.BOM_LINES -> "BOM"
                                            TechPackWorkbenchTab.LABOR_OPERATIONS -> "Operasi"
                                            TechPackWorkbenchTab.SIZE_YIELD -> "Ukuran"
                                            else -> ""
                                        },
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) androidx.compose.ui.graphics.Color.White else WeMadeColors.OnSurface
                                    )
                                }
                            }
                        }

                        ClayCard(
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            shape = ClayShapes.Card,
                            contentPadding = PaddingValues(ClaySpacing.Sm)
                        ) {
                            when (state.activeWorkbenchTab) {
                                TechPackWorkbenchTab.LABOR_OPERATIONS -> {
                                    LaborOperationsTab(
                                        techPack = selected,
                                        canManage = canManage,
                                        onEvent = onEvent,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                                TechPackWorkbenchTab.SIZE_YIELD -> {
                                    SizeYieldTab(
                                        techPack = selected,
                                        canManage = canManage,
                                        onEvent = onEvent,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                                else -> {
                                    BomTableTab(
                                        techPack = selected,
                                        canManage = canManage,
                                        onEvent = onEvent,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }
                        }
                    }
                }
            }
            TechPackMobileTab.COST_PREVIEW -> {
                if (selected == null) {
                    Box(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Pilih Tech Pack terlebih dahulu dari tab Daftar Style",
                            fontSize = 13.sp,
                            color = WeMadeColors.OnSurfaceMuted
                        )
                    }
                } else {
                    MaterialCostPreviewCard(
                        techPack = selected,
                        costPreview = state.costPreview,
                        orderQuantity = state.orderQuantity,
                        isLoading = state.isCostLoading,
                        onEvent = onEvent,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}
