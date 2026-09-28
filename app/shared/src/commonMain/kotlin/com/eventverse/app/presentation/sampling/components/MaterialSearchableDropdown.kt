package com.eventverse.app.presentation.sampling.components

import androidx.compose.foundation.clickable
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
import com.eventverse.app.domain.masterdata.MaterialCategory
import com.eventverse.app.domain.masterdata.MaterialItem
import com.eventverse.app.presentation.designsystem.*
import com.eventverse.app.presentation.theme.WeMadeColors

/**
 * Searchable dropdown untuk memilih Bahan Baku dari Master Data ([MaterialItem]).
 *
 * Mendukung pencarian instan (kode, nama, kategori), opsi free-text/kustom bila belum ada di master data,
 * dan tombol hapus cepat. Menolak Unicode emoji untuk mencegah tofu di Compose Wasm.
 */
@Composable
fun MaterialSearchableDropdown(
    value: String,
    onValueChange: (String) -> Unit,
    availableMaterials: List<MaterialItem>,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String = "Cari / pilih bahan baku...",
    categoryPriority: List<MaterialCategory>? = null,
    enabled: Boolean = true
) {
    var isExpanded by remember { mutableStateOf(false) }
    var searchQuery by remember(value) { mutableStateOf(value) }

    // Filter dan urutkan material berdasarkan query dan prioritas kategori
    val filteredMaterials = remember(searchQuery, availableMaterials, categoryPriority) {
        val q = searchQuery.trim().lowercase()
        val baseList = if (q.isEmpty()) {
            availableMaterials
        } else {
            availableMaterials.filter { item ->
                item.name.lowercase().contains(q) ||
                    item.code.value.lowercase().contains(q) ||
                    item.category.displayName.lowercase().contains(q)
            }
        }

        if (categoryPriority != null && categoryPriority.isNotEmpty()) {
            baseList.sortedWith(
                compareBy<MaterialItem> { item ->
                    val idx = categoryPriority.indexOf(item.category)
                    if (idx >= 0) idx else Int.MAX_VALUE
                }.thenBy { it.name }
            ).take(15)
        } else {
            baseList.take(15)
        }
    }

    Column(modifier = modifier) {
        ClayTextField(
            value = searchQuery,
            onValueChange = { input ->
                searchQuery = input
                onValueChange(input)
                isExpanded = true
            },
            label = label,
            placeholder = placeholder,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
            leadingIcon = {
                IconSearch(modifier = Modifier.size(14.dp), color = WeMadeColors.OnSurfaceMuted)
            },
            trailingIcon = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Xs)
                ) {
                    if (searchQuery.isNotBlank() && enabled) {
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .clickable {
                                    searchQuery = ""
                                    onValueChange("")
                                    isExpanded = false
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            IconClose(
                                modifier = Modifier.size(12.dp),
                                color = WeMadeColors.OnSurfaceMuted
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clickable(enabled = enabled) {
                                isExpanded = !isExpanded
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (isExpanded) {
                            IconChevronUp(modifier = Modifier.size(14.dp), color = WeMadeColors.Primary)
                        } else {
                            IconChevronDown(modifier = Modifier.size(14.dp), color = WeMadeColors.OnSurfaceMuted)
                        }
                    }
                }
            }
        )

        // Dropdown Saran Autocomplete
        if (isExpanded && enabled) {
            Spacer(modifier = Modifier.height(ClaySpacing.Xs))
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
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 220.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(ClaySpacing.Xs)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (filteredMaterials.isNotEmpty()) "Pilih dari Master Data:" else "Tidak ada kecocokan di Master Data",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = WeMadeColors.Primary
                        )
                        Box(
                            modifier = Modifier
                                .clickable { isExpanded = false }
                                .padding(horizontal = ClaySpacing.Xs),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Tutup",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = WeMadeColors.OnSurfaceMuted
                            )
                        }
                    }

                    if (filteredMaterials.isEmpty()) {
                        Text(
                            text = "Ketik bebas untuk menggunakan nama kustom.",
                            fontSize = 11.sp,
                            color = WeMadeColors.OnSurfaceMuted,
                            modifier = Modifier.padding(vertical = ClaySpacing.Xs)
                        )
                    } else {
                        filteredMaterials.forEach { item ->
                            val itemLabel = "${item.code.value} — ${item.name}"
                            val isSelected = searchQuery.equals(itemLabel, ignoreCase = true) ||
                                searchQuery.equals(item.name, ignoreCase = true)

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .claySurface(
                                        shape = ClayShapes.Pill,
                                        background = if (isSelected) WeMadeColors.Primary.copy(alpha = 0.1f) else WeMadeColors.SurfaceMuted,
                                        outline = if (isSelected) WeMadeColors.Primary else WeMadeColors.OutlineSoft,
                                        offset = ClayOffset.Flat,
                                        borderWidth = ClayBorder.Hairline
                                    )
                                    .clickable {
                                        searchQuery = itemLabel
                                        onValueChange(itemLabel)
                                        isExpanded = false
                                    }
                                    .padding(horizontal = ClaySpacing.Md, vertical = ClaySpacing.Xs),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f, fill = false)) {
                                    Text(
                                        text = itemLabel,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) WeMadeColors.Primary else WeMadeColors.OnSurface
                                    )
                                    Text(
                                        text = "Satuan: ${item.baseUom.displayName}",
                                        fontSize = 9.sp,
                                        color = WeMadeColors.OnSurfaceMuted
                                    )
                                }

                                ClayBadge(
                                    text = item.category.displayName,
                                    tint = when (item.category) {
                                        MaterialCategory.YARN -> WeMadeColors.Primary
                                        MaterialCategory.FABRIC -> WeMadeColors.Secondary
                                        MaterialCategory.TRIM -> WeMadeColors.Warning
                                        MaterialCategory.ACCESSORY -> WeMadeColors.Accent
                                        else -> WeMadeColors.OnSurfaceMuted
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
