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
import com.eventverse.app.domain.common.Quantity
import com.eventverse.app.domain.common.UnitOfMeasure
import com.eventverse.app.domain.masterdata.MaterialCategory
import com.eventverse.app.domain.masterdata.MaterialItem
import com.eventverse.app.domain.masterdata.UomConversion
import com.eventverse.app.domain.pipeline.StockOwnershipSemantics
import com.eventverse.app.presentation.designsystem.*
import com.eventverse.app.presentation.theme.WeMadeColors

@Composable
fun MaterialEditorDialog(
    initialMaterial: MaterialItem? = null,
    isSubmitting: Boolean,
    onDismiss: () -> Unit,
    onSave: (
        name: String,
        code: String?,
        category: MaterialCategory,
        baseUom: UnitOfMeasure,
        alternateUoms: List<UomConversion>,
        ownership: StockOwnershipSemantics,
        description: String
    ) -> Unit
) {
    var name by remember { mutableStateOf(initialMaterial?.name ?: "") }
    var code by remember { mutableStateOf(initialMaterial?.code?.value ?: "") }
    var category by remember { mutableStateOf(initialMaterial?.category ?: MaterialCategory.YARN) }
    var baseUom by remember { mutableStateOf(initialMaterial?.baseUom ?: category.defaultUom) }
    var ownership by remember { mutableStateOf(initialMaterial?.defaultOwnership ?: StockOwnershipSemantics.OWNED_RAW_MATERIAL) }
    var description by remember { mutableStateOf(initialMaterial?.description ?: "") }

    // Alternate UoM state
    var alternateUoms by remember { mutableStateOf(initialMaterial?.alternateUoms ?: emptyList()) }
    var newConvQtyStr by remember { mutableStateOf("1.2") }

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
                    text = if (initialMaterial == null) "Tambah Bahan Baku Baru" else "Ubah Bahan Baku",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = WeMadeColors.OnSurface
                )

                ClayTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = "Nama Material *",
                    placeholder = "Misal: Benang Cotton Combed 30s Reaktif"
                )

                if (initialMaterial == null) {
                    ClayTextField(
                        value = code,
                        onValueChange = { code = it },
                        label = "Kode Material (Opsional)",
                        placeholder = "Kosongkan untuk nomor urut otomatis (${category.codePrefix}-0001)"
                    )
                }

                // Category selection
                Column {
                    Text(
                        text = "Kategori Bahan *",
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
                        MaterialCategory.entries.forEach { cat ->
                            val isSel = cat == category
                            Box(
                                modifier = Modifier
                                    .clayFlat(
                                        shape = ClayShapes.Pill,
                                        background = if (isSel) WeMadeColors.Primary else WeMadeColors.Surface,
                                        outline = if (isSel) WeMadeColors.Outline else WeMadeColors.Border,
                                        borderWidth = ClayBorder.Medium
                                    )
                                    .clickable {
                                        category = cat
                                        if (initialMaterial == null) {
                                            baseUom = cat.defaultUom
                                        }
                                    }
                                    .padding(horizontal = 10.dp, vertical = 5.dp)
                            ) {
                                Text(
                                    text = cat.displayName,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSel) WeMadeColors.Surface else WeMadeColors.OnSurface
                                )
                            }
                        }
                    }
                }

                // Base UoM selection
                Column {
                    Text(
                        text = "Satuan Dasar (Base UoM) *",
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
                        listOf(
                            UnitOfMeasure.KILOGRAM,
                            UnitOfMeasure.GRAM,
                            UnitOfMeasure.PIECE,
                            UnitOfMeasure.METER,
                            UnitOfMeasure.YARD
                        ).forEach { uom ->
                            val isSel = uom == baseUom
                            Box(
                                modifier = Modifier
                                    .clayFlat(
                                        shape = ClayShapes.Pill,
                                        background = if (isSel) WeMadeColors.Primary else WeMadeColors.Surface,
                                        outline = if (isSel) WeMadeColors.Outline else WeMadeColors.Border,
                                        borderWidth = ClayBorder.Medium
                                    )
                                    .clickable { baseUom = uom }
                                    .padding(horizontal = 10.dp, vertical = 5.dp)
                            ) {
                                Text(
                                    text = "${uom.displayName} (${uom.code})",
                                    fontSize = 12.sp,
                                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSel) WeMadeColors.Surface else WeMadeColors.OnSurface
                                )
                            }
                        }
                    }
                }

                // Ownership selection
                Column {
                    Text(
                        text = "Kepemilikan Bahan *",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = WeMadeColors.OnSurface
                    )
                    Spacer(modifier = Modifier.height(ClaySpacing.Xs))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)
                    ) {
                        val isOwned = ownership == StockOwnershipSemantics.OWNED_RAW_MATERIAL
                        Box(
                            modifier = Modifier
                                .clayFlat(
                                    shape = ClayShapes.Button,
                                    background = if (isOwned) WeMadeColors.SuccessBg else WeMadeColors.Surface,
                                    outline = if (isOwned) WeMadeColors.Success else WeMadeColors.Border,
                                    borderWidth = ClayBorder.Medium
                                )
                                .clickable { ownership = StockOwnershipSemantics.OWNED_RAW_MATERIAL }
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = "Milik Pabrik",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = WeMadeColors.OnSurface
                            )
                        }

                        val isConsigned = ownership == StockOwnershipSemantics.CONSIGNED_CLIENT_MATERIAL
                        Box(
                            modifier = Modifier
                                .clayFlat(
                                    shape = ClayShapes.Button,
                                    background = if (isConsigned) WeMadeColors.WarningBg else WeMadeColors.Surface,
                                    outline = if (isConsigned) WeMadeColors.Warning else WeMadeColors.Border,
                                    borderWidth = ClayBorder.Medium
                                )
                                .clickable { ownership = StockOwnershipSemantics.CONSIGNED_CLIENT_MATERIAL }
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = "Konsinyasi Klien (Rp 0 HPP)",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = WeMadeColors.OnSurface
                            )
                        }
                    }
                }

                // Alternate UoM Conversion Section
                Column {
                    Text(
                        text = "Konversi Satuan Kemasan (Opsional)",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = WeMadeColors.OnSurface
                    )
                    Text(
                        text = "Misal: 1 Cone = 1.2 kg",
                        fontSize = 11.sp,
                        color = WeMadeColors.OnSurfaceMuted
                    )
                    Spacer(modifier = Modifier.height(ClaySpacing.Xs))

                    // Existing list
                    alternateUoms.forEachIndexed { index, conv ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "1 ${conv.from.displayName} = ${conv.equivalent.formatted()}",
                                fontSize = 12.sp,
                                color = WeMadeColors.OnSurface
                            )
                            ClayButton(
                                text = "Hapus",
                                onClick = {
                                    alternateUoms = alternateUoms.filterIndexed { i, _ -> i != index }
                                },
                                style = ClayButtonStyle.Danger,
                                fontSize = 11.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                    }

                    // Add new conversion row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Xs)
                    ) {
                        ClayTextField(
                            value = newConvQtyStr,
                            onValueChange = { newConvQtyStr = it },
                            placeholder = "1.2",
                            modifier = Modifier.weight(1f)
                        )
                        Text(text = baseUom.code, fontSize = 12.sp, color = WeMadeColors.OnSurface)
                        ClayButton(
                            text = "+ Tambah Cone",
                            onClick = {
                                val d = newConvQtyStr.toDoubleOrNull()
                                if (d != null && d > 0.0) {
                                    val qty = Quantity.of(d, baseUom)
                                    val exists = alternateUoms.any { it.from == UnitOfMeasure.CONE }
                                    if (!exists) {
                                        alternateUoms = alternateUoms + UomConversion(UnitOfMeasure.CONE, qty)
                                    }
                                }
                            },
                            style = ClayButtonStyle.Secondary,
                            fontSize = 11.sp
                        )
                    }
                }

                ClayTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = "Deskripsi / Catatan Bahan",
                    placeholder = "Karakteristik serat, sertifikasi, dll."
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
                        text = if (isSubmitting) "Menyimpan…" else "Simpan Bahan",
                        onClick = {
                            if (name.isNotBlank()) {
                                onSave(
                                    name,
                                    code.takeIf { it.isNotBlank() },
                                    category,
                                    baseUom,
                                    alternateUoms,
                                    ownership,
                                    description
                                )
                            }
                        },
                        style = ClayButtonStyle.Primary,
                        enabled = !isSubmitting && name.isNotBlank()
                    )
                }
            }
        }
    }
}
