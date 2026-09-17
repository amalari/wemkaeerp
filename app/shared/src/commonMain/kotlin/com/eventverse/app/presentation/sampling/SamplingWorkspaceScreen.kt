package com.eventverse.app.presentation.sampling

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import com.eventverse.app.presentation.designsystem.*
import com.eventverse.app.presentation.sampling.components.*
import com.eventverse.app.presentation.theme.WeMadeColors

@Composable
fun SamplingWorkspaceScreen(
    tenantSlug: String,
    decision: AccessDecision,
    persona: TestingPersona?,
    modifier: Modifier = Modifier,
    viewModel: SamplingViewModel = remember(tenantSlug) { SamplingViewModel(tenantSlug) },
    onCreateTechPack: ((SamplingOrder) -> Unit)? = null
) {
    val state by viewModel.uiState.collectAsState()

    Column(modifier = modifier.fillMaxSize()) {
        // Top Toolbar
        ClayCard(
            modifier = Modifier.fillMaxWidth().padding(horizontal = ClaySpacing.Md, vertical = ClaySpacing.Sm),
            shape = ClayShapes.Card,
            contentPadding = PaddingValues(horizontal = ClaySpacing.Md, vertical = ClaySpacing.Sm)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)) {
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
                                text = "ORDER SAMPLING & WORKBENCH",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = WeMadeColors.OnSurface
                            )
                            Text(
                                text = "R&D Rajut, Dual Size Chart, Finishing Setoran, QC & Monitoring Makloon",
                                fontSize = 11.sp,
                                color = WeMadeColors.OnSurfaceMuted
                            )
                        }

                        if (state.orders.isNotEmpty()) {
                            ClayBadge(
                                text = "${state.orders.size} SPK",
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

                // View Tabs (Workbench, Kanban, Vendor Monitoring)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Xs),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SamplingViewTab.entries.forEach { tab ->
                            val isSelected = state.activeViewTab == tab
                            ClayButton(
                                text = tab.displayName,
                                style = if (isSelected) ClayButtonStyle.Primary else ClayButtonStyle.Secondary,
                                fontSize = 11.sp,
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                onClick = { viewModel.onEvent(SamplingUiEvent.SelectViewTab(tab)) }
                            )
                        }
                    }

                    // Quick SPK Selector when in Workbench view
                    if (state.activeViewTab == SamplingViewTab.WORKBENCH && state.orders.size > 1) {
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Xs),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            state.orders.take(5).forEach { order ->
                                val isSelected = order.id == state.selectedOrderId
                                ClayButton(
                                    text = order.spkNumber.value,
                                    style = if (isSelected) ClayButtonStyle.Accent else ClayButtonStyle.Secondary,
                                    fontSize = 10.sp,
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                    onClick = { viewModel.onEvent(SamplingUiEvent.SelectOrder(order.id)) }
                                )
                            }
                        }
                    }
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
            } else when (state.activeViewTab) {
                SamplingViewTab.PIPELINE_KANBAN -> {
                    SamplingPipelineKanbanBoard(
                        orders = state.filteredOrders,
                        selectedOrderId = state.selectedOrderId,
                        onSelectOrder = {
                            viewModel.onEvent(SamplingUiEvent.SelectOrder(it))
                            viewModel.onEvent(SamplingUiEvent.SelectViewTab(SamplingViewTab.WORKBENCH))
                        },
                        onAdvanceStage = { id, stage ->
                            viewModel.onEvent(SamplingUiEvent.AdvanceStage(id, stage))
                        },
                        onOpenFinishingDialog = {
                            viewModel.onEvent(SamplingUiEvent.OpenFinishingDialog(it))
                        },
                        onOpenVendorDialog = {
                            viewModel.onEvent(SamplingUiEvent.OpenVendorDialog(it))
                        },
                        onOpenQcDialog = {
                            viewModel.onEvent(SamplingUiEvent.OpenQcDialog(it))
                        },
                        onOpenRevisionDialog = {
                            viewModel.onEvent(SamplingUiEvent.OpenRevisionDialog(it))
                        },
                        onApproveOrder = { id, notes ->
                            viewModel.onEvent(SamplingUiEvent.ApproveOrder(id, true, notes))
                        }
                    )
                }

                SamplingViewTab.VENDOR_MONITORING -> {
                    SamplingVendorMonitoringView(
                        orders = state.orders,
                        onOpenVendorDialog = { viewModel.onEvent(SamplingUiEvent.OpenVendorDialog(it)) },
                        onConfirmReceive = { viewModel.onEvent(SamplingUiEvent.ConfirmVendorReturn(it.id)) }
                    )
                }

                SamplingViewTab.WORKBENCH -> {
                    if (selectedOrder != null) {
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
                                },
                                onOpenFinishingDialog = {
                                    viewModel.onEvent(SamplingUiEvent.OpenFinishingDialog(selectedOrder))
                                },
                                onOpenQcDialog = {
                                    viewModel.onEvent(SamplingUiEvent.OpenQcDialog(selectedOrder))
                                },
                                onOpenVendorDialog = {
                                    viewModel.onEvent(SamplingUiEvent.OpenVendorDialog(selectedOrder))
                                },
                                onConfirmVendorReceive = {
                                    viewModel.onEvent(SamplingUiEvent.ConfirmVendorReturn(selectedOrder.id))
                                },
                                onUpdateTenselity = { entries ->
                                    val updatedProgram = selectedOrder.machineProgram.copy(tenselityEntries = entries)
                                    viewModel.onEvent(SamplingUiEvent.SaveFullOrder(selectedOrder.copy(machineProgram = updatedProgram)))
                                },
                                onCreateTechPack = onCreateTechPack
                            )
                        } else {
                            SamplingDesktopWorkbench(
                                order = selectedOrder,
                                onCreateTechPack = onCreateTechPack,
                                onToggleMilestone = { step, done ->
                                    viewModel.onEvent(SamplingUiEvent.ToggleMilestone(selectedOrder.id, step, done))
                                },
                                onApproveOrder = { isApproved, notes ->
                                    viewModel.onEvent(SamplingUiEvent.ApproveOrder(selectedOrder.id, isApproved, notes))
                                },
                                onOpenFinishingDialog = {
                                    viewModel.onEvent(SamplingUiEvent.OpenFinishingDialog(selectedOrder))
                                },
                                onOpenQcDialog = {
                                    viewModel.onEvent(SamplingUiEvent.OpenQcDialog(selectedOrder))
                                },
                                onOpenVendorDialog = {
                                    viewModel.onEvent(SamplingUiEvent.OpenVendorDialog(selectedOrder))
                                },
                                onConfirmVendorReceive = {
                                    viewModel.onEvent(SamplingUiEvent.ConfirmVendorReturn(selectedOrder.id))
                                },
                                onOpenRevisionDialog = {
                                    viewModel.onEvent(SamplingUiEvent.OpenRevisionDialog(selectedOrder))
                                },
                                onUpdateTenselity = { entries ->
                                    val updatedProgram = selectedOrder.machineProgram.copy(tenselityEntries = entries)
                                    viewModel.onEvent(SamplingUiEvent.SaveFullOrder(selectedOrder.copy(machineProgram = updatedProgram)))
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
        }
    }

    // 1. Create SPK Dialog
    CreateSamplingOrderDialog(
        isOpen = state.isCreateDialogOpen,
        isSubmitting = state.isSubmitting,
        onDismiss = { viewModel.onEvent(SamplingUiEvent.CloseCreateDialog) },
        onSubmit = { client, style, sizeMode, usePreset ->
            viewModel.onEvent(SamplingUiEvent.CreateOrder(client, style, sizeMode, usePreset))
        }
    )

    // 2. Finishing Setoran Dialog
    FinishingSetoranDialog(
        isOpen = state.isFinishingDialogOpen,
        order = state.targetOrderForAction ?: state.selectedOrder,
        isSubmitting = state.isSubmitting,
        onDismiss = { viewModel.onEvent(SamplingUiEvent.CloseFinishingDialog) },
        onSubmit = { deposit ->
            val targetId = (state.targetOrderForAction ?: state.selectedOrder)?.id ?: return@FinishingSetoranDialog
            viewModel.onEvent(SamplingUiEvent.AddFinishingDeposit(targetId, deposit))
        }
    )

    // 3. QC Inspection Dialog
    QcInspectionDialog(
        isOpen = state.isQcDialogOpen,
        order = state.targetOrderForAction ?: state.selectedOrder,
        isSubmitting = state.isSubmitting,
        onDismiss = { viewModel.onEvent(SamplingUiEvent.CloseQcDialog) },
        onSubmit = { report ->
            val targetId = (state.targetOrderForAction ?: state.selectedOrder)?.id ?: return@QcInspectionDialog
            viewModel.onEvent(SamplingUiEvent.SubmitQcInspection(targetId, report))
        }
    )

    // 4. Assign Vendor Dialog
    AssignVendorDialog(
        isOpen = state.isVendorDialogOpen,
        order = state.targetOrderForAction ?: state.selectedOrder,
        isSubmitting = state.isSubmitting,
        onDismiss = { viewModel.onEvent(SamplingUiEvent.CloseVendorDialog) },
        onSubmit = { vendorInfo ->
            val targetId = (state.targetOrderForAction ?: state.selectedOrder)?.id ?: return@AssignVendorDialog
            viewModel.onEvent(SamplingUiEvent.AssignMakloonVendor(targetId, vendorInfo))
        }
    )

    // 5. Revision Dialog
    RevisionNotesDialog(
        isOpen = state.isRevisionDialogOpen,
        order = state.targetOrderForAction ?: state.selectedOrder,
        isSubmitting = state.isSubmitting,
        onDismiss = { viewModel.onEvent(SamplingUiEvent.CloseRevisionDialog) },
        onSubmit = { notes ->
            val targetId = (state.targetOrderForAction ?: state.selectedOrder)?.id ?: return@RevisionNotesDialog
            viewModel.onEvent(SamplingUiEvent.RequestRevision(targetId, notes))
        }
    )
}
