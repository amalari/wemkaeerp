package com.eventverse.app.presentation.sampling.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.eventverse.app.domain.sampling.StageInputRow
import com.eventverse.app.domain.sampling.StageInputSection
import com.eventverse.app.domain.sampling.StageSectionNames
import com.eventverse.app.presentation.designsystem.*
import com.eventverse.app.presentation.theme.WeMadeColors

/** Data representasi satu tab bagian garmen pada tahap Program CAM. */
data class CamPartTab(
    val id: String,
    val name: String,
    val program: String = "",
    val feederInstructions: List<String> = emptyList(),
    val tenselities: List<String> = emptyList()
) {
    /** Tab dianggap full/lengkap jika kode program dan minimal 1 instruksi panah sudah terisi. */
    val isComplete: Boolean get() = program.isNotBlank() && feederInstructions.isNotEmpty()
}

val SUGGESTED_CAM_PARTS = listOf(
    "Badan Depan", "Badan Belakang", "Depan", "Belakang", "Lengan", "Kerah",
    "Rib Bawah", "Manset", "Saku", "Tudung / Hoodie", "Placket"
)

/** Parsing List<StageInputSection> menjadi list tab bagian dan catatan rumus pola. */
fun parseCamSections(sections: List<StageInputSection>): Pair<List<CamPartTab>, String> {
    val progSec = sections.firstOrNull { it.section == StageSectionNames.PROGRAM }
    val feederSec = sections.firstOrNull { it.section == StageSectionNames.FEEDER_INSTRUCTIONS }
    val tenselitySec = sections.firstOrNull { it.section == StageSectionNames.TENSELITY }
    val formulaSec = sections.firstOrNull { it.section == StageSectionNames.PATTERN_FORMULAS }

    val tabNames = linkedSetOf<String>()
    progSec?.rows?.forEach { if (it.label.isNotBlank()) tabNames.add(it.label.trim()) }
    feederSec?.rows?.forEach { row ->
        val name = if (row.label.contains(" • ")) row.label.substringBefore(" • ").trim() else row.label.trim()
        if (name.isNotBlank()) tabNames.add(name)
    }
    tenselitySec?.rows?.forEach { row ->
        val name = if (row.label.contains(" • ")) row.label.substringBefore(" • ").trim() else row.label.trim()
        if (name.isNotBlank()) tabNames.add(name)
    }

    val tabs = tabNames.mapIndexed { idx, name ->
        val progRow = progSec?.rows?.firstOrNull { it.label.equals(name, ignoreCase = true) }
        val feederRows = feederSec?.rows?.filter {
            it.label.startsWith("$name •", ignoreCase = true) || it.label.equals(name, ignoreCase = true)
        }.orEmpty()
        val feederTags = feederRows.flatMap { row ->
            row.value.split("\n").map { it.trim() }.filter { it.isNotBlank() }
        }

        val tenselityRows = tenselitySec?.rows?.filter {
            it.label.startsWith("$name •", ignoreCase = true) || it.label.equals(name, ignoreCase = true)
        }.orEmpty()
        val tenselityTags = tenselityRows.flatMap { row ->
            row.value.split("\n").map { it.trim() }.filter { it.isNotBlank() }
        }

        CamPartTab("tab-$idx-$name", name, progRow?.value.orEmpty(), feederTags, tenselityTags)
    }

    val formulaNote = formulaSec?.rows?.joinToString("\n") { row ->
        if (row.label.isNotBlank() && row.label != "Catatan Pola") "${row.label}: ${row.value}" else row.value
    }.orEmpty()

    return Pair(tabs, formulaNote)
}

