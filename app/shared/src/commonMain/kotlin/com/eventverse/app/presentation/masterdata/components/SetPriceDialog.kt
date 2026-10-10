package com.eventverse.app.presentation.masterdata.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import com.eventverse.app.domain.common.Money
import com.eventverse.app.domain.common.Quantity
import com.eventverse.app.domain.common.UnitOfMeasure
import com.eventverse.app.domain.common.UnitPrice
import com.eventverse.app.domain.masterdata.MaterialItem
import com.eventverse.app.presentation.designsystem.*
import com.eventverse.app.presentation.theme.WeMadeColors
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

@Composable
fun SetPriceDialog(
    material: MaterialItem,
    isSubmitting: Boolean,
    onDismiss: () -> Unit,
    onSave: (unitPrice: UnitPrice, effectiveFrom: Instant, note: String) -> Unit
) {
    var amountStr by remember { mutableStateOf("120000") }
    var perQtyStr by remember { mutableStateOf("1") }
    var selectedUom by remember { mutableStateOf(material.baseUom) }
    var note by remember { mutableStateOf("") }

    val availableUoms = remember(material) {
        listOf(material.baseUom) + material.alternateUoms.map { it.from }
    }

    Dialog(onDismissRequest = onDismiss) {
        ClayCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(ClaySpacing.Md)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(ClaySpacing.Xl),
                verticalArrangement = Arrangement.spacedBy(ClaySpacing.Md)
            ) {
                Text(
                    text = "Tetapkan Tarif Acuan Standar",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = WeMadeColors.OnSurface
                )

                Text(
                    text = "Bahan: ${material.name} (${material.code.value})",
                    fontSize = 13.sp,
                    color = WeMadeColors.OnSurfaceMuted
                )

                ClayTextField(
                    value = amountStr,
                    onValueChange = { amountStr = it.filter { c -> c.isDigit() } },
                    label = "Nominal Tarif (Rupiah / IDR) *",
                    placeholder = "120000"
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ClayTextField(
                        value = perQtyStr,
                        onValueChange = { perQtyStr = it },
                        label = "Per Jumlah *",
                        placeholder = "1",
                        modifier = Modifier.weight(1f)
                    )

                    Column(modifier = Modifier.weight(1.5f)) {
                        Text(
                            text = "Satuan Tarif *",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = WeMadeColors.OnSurface
                        )
                        Spacer(modifier = Modifier.height(ClaySpacing.Xs))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Xs)
                        ) {
                            availableUoms.forEach { uom ->
                                val isSel = uom == selectedUom
                                Box(
                                    modifier = Modifier
                                        .clayFlat(
                                            shape = ClayShapes.Pill,
                                            background = if (isSel) WeMadeColors.Primary else WeMadeColors.Surface,
                                            outline = if (isSel) WeMadeColors.Outline else WeMadeColors.Border,
                                            borderWidth = ClayBorder.Medium
                                        )
                                        .clickable { selectedUom = uom }
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = uom.displayName,
                                        fontSize = 12.sp,
                                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSel) WeMadeColors.Surface else WeMadeColors.OnSurface
                                    )
                                }
                            }
                        }
                    }
                }

                ClayTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = "Keterangan / Catatan Kontrak",
                    placeholder = "Misal: Kontrak Q1 2026 PT Surya Benang"
                )

                Spacer(modifier = Modifier.height(ClaySpacing.Sm))

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ClayButton(
                        text = "Batal",
                        onClick = onDismiss,
                        style = ClayButtonStyle.Ghost
                    )
                    Spacer(modifier = Modifier.width(ClaySpacing.Sm))
                    ClayButton(
                        text = if (isSubmitting) "Menyimpan..." else "Simpan Tarif",
                        onClick = {
                            val amount = amountStr.toLongOrNull() ?: 0L
                            val perD = perQtyStr.toDoubleOrNull() ?: 1.0
                            if (amount > 0L && perD > 0.0) {
                                val money = Money.idr(amount)
                                val qty = Quantity.of(perD, selectedUom)
                                val unitPrice = UnitPrice(money, qty)
                                onSave(unitPrice, Clock.System.now(), note)
                            }
                        },
                        style = ClayButtonStyle.Primary,
                        enabled = !isSubmitting && (amountStr.toLongOrNull() ?: 0L) > 0L
                    )
                }
            }
        }
    }
}
