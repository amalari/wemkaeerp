package com.eventverse.app.presentation.builder.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eventverse.app.infrastructure.api.BuilderApiClient
import com.eventverse.app.presentation.designsystem.ClayButton
import com.eventverse.app.presentation.designsystem.ClayButtonStyle
import com.eventverse.app.presentation.designsystem.ClayCard
import com.eventverse.app.presentation.designsystem.ClaySpacing
import com.eventverse.app.presentation.designsystem.IconPlus
import com.eventverse.app.presentation.designsystem.rememberClayTypography
import com.eventverse.app.presentation.theme.WeMadeColors
import com.eventverse.app.shared.json.JsonValue

/**
 * Ringkasan deployment aktif **satu baris** untuk empty-state chat (TRD-PLAT-014 FR-2-2) —
 * diturunkan dari data overview lama (`GET /api/builder/overview`), tanpa kontrak domain baru.
 * Murni; `null` = bentuk respons tidak dikenali (pemanggil menampilkan status jujur).
 */
internal fun deploymentSummaryLine(raw: JsonValue?): String? {
    val obj = raw as? JsonValue.Obj ?: return null
    val tenant = obj.obj("tenant") ?: return null
    val status = tenant.string("status") ?: "UNKNOWN"
    val active = obj.obj("activeDeployment") ?: return "Production: belum ada deployment aktif · status tenant $status"
    val number = active.int("number")
    val packCode = active.string("packCode")
    val packVersion = active.int("packVersion")
    return buildString {
        append("Production: deployment aktif")
        number?.let { append(" #$it") }
        if (packCode != null) {
            append(" · pack $packCode")
            packVersion?.let { append(" v$it") }
        }
        append(" · status tenant $status")
    }
}

/**
 * Empty-state Chat Builder (FR-2-2): aksi "Buat Pack Baru" (membuka route `newpack`) + ringkasan
 * deployment aktif satu baris. Tampil hanya saat riwayat chat & draf kerja keduanya kosong —
 * tenant baru yang belum mulai apa pun. Gagal memuat ringkasan tetap jujur, tidak mematikan chat.
 */
@Composable
internal fun BuilderChatEmptyState(
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val typography = rememberClayTypography()
    var summary by remember { mutableStateOf<String?>(null) }
    var failed by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        BuilderApiClient().overview()
            .onSuccess { summary = deploymentSummaryLine(it) }
            .onFailure { failed = true }
    }

    ClayCard(modifier = modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)) {
            Text(
                text = "Mulai dari sini",
                style = typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = WeMadeColors.OnSurface
            )
            Text(
                text = "Ceritakan aplikasi yang ingin dibangun, atau mulai dari template industri.",
                style = typography.bodySmall,
                color = WeMadeColors.OnSurfaceMuted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Md),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ClayButton(
                    text = "Buat Pack Baru",
                    onClick = { onNavigate("newpack") },
                    style = ClayButtonStyle.Primary,
                    leading = { IconPlus(Modifier.size(14.dp), color = Color.White) }
                )
            }
            when {
                failed -> Text(
                    text = "Ringkasan deployment tidak dapat dimuat saat ini.",
                    style = typography.bodySmall,
                    fontSize = 11.sp,
                    color = WeMadeColors.Warning,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                summary != null -> Text(
                    text = summary.orEmpty(),
                    style = typography.bodySmall,
                    fontSize = 11.sp,
                    color = WeMadeColors.OnSurfaceMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
