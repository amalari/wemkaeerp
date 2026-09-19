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
import com.eventverse.app.domain.sampling.FinishingPath
import com.eventverse.app.domain.sampling.SamplingOrder
import com.eventverse.app.domain.sampling.StageInputSection
import com.eventverse.app.presentation.designsystem.ClayBadge
import com.eventverse.app.presentation.designsystem.ClayBorder
import com.eventverse.app.presentation.designsystem.ClayButton
import com.eventverse.app.presentation.designsystem.ClayButtonStyle
import com.eventverse.app.presentation.designsystem.ClayCard
import com.eventverse.app.presentation.designsystem.ClayShapes
import com.eventverse.app.presentation.designsystem.ClaySpacing
import com.eventverse.app.presentation.designsystem.IconClose
import com.eventverse.app.presentation.designsystem.clayFlat
import com.eventverse.app.presentation.theme.WeMadeColors

/**
 * Dialog detail SPK — dibuka saat kartu di kolom "SPK Baru" diklik.
 *
 * Selain ringkasan data SPK, dialog ini adalah meja kerja persiapan tim sampling:
 * lembar Program CAM (program, instruksi panah, rumus pola) diisi di sini, lalu submit
 * memindahkan SPK ke tahap Program CAM sekaligus menyimpan lembar kerjanya — gerbang
 * domain yang sama dengan [StageAdvanceDialog] memvalidasi ulang di server.
 */
@Composable
fun SamplingSpkDetailDialog(
    order: SamplingOrder,
    isSubmitting: Boolean,
    onDismiss: () -> Unit,
    onStartCamProgram: (List<StageInputSection>) -> Unit,
    onCreateTechPack: ((SamplingOrder) -> Unit)? = null
) {
    val sectionSpecs = remember { CAM_SECTION_SPECS }
    var sections by remember(order.id) {
        mutableStateOf(sectionSpecs.map { StageInputSection(section = it.sectionName, rows = emptyList()) })
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        ClayCard(
            modifier = Modifier
                .fillMaxWidth(0.6f)
                .heightIn(max = 640.dp),
            contentPadding = PaddingValues(0.dp)
        ) {
            Column(
                modifier = Modifier.padding(ClaySpacing.Lg),
                verticalArrangement = Arrangement.spacedBy(ClaySpacing.Md)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f, fill = false)) {
                        Text(
                            text = "Detail SPK — ${order.spkNumber.value}",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = WeMadeColors.OnSurface
                        )
                        Text(
                            text = "${order.clientName} · ${order.styleName}",
                            fontSize = 11.sp,
                            color = WeMadeColors.OnSurfaceMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    ClayBadge(
                        text = order.pipelineStage.displayName,
                        tint = samplingStageTint(order.pipelineStage)
                    )
                    androidx.compose.material3.IconButton(onClick = onDismiss) {
                        IconClose(modifier = Modifier.size(18.dp))
                    }
                }
                // Ringkasan SPK (read-only)
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
                    SpkDetailRow("Klien", order.clientName)
                    SpkDetailRow("Style", order.styleName)
                    SpkDetailRow("Mode Ukuran", order.sizeMode.displayName)
                    SpkDetailRow("Qty Sample", "${order.sampleQuantity} Pcs")
                    SpkDetailRow(
                        "Jalur Finishing",
                        if (order.finishingPath == FinishingPath.MAKLOON_VENDOR) "Makloon Vendor" else "Internal"
                    )
                    SpkDetailRow("Deadline Program", order.deadlineProgram?.toString() ?: "-")
                    SpkDetailRow("Deadline Pengiriman", order.deadlineDelivery?.toString() ?: "-")
                }

                // Lembar persiapan Program CAM tim sampling
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(ClaySpacing.Md)
                ) {
                    Text(
                        text = "Persiapan Tim Sampling — Program CAM",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = WeMadeColors.OnSurface
                    )
                    Text(
                        text = "Isi program, instruksi panah, dan rumus pola sebelum SPK masuk tahap Program CAM.",
                        fontSize = 10.sp,
                        color = WeMadeColors.OnSurfaceMuted
                    )

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

                // Footer
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)
                ) {
                    if (onCreateTechPack != null) {
                        ClayButton(
                            text = "Buat Tech Pack",
                            style = ClayButtonStyle.Secondary,
                            modifier = Modifier.weight(1f),
                            onClick = { onCreateTechPack(order) }
                        )
                    }
                    ClayButton(
                        text = "Tutup",
                        style = ClayButtonStyle.Secondary,
                        modifier = Modifier.weight(1f),
                        onClick = onDismiss
                    )
                    // Gerbang klien sama dengan dialog tahap: semua section CAM wajib
                    // punya minimal satu baris terisi (server memvalidasi ulang).
                    val canSubmit = sections.all { it.hasFilledRow }
                    ClayButton(
                        text = "Simpan & Masuk Program CAM",
                        style = ClayButtonStyle.Primary,
                        modifier = Modifier.weight(2f),
                        enabled = canSubmit && !isSubmitting,
                        onClick = { onStartCamProgram(sections) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SpkDetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, fontSize = 11.sp, color = WeMadeColors.OnSurfaceMuted)
        Text(
            text = value,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = WeMadeColors.OnSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
