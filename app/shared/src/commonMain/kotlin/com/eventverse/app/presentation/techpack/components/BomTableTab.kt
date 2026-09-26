package com.eventverse.app.presentation.techpack.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eventverse.app.domain.contracts.BomLine
import com.eventverse.app.domain.pipeline.StockOwnershipSemantics
import com.eventverse.app.domain.techpack.TechPack
import com.eventverse.app.presentation.designsystem.*
import com.eventverse.app.presentation.techpack.TechPackUiEvent
import com.eventverse.app.presentation.techpack.format
import com.eventverse.app.presentation.techpack.toPercentageString
import com.eventverse.app.presentation.theme.WeMadeColors

@Composable
fun BomTableTab(
    techPack: TechPack,
    canManage: Boolean,
    onEvent: (TechPackUiEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(ClaySpacing.Md)
    ) {
        // Toolbar: Add Material & Summary
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Daftar Bahan Baku (BOM)",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = WeMadeColors.OnSurface
                )
                ClayBadge(
                    text = "${techPack.bomLines.size} Baris",
                    tint = WeMadeColors.Primary
                )
                if (techPack.unresolvedLines.isNotEmpty()) {
                    ClayBadge(
                        text = "${techPack.unresolvedLines.size} Belum Dipetakan",
                        tint = WeMadeColors.Warning
                    )
                }
            }

            if (canManage && techPack.isEditable) {
                ClayButton(
                    text = "+ Tambah Bahan",
                    onClick = { onEvent(TechPackUiEvent.OpenAddBomLineDialog) },
                    style = ClayButtonStyle.Primary
                )
            }
        }

        // BOM Lines list
        if (techPack.bomLines.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .claySurface(
                        shape = ClayShapes.Card,
                        background = WeMadeColors.Surface,
                        outline = WeMadeColors.OutlineSoft,
                        offset = ClayOffset.Small,
                        borderWidth = ClayBorder.Hairline
                    ),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(ClaySpacing.Xs)
                ) {
                    Text(
                        text = "BOM Masih Kosong",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = WeMadeColors.OnSurfaceMuted
                    )
                    Text(
                        text = "Tambahkan baris bahan baku untuk memulai spesifikasi Tech Pack",
                        fontSize = 12.sp,
                        color = WeMadeColors.OnSurfaceMuted
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)
            ) {
                items(techPack.bomLines, key = { it.lineId }) { line ->
                    BomLineCard(
                        line = line,
                        isEditable = techPack.isEditable && canManage,
                        onEdit = { onEvent(TechPackUiEvent.OpenEditBomLineDialog(line)) },
                        onDelete = { onEvent(TechPackUiEvent.DeleteBomLine(line.lineId)) }
                    )
                }
            }
        }
    }
}

@Composable
private fun BomLineCard(
    line: BomLine,
    isEditable: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .claySurface(
                shape = ClayShapes.Tile,
                background = WeMadeColors.Surface,
                outline = if (line.material.isResolved) WeMadeColors.Outline else WeMadeColors.Warning,
                offset = ClayOffset.Small,
                borderWidth = ClayBorder.Medium
            )
            .padding(ClaySpacing.Md)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)) {
            // Header: Category, Name, Resolved badge, Ownership
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ClayBadge(
                        text = line.category.displayName,
                        tint = WeMadeColors.Primary
                    )

                    Text(
                        text = line.material.displayLabel,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = WeMadeColors.OnSurface
                    )

                    if (line.material.isResolved) {
                        ClayBadge(
                            text = line.material.resolvedCode?.value ?: "Mapped",
                            tint = WeMadeColors.Success
                        )
                    } else {
                        ClayBadge(
                            text = "Free Text",
                            tint = WeMadeColors.Warning
                        )
                    }
                }

                OwnershipBadge(ownership = line.ownership)
            }

            // Metrics row: Net Qty, Waste %, Gross Qty
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Xl),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MetricColumn(
                    label = "Net per Pcs",
                    value = line.netQuantityPerGarment.format()
                )
                MetricColumn(
                    label = "Waste Allowance",
                    value = "${line.wasteAllowance.toPercentageString()}"
                )
                MetricColumn(
                    label = "Gross per Pcs",
                    value = line.grossQuantityPerGarment.format()
                )
            }

            // Notes and actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (line.notes.isNotBlank()) {
                    Text(
                        text = "Catatan: ${line.notes}",
                        fontSize = 11.sp,
                        color = WeMadeColors.OnSurfaceMuted
                    )
                } else {
                    Spacer(modifier = Modifier.width(1.dp))
                }

                if (isEditable) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ClayButton(
                            text = "Edit",
                            onClick = onEdit,
                            style = ClayButtonStyle.Secondary
                        )
                        ClayButton(
                            text = "Hapus",
                            onClick = onDelete,
                            style = ClayButtonStyle.Danger
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun OwnershipBadge(ownership: StockOwnershipSemantics) {
    val (text, tint) = when (ownership) {
        StockOwnershipSemantics.OWNED_RAW_MATERIAL -> "Aset Pabrik" to WeMadeColors.Teal
        StockOwnershipSemantics.CONSIGNED_CLIENT_MATERIAL -> "Konsinyasi Klien (Rp 0)" to WeMadeColors.Warning
        StockOwnershipSemantics.INTERNAL_FINISHED_GOODS -> "Stok Jadi" to WeMadeColors.Primary
        StockOwnershipSemantics.NON_STOCK_SERVICE -> "Jasa Murni" to WeMadeColors.Secondary
    }
    ClayBadge(text = text, tint = tint)
}

@Composable
private fun MetricColumn(label: String, value: String) {
    Column {
        Text(
            text = label,
            fontSize = 10.sp,
            color = WeMadeColors.OnSurfaceMuted
        )
        Text(
            text = value,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = WeMadeColors.OnSurface
        )
    }
}
