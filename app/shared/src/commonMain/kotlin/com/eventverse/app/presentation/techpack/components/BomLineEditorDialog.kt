package com.eventverse.app.presentation.techpack.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.eventverse.app.domain.common.Ratio
import com.eventverse.app.domain.common.UnitOfMeasure
import com.eventverse.app.domain.contracts.BomLine
import com.eventverse.app.domain.contracts.MaterialRef
import com.eventverse.app.domain.masterdata.MaterialCategory
import com.eventverse.app.domain.masterdata.MaterialItem
import com.eventverse.app.domain.pipeline.StockOwnershipSemantics
import com.eventverse.app.presentation.designsystem.*
import com.eventverse.app.presentation.techpack.toPercentageString
import com.eventverse.app.presentation.theme.WeMadeColors

@Composable
fun BomLineEditorDialog(
    initialLine: BomLine?,
    availableMaterials: List<MaterialItem>,
    isSubmitting: Boolean,
    onDismiss: () -> Unit,
    onSave: (BomLine) -> Unit
) {
    var materialSearch by remember { mutableStateOf(initialLine?.material?.displayLabel ?: "") }
    var selectedMaterialItem by remember {
        mutableStateOf(
            initialLine?.material?.materialId?.let { id -> availableMaterials.firstOrNull { it.id == id } }
        )
    }

    var category by remember { mutableStateOf(initialLine?.category ?: MaterialCategory.YARN) }
    var netQtyText by remember {
        val qty = initialLine?.netQuantityPerGarment?.toDouble()
        mutableStateOf(qty?.toString() ?: "1.0")
    }
    var selectedUom by remember { mutableStateOf(initialLine?.netQuantityPerGarment?.uom ?: UnitOfMeasure.KILOGRAM) }
    var wastePercentText by remember {
        val pct = initialLine?.wasteAllowance?.toPercentageString()?.removeSuffix("%")
        mutableStateOf(pct ?: "5.0")
    }
    var ownership by remember { mutableStateOf(initialLine?.ownership ?: StockOwnershipSemantics.OWNED_RAW_MATERIAL) }
    var notes by remember { mutableStateOf(initialLine?.notes ?: "") }
    var showAutocomplete by remember { mutableStateOf(false) }

    val matchingMaterials = remember(materialSearch, availableMaterials) {
        if (materialSearch.isBlank()) emptyList()
        else availableMaterials.filter {
            it.name.contains(materialSearch, ignoreCase = true) ||
            it.code.value.contains(materialSearch, ignoreCase = true)
        }.take(5)
    }

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
                    text = if (initialLine == null) "Tambah Baris Bahan (BOM)" else "Edit Baris Bahan",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = WeMadeColors.OnSurface
                )

                // Material Input with autocomplete
                Column(verticalArrangement = Arrangement.spacedBy(ClaySpacing.Xs)) {
                    Text(
                        text = "Nama Bahan Baku (Master Data / Free-Text):",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = WeMadeColors.OnSurface
                    )
                    ClayTextField(
                        value = materialSearch,
                        onValueChange = {
                            materialSearch = it
                            selectedMaterialItem = null
                            showAutocomplete = true
                        },
                        placeholder = "Ketik nama bahan (contoh: Cotton 30s Navy)...",
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (selectedMaterialItem != null) {
                        ClayBadge(
                            text = "Terpetakan ke Master Data: ${selectedMaterialItem!!.code.value} (${selectedMaterialItem!!.name})",
                            tint = WeMadeColors.Success
                        )
                    } else if (showAutocomplete && matchingMaterials.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .claySurface(
                                    shape = ClayShapes.Tile,
                                    background = WeMadeColors.Surface,
                                    outline = WeMadeColors.Primary,
                                    offset = ClayOffset.Small,
                                    borderWidth = ClayBorder.Hairline
                                )
                                .padding(ClaySpacing.Sm)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(ClaySpacing.Xs)) {
                                Text(
                                    text = "Saran dari Master Data:",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = WeMadeColors.Primary
                                )
                                matchingMaterials.forEach { item ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                selectedMaterialItem = item
                                                materialSearch = item.name
                                                category = item.category
                                                selectedUom = item.baseUom
                                                ownership = item.defaultOwnership
                                                showAutocomplete = false
                                            }
                                            .padding(vertical = ClaySpacing.Xs),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "${item.code.value} — ${item.name}",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = WeMadeColors.OnSurface
                                        )
                                        ClayBadge(text = item.category.displayName, tint = WeMadeColors.Secondary)
                                    }
                                }
                            }
                        }
                    }
                }

                // Category Selector
                Column(verticalArrangement = Arrangement.spacedBy(ClaySpacing.Xs)) {
                    Text(
                        text = "Kategori Material:",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = WeMadeColors.OnSurface
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Xs)
                    ) {
                        MaterialCategory.entries.take(4).forEach { cat ->
                            val isSelected = category == cat
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { category = cat }
                                    .claySurface(
                                        shape = ClayShapes.Pill,
                                        background = if (isSelected) WeMadeColors.Primary else WeMadeColors.SurfaceMuted,
                                        outline = if (isSelected) WeMadeColors.Outline else WeMadeColors.OutlineSoft,
                                        offset = if (isSelected) ClayOffset.Small else ClayOffset.Flat,
                                        borderWidth = ClayBorder.Hairline
                                    )
                                    .padding(vertical = ClaySpacing.Xs),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = cat.displayName.substringBefore(" /"),
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) androidx.compose.ui.graphics.Color.White else WeMadeColors.OnSurface
                                )
                            }
                        }
                    }
                }

                // Net Quantity & UoM row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Md)
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(ClaySpacing.Xs)
                    ) {
                        Text(
                            text = "Kuantitas Net per Pcs:",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = WeMadeColors.OnSurface
                        )
                        ClayTextField(
                            value = netQtyText,
                            onValueChange = { netQtyText = it },
                            placeholder = "0.25",
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(ClaySpacing.Xs)
                    ) {
                        Text(
                            text = "Satuan (UoM):",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = WeMadeColors.OnSurface
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Xs)) {
                            listOf(UnitOfMeasure.KILOGRAM, UnitOfMeasure.GRAM, UnitOfMeasure.PIECE, UnitOfMeasure.METER).forEach { uom ->
                                val isSelected = selectedUom == uom
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { selectedUom = uom }
                                        .claySurface(
                                            shape = ClayShapes.Pill,
                                            background = if (isSelected) WeMadeColors.Teal else WeMadeColors.SurfaceMuted,
                                            outline = if (isSelected) WeMadeColors.Outline else WeMadeColors.OutlineSoft,
                                            offset = if (isSelected) ClayOffset.Small else ClayOffset.Flat,
                                            borderWidth = ClayBorder.Hairline
                                        )
                                        .padding(vertical = ClaySpacing.Xs),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = uom.code,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) androidx.compose.ui.graphics.Color.White else WeMadeColors.OnSurface
                                    )
                                }
                            }
                        }
                    }
                }

                // Waste Allowance %
                Column(verticalArrangement = Arrangement.spacedBy(ClaySpacing.Xs)) {
                    Text(
                        text = "Waste Allowance (% Toleransi Rusak/Susut):",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = WeMadeColors.OnSurface
                    )
                    ClayTextField(
                        value = wastePercentText,
                        onValueChange = { wastePercentText = it },
                        placeholder = "5.0",
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Ownership Semantics
                Column(verticalArrangement = Arrangement.spacedBy(ClaySpacing.Xs)) {
                    Text(
                        text = "Kepemilikan Stok Bahan:",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = WeMadeColors.OnSurface
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)
                    ) {
                        val owned = StockOwnershipSemantics.OWNED_RAW_MATERIAL
                        val isOwned = ownership == owned
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { ownership = owned }
                                .claySurface(
                                    shape = ClayShapes.Pill,
                                    background = if (isOwned) WeMadeColors.Teal else WeMadeColors.SurfaceMuted,
                                    outline = if (isOwned) WeMadeColors.Outline else WeMadeColors.OutlineSoft,
                                    offset = if (isOwned) ClayOffset.Small else ClayOffset.Flat,
                                    borderWidth = ClayBorder.Hairline
                                )
                                .padding(vertical = ClaySpacing.Sm),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Aset Milik Pabrik",
                                fontSize = 11.sp,
                                fontWeight = if (isOwned) FontWeight.Bold else FontWeight.Normal,
                                color = if (isOwned) androidx.compose.ui.graphics.Color.White else WeMadeColors.OnSurface
                            )
                        }

                        val consigned = StockOwnershipSemantics.CONSIGNED_CLIENT_MATERIAL
                        val isConsigned = ownership == consigned
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { ownership = consigned }
                                .claySurface(
                                    shape = ClayShapes.Pill,
                                    background = if (isConsigned) WeMadeColors.Warning else WeMadeColors.SurfaceMuted,
                                    outline = if (isConsigned) WeMadeColors.Outline else WeMadeColors.OutlineSoft,
                                    offset = if (isConsigned) ClayOffset.Small else ClayOffset.Flat,
                                    borderWidth = ClayBorder.Hairline
                                )
                                .padding(vertical = ClaySpacing.Sm),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Kain Titipan Klien (Rp 0)",
                                fontSize = 11.sp,
                                fontWeight = if (isConsigned) FontWeight.Bold else FontWeight.Normal,
                                color = if (isConsigned) androidx.compose.ui.graphics.Color.White else WeMadeColors.OnSurface
                            )
                        }
                    }
                }

                // Notes
                Column(verticalArrangement = Arrangement.spacedBy(ClaySpacing.Xs)) {
                    Text(
                        text = "Catatan Penggunaan / Penempatan:",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = WeMadeColors.OnSurface
                    )
                    ClayTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        placeholder = "Contoh: Bagian badan depan, rib leher, kancing cadangan...",
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Actions: Cancel & Save
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
                        text = if (isSubmitting) "Menyimpan..." else "Simpan Baris BOM",
                        onClick = {
                            val lineId = initialLine?.lineId ?: "line-${kotlinx.datetime.Clock.System.now().toEpochMilliseconds()}"
                            val matRef = if (selectedMaterialItem != null) {
                                MaterialRef.resolved(
                                    selectedMaterialItem!!.id,
                                    selectedMaterialItem!!.code,
                                    selectedMaterialItem!!.name
                                )
                            } else {
                                MaterialRef.unresolved(materialSearch.trim())
                            }

                            val qtyDbl = netQtyText.toDoubleOrNull() ?: 1.0
                            val netQty = Quantity.of(qtyDbl, selectedUom)
                            val wastePct = wastePercentText.toDoubleOrNull() ?: 0.0
                            val waste = Ratio.percent(wastePct)

                            val line = BomLine(
                                lineId = lineId,
                                material = matRef,
                                category = category,
                                netQuantityPerGarment = netQty,
                                wasteAllowance = waste,
                                ownership = ownership,
                                notes = notes.trim()
                            )
                            onSave(line)
                        },
                        style = ClayButtonStyle.Primary,
                        enabled = materialSearch.isNotBlank() && !isSubmitting
                    )
                }
            }
        }
    }
}
