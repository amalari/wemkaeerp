package com.eventverse.app.presentation.masterdata.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eventverse.app.domain.masterdata.MaterialCategory
import com.eventverse.app.domain.masterdata.MaterialId
import com.eventverse.app.domain.masterdata.MaterialItem
import com.eventverse.app.domain.pipeline.StockOwnershipSemantics
import com.eventverse.app.presentation.designsystem.*
import com.eventverse.app.presentation.theme.WeMadeColors

@Composable
fun MaterialCatalogList(
    materials: List<MaterialItem>,
    selectedMaterialId: MaterialId?,
    searchQuery: String,
    selectedCategory: MaterialCategory?,
    canManage: Boolean,
    onSelectMaterial: (MaterialId) -> Unit,
    onSearchChange: (String) -> Unit,
    onCategorySelect: (MaterialCategory?) -> Unit,
    onOpenCreate: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        // Toolbar: Search + Add Button
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)
        ) {
            ClayTextField(
                value = searchQuery,
                onValueChange = onSearchChange,
                placeholder = "Cari kode, nama, atau deskripsi…",
                leadingIcon = { IconSearch(color = WeMadeColors.OnSurfaceMuted) },
                modifier = Modifier.weight(1f)
            )

            if (canManage) {
                ClayButton(
                    text = "+ Bahan",
                    onClick = onOpenCreate,
                    style = ClayButtonStyle.Primary
                )
            }
        }

        Spacer(modifier = Modifier.height(ClaySpacing.Sm))

        // Category Filter Chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Xs)
        ) {
            CategoryChip(
                label = "Semua",
                isSelected = selectedCategory == null,
                onClick = { onCategorySelect(null) }
            )

            MaterialCategory.entries.forEach { cat ->
                CategoryChip(
                    label = cat.displayName,
                    isSelected = selectedCategory == cat,
                    onClick = { onCategorySelect(cat) }
                )
            }
        }

        Spacer(modifier = Modifier.height(ClaySpacing.Md))

        // Catalog List
        if (materials.isEmpty()) {
            ClayCard(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(ClaySpacing.Lg),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Tidak Ada Bahan Ditemukan",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = WeMadeColors.OnSurface
                    )
                    Spacer(modifier = Modifier.height(ClaySpacing.Xs))
                    Text(
                        text = "Coba ubah kata kunci pencarian atau filter kategori di atas.",
                        fontSize = 13.sp,
                        color = WeMadeColors.OnSurfaceMuted
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)
            ) {
                items(materials, key = { it.id.value }) { item ->
                    val isSelected = item.id == selectedMaterialId

                    ClayCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectMaterial(item.id) },
                        borderWidth = if (isSelected) ClayBorder.Thick else ClayBorder.Medium,
                        outlineColor = if (isSelected) WeMadeColors.Primary else WeMadeColors.Outline
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(ClaySpacing.Md)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Xs),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    ClayBadge(
                                        text = item.code.value,
                                        tint = WeMadeColors.Primary
                                    )
                                    ClayBadge(
                                        text = item.category.displayName,
                                        tint = WeMadeColors.OnSurfaceMuted
                                    )
                                }

                                if (item.defaultOwnership == StockOwnershipSemantics.CONSIGNED_CLIENT_MATERIAL) {
                                    ClayBadge(
                                        text = "Konsinyasi",
                                        tint = WeMadeColors.Warning
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(ClaySpacing.Sm))

                            Text(
                                text = item.name,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = WeMadeColors.OnSurface
                            )

                            if (item.description.isNotBlank()) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = item.description,
                                    fontSize = 12.sp,
                                    color = WeMadeColors.OnSurfaceMuted,
                                    maxLines = 1
                                )
                            }

                            Spacer(modifier = Modifier.height(ClaySpacing.Sm))

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Satuan: ${item.baseUom.displayName} (${item.baseUom.code})",
                                    fontSize = 12.sp,
                                    color = WeMadeColors.OnSurfaceMuted
                                )

                                if (item.alternateUoms.isNotEmpty()) {
                                    val firstConv = item.alternateUoms.first()
                                    ClayBadge(
                                        text = "1 ${firstConv.from.code} = ${firstConv.equivalent.formatted()}",
                                        tint = WeMadeColors.Success
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clayFlat(
                shape = ClayShapes.Pill,
                background = if (isSelected) WeMadeColors.Primary else WeMadeColors.Surface,
                outline = if (isSelected) WeMadeColors.Outline else WeMadeColors.Border,
                borderWidth = ClayBorder.Medium
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) WeMadeColors.Surface else WeMadeColors.OnSurface
        )
    }
}
