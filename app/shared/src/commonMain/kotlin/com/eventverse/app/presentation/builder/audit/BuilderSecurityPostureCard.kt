package com.eventverse.app.presentation.builder.audit

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eventverse.app.presentation.designsystem.ClayBadge
import com.eventverse.app.presentation.designsystem.ClayCard
import com.eventverse.app.presentation.designsystem.ClayShapes
import com.eventverse.app.presentation.designsystem.ClaySpacing
import com.eventverse.app.presentation.designsystem.IconShield
import com.eventverse.app.presentation.designsystem.rememberClayTypography
import com.eventverse.app.presentation.builder.deploymentStatusTint
import com.eventverse.app.presentation.theme.WeMadeColors

/**
 * Kartu "Postur Security" (TRD-PLAT-014 FR-5-3) — menegaskan janji "modul standar security-nya
 * platform" dengan data yang **sudah ada** (PLAN §7.2): status deployment aktif, versi ter-pin,
 * jumlah modul live. Yang belum ada di payload tampil jujur "belum tersedia", bukan angka rekayasa.
 */
@Composable
fun BuilderSecurityPostureCard(posture: SecurityPostureUi?, modifier: Modifier = Modifier) {
    val typography = rememberClayTypography()
    ClayCard(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(36.dp).background(WeMadeColors.SurfaceMuted, ClayShapes.Tile),
                contentAlignment = Alignment.Center
            ) {
                IconShield(Modifier.size(18.dp), color = WeMadeColors.Primary)
            }
            Column(modifier = Modifier.weight(1f, fill = false)) {
                Text(
                    text = "Postur Security",
                    style = typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = WeMadeColors.OnSurface
                )
                Text(
                    text = "Modul standar security-nya platform. Status runtime yang sedang berjalan:",
                    style = typography.bodySmall,
                    fontSize = 11.sp,
                    color = WeMadeColors.OnSurfaceMuted
                )
            }
        }

        Spacer(Modifier.height(ClaySpacing.Md))

        // Postur belum termuat (masih memuat / fetch gagal) ≠ "tidak ada deployment aktif":
        // klaim faktual hanya boleh muncul bila datanya benar-benar ada (keputusan beku PLAN §7.2).
        val activeStatus = posture?.activeDeploymentStatus
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PostureSlot(
                label = "Deployment aktif",
                value = when {
                    posture == null -> "belum tersedia"
                    activeStatus == null -> "Tidak ada deployment aktif"
                    else -> activeStatus
                },
                badgeTint = activeStatus?.uppercase()?.let { deploymentStatusTint(it) },
                modifier = Modifier.weight(1f, fill = false)
            )
            PostureSlot(
                label = "Versi ter-pin",
                value = posture?.pinnedPackVersion?.let { "pack v$it" } ?: "-",
                modifier = Modifier.weight(1f, fill = false)
            )
            PostureSlot(
                label = "Modul live",
                value = posture?.liveModuleCount?.let { "$it modul" } ?: "belum tersedia",
                modifier = Modifier.weight(1f, fill = false)
            )
        }
    }
}

@Composable
private fun PostureSlot(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    badgeTint: Color? = null,
) {
    val typography = rememberClayTypography()
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = label,
            style = typography.bodySmall,
            fontSize = 10.sp,
            color = WeMadeColors.OnSurfaceMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (badgeTint != null) {
            ClayBadge(text = value, tint = badgeTint, dot = true, fontSize = 10.sp)
        } else {
            Text(
                text = value,
                style = typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = WeMadeColors.OnSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
