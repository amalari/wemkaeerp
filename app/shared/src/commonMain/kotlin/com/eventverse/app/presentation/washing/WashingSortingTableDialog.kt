package com.eventverse.app.presentation.washing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.eventverse.app.domain.workqueue.WashingBatch
import com.eventverse.app.domain.workqueue.WashingSortOutput
import com.eventverse.app.presentation.designsystem.ClayBadge
import com.eventverse.app.presentation.designsystem.ClayBorder
import com.eventverse.app.presentation.designsystem.ClayButton
import com.eventverse.app.presentation.designsystem.ClayButtonStyle
import com.eventverse.app.presentation.designsystem.ClayCard
import com.eventverse.app.presentation.designsystem.ClayShapes
import com.eventverse.app.presentation.designsystem.ClaySpacing
import com.eventverse.app.presentation.designsystem.IconCheck
import com.eventverse.app.presentation.designsystem.IconPackage
import com.eventverse.app.presentation.designsystem.IconWarning
import com.eventverse.app.presentation.designsystem.clayFlat
import com.eventverse.app.presentation.theme.WeMadeColors

private data class SortRowState(
    val outputPcs: String,
    val scrapPcs: String,
    val defectPcs: String,
    val notes: String
)

/**
 * Dialog Meja Sortir Pasca-Dryer.
 * Merekonsiliasi pakaian keluar mesin pengering dan memecah tumpukan menjadi Kartu Lot
 * per PO & per Ukuran untuk diserahkan ke Meja Setrika Uap (STEAM).
 */