/** Serialisasi list tab bagian dan catatan rumus pola kembali menjadi List<StageInputSection>. */
fun serializeCamSections(tabs: List<CamPartTab>, rumusPolaNote: String): List<StageInputSection> {
    val progRows = tabs.map { tab ->
        StageInputRow(label = tab.name, value = tab.program)
    }
    val feederRows = tabs.flatMap { tab ->
        tab.feederInstructions.filter { it.isNotBlank() }.map { tag ->
            StageInputRow(label = tab.name, value = tag)
        }
    }
    val tenselityRows = tabs.flatMap { tab ->
        tab.tenselities.filter { it.isNotBlank() }.map { tag ->
            StageInputRow(label = tab.name, value = tag)
        }
    }
    val formulaRows = if (rumusPolaNote.isNotBlank()) {
        listOf(StageInputRow(label = "Catatan Pola", value = rumusPolaNote))
    } else emptyList()

    return listOf(
        StageInputSection(StageSectionNames.PROGRAM, progRows),
        StageInputSection(StageSectionNames.FEEDER_INSTRUCTIONS, feederRows),
        StageInputSection(StageSectionNames.TENSELITY, tenselityRows),
        StageInputSection(StageSectionNames.PATTERN_FORMULAS, formulaRows)
    )
}

/**
 * Section Program CAM dengan UI Tabbing dinamis per bagian garmen,
 * memuat program CAM serta instruksi panah & tenselity dalam format taggable,
 * dan catatan rumus pola di section terpisah.
 */
