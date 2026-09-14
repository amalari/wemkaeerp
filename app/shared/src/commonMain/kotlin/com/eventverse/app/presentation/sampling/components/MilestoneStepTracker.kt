package com.eventverse.app.presentation.sampling.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eventverse.app.domain.sampling.MilestoneProgress
import com.eventverse.app.domain.sampling.MilestoneStep
import com.eventverse.app.presentation.designsystem.*
import com.eventverse.app.presentation.theme.WeMadeColors

@Composable
fun MilestoneStepTracker(
    milestones: List<MilestoneProgress>,
    onToggleMilestone: (MilestoneStep, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "STATUS ALUR SAMPLING",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = WeMadeColors.OnSurface
            )
            val doneCount = milestones.count { it.isCompleted }
            ClayBadge(
                text = "$doneCount / ${milestones.size} Selesai",
                tint = if (doneCount == milestones.size) WeMadeColors.Success else WeMadeColors.Primary
            )
        }

        milestones.forEach { item ->
            val isDone = item.isCompleted
            val containerColor = if (isDone) WeMadeColors.Primary.copy(alpha = 0.12f) else WeMadeColors.Surface
            val outlineColor = if (isDone) WeMadeColors.Primary else WeMadeColors.Outline

            ClayCard(
                modifier = Modifier.fillMaxWidth(),
                shape = ClayShapes.Tile,
                containerColor = containerColor,
                outlineColor = outlineColor,
                contentPadding = PaddingValues(horizontal = ClaySpacing.Md, vertical = ClaySpacing.Sm),
                onClick = { onToggleMilestone(item.step, !isDone) }
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ClayBadge(
                            text = if (isDone) "SELESAI" else "PENDING",
                            tint = if (isDone) WeMadeColors.Success else WeMadeColors.OnSurfaceMuted,
                            fontSize = 10.sp
                        )
                        Text(
                            text = item.step.name.replace('_', ' '),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isDone) WeMadeColors.Primary else WeMadeColors.OnSurface
                        )
                    }

                    if (item.completedAt != null) {
                        Text(
                            text = item.completedAt.toString(),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = WeMadeColors.OnSurfaceMuted
                        )
                    } else {
                        Text(
                            text = "Ketuk untuk ACC",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Normal,
                            color = WeMadeColors.OnSurfaceMuted
                        )
                    }
                }
            }
        }
    }
}
