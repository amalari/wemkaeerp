package com.eventverse.app.presentation.production

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eventverse.app.domain.production.BulkProductionStatus
import com.eventverse.app.domain.production.BulkWorkOrder
import com.eventverse.app.domain.rbac.AccessDecision
import com.eventverse.app.domain.rbac.TestingPersona
import com.eventverse.app.presentation.designsystem.*
import com.eventverse.app.presentation.production.components.AllocateLineDialog
import com.eventverse.app.presentation.production.components.BulkWorkOrderDetailPane
import com.eventverse.app.presentation.production.components.ProductionHealthBadge
import com.eventverse.app.presentation.production.components.ProductionStatusBadge
import com.eventverse.app.presentation.production.components.RecordProgressDialog
import com.eventverse.app.presentation.theme.WeMadeColors

/**
 * Layar kerja modul `PRODUCTION_MRP` — Jadwal Mesin & SPK Massal.
 *
 * Master-detail: daftar SPK di kiri, rincian SPK terpilih di kanan. Di bawah
 * [ClayBreakpoints.MasterDetail] keduanya bertumpuk jadi satu kolom, karena dua panel di lebar
 * ponsel menyisakan ruang yang membuat teks pecah per huruf.
 */
@Composable
fun ProductionWorkspaceScreen(
    tenantSlug: String,
    decision: AccessDecision,
    persona: TestingPersona?,
    modifier: Modifier = Modifier,
    viewModel: ProductionViewModel = remember(tenantSlug) { ProductionViewModel(tenantSlug) }
) {
    val state by viewModel.uiState.collectAsState()
    val accessLevel = decision.config.level

    Column(modifier = modifier.fillMaxSize()) {
        ProductionHeader(state = state)

        state.statusMessage?.let { message ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = ClaySpacing.Lg, vertical = ClaySpacing.Xs)
            ) {
                ClayBadge(
                    text = message,
                    tint = if (state.isErrorMessage) WeMadeColors.Error else WeMadeColors.Success,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val isCompact = maxWidth < ClayBreakpoints.MasterDetail

            when {
                state.isLoading && state.workOrders.isEmpty() -> LoadingPane()
                state.workOrders.isEmpty() -> EmptyStatePane()
                isCompact -> CompactPane(state, accessLevel, viewModel)
                else -> MasterDetailPane(state, accessLevel, viewModel)
            }
        }
    }

    AllocateLineDialog(
        isOpen = state.isAllocateDialogOpen,
        order = state.selectedWorkOrder,
        isSubmitting = state.isSubmitting,
        onDismiss = { viewModel.onEvent(ProductionUiEvent.CloseAllocateDialog) },
        onSubmit = { allocation ->
            state.selectedWorkOrder?.let {
                viewModel.onEvent(ProductionUiEvent.AllocateLine(it.id, allocation))
            }
        }
    )

    RecordProgressDialog(
        isOpen = state.isProgressDialogOpen,
        order = state.selectedWorkOrder,
        stage = state.progressDialogStage,
        isSubmitting = state.isSubmitting,
        onDismiss = { viewModel.onEvent(ProductionUiEvent.CloseProgressDialog) },
        onSubmit = { completed, rework, reject ->
            state.selectedWorkOrder?.let {
                viewModel.onEvent(
                    ProductionUiEvent.RecordProgress(
                        id = it.id,
                        stage = state.progressDialogStage,
                        completedPcs = completed,
                        reworkPcs = rework,
                        rejectPcs = reject
                    )
                )
            }
        }
    )
}

