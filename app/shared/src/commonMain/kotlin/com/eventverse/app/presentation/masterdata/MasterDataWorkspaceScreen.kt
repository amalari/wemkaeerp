package com.eventverse.app.presentation.masterdata

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eventverse.app.domain.rbac.AccessDecision
import com.eventverse.app.domain.rbac.AccessLevel
import com.eventverse.app.domain.rbac.TestingPersona
import com.eventverse.app.presentation.designsystem.*
import com.eventverse.app.presentation.masterdata.components.*
import com.eventverse.app.presentation.theme.WeMadeColors

@Composable
fun MasterDataWorkspaceScreen(
    tenantSlug: String,
    decision: AccessDecision,
    persona: TestingPersona?,
    modifier: Modifier = Modifier,
    viewModel: MasterDataViewModel = remember(tenantSlug) { MasterDataViewModel(tenantSlug) }
) {
    val state by viewModel.uiState.collectAsState()
    val canManage = decision.config.canWrite

    Column(modifier = modifier.fillMaxSize()) {
        // Top Toolbar
        ClayCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = ClaySpacing.Md, vertical = ClaySpacing.Sm),
            shape = ClayShapes.Card,
            contentPadding = PaddingValues(horizontal = ClaySpacing.Md, vertical = ClaySpacing.Sm)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Md),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .claySurface(
                                shape = ClayShapes.Tile,
                                background = WeMadeColors.TealBg,
                                outline = WeMadeColors.Outline,
                                offset = ClayOffset.Small,
                                borderWidth = ClayBorder.Medium
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        IconDatabase(modifier = Modifier.size(20.dp), color = WeMadeColors.Teal)
                    }

                    Column {
                        Text(
                            text = "MASTER DATA BAHAN & HARGA",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = WeMadeColors.OnSurface
                        )
                        Text(
                            text = "Katalog material terstandarisasi, satuan fisik, konversi kemasan, dan tarif acuan HPP",
                            fontSize = 11.sp,
                            color = WeMadeColors.OnSurfaceMuted
                        )
                    }

                    if (state.materials.isNotEmpty()) {
                        ClayBadge(
                            text = "${state.materials.size} Bahan",
                            tint = WeMadeColors.Primary
                        )
                    }

                    ClayBadge(
                        text = "Fondasi Sistem",
                        tint = WeMadeColors.Teal
                    )
                }

                if (canManage) {
                    ClayButton(
                        text = "+ Tambah Bahan",
                        onClick = { viewModel.onEvent(MasterDataUiEvent.OpenCreateDialog) },
                        style = ClayButtonStyle.Primary
                    )
                }
            }
        }

        // Status banner if any
        val msg = state.statusMessage
        if (msg != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = ClaySpacing.Md, vertical = ClaySpacing.Xs)
                    .claySurface(
                        shape = ClayShapes.Chip,
                        background = if (state.isErrorMessage) WeMadeColors.ErrorBg else WeMadeColors.SuccessBg,
                        outline = if (state.isErrorMessage) WeMadeColors.Error else WeMadeColors.Success,
                        offset = ClayOffset.Small,
                        borderWidth = ClayBorder.Medium
                    )
                    .padding(horizontal = ClaySpacing.Md, vertical = ClaySpacing.Sm)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = msg,
                        fontSize = 13.sp,
                        color = if (state.isErrorMessage) WeMadeColors.Error else WeMadeColors.Success,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Tutup",
                        fontSize = 12.sp,
                        color = WeMadeColors.OnSurfaceMuted,
                        modifier = Modifier.clickable { viewModel.onEvent(MasterDataUiEvent.DismissStatusMessage) }
                    )
                }
            }
        }

        // Main Workbench Area
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = ClaySpacing.Md, vertical = ClaySpacing.Sm)
        ) {
            if (maxWidth >= ClayBreakpoints.MasterDetail) {
                MaterialDesktopWorkbench(
                    state = state,
                    canManage = canManage,
                    onEvent = viewModel::onEvent,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                MaterialMobileWorkbench(
                    state = state,
                    canManage = canManage,
                    onEvent = viewModel::onEvent,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }

    // Dialog: Create Material
    if (state.isCreateDialogOpen) {
        MaterialEditorDialog(
            initialMaterial = null,
            isSubmitting = state.isSubmitting,
            onDismiss = { viewModel.onEvent(MasterDataUiEvent.CloseCreateDialog) },
            onSave = { name, code, category, baseUom, alternateUoms, ownership, description ->
                viewModel.onEvent(
                    MasterDataUiEvent.CreateMaterial(
                        name = name,
                        code = code,
                        category = category,
                        baseUom = baseUom,
                        alternateUoms = alternateUoms,
                        defaultOwnership = ownership,
                        description = description
                    )
                )
            }
        )
    }

    // Dialog: Edit Material
    if (state.isEditDialogOpen) {
        val selected = state.selectedMaterial
        if (selected != null) {
            MaterialEditorDialog(
                initialMaterial = selected,
                isSubmitting = state.isSubmitting,
                onDismiss = { viewModel.onEvent(MasterDataUiEvent.CloseEditDialog) },
                onSave = { name, _, category, _, alternateUoms, ownership, description ->
                    viewModel.onEvent(
                        MasterDataUiEvent.UpdateMaterial(
                            name = name,
                            category = category,
                            defaultOwnership = ownership,
                            alternateUoms = alternateUoms,
                            description = description
                        )
                    )
                }
            )
        }
    }

    // Dialog: Set Price
    if (state.isSetPriceDialogOpen) {
        val selected = state.selectedMaterial
        if (selected != null) {
            SetPriceDialog(
                material = selected,
                isSubmitting = state.isSubmitting,
                onDismiss = { viewModel.onEvent(MasterDataUiEvent.CloseSetPriceDialog) },
                onSave = { unitPrice, effectiveFrom, note ->
                    viewModel.onEvent(
                        MasterDataUiEvent.SetStandardPrice(
                            materialId = selected.id,
                            unitPrice = unitPrice,
                            effectiveFrom = effectiveFrom,
                            note = note
                        )
                    )
                }
            )
        }
    }
}
