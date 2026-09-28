package com.eventverse.app.presentation.sampling.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eventverse.app.domain.masterdata.MaterialCategory
import com.eventverse.app.domain.masterdata.MaterialItem
import com.eventverse.app.presentation.designsystem.*
import com.eventverse.app.presentation.theme.WeMadeColors

/**
 * Section input untuk bahan baku tambahan non-perbagian (mis. zipper, kancing, label, benang jahit).
 * Menggunakan [MaterialSearchableDropdown] untuk memilih dari Master Data Bahan Baku.
 */
@Composable
fun AdditionalMaterialsSection(
    items: List<AdditionalMaterialItem>,
    availableMaterials: List<MaterialItem>,
    onItemsChange: (List<AdditionalMaterialItem>) -> Unit,
    modifier: Modifier = Modifier
) {
    ClayCard(modifier = modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(ClaySpacing.Md)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(ClaySpacing.Xs)
                ) {
                    Text(
                        text = "TAMBAHAN BAHAN BAKU (NON-PERBAGIAN)",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = WeMadeColors.OnSurface
                    )
                    Text(
                        text = "Bahan pelengkap & aksesoris yang tidak terikat per bagian (mis. zipper, kancing, label, benang jahit).",
                        fontSize = 10.sp,
                        color = WeMadeColors.OnSurfaceMuted
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)
                ) {
                    if (items.isNotEmpty()) {
                        val filledCount = items.count { it.isFilled }
                        ClayBadge(
                            text = "$filledCount Item",
                            tint = if (filledCount > 0) WeMadeColors.Success else WeMadeColors.Warning
                        )
                    }

                    ClayButton(
                        text = "+ Tambah Bahan",
                        style = ClayButtonStyle.Ghost,
                        fontSize = 11.sp,
                        contentPadding = PaddingValues(horizontal = ClaySpacing.Md, vertical = ClaySpacing.Xs),
                        onClick = {
                            val newItem = AdditionalMaterialItem(id = "add-${items.size}-${ClockTimePlaceholder()}")
                            onItemsChange(items + newItem)
                        }
                    )
                }
            }

            // Konten Baris-baris Bahan Baku
            if (items.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clayFlat(
                            shape = ClayShapes.Card,
                            background = WeMadeColors.SurfaceMuted,
                            outline = WeMadeColors.OutlineSoft,
                            borderWidth = ClayBorder.Hairline
                        )
                        .padding(ClaySpacing.Md),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(ClaySpacing.Xs)
                ) {
                    Text(
                        text = "Belum Ada Bahan Tambahan",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = WeMadeColors.OnSurface
                    )
                    Text(
                        text = "Klik \"+ Tambah Bahan\" jika garmen membutuhkan aksesoris seperti zipper, kancing, label, atau tali.",
                        fontSize = 11.sp,
                        color = WeMadeColors.OnSurfaceMuted
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)) {
                    items.forEachIndexed { index, item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clayFlat(
                                    shape = ClayShapes.Tile,
                                    background = WeMadeColors.Surface,
                                    outline = WeMadeColors.OutlineSoft,
                                    borderWidth = ClayBorder.Hairline
                                )
                                .padding(ClaySpacing.Sm),
                            horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Kolom 1: Master Bahan Baku (Searchable Dropdown)
                            MaterialSearchableDropdown(
                                value = item.materialName,
                                onValueChange = { newName ->
                                    val updated = items.toMutableList().apply {
                                        this[index] = item.copy(materialName = newName)
                                    }
                                    onItemsChange(updated)
                                },
                                availableMaterials = availableMaterials,
                                placeholder = "Pilih / cari bahan (mis. Zipper, Kancing)...",
                                categoryPriority = listOf(
                                    MaterialCategory.TRIM,
                                    MaterialCategory.ACCESSORY,
                                    MaterialCategory.YARN,
                                    MaterialCategory.FABRIC
                                ),
                                modifier = Modifier.weight(1.5f)
                            )

                            // Kolom 2: Jumlah / Kebutuhan
                            ClayTextField(
                                value = item.quantity,
                                onValueChange = { newQty ->
                                    val updated = items.toMutableList().apply {
                                        this[index] = item.copy(quantity = newQty)
                                    }
                                    onItemsChange(updated)
                                },
                                placeholder = "Jumlah (mis. 1 PCS)",
                                modifier = Modifier.weight(0.9f)
                            )

                            // Kolom 3: Catatan / Spesifikasi
                            ClayTextField(
                                value = item.notes,
                                onValueChange = { newNotes ->
                                    val updated = items.toMutableList().apply {
                                        this[index] = item.copy(notes = newNotes)
                                    }
                                    onItemsChange(updated)
                                },
                                placeholder = "Catatan (mis. Gigi besi hitam 50cm)",
                                modifier = Modifier.weight(1.2f)
                            )

                            // Tombol Hapus
                            IconButton(
                                onClick = {
                                    val updated = items.toMutableList().apply { removeAt(index) }
                                    onItemsChange(updated)
                                }
                            ) {
                                IconTrash(
                                    modifier = Modifier.size(16.dp),
                                    color = WeMadeColors.Error
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun ClockTimePlaceholder(): Long =
    kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
