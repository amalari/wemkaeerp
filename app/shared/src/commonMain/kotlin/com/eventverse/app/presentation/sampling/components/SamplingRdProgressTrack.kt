package com.eventverse.app.presentation.sampling.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eventverse.app.domain.sampling.RD_STALL_WARNING_DAYS
import com.eventverse.app.domain.sampling.RdStep
import com.eventverse.app.domain.sampling.RdStepState
import com.eventverse.app.domain.sampling.SamplingOrder
import com.eventverse.app.domain.sampling.daysInCurrentStage
import com.eventverse.app.domain.sampling.rdProgress
import com.eventverse.app.domain.stageflow.StageCode
import com.eventverse.app.domain.stageflow.StageDefinition
import com.eventverse.app.presentation.designsystem.ClayBadge
import com.eventverse.app.presentation.designsystem.ClayShapes
import com.eventverse.app.presentation.designsystem.ClaySpacing
import com.eventverse.app.presentation.theme.WeMadeColors
import kotlinx.datetime.Clock

/**
 * Jejak progres SPK di kolom R&D: bar tersegmen (satu segmen per tahap wajib atau proses
 * sisipan) di bawah judul tahap aktif, hitungan langkah, dan lama tertahan. Kolom R&D
 * menggabungkan enam tahap, jadi posisi persisnya dibaca dari sini, bukan dari kolom.
 */
@Composable
fun SamplingRdProgressTrack(order: SamplingOrder) {
    // Tahap yang dilompati rute desain ("Hanya Produksi") tidak dihitung: "4/5", bukan "4/6"
    // dengan satu segmen abu-abu yang tidak akan pernah menyala.
    val steps = order.rdProgress().filterNot { it.state == RdStepState.SKIPPED }
    val activeIndex = steps.indexOfFirst { it.state == RdStepState.ACTIVE }
    val days = order.daysInCurrentStage(Clock.System.now())
    val isStalled = days != null && days >= RD_STALL_WARNING_DAYS

    Column(verticalArrangement = Arrangement.spacedBy(ClaySpacing.Xs)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = steps.getOrNull(activeIndex)?.label.orEmpty(),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = WeMadeColors.OnSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            Spacer(Modifier.width(ClaySpacing.Xs))
            Text(
                text = buildString {
                    if (activeIndex >= 0) append("${activeIndex + 1}/${steps.size}")
                    if (days != null) append(" · $days hari")
                },
                fontSize = 11.sp,
                fontWeight = if (isStalled) FontWeight.Bold else FontWeight.Normal,
                color = if (isStalled) WeMadeColors.Warning else WeMadeColors.OnSurfaceMuted
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Xxs)
        ) {
            steps.forEach { step -> RdSegment(step, Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun RdSegment(step: RdStep, modifier: Modifier) {
    val tint = when {
        step.state == RdStepState.DONE -> WeMadeColors.Success
        step.state == RdStepState.ACTIVE -> WeMadeColors.Primary
        step.isSubcontracted -> WeMadeColors.Accent.copy(alpha = 0.35f)
        else -> WeMadeColors.OnSurfaceDisabled.copy(alpha = 0.45f)
    }
    Box(modifier = modifier.height(6.dp).clip(ClayShapes.Pill).background(tint))
}

/**
 * Chip hitungan per tahap di kepala kolom R&D — klik untuk menyaring kartu ke satu tahap,
 * klik lagi untuk melepas saringan.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SamplingRdStageFilterRow(
    orders: List<SamplingOrder>,
    /** Tahap bermeja operator pada kerangka pabrik — urutan kolom R&D. */
    stages: List<StageDefinition>,
    selected: StageCode?,
    onSelect: (StageCode?) -> Unit
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Xxs),
        verticalArrangement = Arrangement.spacedBy(ClaySpacing.Xxs)
    ) {
        stages.forEach { stage ->
            val count = orders.count { it.stageCode == stage.code }
            val isSelected = selected == stage.code
            ClayBadge(
                // Pemisah wajib: label tenant boleh berisi angka ("QC 2"), dan "QC 2 0" tak terbaca.
                text = "${stage.shortLabel} · $count",
                tint = if (isSelected) WeMadeColors.Primary else WeMadeColors.OnSurfaceMuted,
                fontSize = 10.sp,
                modifier = Modifier
                    .clip(ClayShapes.Pill)
                    .clickable { onSelect(if (isSelected) null else stage.code) }
            )
        }
    }
}
