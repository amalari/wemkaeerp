package com.eventverse.app.presentation.techpack.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.eventverse.app.domain.common.Ratio
import com.eventverse.app.domain.contracts.SizeYieldFactor
import com.eventverse.app.presentation.designsystem.*
import com.eventverse.app.presentation.techpack.toFormattedString
import com.eventverse.app.presentation.techpack.toPercentageString
import com.eventverse.app.presentation.theme.WeMadeColors

@Composable
fun SizeYieldEditorDialog(
    initialFactors: List<SizeYieldFactor>,
    isSubmitting: Boolean,
    onDismiss: () -> Unit,
    onSave: (List<SizeYieldFactor>) -> Unit
) {
    var factorsList by remember {
        val list = if (initialFactors.isEmpty()) {
            listOf(
                SizeYieldFactor("S", Ratio(90, 100), 20L),
                SizeYieldFactor("M", Ratio.ONE, 40L),
                SizeYieldFactor("L", Ratio(110, 100), 30L),
                SizeYieldFactor("XL", Ratio(125, 100), 10L)
            )
        } else {
            initialFactors
        }
        mutableStateOf(list)
    }

    var newLabel by remember { mutableStateOf("") }
    var newScaleText by remember { mutableStateOf("1.0") }
    var newQtyText by remember { mutableStateOf("0") }

    Dialog(onDismissRequest = onDismiss) {
        ClayCard(
            modifier = Modifier.fillMaxWidth().padding(ClaySpacing.Md),
            shape = ClayShapes.Card,
            contentPadding = PaddingValues(ClaySpacing.Lg)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(ClaySpacing.Md)
            ) {
                Text(
                    text = "Kelola Skala Yield Ukuran",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = WeMadeColors.OnSurface
                )

                Text(
                    text = "Atur faktor pengali konsumsi kain untuk tiap variasi ukuran beserta alokasi kuantitas order.",
                    fontSize = 12.sp,
                    color = WeMadeColors.OnSurfaceMuted
                )

                // Current Factors List
                Column(verticalArrangement = Arrangement.spacedBy(ClaySpacing.Xs)) {
                    factorsList.forEachIndexed { index, factor ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                ClayBadge(text = factor.sizeLabel, tint = WeMadeColors.Primary)
                                Text(
                                    text = "${factor.scale.toPercentageString()} (${factor.scale.toFormattedString()}x)",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = WeMadeColors.OnSurface
                                )
                                Text(
                                    text = "Order: ${factor.orderedQuantity} pcs",
                                    fontSize = 11.sp,
                                    color = WeMadeColors.OnSurfaceMuted
                                )
                            }

                            ClayButton(
                                text = "Hapus",
                                onClick = {
                                    factorsList = factorsList.toMutableList().apply { removeAt(index) }
                                },
                                style = ClayButtonStyle.Danger
                            )
                        }
                    }
                }

                // Add new size row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ClayTextField(
                        value = newLabel,
                        onValueChange = { newLabel = it },
                        placeholder = "Ukuran (XXL)",
                        modifier = Modifier.weight(1f)
                    )
                    ClayTextField(
                        value = newScaleText,
                        onValueChange = { newScaleText = it },
                        placeholder = "Skala (1.3)",
                        modifier = Modifier.weight(1f)
                    )
                    ClayTextField(
                        value = newQtyText,
                        onValueChange = { newQtyText = it },
                        placeholder = "Qty (10)",
                        modifier = Modifier.weight(1f)
                    )
                    ClayButton(
                        text = "+ Tambah",
                        onClick = {
                            val scaleDbl = newScaleText.toDoubleOrNull() ?: 1.0
                            val qty = newQtyText.toLongOrNull() ?: 0L
                            if (newLabel.isNotBlank()) {
                                factorsList = factorsList + SizeYieldFactor(
                                    sizeLabel = newLabel.trim().uppercase(),
                                    scale = Ratio.percent(scaleDbl * 100.0),
                                    orderedQuantity = qty
                                )
                                newLabel = ""
                                newScaleText = "1.0"
                                newQtyText = "0"
                            }
                        },
                        style = ClayButtonStyle.Secondary
                    )
                }

                // Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ClayButton(
                        text = "Batal",
                        onClick = onDismiss,
                        style = ClayButtonStyle.Secondary
                    )
                    Spacer(modifier = Modifier.width(ClaySpacing.Sm))
                    ClayButton(
                        text = if (isSubmitting) "Menyimpan..." else "Simpan Skala Ukuran",
                        onClick = { onSave(factorsList) },
                        style = ClayButtonStyle.Primary,
                        enabled = !isSubmitting
                    )
                }
            }
        }
    }
}
