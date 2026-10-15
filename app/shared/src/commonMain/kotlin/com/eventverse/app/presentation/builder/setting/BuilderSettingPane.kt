package com.eventverse.app.presentation.builder.setting

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
 * STUB Track T3 — **digantikan T3 utuh** (PLAN-builder-console-v2-navigation §4). Pane Setting empat
 * tab: Identitas Bisnis, Pack & Tema, Tagihan (Billing lama pindah ke sini), Kolaborator
 * (TRD-PLAT-014 FR-6). [initialTab] dibawa shell dari sub-segmen route (`setting/billing` hasil
 * redirect `/builder/billing`); T3 memakainya untuk membuka tab yang tepat. Isi sekarang hanya
 * placeholder jujur supaya menu lima entri v2 kompilasi.
 */
@Composable
fun BuilderSettingPane(
    modifier: Modifier = Modifier,
    initialTab: String? = null
) {
    val typography = rememberClayTypography()

    ClayCard(modifier = modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)) {
            Text(
                text = "Pengaturan",
                style = typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = WeMadeColors.OnSurface
            )
            Text(
                text = "Pengaturan Builder (identitas, pack & tema, tagihan, kolaborator) sedang " +
                    "dipindah ke pane bertab (Track T3)" +
                    (initialTab?.let { " · tab awal: $it." } ?: "."),
                style = typography.bodySmall,
                color = WeMadeColors.OnSurfaceMuted
            )
        }
    }
}
