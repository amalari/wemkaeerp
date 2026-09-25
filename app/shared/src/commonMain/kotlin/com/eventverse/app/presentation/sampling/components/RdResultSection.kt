package com.eventverse.app.presentation.sampling.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import com.eventverse.app.domain.sampling.StageInputRow
import com.eventverse.app.domain.sampling.StageInputSection
import com.eventverse.app.domain.sampling.StageSectionNames
import com.eventverse.app.presentation.designsystem.*
import com.eventverse.app.presentation.theme.WeMadeColors

/**
 * Section "Hasil R&D" di dialog Detail SPK — tampil saat SPK sudah turun ke lantai R&D
 * (rajut sampai kemas). Gramasi, waktu, dan ukuran jadi baru diketahui setelah sampel dibuat,
 * jadi diisi di sini, bukan di Program CAM.
 *
 * Datanya tetap disimpan di lembar Program CAM (satu lembar teknis per SPK), sehingga bagian
 * garmen tidak perlu diketik ulang: daftar bagian diambil dari tab yang dibuat di CAM.
 */
@Composable
fun RdResultSection(
    sections: List<StageInputSection>,
    onSectionsChange: (List<StageInputSection>) -> Unit
) {
    val sheet = remember(sections) { parseCamSections(sections) }

    fun emit(tabs: List<CamPartTab> = sheet.tabs, measurements: List<StageInputRow> = sheet.finishedMeasurements) =
        onSectionsChange(serializeCamSections(tabs, sheet.formulaNote, measurements))

    ClayCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(ClaySpacing.Md)) {
            Column(verticalArrangement = Arrangement.spacedBy(ClaySpacing.Xs)) {
                Text(
                    text = "HASIL R&D — GRAMASI & WAKTU PER BAGIAN",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = WeMadeColors.OnSurface
                )
                Text(
                    text = "Isi setelah panel selesai dirajut. Bagian garmen mengikuti Program CAM.",
                    fontSize = 10.sp,
                    color = WeMadeColors.OnSurfaceMuted
                )
            }

            if (sheet.tabs.isEmpty()) {
                Text(
                    text = "Belum ada bagian garmen di Program CAM.",
                    fontSize = 11.sp,
                    color = WeMadeColors.OnSurfaceMuted
                )
            }

            sheet.tabs.forEach { tab ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(0.8f)) {
                        Text(
                            text = tab.name.uppercase(),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = WeMadeColors.OnSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = tab.program.ifBlank { "—" },
                            fontSize = 10.sp,
                            color = WeMadeColors.OnSurfaceMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    ClayTextField(
                        value = tab.gramasi,
                        onValueChange = { v -> emit(tabs = sheet.tabs.map { if (it.id == tab.id) it.copy(gramasi = v) else it }) },
                        placeholder = "Gramasi (mis. 117 GR)",
                        modifier = Modifier.weight(1f)
                    )
                    ClayTextField(
                        value = tab.waktu,
                        onValueChange = { v -> emit(tabs = sheet.tabs.map { if (it.id == tab.id) it.copy(waktu = v) else it }) },
                        placeholder = "Waktu (mis. 37 MENIT)",
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }

    ClayCard(modifier = Modifier.fillMaxWidth()) {
        DynamicSectionTable(
            sectionName = StageSectionNames.FINISHED_MEASUREMENTS,
            hint = "Ukuran hasil jadi sampel — tambah baris sesuai kebutuhan (mis. P BADAN : 55 CM).",
            rows = sheet.finishedMeasurements,
            labelPlaceholder = "Label (mis. P BADAN)",
            valuePlaceholder = "Nilai (mis. 55 CM)",
            onRowsChange = { emit(measurements = it) }
        )
    }
}
