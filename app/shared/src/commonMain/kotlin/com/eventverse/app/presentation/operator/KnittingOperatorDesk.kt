package com.eventverse.app.presentation.operator

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eventverse.app.domain.sampling.SamplingOrder
import com.eventverse.app.domain.sampling.SamplingPipelineStage
import com.eventverse.app.presentation.designsystem.ClayBadge
import com.eventverse.app.presentation.designsystem.ClayButton
import com.eventverse.app.presentation.designsystem.ClayButtonStyle
import com.eventverse.app.presentation.designsystem.ClayCard
import com.eventverse.app.presentation.designsystem.ClayShapes
import com.eventverse.app.presentation.designsystem.ClaySpacing
import com.eventverse.app.presentation.designsystem.ClayTag
import com.eventverse.app.presentation.designsystem.clayFlat
import com.eventverse.app.presentation.designsystem.ClayBorder
import com.eventverse.app.presentation.theme.WeMadeColors

/**
 * Meja operator rajut — antrean SPK yang sedang di mesin rajut.
 *
 * Saat ini isinya hanya SPK sample (badge SAMPLE): tim sampling bekerja sebagai operator rajut
 * di meja yang sama dengan operator produksi, bukan dari Kanban sampling. Program CAM tampil
 * read-only karena itulah instruksi yang dijalankan di mesin; hasil turun mesin diisi lewat
 * lembar kerja tahap (gramasi, waktu, size chart, tenselity) lalu SPK pindah ke Linking.
 */
@Composable
fun KnittingOperatorDesk(
    queue: List<SamplingOrder>,
    isSubmitting: Boolean,
    onFinishKnitting: (SamplingOrder) -> Unit,
    modifier: Modifier = Modifier
) {
    if (queue.isEmpty()) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            ClayCard(
                modifier = Modifier.widthIn(max = 420.dp),
                contentPadding = PaddingValues(ClaySpacing.Xl)
            ) {
                Text(
                    text = "Tidak Ada Antrean Rajut",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = WeMadeColors.OnSurface
                )
                Text(
                    text = "SPK masuk ke sini setelah Program CAM disimpan di Order Sampling.",
                    fontSize = 12.sp,
                    color = WeMadeColors.OnSurfaceMuted
                )
            }
        }
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(ClaySpacing.Md)
    ) {
        items(queue, key = { it.id.value }) { order ->
            KnittingQueueCard(order = order, isSubmitting = isSubmitting, onFinish = { onFinishKnitting(order) })
        }
    }
}

@Composable
private fun KnittingQueueCard(order: SamplingOrder, isSubmitting: Boolean, onFinish: () -> Unit) {
    ClayCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(ClaySpacing.Lg)) {
        Column(verticalArrangement = Arrangement.spacedBy(ClaySpacing.Md)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f, fill = false)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = order.spkNumber.value,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = WeMadeColors.Primary
                        )
                        ClayBadge(text = "SAMPLE", tint = WeMadeColors.Accent)
                        ClayTag(text = "${order.sampleQuantity} Pcs", tint = WeMadeColors.Info)
                    }
                    Text(
                        text = "${order.clientName} • ${order.styleName}",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = WeMadeColors.OnSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                ClayButton(
                    text = "Turun Mesin -> Serah ke Linking",
                    style = ClayButtonStyle.Primary,
                    enabled = !isSubmitting,
                    onClick = onFinish
                )
            }
            CamProgramReadOnly(order)
        }
    }
}

/** Instruksi mesin dari lembar Program CAM — dibaca operator, tidak diubah di sini. */
@Composable
private fun CamProgramReadOnly(order: SamplingOrder) {
    val cam = order.stageInputFor(SamplingPipelineStage.CAM_PROGRAMMING)
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
        verticalArrangement = Arrangement.spacedBy(ClaySpacing.Xxs)
    ) {
        Text(
            text = "PROGRAM CAM",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = WeMadeColors.OnSurface
        )
        if (cam == null || cam.sections.isEmpty()) {
            Text(text = "Belum ada lembar Program CAM", fontSize = 11.sp, color = WeMadeColors.OnSurfaceMuted)
        } else {
            cam.sections.forEach { section ->
                Text(
                    text = "• ${section.section}: " + section.rows
                        .filter { it.isFilled }
                        .joinToString("; ") { "${it.label} : ${it.value}" },
                    fontSize = 11.sp,
                    color = WeMadeColors.OnSurface
                )
            }
        }
    }
}
