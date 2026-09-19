package com.eventverse.app.presentation.sampling.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.eventverse.app.domain.sampling.SamplingOrder
import com.eventverse.app.domain.sampling.SamplingPipelineStage
import com.eventverse.app.domain.sampling.StageInputRow
import com.eventverse.app.domain.sampling.StageInputSection
import com.eventverse.app.presentation.designsystem.ClayBadge
import com.eventverse.app.presentation.designsystem.ClayBorder
import com.eventverse.app.presentation.designsystem.ClayButton
import com.eventverse.app.presentation.designsystem.ClayButtonStyle
import com.eventverse.app.presentation.designsystem.ClayCard
import com.eventverse.app.presentation.designsystem.ClayShapes
import com.eventverse.app.presentation.designsystem.ClaySpacing
import com.eventverse.app.presentation.designsystem.ClayTextField
import com.eventverse.app.presentation.designsystem.IconClose
import com.eventverse.app.presentation.designsystem.IconPlus
import com.eventverse.app.presentation.designsystem.IconTrash
import com.eventverse.app.presentation.designsystem.clayFlat
import com.eventverse.app.presentation.theme.WeMadeColors

/**
 * Dialog lembar kerja dinamis saat pindah tahap (drag ATAU tombol).
 *
 * - SPK & data tahap sebelumnya ditampilkan read-only di atas.
 * - Section input aktif dikosongkan (tanpa prefill) — operator menambah baris sendiri
 *   lewat tombol tambah; label & value bebas, tidak terikat field fix.
 */
@Composable
fun StageAdvanceDialog(
    order: SamplingOrder,
    targetStage: SamplingPipelineStage,
    isSubmitting: Boolean,
    onConfirm: (List<StageInputSection>) -> Unit,
    onDismiss: () -> Unit
) {
    val sectionSpecs = remember(targetStage) { stageSectionsFor(targetStage) }
    var sections by remember(targetStage, order.id) {
        mutableStateOf(sectionSpecs.map { StageInputSection(section = it.sectionName, rows = emptyList()) })
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        ClayCard(
            modifier = Modifier
                .fillMaxWidth(0.72f)
                .heightIn(max = 640.dp),
            contentPadding = PaddingValues(0.dp)
        ) {
            Column(
                modifier = Modifier.padding(ClaySpacing.Lg),
                verticalArrangement = Arrangement.spacedBy(ClaySpacing.Md)
            ) {
                StageAdvanceDialogHeader(order = order, targetStage = targetStage, onDismiss = onDismiss)

                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(ClaySpacing.Md)
                ) {
                    PreviousStageSummary(order = order, targetStage = targetStage)

                    sections.forEachIndexed { sectionIndex, section ->
                        DynamicSectionTable(
                            sectionName = section.section,
                            hint = sectionSpecs[sectionIndex].hint,
                            rows = section.rows,
                            onRowsChange = { newRows ->
                                sections = sections.toMutableList().apply {
                                    this[sectionIndex] = section.copy(rows = newRows)
                                }
                            }
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)
                ) {
                    ClayButton(
                        text = "Batal",
                        style = ClayButtonStyle.Secondary,
                        modifier = Modifier.weight(1f),
                        onClick = onDismiss
                    )
                    // Gerbang klien: semua section wajib punya minimal satu baris terisi
                    // sebelum tombol lanjut aktif (server memvalidasi ulang).
                    val canSubmit = sections.all { it.hasFilledRow }
                    ClayButton(
                        text = "Simpan & Lanjut ke ${targetStage.displayName}",
                        style = ClayButtonStyle.Primary,
                        modifier = Modifier.weight(2f),
                        enabled = canSubmit && !isSubmitting,
                        onClick = { onConfirm(sections) }
                    )
                }
            }
        }
    }
}



