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
import com.eventverse.app.presentation.techpack.TechPackUiEvent
import com.eventverse.app.presentation.techpack.TechPackUiState
import com.eventverse.app.presentation.techpack.TechPackWorkbenchTab
import com.eventverse.app.presentation.theme.WeMadeColors

@Composable
fun TechPackDesktopWorkbench(
    state: TechPackUiState,
    canManage: Boolean,
    onEvent: (TechPackUiEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Md)
    ) {
        // Left column: List of Tech Packs
        TechPackListPanel(
            state = state,
            onEvent = onEvent,
            modifier = Modifier.width(320.dp)
        )

        // Right column: Detail & Workspace Tabs
        val selected = state.selectedTechPack
        if (selected == null) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .claySurface(
                        shape = ClayShapes.Card,
                        background = WeMadeColors.Surface,
                        outline = WeMadeColors.OutlineSoft,
                        offset = ClayOffset.Small,
                        borderWidth = ClayBorder.Hairline
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Pilih Tech Pack dari daftar di sebelah kiri untuk melihat detail",
                    fontSize = 14.sp,
                    color = WeMadeColors.OnSurfaceMuted
                )
            }
        } else {
            Column(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)
            ) {
                // Header (Style title, client, SPK, status, actions)
                TechPackDetailHeader(
                    techPack = selected,
                    state = state,
                    canManage = canManage,
                    onEvent = onEvent
                )

                // Workbench Tabs Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TechPackWorkbenchTab.entries.forEach { tab ->
                        val isSelected = state.activeWorkbenchTab == tab
                        Box(
                            modifier = Modifier
                                .clickable { onEvent(TechPackUiEvent.SelectWorkbenchTab(tab)) }
                                .claySurface(
                                    shape = ClayShapes.Pill,
                                    background = if (isSelected) WeMadeColors.Primary else WeMadeColors.Surface,
                                    outline = if (isSelected) WeMadeColors.Outline else WeMadeColors.OutlineSoft,
                                    offset = if (isSelected) ClayOffset.Small else ClayOffset.Flat,
                                    borderWidth = ClayBorder.Hairline
                                )
                                .padding(horizontal = ClaySpacing.Md, vertical = ClaySpacing.Sm)
                        ) {
                            Text(
                                text = tab.displayName,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) androidx.compose.ui.graphics.Color.White else WeMadeColors.OnSurface
                            )
                        }
                    }
                }

                // Workbench Tab Content
                ClayCard(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    shape = ClayShapes.Card,
                    contentPadding = PaddingValues(ClaySpacing.Md)
                ) {
                    when (state.activeWorkbenchTab) {
                        TechPackWorkbenchTab.BOM_LINES -> {
                            BomTableTab(
                                techPack = selected,
                                canManage = canManage,
                                onEvent = onEvent,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
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
                        TechPackWorkbenchTab.COST_PREVIEW -> {
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
    }
}
