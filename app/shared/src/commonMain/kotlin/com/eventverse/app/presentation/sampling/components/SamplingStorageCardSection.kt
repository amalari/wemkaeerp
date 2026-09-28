package com.eventverse.app.presentation.sampling.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import com.eventverse.app.domain.sampling.ExitStages
import com.eventverse.app.domain.sampling.SamplingOrder
import com.eventverse.app.domain.sampling.storage.DealStorageReadiness
import com.eventverse.app.presentation.designsystem.ClayBadge
import com.eventverse.app.presentation.designsystem.ClayButton
import com.eventverse.app.presentation.designsystem.ClayButtonStyle
import com.eventverse.app.presentation.designsystem.ClaySpacing
import com.eventverse.app.presentation.theme.WeMadeColors

/**
 * Isi kartu SPK yang sedang di penyimpanan: siapa penanggung jawabnya, seberapa lengkap
 * deal-nya, dan tombol rilis kirim. Tombol tetap aktif walau deal belum lengkap — dialog
 * rilis yang menuntut alasan kirim parsial, supaya keputusan itu selalu disengaja.
 */
@Composable
internal fun SamplingStorageCardSection(
    order: SamplingOrder,
    readiness: DealStorageReadiness?,
    onRelease: () -> Unit
) {
    val custodian = order.stageHistory
        .lastOrNull { it.toCode == ExitStages.STORAGE }
        ?.actorEmail
        ?.substringBefore('@')
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(ClaySpacing.Xs)
    ) {
        if (readiness != null && readiness.total > 1) {
            ClayBadge(
                text = "${readiness.ready.size}/${readiness.total} SPK deal tersimpan",
                tint = if (readiness.isComplete) WeMadeColors.Success else WeMadeColors.Warning,
                dot = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (!custodian.isNullOrBlank()) {
            Text(
                text = "Penanggung jawab: $custodian",
                fontSize = 11.sp,
                color = WeMadeColors.OnSurfaceMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        ClayButton(
            text = if (readiness?.isComplete != false) "Rilis Kirim ke Buyer" else "Rilis Kirim (Parsial)",
            style = ClayButtonStyle.Accent,
            modifier = Modifier.fillMaxWidth(),
            onClick = onRelease
        )
    }
}