@Composable
private fun StageAdvanceDialogHeader(
    order: SamplingOrder,
    targetStage: SamplingPipelineStage,
    onDismiss: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f, fill = false)) {
            Text(
                text = "Lembar Kerja — ${targetStage.displayName}",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = WeMadeColors.OnSurface
            )
            Text(
                text = "SPK ${order.spkNumber.value} · ${order.clientName} · ${order.styleName}",
                fontSize = 11.sp,
                color = WeMadeColors.OnSurfaceMuted
            )
        }
        ClayBadge(text = "${order.sampleQuantity} Pcs", tint = WeMadeColors.Info)
        androidx.compose.material3.IconButton(onClick = onDismiss) {
            IconClose(modifier = Modifier.size(18.dp))
        }
    }
}

/** Data tahap-tahap sebelumnya — read-only, supaya operator lihat konteks tanpa pindah layar. */
@Composable
private fun PreviousStageSummary(order: SamplingOrder, targetStage: SamplingPipelineStage) {
    val summaryStages = SamplingPipelineStage.entries
        .filter { it.order < targetStage.order }
        .mapNotNull { stage -> order.stageInputFor(stage)?.takeIf { work -> work.sections.isNotEmpty() } }

    if (summaryStages.isEmpty()) return

    Column(verticalArrangement = Arrangement.spacedBy(ClaySpacing.Xs)) {
        Text(
            text = "Data Tahap Sebelumnya",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = WeMadeColors.OnSurface
        )
        summaryStages.forEach { work ->
            Text(
                text = work.stage.displayName,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = WeMadeColors.Primary
            )
            work.sections.forEach { section ->
                Text(
                    text = "• ${section.section}: " + section.rows
                        .filter { it.isFilled }
                        .joinToString("; ") { "${it.label} : ${it.value}" },
                    fontSize = 11.sp,
                    color = WeMadeColors.OnSurfaceMuted
                )
            }
        }
    }
}

/**
 * Satu section tabel dinamis: header + baris label/value yang bisa ditambah-hapus.
 * Dipakai ulang oleh SEMUA section (gramasi, waktu, size chart, tenselity, program, …)
 * — Aturan Tiga Kali: satu komponen, bukan enam blok yang disalin.
 * Dipakai bersama oleh [StageAdvanceDialog] dan [SamplingSpkDetailDialog].
 */
@Composable
fun DynamicSectionTable(
    sectionName: String,
    hint: String,
    rows: List<StageInputRow>,
    onRowsChange: (List<StageInputRow>) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clayFlat(
                shape = ClayShapes.Tile,
                background = WeMadeColors.SurfaceMuted,
                outline = WeMadeColors.Outline,
                borderWidth = ClayBorder.Medium
            )
            .padding(ClaySpacing.Sm),
        verticalArrangement = Arrangement.spacedBy(ClaySpacing.Xs)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f, fill = false)) {
                Text(
                    text = sectionName,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = WeMadeColors.OnSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = hint,
                    fontSize = 10.sp,
                    color = WeMadeColors.OnSurfaceMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            androidx.compose.material3.IconButton(
                onClick = { onRowsChange(rows + StageInputRow(label = "", value = "")) }
            ) {
                IconPlus(modifier = Modifier.size(18.dp))
            }
        }

        if (rows.isEmpty()) {
            Text(
                text = "Belum ada baris — tekan + untuk menambah",
                fontSize = 10.sp,
                color = WeMadeColors.OnSurfaceMuted
            )
        }

        rows.forEachIndexed { rowIndex, row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Xs),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ClayTextField(
                    value = row.label,
                    onValueChange = { newLabel ->
                        onRowsChange(rows.toMutableList().apply {
                            this[rowIndex] = row.copy(label = newLabel)
                        })
                    },
                    placeholder = "Label (mis. DEPAN)",
                    modifier = Modifier.weight(1f)
                )
                ClayTextField(
                    value = row.value,
                    onValueChange = { newValue ->
                        onRowsChange(rows.toMutableList().apply {
                            this[rowIndex] = row.copy(value = newValue)
                        })
                    },
                    placeholder = "Nilai (mis. 117 GR)",
                    modifier = Modifier.weight(1f)
                )
                androidx.compose.material3.IconButton(
                    onClick = { onRowsChange(rows.filterIndexed { index, _ -> index != rowIndex }) }
                ) {
                    IconTrash(modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}
