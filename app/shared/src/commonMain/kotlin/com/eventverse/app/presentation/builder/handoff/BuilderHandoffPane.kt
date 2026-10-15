package com.eventverse.app.presentation.builder.handoff

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.eventverse.app.presentation.designsystem.ClayCard
import com.eventverse.app.presentation.designsystem.ClaySpacing
import com.eventverse.app.presentation.designsystem.rememberClayTypography
import com.eventverse.app.presentation.theme.WeMadeColors

/**
 * STUB Track T3 — **digantikan T3 utuh** (PLAN-builder-console-v2-navigation §4). Pane Handoff sisi
 * owner: daftar `BuildRequest` milik tenant sendiri (`findByTenant`), brief beku, status & quote/ETA
 * (TRD-PLAT-014 FR-4). Isi sekarang hanya placeholder jujur supaya menu lima entri v2 kompilasi;
 * T3 mengganti file ini tanpa menyentuh shell.
 */
@Composable
fun BuilderHandoffPane(modifier: Modifier = Modifier) {
    val typography = rememberClayTypography()

    ClayCard(modifier = modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)) {
            Text(
                text = "Handoff",
                style = typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = WeMadeColors.OnSurface
            )
            Text(
                text = "Pane serah-terima ke developer sedang dibangun (Track T3). " +
                    "Antrean pembuatan milik tenant ini akan tampil di sini.",
                style = typography.bodySmall,
                color = WeMadeColors.OnSurfaceMuted
            )
        }
    }
}