@Composable
private fun ProductionHeader(state: ProductionUiState) {
    ClayCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = ClaySpacing.Lg, vertical = ClaySpacing.Md),
        contentPadding = PaddingValues(horizontal = ClaySpacing.Xl, vertical = ClaySpacing.Lg)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f, fill = false)) {
                Text(
                    text = "JADWAL MESIN & SPK MASSAL",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = WeMadeColors.OnSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Alokasi lini, antrean potong-jahit-finishing, dan realisasi harian",
                    fontSize = 11.sp,
                    color = WeMadeColors.OnSurfaceMuted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(ClaySpacing.Sm))
            ClayBadge(text = "${state.activeOrders.size} SPK Aktif", tint = WeMadeColors.Primary)
        }

        Spacer(Modifier.height(ClaySpacing.Lg))

        Row(horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)) {
            KpiTag("Pesanan", "${state.totalOrderedPcs} pcs", WeMadeColors.Primary)
            KpiTag("WIP", "${state.totalWip} pcs", WeMadeColors.Warning)
            KpiTag("Selesai", "${state.totalCompletedPcs} pcs", WeMadeColors.Success)
            if (state.totalRejectPcs > 0) {
                KpiTag("Reject", "${state.totalRejectPcs} pcs", WeMadeColors.Defect)
            }
        }
    }
}

@Composable
private fun KpiTag(label: String, value: String, tint: androidx.compose.ui.graphics.Color) {
    ClayTag(text = "$label: $value", tint = tint)
}

@Composable
private fun LoadingPane() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = WeMadeColors.Primary)
    }
}

@Composable
private fun EmptyStatePane() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        ClayCard(
            modifier = Modifier.widthIn(max = 460.dp).padding(ClaySpacing.Xl),
            contentPadding = PaddingValues(ClaySpacing.Xl)
        ) {
            Text(
                text = "Belum Ada SPK Massal",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = WeMadeColors.OnSurface
            )
            Spacer(Modifier.height(ClaySpacing.Sm))
            Text(
                text = "SPK massal tidak dibuat dari layar ini. Ia lahir dari Deal yang sampelnya " +
                    "sudah di-ACC buyer — buka Deal, masuk Tab \"Produksi Massal & PO\", " +
                    "lalu tekan [ Luncurkan SPK Massal ].",
                fontSize = 12.sp,
                color = WeMadeColors.OnSurfaceMuted
            )
        }
    }
}

