package com.eventverse.app.presentation.vendor.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eventverse.app.domain.vendor.VendorAssignment
import com.eventverse.app.domain.vendor.VendorPriceSource
import com.eventverse.app.domain.vendor.VendorQueueItem
import com.eventverse.app.presentation.crm.components.formatRupiah
import com.eventverse.app.presentation.designsystem.ClayBadge
import com.eventverse.app.presentation.designsystem.ClayButton
import com.eventverse.app.presentation.designsystem.ClayButtonStyle
import com.eventverse.app.presentation.designsystem.ClayCard
import com.eventverse.app.presentation.designsystem.ClayShapes
import com.eventverse.app.presentation.designsystem.ClaySpacing
import com.eventverse.app.presentation.designsystem.ClayTag
import com.eventverse.app.presentation.designsystem.IconInbox
import com.eventverse.app.presentation.designsystem.IconTruck
import com.eventverse.app.presentation.theme.WeMadeColors
import com.eventverse.app.presentation.vendor.VendorContactsUiEvent
import com.eventverse.app.presentation.vendor.VendorDialog

/**
 * Antrean proses Vendor Luar. Yang belum punya vendor berada di atas; baris yang sudah ditugaskan
 * tetap tampil supaya admin bisa mengganti vendor selama Surat Jalan belum terbit.
 *
 * Tanpa [canManage] (staf sampling), antrean tetap terbaca — mereka perlu tahu SPK-nya di vendor
 * mana — tetapi tombol penunjukan diganti keterangan siapa yang berwenang.
 */
@Composable
internal fun VendorQueuePane(
    queue: List<VendorQueueItem>,
    canManage: Boolean,
    onEvent: (VendorContactsUiEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    if (queue.isEmpty()) {
        VendorEmptyState(
            title = "Belum ada proses Vendor Luar",
            body = "Proses yang ditandai \"Vendor Luar\" saat menyusun alur SPK sampling akan muncul di sini untuk ditunjuk vendornya.",
            modifier = modifier
        )
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(ClaySpacing.Md),
        contentPadding = PaddingValues(bottom = ClaySpacing.Xl)
    ) {
        items(queue, key = { "${it.need.subjectId}/${it.need.processCode}" }) { item ->
            VendorQueueCard(item = item, canManage = canManage, onEvent = onEvent)
        }
    }
}

@Composable
private fun VendorQueueCard(
    item: VendorQueueItem,
    canManage: Boolean,
    onEvent: (VendorContactsUiEvent) -> Unit
) {
    val need = item.need
    ClayCard(
        modifier = Modifier.fillMaxWidth(),
        shape = ClayShapes.Card,
        outlineColor = if (item.isPending) WeMadeColors.Warning else WeMadeColors.Outline,
        // Warna status cukup di outline; bayangan oranye tebal membuat kartu terlihat seperti tombol.
        shadowColor = WeMadeColors.Outline,
        contentPadding = PaddingValues(ClaySpacing.Lg)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(ClaySpacing.Xs)) {
                Row(horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm), verticalAlignment = Alignment.CenterVertically) {
                    Text(need.subjectLabel, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = WeMadeColors.OnSurface)
                    ClayTag(text = need.processName, tint = WeMadeColors.Primary)
                }
                Text(
                    text = "${need.clientName} · ${need.styleName} · ${need.quantityPcs} pcs",
                    fontSize = 12.sp,
                    color = WeMadeColors.OnSurfaceMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                need.dueDate?.let {
                    Text("Tenggat finishing: $it", fontSize = 11.sp, color = WeMadeColors.OnSurfaceMuted)
                }
            }
            Spacer(Modifier.width(ClaySpacing.Sm))
            if (item.isPending) {
                ClayBadge(text = "Menunggu Vendor", tint = WeMadeColors.Warning, dot = true)
            } else {
                ClayBadge(text = "Ditugaskan", tint = WeMadeColors.Success, dot = true)
            }
        }

        Spacer(Modifier.size(ClaySpacing.Md))
        val assignment = item.assignment
        if (assignment != null) {
            AssignmentSummary(assignment)
            Spacer(Modifier.size(ClaySpacing.Md))
        }

        if (canManage) {
            Row(horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)) {
                ClayButton(
                    text = if (assignment == null) "Tunjuk Vendor" else "Ganti Vendor",
                    style = if (assignment == null) ClayButtonStyle.Primary else ClayButtonStyle.Secondary,
                    leading = { IconTruck(modifier = Modifier.size(14.dp), color = if (assignment == null) WeMadeColors.Surface else WeMadeColors.OnSurface) },
                    onClick = { onEvent(VendorContactsUiEvent.OpenDialog(VendorDialog.Assign(item))) }
                )
                if (assignment != null) {
                    ClayButton(
                        text = "Batalkan",
                        style = ClayButtonStyle.Ghost,
                        onClick = { onEvent(VendorContactsUiEvent.CancelAssignment(assignment)) }
                    )
                }
            }
        } else if (assignment == null) {
            Text(
                text = "Vendor akan ditunjuk oleh admin produksi.",
                fontSize = 11.sp,
                color = WeMadeColors.OnSurfaceMuted
            )
        }
    }
}

@Composable
private fun AssignmentSummary(assignment: VendorAssignment) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f, fill = false)) {
            Text(
                text = assignment.vendorName.value,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = WeMadeColors.OnSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${formatRupiah(assignment.pricePerUnitIdr)} / ${assignment.unit.shortLabel}" +
                    (assignment.expectedReturnAt?.let { " · kembali $it" } ?: ""),
                fontSize = 11.sp,
                color = WeMadeColors.OnSurfaceMuted
            )
        }
        Spacer(Modifier.width(ClaySpacing.Sm))
        Column(horizontalAlignment = Alignment.End) {
            Text(formatRupiah(assignment.totalIdr), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = WeMadeColors.OnSurface)
            ClayTag(
                text = assignment.priceSource.displayName,
                tint = if (assignment.priceSource == VendorPriceSource.NEGOTIATED) WeMadeColors.Accent else WeMadeColors.Teal
            )
        }
    }
}

@Composable
internal fun VendorEmptyState(title: String, body: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(ClaySpacing.Xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(ClaySpacing.Sm, Alignment.CenterVertically)
    ) {
        IconInbox(modifier = Modifier.size(32.dp), color = WeMadeColors.OnSurfaceMuted)
        Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = WeMadeColors.OnSurface)
        Text(body, fontSize = 12.sp, color = WeMadeColors.OnSurfaceMuted)
    }
}
