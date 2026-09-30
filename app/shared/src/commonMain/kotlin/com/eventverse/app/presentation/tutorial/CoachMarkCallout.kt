package com.eventverse.app.presentation.tutorial

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eventverse.app.presentation.designsystem.ClayBadge
import com.eventverse.app.presentation.designsystem.ClayButton
import com.eventverse.app.presentation.designsystem.ClayButtonStyle
import com.eventverse.app.presentation.designsystem.ClayCard
import com.eventverse.app.presentation.designsystem.ClaySpacing
import com.eventverse.app.presentation.theme.WeMadeColors

/** Kartu penjelasan satu langkah coach mark: progres, judul, isi, dan kendali maju-mundur. */
@Composable
internal fun CoachMarkCallout(
    run: TutorialRun,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ClayCard(modifier = modifier.width(340.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(ClaySpacing.Xl)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = run.tutorial.title,
                fontSize = 12.sp,
                color = WeMadeColors.OnSurfaceMuted,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            Spacer(Modifier.width(ClaySpacing.Sm))
            ClayBadge(text = "${run.stepIndex + 1}/${run.tutorial.steps.size}", tint = WeMadeColors.Primary)
        }
        Text(
            text = run.step.title,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = WeMadeColors.OnSurface,
            modifier = Modifier.padding(top = ClaySpacing.Md)
        )
        Text(
            text = run.step.body,
            fontSize = 13.sp,
            color = WeMadeColors.OnSurface,
            modifier = Modifier.padding(top = ClaySpacing.Xs)
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = ClaySpacing.Xl),
            horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ClayButton(text = "Tutup", onClick = onClose, style = ClayButtonStyle.Ghost, fontSize = 12.sp)
            Spacer(Modifier.weight(1f))
            if (!run.isFirst) ClayButton(text = "Kembali", onClick = onBack, style = ClayButtonStyle.Secondary, fontSize = 12.sp)
            ClayButton(text = if (run.isLast) "Selesai" else "Lanjut", onClick = onNext, fontSize = 12.sp)
        }
    }
}
