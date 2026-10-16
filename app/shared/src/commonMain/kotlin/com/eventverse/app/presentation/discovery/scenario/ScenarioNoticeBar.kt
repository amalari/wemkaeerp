package com.eventverse.app.presentation.discovery.scenario

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.eventverse.app.presentation.designsystem.ClayBorder
import com.eventverse.app.presentation.designsystem.ClayCard
import com.eventverse.app.presentation.designsystem.ClayOffset
import com.eventverse.app.presentation.designsystem.ClayShapes
import com.eventverse.app.presentation.designsystem.ClaySpacing
import com.eventverse.app.presentation.designsystem.rememberClayTypography
import com.eventverse.app.presentation.theme.WeMadeColors

/**
 * Papan pesan pemutar skenario (temuan 10c): kartu berwarna menurut [ScenarioNoticeTone] supaya alasan penolakan/peringatan
 * terlihat jelas, bukan teks abu-abu kecil. Hanya menggambar; nada ditentukan [scenarioAdvanceNotice].
 */
@Composable
fun ScenarioNoticeBar(notice: ScenarioNotice, modifier: Modifier = Modifier) {
    val typography = rememberClayTypography()
    val (tint, bg, label) = when (notice.tone) {
        ScenarioNoticeTone.BLOCKED -> Triple(WeMadeColors.Error, WeMadeColors.ErrorBg, "Tidak bisa lanjut")
        ScenarioNoticeTone.WARNING -> Triple(WeMadeColors.Warning, WeMadeColors.WarningBg, "Periksa dulu")
        ScenarioNoticeTone.INFO -> Triple(WeMadeColors.Info, WeMadeColors.InfoBg, "Info")
    }
    ClayCard(
        modifier = modifier.fillMaxWidth(),
        shape = ClayShapes.Chip,
        containerColor = bg,
        outlineColor = tint,
        borderWidth = ClayBorder.Medium,
        offset = ClayOffset.Pressed,
        contentPadding = PaddingValues(horizontal = ClaySpacing.Md, vertical = ClaySpacing.Sm)
    ) {
        Text(label, style = typography.labelSmall, fontWeight = FontWeight.Bold, color = tint, maxLines = 1)
        Text(notice.text, style = typography.bodySmall, color = WeMadeColors.OnSurface, maxLines = 4, overflow = TextOverflow.Ellipsis)
    }
}
