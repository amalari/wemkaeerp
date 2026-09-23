package com.eventverse.app.presentation.deal.components

import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eventverse.app.domain.sampling.FinishingPath
import com.eventverse.app.domain.sampling.SamplingOrder
import com.eventverse.app.domain.sampling.SamplingPipelineStage
import com.eventverse.app.presentation.designsystem.*
import com.eventverse.app.presentation.theme.WeMadeColors
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Monitoring lantai produksi sampel di Tab 1 Deal — satu kartu per [SamplingPipelineStage]
 * dari Program CAM sampai Pengemasan.
 *
 * Diturunkan dari enum, bukan ditulis sebagai daftar kartu tetap: dulu kartunya hardcode lima
 * fase (dengan satu "Finishing" gabungan), sehingga ketika `FINISHING_QC` dipecah menjadi
 * Cuci → Setrika → QC Finishing → Pengemasan, monitoring ini tertinggal menampilkan fase yang
 * sudah tidak ada. Waktu mulai/selesai diambil dari [SamplingOrder.stageHistory].
 */
@Composable
internal fun SamplingMonitoringTimeline(
    order: SamplingOrder,
    modifier: Modifier = Modifier
) {
    val floorStages = SamplingPipelineStage.entries.filter {
        it.order in SamplingPipelineStage.CAM_PROGRAMMING.order..SamplingPipelineStage.PENGEMASAN.order
    }
    val isMakloon = order.finishingPath == FinishingPath.MAKLOON_VENDOR
    val activeIndex = floorStages.indexOf(order.pipelineStage)
        .takeIf { it >= 0 }
        ?: if (order.pipelineStage > SamplingPipelineStage.PENGEMASAN) floorStages.lastIndex else 0

    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val stepPx = with(density) { (CardWidth + ClaySpacing.Sm).toPx() }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CarouselArrow(
            enabled = scrollState.canScrollBackward,
            onClick = { scope.launch { scrollState.animateScrollBy(-stepPx) } }
        ) { IconArrowBack(Modifier.size(14.dp), color = it) }

        BoxWithConstraints(modifier = Modifier.weight(1f)) {
            val viewportPx = with(density) { maxWidth.toPx() }
            val cardPx = with(density) { CardWidth.toPx() }

            // Tahap yang sedang berjalan diletakkan di tengah; scrollTo meng-clamp sendiri ke
            // [0, maxValue], jadi tahap di ujung kiri/kanan otomatis mentok ke pinggir.
            LaunchedEffect(activeIndex, viewportPx, scrollState.maxValue) {
                val target = activeIndex * stepPx + cardPx / 2f - viewportPx / 2f
                scrollState.scrollTo(target.roundToInt().coerceIn(0, scrollState.maxValue))
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(scrollState),
                horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)
            ) {
                floorStages.forEachIndexed { index, stage ->
                    val isDone = order.pipelineStage > stage
                    val isActive = order.pipelineStage == stage
                    val enteredAt = order.stageHistory.firstOrNull { it.toStage == stage }?.at
                        ?: if (stage == SamplingPipelineStage.CAM_PROGRAMMING && (isDone || isActive)) order.createdAt else null
                    val leftAt = order.stageHistory.lastOrNull { it.fromStage == stage && it.toStage > stage }?.at

                    TimelineStepCard(
                        stepNumber = "${index + 1}",
                        title = stage.displayName,
                        status = when {
                            isDone -> "Selesai"
                            isActive && isMakloon && stage.isWetOrPressWork -> "Di Vendor Makloon"
                            isActive -> "Dikerjakan"
                            else -> null
                        },
                        isDone = isDone,
                        isActive = isActive,
                        mulaiText = if (isDone || isActive) formatInstantWithTime(enteredAt ?: order.updatedAt) else "-",
                        selesaiText = if (isDone) formatInstantWithTime(leftAt ?: order.updatedAt) else "-",
                        modifier = Modifier.width(CardWidth)
                    )
                }
            }
        }

        CarouselArrow(
            enabled = scrollState.canScrollForward,
            onClick = { scope.launch { scrollState.animateScrollBy(stepPx) } }
        ) { IconArrowForward(Modifier.size(14.dp), color = it) }
    }
}

private val CardWidth = 128.dp

@Composable
private fun CarouselArrow(
    enabled: Boolean,
    onClick: () -> Unit,
    icon: @Composable (Color) -> Unit
) {
    ClayIconButton(onClick = onClick, size = 28.dp, enabled = enabled) {
        icon(if (enabled) WeMadeColors.OnSurface else WeMadeColors.OnSurfaceMuted.copy(alpha = 0.4f))
    }
}

@Composable
private fun TimelineStepCard(
    stepNumber: String,
    title: String,
    status: String?,
    isDone: Boolean,
    isActive: Boolean,
    mulaiText: String,
    selesaiText: String,
    modifier: Modifier = Modifier
) {
    val cardBg = when {
        isDone -> WeMadeColors.Success.copy(alpha = 0.08f)
        isActive -> WeMadeColors.Purple.copy(alpha = 0.08f)
        else -> WeMadeColors.Surface.copy(alpha = 0.90f)
    }
    val cardOutline = when {
        isDone -> WeMadeColors.Success.copy(alpha = 0.5f)
        isActive -> WeMadeColors.Purple.copy(alpha = 0.55f)
        else -> WeMadeColors.Border
    }

    Column(
        modifier = modifier
            .clayFlat(
                shape = ClayShapes.Card,
                background = cardBg,
                outline = cardOutline,
                borderWidth = ClayBorder.Hairline
            )
            .padding(ClaySpacing.Sm),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "$stepNumber. $title",
                modifier = Modifier.weight(1f, fill = false),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = when {
                    isDone -> WeMadeColors.Success
                    isActive -> WeMadeColors.Purple
                    else -> WeMadeColors.OnSurface
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (isDone) {
                IconCheck(Modifier.size(11.dp), color = WeMadeColors.Success)
            } else if (isActive) {
                IconActivity(Modifier.size(11.dp), color = WeMadeColors.Purple)
            }
        }

        // Tahap yang belum dijangkau cukup judulnya saja — belum ada status untuk dilaporkan.
        if (status != null) {
            ClayBadge(
                text = status,
                tint = when {
                    isDone -> WeMadeColors.Success
                    isActive -> WeMadeColors.Purple
                    else -> WeMadeColors.OnSurfaceMuted
                },
                fontSize = 9.sp
            )
        }

        Spacer(Modifier.height(2.dp))

        TimestampLine(label = "Mulai:", value = mulaiText, highlight = false)
        TimestampLine(label = "Selesai:", value = selesaiText, highlight = isDone)
    }
}

@Composable
private fun TimestampLine(label: String, value: String, highlight: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
        Text(text = label, fontSize = 9.sp, color = WeMadeColors.OnSurfaceMuted)
        Text(
            text = value,
            fontSize = 9.sp,
            lineHeight = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (highlight) WeMadeColors.Success else WeMadeColors.OnSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}
