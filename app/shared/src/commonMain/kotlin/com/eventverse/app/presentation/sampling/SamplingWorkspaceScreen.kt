package com.eventverse.app.presentation.sampling

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
import com.eventverse.app.domain.sampling.SamplingOrder
import com.eventverse.app.domain.sampling.SamplingStatus
import com.eventverse.app.presentation.designsystem.*
import com.eventverse.app.presentation.sampling.components.CreateSamplingOrderDialog
import com.eventverse.app.presentation.sampling.components.SamplingDesktopWorkbench
import com.eventverse.app.presentation.sampling.components.SamplingMobileWorkbench
import com.eventverse.app.presentation.theme.WeMadeColors

@Composable
fun SamplingWorkspaceScreen(
    tenantSlug: String,
    decision: AccessDecision,
    persona: TestingPersona?,
    modifier: Modifier = Modifier,
    viewModel: SamplingViewModel = remember(tenantSlug) { SamplingViewModel(tenantSlug) }
) {
    val state by viewModel.uiState.collectAsState()

    Column(modifier = modifier.fillMaxSize()) {
        // Top Toolbar
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
                            text = "ORDER SAMPLING & SPK",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = WeMadeColors.OnSurface
                        )
                        Text(
                            text = "Prototyping, parameter rajut mentah & ACC buyer",
                            fontSize = 11.sp,
                            color = WeMadeColors.OnSurfaceMuted
                        )
                    }

                    if (state.orders.size > 1) {
                        ClayBadge(
                            text = "${state.orders.size} SPK Terdaftar",
                            tint = WeMadeColors.Primary
                        )
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ClayButton(
                        text = "+ Buat SPK Sample",
                        style = ClayButtonStyle.Primary,
                        onClick = { viewModel.onEvent(SamplingUiEvent.OpenCreateDialog) }
                    )
                }
            }
        }

        // Status banner if any
        if (state.statusMessage != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = ClaySpacing.Md, vertical = ClaySpacing.Xs)
            ) {
                ClayBadge(
                    text = state.statusMessage ?: "",
                    tint = if (state.isErrorMessage) WeMadeColors.Error else WeMadeColors.Success,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // Main Content Area
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val isCompact = maxWidth < 840.dp
            val selectedOrder = state.selectedOrder

            if (state.isLoading && state.orders.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = WeMadeColors.Primary)
                }
            } else if (selectedOrder != null) {
                if (isCompact) {
                    SamplingMobileWorkbench(
                        order = selectedOrder,
                        activeTab = state.activeMobileTab,
                        onTabSelected = { viewModel.onEvent(SamplingUiEvent.SelectMobileTab(it)) },
                        onToggleMilestone = { step, done ->
                            viewModel.onEvent(SamplingUiEvent.ToggleMilestone(selectedOrder.id, step, done))
                        },
                        onApproveOrder = { isApproved, notes ->
                            viewModel.onEvent(SamplingUiEvent.ApproveOrder(selectedOrder.id, isApproved, notes))
                        }
                    )
                } else {
                    SamplingDesktopWorkbench(
                        order = selectedOrder,
                        onToggleMilestone = { step, done ->
                            viewModel.onEvent(SamplingUiEvent.ToggleMilestone(selectedOrder.id, step, done))
                        },
                        onApproveOrder = { isApproved, notes ->
                            viewModel.onEvent(SamplingUiEvent.ApproveOrder(selectedOrder.id, isApproved, notes))
                        }
                    )
                }
            } else {
                // Empty state
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    ClayCard(
                        modifier = Modifier.widthIn(max = 420.dp).padding(ClaySpacing.Xl),
                        shape = ClayShapes.Card,
                        contentPadding = PaddingValues(ClaySpacing.Xl)
                    ) {
                        Text(
                            text = "Belum Ada SPK Sample",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = WeMadeColors.OnSurface
                        )
                        Spacer(modifier = Modifier.height(ClaySpacing.Sm))
                        Text(
                            text = "Buat SPK Sample pertama untuk mulai memprogram mesin rajut dan mencatat ukuran sampel.",
                            fontSize = 12.sp,
                            color = WeMadeColors.OnSurfaceMuted
                        )
                        Spacer(modifier = Modifier.height(ClaySpacing.Lg))
                        ClayButton(
                            text = "Buat SPK Sample Sekarang",
                            style = ClayButtonStyle.Accent,
                            onClick = { viewModel.onEvent(SamplingUiEvent.OpenCreateDialog) }
                        )
                    }
                }
            }
        }
    }

    CreateSamplingOrderDialog(
        isOpen = state.isCreateDialogOpen,
        isSubmitting = state.isSubmitting,
        onDismiss = { viewModel.onEvent(SamplingUiEvent.CloseCreateDialog) },
        onSubmit = { client, style, sizeMode, usePreset ->
            viewModel.onEvent(SamplingUiEvent.CreateOrder(client, style, sizeMode, usePreset))
        }
    )
}
