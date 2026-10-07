package com.eventverse.app.presentation.vendor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eventverse.app.domain.rbac.AccessDecision
import com.eventverse.app.presentation.designsystem.ClayBadge
import com.eventverse.app.presentation.designsystem.ClayBorder
import com.eventverse.app.presentation.designsystem.ClayBreakpoints
import com.eventverse.app.presentation.designsystem.ClayButton
import com.eventverse.app.presentation.designsystem.ClayCard
import com.eventverse.app.presentation.designsystem.ClayChoiceChip
import com.eventverse.app.presentation.designsystem.ClayOffset
import com.eventverse.app.presentation.designsystem.ClayPaneWidth
import com.eventverse.app.presentation.designsystem.ClayShapes
import com.eventverse.app.presentation.designsystem.ClaySpacing
import com.eventverse.app.presentation.designsystem.ClayStatusBanner
import com.eventverse.app.presentation.designsystem.IconTruck
import com.eventverse.app.presentation.designsystem.claySurface
import com.eventverse.app.presentation.theme.WeMadeColors
import com.eventverse.app.presentation.vendor.components.VendorAssignDialog
import com.eventverse.app.presentation.vendor.components.VendorContactDetail
import com.eventverse.app.presentation.vendor.components.VendorContactList
import com.eventverse.app.presentation.vendor.components.VendorProfileDialog
import com.eventverse.app.presentation.vendor.components.VendorQueuePane
import com.eventverse.app.presentation.vendor.components.VendorRateDialog
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn

/**
 * Layar "Kontak Vendor" — shell: toolbar, tab, dan perakitan dialog.
 *
 * [canManage] mengikuti level `MANAGE` karena server menggerbang semua penulisan di level itu;
 * memakai `canWrite` (OPERATE) di sini akan menampilkan tombol yang pasti berakhir 403.
 */
@Composable
fun VendorContactsWorkspaceScreen(
    tenantSlug: String,
    decision: AccessDecision,
    modifier: Modifier = Modifier,
    viewModel: VendorContactsViewModel = remember(tenantSlug) { VendorContactsViewModel(tenantSlug) }
) {
    val state by viewModel.uiState.collectAsState()
    val canManage = decision.config.canManage
    val today = remember { Clock.System.todayIn(TimeZone.currentSystemDefault()) }
    val onEvent = viewModel::onEvent

    Column(modifier = modifier.fillMaxSize()) {
        VendorToolbar(state = state, canManage = canManage, onEvent = onEvent)

        state.statusMessage?.takeIf { state.dialog == null }?.let { message ->
            ClayStatusBanner(
                message = message,
                isError = state.isErrorMessage,
                onDismiss = { onEvent(VendorContactsUiEvent.DismissMessage) },
                modifier = Modifier.padding(horizontal = ClaySpacing.Md, vertical = ClaySpacing.Xs)
            )
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = ClaySpacing.Md, vertical = ClaySpacing.Sm)
        ) {
            when (state.tab) {
                VendorTab.QUEUE -> VendorQueuePane(queue = state.queue, canManage = canManage, onEvent = onEvent)
                VendorTab.CONTACTS -> ContactsWorkbench(state = state, canManage = canManage, today = today, onEvent = onEvent)
            }
        }
    }

    VendorDialogs(state = state, today = today, onEvent = onEvent)
}

@Composable
private fun VendorToolbar(state: VendorContactsUiState, canManage: Boolean, onEvent: (VendorContactsUiEvent) -> Unit) {
    ClayCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = ClaySpacing.Md, vertical = ClaySpacing.Sm),
        contentPadding = PaddingValues(horizontal = ClaySpacing.Md, vertical = ClaySpacing.Sm)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f, fill = false),
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
                    IconTruck(modifier = Modifier.size(20.dp), color = WeMadeColors.Teal)
                }
                Column(modifier = Modifier.weight(1f, fill = false)) {
                    Text("KONTAK VENDOR & MAKLOON", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = WeMadeColors.OnSurface)
                    Text(
                        text = "Vendor rekanan, harga layanan per vendor, dan penunjukan vendor untuk proses Vendor Luar",
                        fontSize = 11.sp,
                        color = WeMadeColors.OnSurfaceMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm), verticalAlignment = Alignment.CenterVertically) {
                if (!canManage) ClayBadge(text = "Hanya Lihat", tint = WeMadeColors.OnSurfaceMuted)
                if (canManage) {
                    ClayButton(text = "+ Tambah Vendor", onClick = { onEvent(VendorContactsUiEvent.OpenDialog(VendorDialog.CreateVendor())) })
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm), modifier = Modifier.padding(top = ClaySpacing.Sm)) {
            VendorTab.entries.forEach { tab ->
                val label = when (tab) {
                    VendorTab.QUEUE -> "${tab.displayName} (${state.pendingCount})"
                    VendorTab.CONTACTS -> "${tab.displayName} (${state.vendors.count { it.isActive }})"
                }
                ClayChoiceChip(
                    text = label,
                    selected = state.tab == tab,
                    tint = if (tab == VendorTab.QUEUE && state.pendingCount > 0) WeMadeColors.Accent else WeMadeColors.Primary,
                    onClick = { onEvent(VendorContactsUiEvent.SelectTab(tab)) }
                )
            }
        }
    }
}