@Composable
fun WashingSortingTableDialog(
    batch: WashingBatch,
    isSubmitting: Boolean = false,
    onDismiss: () -> Unit,
    onSubmit: (outputs: List<WashingSortOutput>) -> Unit
) {
    // Kelompokkan item asal batch per (subjectId + sizeLabel)
    val groupedInputs = remember(batch.items) {
        batch.items.groupBy { "${it.subjectId}|${it.sizeLabel}" }
            .map { (key, items) ->
                val first = items.first()
                val totalIn = items.sumOf { it.inputPcs }
                Triple(first, totalIn, key)
            }
    }

    // State per row: key -> SortRowState
    val rowStates = remember(groupedInputs) {
        val map = mutableStateMapOf<String, SortRowState>()
        groupedInputs.forEach { (_, totalIn, key) ->
            map[key] = SortRowState(
                outputPcs = totalIn.toString(),
                scrapPcs = "0",
                defectPcs = "0",
                notes = ""
            )
        }
        map
    }

    val parsedOutputs = groupedInputs.map { (first, _, key) ->
        val state = rowStates[key] ?: SortRowState("0", "0", "0", "")
        WashingSortOutput(
            subjectId = first.subjectId,
            orderNumber = first.orderNumber,
            sizeLabel = first.sizeLabel,
            outputPcs = state.outputPcs.toIntOrNull()?.coerceAtLeast(0) ?: 0,
            scrapPcs = state.scrapPcs.toIntOrNull()?.coerceAtLeast(0) ?: 0,
            defectPcs = state.defectPcs.toIntOrNull()?.coerceAtLeast(0) ?: 0,
            notes = state.notes
        )
    }

    val totalOutputPcs = parsedOutputs.sumOf { it.outputPcs }
    val totalScrapPcs = parsedOutputs.sumOf { it.scrapPcs }
    val totalDefectPcs = parsedOutputs.sumOf { it.defectPcs }
    val totalAccounted = parsedOutputs.sumOf { it.totalAccountedPcs }
    val missingDelta = batch.totalInputPcs - totalAccounted
    val isBalanced = missingDelta == 0

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        ClayCard(
            modifier = Modifier
                .width(820.dp)
                .heightIn(max = 700.dp)
                .padding(ClaySpacing.Md)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(ClaySpacing.Md),
                verticalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Meja Sortir Pasca-Dryer (Pemisahan Lot Setrika)",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = WeMadeColors.OnSurface
                        )
                        Text(
                            text = "Batch ${batch.batchCode} · ${batch.machineDrumNo} · ${batch.washRecipe}",
                            fontSize = 12.sp,
                            color = WeMadeColors.OnSurfaceMuted
                        )
                    }
                    ClayBadge(
                        text = if (isBalanced) "Stok Seimbang" else "Selisih: $missingDelta Pcs",
                        tint = if (isBalanced) WeMadeColors.Success else WeMadeColors.Error,
                        leading = {
                            if (isBalanced) IconCheck(modifier = Modifier.size(12.dp))
                            else IconWarning(modifier = Modifier.size(12.dp))
                        }
                    )
                }

                // Info Banner
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clayFlat(
                            shape = ClayShapes.Chip,
                            background = WeMadeColors.SurfaceMuted,
                            outline = WeMadeColors.Outline,
                            borderWidth = ClayBorder.Hairline
                        )
                        .padding(horizontal = ClaySpacing.Sm, vertical = ClaySpacing.Xs),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Total Masuk Cuci: ${batch.totalInputPcs} Pcs", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = WeMadeColors.OnSurface)
                    Text("Siap Setrika: $totalOutputPcs Pcs", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = WeMadeColors.Primary)
                    Text("Cacat Cuci/Robek: $totalScrapPcs Pcs", fontSize = 12.sp, color = WeMadeColors.Error)
                    Text("Rework Noda: $totalDefectPcs Pcs", fontSize = 12.sp, color = WeMadeColors.Warning)
                }

                Text(
                    text = "Rincian Hitung Fisik per PO & Ukuran (Size):",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = WeMadeColors.OnSurface
                )

                // Table of Grouped Rows per PO and Size
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .heightIn(max = 340.dp)
                        .clayFlat(
                            shape = ClayShapes.Card,
                            background = WeMadeColors.SurfaceMuted,
                            outline = WeMadeColors.Outline,
                            borderWidth = ClayBorder.Hairline
                        )
                        .padding(ClaySpacing.Xs),
                    verticalArrangement = Arrangement.spacedBy(ClaySpacing.Xs)
                ) {
                    items(groupedInputs, key = { it.third }) { (first, totalIn, key) ->
                        val state = rowStates[key] ?: SortRowState("0", "0", "0", "")

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clayFlat(
                                    shape = ClayShapes.Chip,
                                    background = WeMadeColors.Surface,
                                    outline = WeMadeColors.Outline,
                                    borderWidth = ClayBorder.Hairline
                                )
                                .padding(ClaySpacing.Sm),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)
                        ) {
                            IconPackage(modifier = Modifier.size(20.dp), color = WeMadeColors.Primary)

                            Column(modifier = Modifier.weight(1.3f)) {
                                Text(
                                    text = "${first.orderNumber} · ${first.articleName}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = WeMadeColors.OnSurface
                                )
                                Text(
                                    text = "Ukuran: ${first.sizeLabel} | Input Cuci: $totalIn Pcs",
                                    fontSize = 11.sp,
                                    color = WeMadeColors.OnSurfaceMuted
                                )
                            }

                            // Input Siap Setrika
                            OutlinedTextField(
                                value = state.outputPcs,
                                onValueChange = { rowStates[key] = state.copy(outputPcs = it) },
                                label = { Text("Setrika (Pcs)", fontSize = 10.sp) },
                                modifier = Modifier.width(105.dp),
                                singleLine = true,
                                colors = outlinedColors()
                            )

                            // Input Robek / Scrap
                            OutlinedTextField(
                                value = state.scrapPcs,
                                onValueChange = { rowStates[key] = state.copy(scrapPcs = it) },
                                label = { Text("Robek (Pcs)", fontSize = 10.sp) },
                                modifier = Modifier.width(95.dp),
                                singleLine = true,
                                colors = outlinedColors()
                            )

                            // Input Noda / Defect
                            OutlinedTextField(
                                value = state.defectPcs,
                                onValueChange = { rowStates[key] = state.copy(defectPcs = it) },
                                label = { Text("Noda (Pcs)", fontSize = 10.sp) },
                                modifier = Modifier.width(95.dp),
                                singleLine = true,
                                colors = outlinedColors()
                            )
                        }
                    }
                }

                // Missing Warning Banner if not balanced
                if (!isBalanced) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clayFlat(
                                shape = ClayShapes.Chip,
                                background = WeMadeColors.WarningBg,
                                outline = WeMadeColors.Warning,
                                borderWidth = ClayBorder.Hairline
                            )
                            .padding(ClaySpacing.Sm),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)
                    ) {
                        IconWarning(modifier = Modifier.size(16.dp), color = WeMadeColors.Warning)
                        Text(
                            text = "Tercatat $totalAccounted dari ${batch.totalInputPcs} Pcs. Selisih $missingDelta Pcs akan dicatat sebagai 'missing_pcs' untuk investigasi drum/tumbler.",
                            fontSize = 11.sp,
                            color = WeMadeColors.OnSurface
                        )
                    }
                }

                // Footer
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ClayButton(
                        text = "Tutup",
                        style = ClayButtonStyle.Ghost,
                        enabled = !isSubmitting,
                        onClick = onDismiss
                    )
                    Spacer(modifier = Modifier.width(ClaySpacing.Sm))
                    ClayButton(
                        text = if (isSubmitting) "Menerbitkan..." else "Terbitkan Kartu Lot ke Meja Setrika ($totalOutputPcs Pcs)",
                        style = ClayButtonStyle.Success,
                        enabled = !isSubmitting && totalOutputPcs > 0,
                        onClick = { onSubmit(parsedOutputs) }
                    )
                }
            }
        }
    }
}

@Composable
private fun outlinedColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = WeMadeColors.Primary,
    unfocusedBorderColor = WeMadeColors.Outline,
    focusedContainerColor = WeMadeColors.Surface,
    unfocusedContainerColor = WeMadeColors.Surface
)