@Composable
fun CamProgramTabbedSection(
    sections: List<StageInputSection>,
    onSectionsChange: (List<StageInputSection>) -> Unit,
    validationTrigger: Int = 0
) {
    val parsed = remember(sections) { parseCamSections(sections) }
    var tabs by remember(sections) { mutableStateOf(parsed.first) }
    var rumusPolaNote by remember(sections) { mutableStateOf(parsed.second) }
    var selectedTabId by remember { mutableStateOf(tabs.firstOrNull()?.id.orEmpty()) }
    var showAddDialog by remember { mutableStateOf(false) }
    var showValidationErrors by remember { mutableStateOf(false) }

    LaunchedEffect(validationTrigger) {
        if (validationTrigger > 0) {
            val hasIncomplete = tabs.isEmpty() || tabs.any { !it.isComplete }
            showValidationErrors = hasIncomplete
            if (hasIncomplete) {
                val firstIncomplete = tabs.firstOrNull { !it.isComplete }
                if (firstIncomplete != null) {
                    selectedTabId = firstIncomplete.id
                }
            }
        }
    }

    if (showValidationErrors && tabs.isNotEmpty() && tabs.all { it.isComplete }) {
        showValidationErrors = false
    }

    if (tabs.none { it.id == selectedTabId } && tabs.isNotEmpty()) {
        selectedTabId = tabs.first().id
    }
    val activeTab = tabs.firstOrNull { it.id == selectedTabId } ?: tabs.firstOrNull()

    fun updateAndEmit(newTabs: List<CamPartTab>, newNote: String) {
        tabs = newTabs
        rumusPolaNote = newNote
        onSectionsChange(serializeCamSections(newTabs, newNote))
    }

    Column(verticalArrangement = Arrangement.spacedBy(ClaySpacing.Md)) {
        // Section 1: Program CAM per Bagian Garmen (Tabbing)
        ClayCard(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(ClaySpacing.Md)) {
                Column(verticalArrangement = Arrangement.spacedBy(ClaySpacing.Xs)) {
                    Text(
                        text = "PROGRAM CAM — INPUT TIM SAMPLING",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = WeMadeColors.OnSurface
                    )
                    Text(
                        text = "Atur program CAM, instruksi panah, dan tenselity per bagian garmen.",
                        fontSize = 10.sp,
                        color = WeMadeColors.OnSurfaceMuted
                    )
                }

                // Pesan Error Validasi bila ada tab yang belum lengkap
                if (showValidationErrors) {
                    val errorText = when {
                        tabs.isEmpty() -> "Validasi Gagal: Minimal 1 bagian garmen harus ditambahkan dan diisi lengkap!"
                        else -> {
                            val incompleteNames = tabs.filter { !it.isComplete }.map { it.name }
                            "Validasi Gagal: Bagian ${incompleteNames.joinToString { "\"$it\"" }} belum lengkap. Kode program dan instruksi panah wajib diisi!"
                        }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clayFlat(
                                shape = ClayShapes.Chip,
                                background = WeMadeColors.Error.copy(alpha = 0.1f),
                                outline = WeMadeColors.Error,
                                borderWidth = ClayBorder.Medium
                            )
                            .padding(horizontal = ClaySpacing.Md, vertical = ClaySpacing.Sm),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)
                    ) {
                        IconWarning(modifier = Modifier.size(16.dp), color = WeMadeColors.Error)
                        Text(
                            text = errorText,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = WeMadeColors.Error
                        )
                    }
                }

                // Baris Tabs Bagian Dinamis
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    tabs.forEach { tab ->
                        val isSelected = tab.id == selectedTabId
                        val isComplete = tab.isComplete
                        val isErrorTab = showValidationErrors && !isComplete

                        Row(
                            modifier = Modifier
                                .clickable { selectedTabId = tab.id }
                                .claySurface(
                                    shape = ClayShapes.Pill,
                                    background = when {
                                        isSelected && isErrorTab -> WeMadeColors.Error
                                        isSelected -> WeMadeColors.Primary
                                        isErrorTab -> WeMadeColors.Error.copy(alpha = 0.12f)
                                        else -> WeMadeColors.SurfaceMuted
                                    },
                                    outline = when {
                                        isErrorTab -> WeMadeColors.Error
                                        isSelected -> WeMadeColors.Outline
                                        else -> WeMadeColors.OutlineSoft
                                    },
                                    offset = if (isSelected || isErrorTab) ClayOffset.Small else ClayOffset.Flat,
                                    borderWidth = if (isErrorTab) ClayBorder.Thick else ClayBorder.Medium
                                )
                                .padding(horizontal = ClaySpacing.Md, vertical = ClaySpacing.Xs),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Xs)
                        ) {
                            // Tanda status Lengkap vs Belum
                            if (isComplete) {
                                IconCheck(
                                    modifier = Modifier.size(12.dp),
                                    color = if (isSelected) WeMadeColors.Surface else WeMadeColors.Success
                                )
                            } else {
                                IconWarning(
                                    modifier = Modifier.size(12.dp),
                                    color = when {
                                        isSelected -> WeMadeColors.Surface
                                        isErrorTab -> WeMadeColors.Error
                                        else -> WeMadeColors.Warning
                                    }
                                )
                            }

                            Text(
                                text = tab.name,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = when {
                                    isSelected -> WeMadeColors.Surface
                                    isErrorTab -> WeMadeColors.Error
                                    else -> WeMadeColors.OnSurface
                                }
                            )

                            Text(
                                text = if (isComplete) "Lengkap" else "Belum",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = when {
                                    isSelected -> WeMadeColors.Surface.copy(alpha = 0.85f)
                                    isErrorTab -> WeMadeColors.Error
                                    isComplete -> WeMadeColors.Success
                                    else -> WeMadeColors.Warning
                                }
                            )

                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .clickable {
                                        val newTabs = tabs.filter { it.id != tab.id }
                                        if (selectedTabId == tab.id) {
                                            selectedTabId = newTabs.firstOrNull()?.id.orEmpty()
                                        }
                                        updateAndEmit(newTabs, rumusPolaNote)
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                IconClose(
                                    modifier = Modifier.size(10.dp),
                                    color = when {
                                        isSelected -> WeMadeColors.Surface
                                        isErrorTab -> WeMadeColors.Error
                                        else -> WeMadeColors.OnSurfaceMuted
                                    }
                                )
                            }
                        }
                    }

                    ClayButton(
                        text = "+ Bagian",
                        style = if (showValidationErrors && tabs.isEmpty()) ClayButtonStyle.Primary else ClayButtonStyle.Ghost,
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
                        Text(
                            text = "BAGIAN AKTIF: ${activeTab.name.uppercase()}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = WeMadeColors.Primary
                        )

                        // 1. Program CAM (1 input tunggal)
                        val isProgError = showValidationErrors && activeTab.program.isBlank()
                        ClayTextField(
                            value = activeTab.program,
                            onValueChange = { newProg ->
                                val newTabs = tabs.map {
                                    if (it.id == activeTab.id) it.copy(program = newProg) else it
                                }
                                updateAndEmit(newTabs, rumusPolaNote)
                            },
                            label = "KODE PROGRAM CAM (${activeTab.name.uppercase()})",
                            placeholder = "mis. BIAN-D atau HD-OVS-DPN.001",
                            isError = isProgError,
                            errorMessage = if (isProgError) "Kode program (${activeTab.name}) wajib diisi!" else null,
                            modifier = Modifier.fillMaxWidth()
                        )

                        // 2. Instruksi Panah (Taggable dengan urutan nomor)
                        val isFeederError = showValidationErrors && activeTab.feederInstructions.isEmpty()
                        ClayTagInput(
                            label = "INSTRUKSI PANAH (${activeTab.name.uppercase()})",
                            placeholder = "Ketik lalu tekan Enter atau koma (,) untuk buat tag...",
                            tags = activeTab.feederInstructions,
                            numbered = true,
                            isError = isFeederError,
                            errorMessage = if (isFeederError) "Minimal 1 instruksi panah (feeder ${activeTab.name}) wajib diisi!" else null,
                            onTagsChange = { newFeeders ->
                                val newTabs = tabs.map {
                                    if (it.id == activeTab.id) it.copy(feederInstructions = newFeeders) else it
                                }
                                updateAndEmit(newTabs, rumusPolaNote)
                            }
                        )

                        // 3. Tenselity (Taggable)
                        ClayTagInput(
                            label = "TENSELITY / SETTING TENSION (${activeTab.name.uppercase()})",
                            placeholder = "Ketik lalu tekan Enter atau koma (,) untuk buat tag...",
                            tags = activeTab.tenselities,
                            numbered = false,
                            onTagsChange = { newTenselities ->
                                val newTabs = tabs.map {
                                    if (it.id == activeTab.id) it.copy(tenselities = newTenselities) else it
                                }
                                updateAndEmit(newTabs, rumusPolaNote)
                            }
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clayFlat(
                                shape = ClayShapes.Card,
                                background = if (showValidationErrors) WeMadeColors.Error.copy(alpha = 0.06f) else WeMadeColors.SurfaceMuted,
                                outline = if (showValidationErrors) WeMadeColors.Error else WeMadeColors.OutlineSoft,
                                borderWidth = if (showValidationErrors) ClayBorder.Medium else ClayBorder.Hairline
                            )
                            .padding(ClaySpacing.Lg),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(ClaySpacing.Xs)
                    ) {
                        Text(
                            text = if (showValidationErrors) "Minimal 1 Bagian Garmen Wajib Ditambahkan!" else "Belum Ada Bagian Garmen",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (showValidationErrors) WeMadeColors.Error else WeMadeColors.OnSurface
                        )
                        Text(
                            text = "Klik tombol \"+ Bagian\" di atas untuk menambahkan bagian garmen yang akan diprogram.",
                            fontSize = 11.sp,
                            color = if (showValidationErrors) WeMadeColors.Error else WeMadeColors.OnSurfaceMuted
                        )
                    }
                }
            }
        }

        // Section 2: Catatan Rumus Pola (Beda Section)
        ClayCard(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Xs)
                ) {
                    IconNote(modifier = Modifier.size(16.dp), color = WeMadeColors.Primary)
                    Text(
                        text = "CATATAN RUMUS POLA",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = WeMadeColors.OnSurface
                    )
                }
                Text(
                    text = "Rumus kalkulasi pola (mis. P Badan & L Dada), hitungan jarum, atau catatan teknis rajut.",
                    fontSize = 10.sp,
                    color = WeMadeColors.OnSurfaceMuted
                )
                ClayTextField(
                    value = rumusPolaNote,
                    onValueChange = { newNote ->
                        updateAndEmit(tabs, newNote)
                    },
                    placeholder = "mis. PB & LD : 25 X 8\nP BADAN : 2.94 K\nTarget gramasi rajut mentah 117 gr...",
                    singleLine = false,
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }

    // Dialog Tambah Bagian Baru
    if (showAddDialog) {
        AddCamPartDialog(
            existingPartNames = tabs.map { it.name },
            onDismiss = { showAddDialog = false },
            onAddPart = { newPartName ->
                val newTab = CamPartTab("tab-${tabs.size}-$newPartName", newPartName)
                val newTabs = tabs + newTab
                selectedTabId = newTab.id
                showAddDialog = false
                updateAndEmit(newTabs, rumusPolaNote)
            }
        )
    }
}
