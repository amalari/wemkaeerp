package com.eventverse.app.presentation.techpack

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eventverse.app.domain.rbac.AccessDecision
import com.eventverse.app.domain.rbac.TestingPersona
import com.eventverse.app.presentation.designsystem.*
import com.eventverse.app.presentation.techpack.components.*
import com.eventverse.app.presentation.theme.WeMadeColors

/**
 * Layar kerja utama untuk modul TECH_PACK_BOM (Product Engineering).
 *
 * Mengikuti pola Claymorphism & Neo-Brutalism WeMade ERP:
 * - Toolbar atas: Judul, counter style, status koneksi, dan CTA (+ Blank, + Dari Sampling).
 * - Layout responsif via [BoxWithConstraints] (< 840dp = Mobile 3-tab workbench, >= 840dp = Desktop master-detail).
 * - Seluruh dialog editing BOM, Labor SAM, Size Yield, dan Creation di-mount di level ini.
 */
@Composable
fun TechPackWorkspaceScreen(
    tenantSlug: String,
    decision: AccessDecision,
    persona: TestingPersona?,
    modifier: Modifier = Modifier,
    viewModel: TechPackViewModel = remember(tenantSlug) { TechPackViewModel(tenantSlug) }
) {
    val state by viewModel.uiState.collectAsState()
    val canManage = decision.config.canManage

    Column(modifier = modifier.fillMaxSize()) {
        // 1. Top Toolbar
        ClayCard(
            modifier = Modifier.fillMaxWidth().padding(horizontal = ClaySpacing.Md, vertical = ClaySpacing.Sm),
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
                    Column {
                        Text(
                            text = "TECH PACK & BOM",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = WeMadeColors.OnSurface
                        )
                        Text(
                            text = "Spesifikasi bahan, konsumsi benang, operasi kerja (SAM), & yield ukuran",
                            fontSize = 11.sp,
                            color = WeMadeColors.OnSurfaceMuted
                        )
                    }

                    if (state.techPacks.isNotEmpty()) {
                        ClayBadge(
                            text = "${state.techPacks.size} Style Terdaftar",
                            tint = WeMadeColors.Primary
                        )
                    }

                    val blockingCount = state.blockingUnresolvedCount
                    if (blockingCount > 0) {
                        ClayBadge(
                            text = "$blockingCount Bahan Belum Tervalidasi",
                            tint = WeMadeColors.Warning
                        )
                    }
                }

                if (canManage) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ClayButton(
                            text = "+ Blank Tech Pack",
                            style = ClayButtonStyle.Secondary,
                            onClick = { viewModel.onEvent(TechPackUiEvent.OpenCreateBlankDialog) }
                        )

                        ClayButton(
                            text = "+ Dari Sampling SPK",
                            style = ClayButtonStyle.Primary,
                            onClick = { viewModel.onEvent(TechPackUiEvent.OpenCreateFromSamplingDialog) }
                        )
                    }
                }
            }
        }

        // 2. Status message banner if present
        state.statusMessage?.let { msg ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = ClaySpacing.Md, vertical = ClaySpacing.Xs)
                    .clickable { viewModel.onEvent(TechPackUiEvent.DismissStatusMessage) }
            ) {
                ClayBadge(
                    text = msg,
                    tint = if (state.isErrorMessage) WeMadeColors.Error else WeMadeColors.Success,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // 3. Main Workspace Area
        BoxWithConstraints(modifier = Modifier.fillMaxSize().padding(ClaySpacing.Md)) {
            val isCompact = maxWidth < ClayBreakpoints.MasterDetail

            if (state.isLoading && state.techPacks.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = WeMadeColors.Primary)
                }
            } else if (state.techPacks.isEmpty()) {
                // Empty state
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    ClayCard(
                        modifier = Modifier.widthIn(max = 440.dp).padding(ClaySpacing.Xl),
                        shape = ClayShapes.Card,
                        contentPadding = PaddingValues(ClaySpacing.Xl)
                    ) {
                        Text(
                            text = "Belum Ada Tech Pack & BOM",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = WeMadeColors.OnSurface
                        )
                        Spacer(modifier = Modifier.height(ClaySpacing.Sm))
                        Text(
                            text = "Tech Pack mendefinisikan resep produksi garment rajut: konsumsi bahan (BOM), operasi kerja ber-SAM, rasio yield ukuran, serta estimasi biaya bahan sebelum masuk ke SPK Masal.",
                            fontSize = 12.sp,
                            color = WeMadeColors.OnSurfaceMuted
                        )
                        Spacer(modifier = Modifier.height(ClaySpacing.Lg))

                        if (canManage) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                ClayButton(
                                    text = "Buat Blank",
                                    style = ClayButtonStyle.Secondary,
                                    onClick = { viewModel.onEvent(TechPackUiEvent.OpenCreateBlankDialog) }
                                )

                                ClayButton(
                                    text = "Import dari Sampling",
                                    style = ClayButtonStyle.Primary,
                                    onClick = { viewModel.onEvent(TechPackUiEvent.OpenCreateFromSamplingDialog) }
                                )
                            }
                        }
                    }
                }
            } else {
                if (isCompact) {
                    TechPackMobileWorkbench(
                        state = state,
                        canManage = canManage,
                        onEvent = viewModel::onEvent
                    )
                } else {
                    TechPackDesktopWorkbench(
                        state = state,
                        canManage = canManage,
                        onEvent = viewModel::onEvent
                    )
                }
            }
        }
    }

    // 4. Modals & Dialogs
    if (state.isCreateBlankDialogOpen) {
        CreateBlankTechPackDialog(
            isSubmitting = state.isSubmitting,
            onDismiss = { viewModel.onEvent(TechPackUiEvent.CloseCreateBlankDialog) },
            onSave = { styleName, styleCode, clientName ->
                viewModel.onEvent(TechPackUiEvent.CreateBlankTechPack(styleName, styleCode, clientName))
            }
        )
    }

    if (state.isCreateFromSamplingDialogOpen) {
        CreateFromSamplingDialog(
            isSubmitting = state.isSubmitting,
            onDismiss = { viewModel.onEvent(TechPackUiEvent.CloseCreateFromSamplingDialog) },
            onSave = { samplingOrderId, styleCode, ownership, waste ->
                viewModel.onEvent(TechPackUiEvent.CreateFromSampling(samplingOrderId, styleCode, ownership, waste))
            }
        )
    }

    if (state.isBomLineDialogOpen) {
        BomLineEditorDialog(
            initialLine = state.editingBomLine,
            availableMaterials = state.availableMaterials,
            isSubmitting = state.isSubmitting,
            onDismiss = { viewModel.onEvent(TechPackUiEvent.CloseBomLineDialog) },
            onSave = { line ->
                viewModel.onEvent(TechPackUiEvent.SaveBomLine(line))
            }
        )
    }

    if (state.isLaborOpDialogOpen) {
        LaborOperationDialog(
            initialOp = state.editingLaborOp,
            isSubmitting = state.isSubmitting,
            onDismiss = { viewModel.onEvent(TechPackUiEvent.CloseLaborOpDialog) },
            onSave = { op ->
                viewModel.onEvent(TechPackUiEvent.SaveLaborOp(op))
            }
        )
    }

    if (state.isSizeYieldDialogOpen) {
        val currentFactors = state.selectedTechPack?.sizeYieldFactors ?: emptyList()
        SizeYieldEditorDialog(
            initialFactors = currentFactors,
            isSubmitting = state.isSubmitting,
            onDismiss = { viewModel.onEvent(TechPackUiEvent.CloseSizeYieldDialog) },
            onSave = { factors ->
                viewModel.onEvent(TechPackUiEvent.UpdateSizeYieldFactors(factors))
            }
        )
    }
}