@Composable
private fun MasterDetailPane(
    state: ProductionUiState,
    accessLevel: com.eventverse.app.domain.rbac.AccessLevel,
    viewModel: ProductionViewModel
) {
    Row(modifier = Modifier.fillMaxSize()) {
        WorkOrderList(
            state = state,
            viewModel = viewModel,
            modifier = Modifier.width(ClayPaneWidth.List).fillMaxHeight()
        )
        state.selectedWorkOrder?.let { order ->
            BulkWorkOrderDetailPane(
                order = order,
                accessLevel = accessLevel,
                onAllocateLine = { viewModel.onEvent(ProductionUiEvent.OpenAllocateDialog(order.id)) },
                onRemoveLine = { line -> viewModel.onEvent(ProductionUiEvent.RemoveLine(order.id, line)) },
                onRecordProgress = { stage ->
                    viewModel.onEvent(ProductionUiEvent.OpenProgressDialog(order.id, stage))
                },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun CompactPane(
    state: ProductionUiState,
    accessLevel: com.eventverse.app.domain.rbac.AccessLevel,
    viewModel: ProductionViewModel
) {
    val order = state.selectedWorkOrder
    if (order == null) {
        WorkOrderList(state = state, viewModel = viewModel, modifier = Modifier.fillMaxSize())
        return
    }
    BulkWorkOrderDetailPane(
        order = order,
        accessLevel = accessLevel,
        onAllocateLine = { viewModel.onEvent(ProductionUiEvent.OpenAllocateDialog(order.id)) },
        onRemoveLine = { line -> viewModel.onEvent(ProductionUiEvent.RemoveLine(order.id, line)) },
        onRecordProgress = { stage ->
            viewModel.onEvent(ProductionUiEvent.OpenProgressDialog(order.id, stage))
        },
        modifier = Modifier.fillMaxSize()
    )
}

@Composable
private fun WorkOrderList(
    state: ProductionUiState,
    viewModel: ProductionViewModel,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.padding(ClaySpacing.Lg)) {
        ClayTextField(
            value = state.searchQuery,
            onValueChange = { viewModel.onEvent(ProductionUiEvent.UpdateSearchQuery(it)) },
            label = "Cari SPK / klien / style",
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(ClaySpacing.Md))

        StatusFilterRow(
            selected = state.statusFilter,
            onSelect = { viewModel.onEvent(ProductionUiEvent.SetStatusFilter(it)) }
        )

        Spacer(Modifier.height(ClaySpacing.Md))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(ClaySpacing.Md)) {
            items(state.filteredWorkOrders, key = { it.id.value }) { order ->
                WorkOrderCard(
                    order = order,
                    isSelected = order.id == state.selectedWorkOrder?.id,
                    onClick = { viewModel.onEvent(ProductionUiEvent.SelectWorkOrder(order.id)) }
                )
            }
        }
    }
}

@Composable
private fun WorkOrderCard(
    order: BulkWorkOrder,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    ClayCard(
        modifier = Modifier.fillMaxWidth(),
        // Ketebalan outline tetap; yang membedakan state adalah warnanya (Kontrak 8).
        outlineColor = if (isSelected) WeMadeColors.Primary else WeMadeColors.Outline,
        selected = isSelected,
        offset = ClayOffset.Small,
        contentPadding = PaddingValues(ClaySpacing.Lg),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = order.spkNumber.value,
                modifier = Modifier.weight(1f, fill = false),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = WeMadeColors.OnSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            order.sizeLabel?.let { size ->
                Spacer(Modifier.width(ClaySpacing.Sm))
                ClayTag(text = size, tint = WeMadeColors.Primary)
            }
            Spacer(Modifier.width(ClaySpacing.Sm))
            ProductionHealthBadge(order.healthStatus)
        }

        Spacer(Modifier.height(ClaySpacing.Xs))
        Text(
            text = "${order.clientName} — ${order.styleName}",
            fontSize = 11.sp,
            color = WeMadeColors.OnSurfaceMuted,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(Modifier.height(ClaySpacing.Md))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(modifier = Modifier.weight(1f, fill = false)) {
                ProductionStatusBadge(order.status)
            }
            Spacer(Modifier.width(ClaySpacing.Sm))
            ClayTag(
                text = "${order.completedPcs}/${order.totalOrderedPcs} pcs",
                tint = WeMadeColors.Info
            )
        }
    }
}

/**
 * Penyaring status SPK.
 *
 * Hanya status yang benar-benar dilewati SPK di lantai produksi yang ditampilkan; `DRAFT`
 * ikut masuk karena SPK bisa tertahan di sana kalau size breakdown-nya belum beres, dan
 * SPK yang tertahan justru yang paling perlu dicari.
 */
@Composable
private fun StatusFilterRow(
    selected: BulkProductionStatus?,
    onSelect: (BulkProductionStatus?) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)
    ) {
        FilterChip(label = "Semua", isActive = selected == null, onClick = { onSelect(null) })
        BulkProductionStatus.entries.forEach { status ->
            FilterChip(
                label = status.displayName,
                isActive = selected == status,
                onClick = { onSelect(if (selected == status) null else status) }
            )
        }
    }
}

@Composable
private fun FilterChip(label: String, isActive: Boolean, onClick: () -> Unit) {
    ClayActionSurface(
        onClick = onClick,
        containerColor = if (isActive) WeMadeColors.PrimaryContainer else WeMadeColors.Surface,
        outlineColor = if (isActive) WeMadeColors.Primary else WeMadeColors.Outline,
        offset = ClayOffset.Small,
        contentPadding = PaddingValues(horizontal = ClaySpacing.Lg, vertical = ClaySpacing.Sm)
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
            color = if (isActive) WeMadeColors.Primary else WeMadeColors.OnSurfaceMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
