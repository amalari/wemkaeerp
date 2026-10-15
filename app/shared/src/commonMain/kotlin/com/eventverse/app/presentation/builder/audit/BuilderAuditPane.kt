package com.eventverse.app.presentation.builder.audit

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
 * STUB Track T3 — **digantikan T3 utuh** (PLAN-builder-console-v2-navigation §4). Pane Audit:
 * jejak perubahan per tenant di atas `AuditLogRepository.findByTenant` yang sudah ada + kartu
 * "Postur Security" (TRD-PLAT-014 FR-5). Isi sekarang hanya placeholder jujur supaya menu lima
 * entri v2 kompilasi; T3 mengganti file ini tanpa menyentuh shell.
 */
@Composable
fun BuilderAuditPane(modifier: Modifier = Modifier) {
    val typography = rememberClayTypography()

    ClayCard(modifier = modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)) {
            Text(
                text = "Audit",
                style = typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = WeMadeColors.OnSurface
            )
            Text(
                text = "Jejak perubahan tenant (deploy, rollback, act-as, dan lainnya) akan tampil " +
                    "di sini; sedang dibangun (Track T3).",
                style = typography.bodySmall,
                color = WeMadeColors.OnSurfaceMuted
            )
        }
    }
}
