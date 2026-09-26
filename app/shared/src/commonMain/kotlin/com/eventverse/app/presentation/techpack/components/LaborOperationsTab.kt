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
import com.eventverse.app.domain.contracts.LaborOperation
import com.eventverse.app.domain.techpack.TechPack
import com.eventverse.app.presentation.designsystem.*
import com.eventverse.app.presentation.techpack.TechPackUiEvent
import com.eventverse.app.presentation.techpack.toFormattedString
import com.eventverse.app.presentation.theme.WeMadeColors

@Composable
fun LaborOperationsTab(
    techPack: TechPack,
    canManage: Boolean,
    onEvent: (TechPackUiEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(ClaySpacing.Md)
    ) {
        // Toolbar
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
                    text = "Operasi Kerja & Standar Waktu (SAM)",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = WeMadeColors.OnSurface
                )
                ClayBadge(
                    text = "${techPack.laborOperations.size} Operasi",
                    tint = WeMadeColors.Primary
                )
                ClayBadge(
                    text = "Total SAM: ${techPack.totalSamMinutes.toFormattedString()} mnt",
                    tint = WeMadeColors.Teal
                )
            }

            if (canManage && techPack.isEditable) {
                ClayButton(
                    text = "+ Tambah Operasi",
                    onClick = { onEvent(TechPackUiEvent.OpenAddLaborOpDialog) },
                    style = ClayButtonStyle.Primary
                )
            }
        }

        // Operations list
        if (techPack.laborOperations.isEmpty()) {
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
                        text = "Belum Ada Operasi Kerja",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = WeMadeColors.OnSurfaceMuted
                    )
                    Text(
                        text = "Tambahkan rincian proses knitting, linking, washing, atau finishing beserta SAM",
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
                items(techPack.laborOperations, key = { it.operationId }) { op ->
                    LaborOperationCard(
                        op = op,
                        isEditable = techPack.isEditable && canManage,
                        onEdit = { onEvent(TechPackUiEvent.OpenEditLaborOpDialog(op)) },
                        onDelete = { onEvent(TechPackUiEvent.DeleteLaborOp(op.operationId)) }
                    )
                }
            }
        }
    }
}

@Composable
private fun LaborOperationCard(
    op: LaborOperation,
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
                outline = WeMadeColors.Outline,
                offset = ClayOffset.Small,
                borderWidth = ClayBorder.Medium
            )
            .padding(ClaySpacing.Md)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(ClaySpacing.Xs)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = op.name,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = WeMadeColors.OnSurface
                    )

                    if (op.workstation.isNotBlank()) {
                        ClayBadge(
                            text = op.workstation,
                            tint = WeMadeColors.Secondary
                        )
                    }

                    if (op.isSubcontracted) {
                        ClayBadge(
                            text = "Subkon Eksternal",
                            tint = WeMadeColors.Warning
                        )
                    } else {
                        ClayBadge(
                            text = "Internal Pabrik",
                            tint = WeMadeColors.Teal
                        )
                    }
                }

                Text(
                    text = "Standard Allowed Minutes: ${op.samMinutes.toFormattedString()} menit",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = WeMadeColors.Primary
                )
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