/** Master-detail di layar lebar; di layar sempit daftar dan detail ditumpuk. */
@Composable
private fun ContactsWorkbench(
    state: VendorContactsUiState,
    canManage: Boolean,
    today: kotlinx.datetime.LocalDate,
    onEvent: (VendorContactsUiEvent) -> Unit
) {
    val selected = state.selectedVendor
    val list: @Composable (Modifier) -> Unit = { modifier ->
        VendorContactList(
            vendors = state.filteredVendors,
            selectedVendor = selected,
            searchQuery = state.searchQuery,
            showInactive = state.showInactive,
            today = today,
            onSearch = { onEvent(VendorContactsUiEvent.UpdateSearch(it)) },
            onToggleInactive = { onEvent(VendorContactsUiEvent.SetShowInactive(it)) },
            onSelect = { onEvent(VendorContactsUiEvent.SelectVendor(it.id)) },
            modifier = modifier
        )
    }
    val detail: @Composable (Modifier) -> Unit = { modifier ->
        if (selected != null) {
            VendorContactDetail(
                vendor = selected,
                history = state.selectedVendorHistory,
                today = today,
                canManage = canManage,
                onEdit = { onEvent(VendorContactsUiEvent.OpenDialog(VendorDialog.EditVendor(selected.id))) },
                onAddRate = { onEvent(VendorContactsUiEvent.OpenDialog(VendorDialog.AddRate(selected.id))) },
                modifier = modifier
            )
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        if (maxWidth >= ClayBreakpoints.MasterDetail) {
            Row(horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Lg), modifier = Modifier.fillMaxSize()) {
                list(Modifier.width(ClayPaneWidth.List).fillMaxHeight())
                detail(Modifier.weight(1f).fillMaxHeight())
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(ClaySpacing.Lg), modifier = Modifier.fillMaxSize()) {
                list(Modifier.weight(1f).fillMaxWidth())
                detail(Modifier.weight(1f).fillMaxWidth())
            }
        }
    }
}

@Composable
private fun VendorDialogs(state: VendorContactsUiState, today: kotlinx.datetime.LocalDate, onEvent: (VendorContactsUiEvent) -> Unit) {
    val close = { onEvent(VendorContactsUiEvent.CloseDialog) }
    val error = state.statusMessage?.takeIf { state.isErrorMessage }

    when (val dialog = state.dialog) {
        null -> Unit
        is VendorDialog.CreateVendor -> VendorProfileDialog(
            initial = null,
            isSubmitting = state.isSubmitting,
            errorMessage = error,
            onDismiss = { dialog.resumeAssign?.let { onEvent(VendorContactsUiEvent.OpenDialog(VendorDialog.Assign(it))) } ?: close() },
            onSave = { onEvent(VendorContactsUiEvent.SaveVendor(null, it)) }
        )
        is VendorDialog.EditVendor -> state.vendorById(dialog.vendorId)?.let { vendor ->
            VendorProfileDialog(
                initial = vendor,
                isSubmitting = state.isSubmitting,
                errorMessage = error,
                onDismiss = close,
                onSave = { onEvent(VendorContactsUiEvent.SaveVendor(vendor.id, it)) }
            )
        }
        is VendorDialog.AddRate -> state.vendorById(dialog.vendorId)?.let { vendor ->
            VendorRateDialog(
                vendor = vendor,
                today = today,
                isSubmitting = state.isSubmitting,
                errorMessage = error,
                onDismiss = close,
                onSave = { onEvent(VendorContactsUiEvent.SaveRate(vendor.id, it)) }
            )
        }
        is VendorDialog.Assign -> VendorAssignDialog(
            item = dialog.item,
            options = state.optionsFor(dialog.item.need.processCode, today),
            isSubmitting = state.isSubmitting,
            errorMessage = error,
            onDismiss = close,
            onAddVendor = { onEvent(VendorContactsUiEvent.OpenDialog(VendorDialog.CreateVendor(resumeAssign = dialog.item))) },
            onConfirm = { onEvent(VendorContactsUiEvent.Assign(it)) }
        )
    }
}
