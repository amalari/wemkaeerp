package com.eventverse.app.presentation.sampling.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eventverse.app.domain.masterdata.MaterialCategory
import com.eventverse.app.domain.masterdata.MaterialItem
import com.eventverse.app.domain.sampling.StageInputRow
import com.eventverse.app.domain.sampling.StageInputSection
import com.eventverse.app.domain.sampling.StageSectionNames
import com.eventverse.app.presentation.designsystem.*
import com.eventverse.app.presentation.theme.WeMadeColors

/**
 * Section "Hasil R&D" di dialog Detail SPK — tampil saat SPK sudah turun ke lantai R&D
 * (rajut sampai kemas). Gramasi, waktu, bahan baku, dan ukuran jadi diisi di sini setelah panel selesai dirajut.
 *
 * Tab bagian garmen diturunkan langsung dari Program CAM (satu lembar teknis per SPK)
 * dengan sinkronisasi dua arah: penambahan atau penghapusan bagian di CAM langsung terefleksi
 * di R&D, dan sebaliknya.
 */
@Composable
fun RdResultSection(
    sections: List<StageInputSection>,
    onSectionsChange: (List<StageInputSection>) -> Unit,
    availableMaterials: List<MaterialItem> = emptyList()
) {
    val sheet = remember(sections) { parseCamSections(sections) }
    var tabs by remember(sections) { mutableStateOf(sheet.tabs) }
    var additionalMaterials by remember(sections) { mutableStateOf(sheet.additionalMaterials) }
    var selectedTabId by remember { mutableStateOf(tabs.firstOrNull()?.id.orEmpty()) }
    var selectedTabName by remember { mutableStateOf(tabs.firstOrNull()?.name.orEmpty()) }
    var showAddDialog by remember { mutableStateOf(false) }

    // Pertahankan seleksi tab saat tabs berubah dari luar (sinkronisasi dari Program CAM)
    val activeTab = tabs.firstOrNull { it.id == selectedTabId }
        ?: tabs.firstOrNull { it.name.equals(selectedTabName, ignoreCase = true) }
        ?: tabs.firstOrNull()

    LaunchedEffect(activeTab) {
        if (activeTab != null) {
            selectedTabId = activeTab.id
            selectedTabName = activeTab.name
        }
    }

    fun updateAndEmit(
        newTabs: List<CamPartTab> = tabs,
        newMeasurements: List<StageInputRow> = sheet.finishedMeasurements,
        newAdditionalMaterials: List<AdditionalMaterialItem> = additionalMaterials
    ) {
        tabs = newTabs
        additionalMaterials = newAdditionalMaterials
        onSectionsChange(serializeCamSections(newTabs, sheet.formulaNote, newMeasurements, newAdditionalMaterials))
    }

    Column(verticalArrangement = Arrangement.spacedBy(ClaySpacing.Md)) {
        // Section 1: Gramasi, Waktu & Bahan Baku per Bagian (Tabbing Sinkron dengan CAM)
        ClayCard(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(ClaySpacing.Md)) {
                // Header dengan Badge progres
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(ClaySpacing.Xs)) {
                        Text(
                            text = "HASIL R&D - GRAMASI, WAKTU & BAHAN BAKU PER BAGIAN",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = WeMadeColors.OnSurface
                        )
                        Text(
                            text = "Isi setelah panel selesai dirajut. Tab bagian garmen sinkron dengan Program CAM.",
                            fontSize = 10.sp,
                            color = WeMadeColors.OnSurfaceMuted
                        )
                    }

                    if (tabs.isNotEmpty()) {
                        val filledCount = tabs.count { it.gramasi.isNotBlank() && it.waktu.isNotBlank() }
                        ClayBadge(
                            text = "$filledCount/${tabs.size} Terisi",
                            tint = if (filledCount == tabs.size) WeMadeColors.Success else WeMadeColors.Warning
                        )
                    }
                }

                // Baris Tabs Bagian Dinamis (Sama dengan Program CAM)
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    tabs.forEach { tab ->
                        val isSelected = tab.id == activeTab?.id
                        val isFilled = tab.gramasi.isNotBlank() && tab.waktu.isNotBlank()

                        Row(
                            modifier = Modifier
                                .clickable {
                                    selectedTabId = tab.id
                                    selectedTabName = tab.name
                                }
                                .claySurface(
                                    shape = ClayShapes.Pill,
                                    background = when {
                                        isSelected -> WeMadeColors.Primary
                                        else -> WeMadeColors.SurfaceMuted
                                    },
                                    outline = when {
                                        isSelected -> WeMadeColors.Outline
                                        else -> WeMadeColors.OutlineSoft
                                    },
                                    offset = if (isSelected) ClayOffset.Small else ClayOffset.Flat,
                                    borderWidth = ClayBorder.Medium
                                )
                                .padding(horizontal = ClaySpacing.Md, vertical = ClaySpacing.Xs),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Xs)
                        ) {
                            if (isFilled) {
                                IconCheck(
                                    modifier = Modifier.size(12.dp),
                                    color = if (isSelected) WeMadeColors.Surface else WeMadeColors.Success
                                )
                            } else {
                                IconWarning(
                                    modifier = Modifier.size(12.dp),
                                    color = if (isSelected) WeMadeColors.Surface else WeMadeColors.Warning
                                )
                            }

                            Text(
                                text = tab.name,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) WeMadeColors.Surface else WeMadeColors.OnSurface
                            )

                            Text(
                                text = if (isFilled) "Terisi" else "Belum",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = when {
                                    isSelected -> WeMadeColors.Surface.copy(alpha = 0.85f)
                                    isFilled -> WeMadeColors.Success
                                    else -> WeMadeColors.Warning
                                }
                            )

                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .clickable {
                                        val newTabs = tabs.filter { it.id != tab.id }
                                        if (activeTab?.id == tab.id) {
                                            val remaining = newTabs.firstOrNull()
                                            selectedTabId = remaining?.id.orEmpty()
                                            selectedTabName = remaining?.name.orEmpty()
                                        }
                                        updateAndEmit(newTabs = newTabs)
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                IconClose(
                                    modifier = Modifier.size(10.dp),
                                    color = if (isSelected) WeMadeColors.Surface else WeMadeColors.OnSurfaceMuted
                                )
                            }
                        }
                    }

                    ClayButton(
                        text = "+ Bagian",
                        style = ClayButtonStyle.Ghost,
                        fontSize = 11.sp,
                        contentPadding = PaddingValues(horizontal = ClaySpacing.Md, vertical = ClaySpacing.Xs),
                        onClick = { showAddDialog = true }
                    )
                }

                // Konten Tab Aktif / Empty State
                if (activeTab != null) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clayFlat(
                                shape = ClayShapes.Card,
                                background = WeMadeColors.Surface,
                                outline = WeMadeColors.OutlineSoft,
                                borderWidth = ClayBorder.Hairline
                            )
                            .padding(ClaySpacing.Md),
                        verticalArrangement = Arrangement.spacedBy(ClaySpacing.Md)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "BAGIAN AKTIF: ${activeTab.name.uppercase()}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = WeMadeColors.Primary
                            )
                            if (activeTab.program.isNotBlank()) {
                                Text(
                                    text = "Kode CAM: ${activeTab.program}",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = WeMadeColors.OnSurfaceMuted
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Md),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            MaterialSearchableDropdown(
                                value = activeTab.material,
                                onValueChange = { newMaterial ->
                                    val newTabs = tabs.map {
                                        if (it.id == activeTab.id) it.copy(material = newMaterial) else it
                                    }
                                    updateAndEmit(newTabs = newTabs)
                                },
                                availableMaterials = availableMaterials,
                                label = "MASTER BAHAN BAKU (${activeTab.name.uppercase()})",
                                placeholder = "Cari benang / bahan...",
                                categoryPriority = listOf(MaterialCategory.YARN, MaterialCategory.FABRIC),
                                modifier = Modifier.weight(1.3f)
                            )
                            ClayTextField(
                                value = activeTab.gramasi,
                                onValueChange = { newGramasi ->
                                    val newTabs = tabs.map {
                                        if (it.id == activeTab.id) it.copy(gramasi = newGramasi) else it
                                    }
                                    updateAndEmit(newTabs = newTabs)
                                },
                                label = "GRAMASI PANEL (${activeTab.name.uppercase()})",
                                placeholder = "mis. 117 GR",
                                modifier = Modifier.weight(0.85f)
                            )
                            ClayTextField(
                                value = activeTab.waktu,
                                onValueChange = { newWaktu ->
                                    val newTabs = tabs.map {
                                        if (it.id == activeTab.id) it.copy(waktu = newWaktu) else it
                                    }
                                    updateAndEmit(newTabs = newTabs)
                                },
                                label = "WAKTU RAJUT (${activeTab.name.uppercase()})",
                                placeholder = "mis. 37 MENIT",
                                modifier = Modifier.weight(0.85f)
                            )
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clayFlat(
                                shape = ClayShapes.Card,
                                background = WeMadeColors.SurfaceMuted,
                                outline = WeMadeColors.OutlineSoft,
                                borderWidth = ClayBorder.Hairline
                            )
                            .padding(ClaySpacing.Lg),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(ClaySpacing.Xs)
                    ) {
                        Text(
                            text = "Belum Ada Bagian Garmen",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = WeMadeColors.OnSurface
                        )
                        Text(
                            text = "Bagian garmen diturunkan dari Program CAM. Klik \"+ Bagian\" di atas untuk menambahkan bagian baru.",
                            fontSize = 11.sp,
                            color = WeMadeColors.OnSurfaceMuted
                        )
                    }
                }
            }
        }

        // Section 2: Tambahan Bahan Baku (Non-Perbagian / Aksesoris seperti Zipper, Kancing, dll.)
        AdditionalMaterialsSection(
            items = additionalMaterials,
            availableMaterials = availableMaterials,
            onItemsChange = { updateAndEmit(newAdditionalMaterials = it) }
        )

        // Section 3: Hasil Ukuran Jadi
        ClayCard(modifier = Modifier.fillMaxWidth()) {
            DynamicSectionTable(
                sectionName = StageSectionNames.FINISHED_MEASUREMENTS,
                hint = "Ukuran hasil jadi sampel - tambah baris sesuai kebutuhan (mis. P BADAN : 55 CM).",
                rows = sheet.finishedMeasurements,
                labelPlaceholder = "Label (mis. P BADAN)",
                valuePlaceholder = "Nilai (mis. 55 CM)",
                onRowsChange = { updateAndEmit(newMeasurements = it) }
            )
        }
    }

    // Dialog Tambah Bagian Baru
    if (showAddDialog) {
        AddCamPartDialog(
            existingPartNames = tabs.map { it.name },
            onDismiss = { showAddDialog = false },
            onAddPart = { newPartName ->
                val newTab = CamPartTab(id = "tab-${tabs.size}-$newPartName", name = newPartName)
                val newTabs = tabs + newTab
                selectedTabId = newTab.id
                selectedTabName = newTab.name
                showAddDialog = false
                updateAndEmit(newTabs = newTabs)
            }
        )
    }
}
