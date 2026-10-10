package com.eventverse.app.presentation.orgchart

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eventverse.app.presentation.designsystem.ClayBorder
import com.eventverse.app.presentation.designsystem.ClayButton
import com.eventverse.app.presentation.designsystem.ClayButtonStyle
import com.eventverse.app.presentation.designsystem.ClayOffset
import com.eventverse.app.presentation.designsystem.ClayShapes
import com.eventverse.app.presentation.designsystem.ClaySpacing
import com.eventverse.app.presentation.designsystem.claySurface
import com.eventverse.app.presentation.designsystem.IconCheck
import com.eventverse.app.presentation.theme.WeMadeColors

/*
 * Umpan balik Org Chart: toast berwarna menurut tingkat keparahan dan dialog konfirmasi pemulihan contoh.
 * Buta domain; teks ASCII/Latin-1 saja.
 */

/** Toast hasil aksi. Sukses hijau; peringatan (mis. 409) amber; galat merah. Ketebalan outline tetap. */
@Composable
internal fun OrgChartToastBanner(message: String?, onDismiss: () -> Unit) {
    AnimatedVisibility(visible = message != null, enter = fadeIn(), exit = fadeOut()) {
        if (message == null) return@AnimatedVisibility
        val severity = OrgChartErrorMessages.severityOf(message)
        val tint = when (severity) {
            OrgChartToastSeverity.SUCCESS -> WeMadeColors.Success
            OrgChartToastSeverity.WARNING -> WeMadeColors.Warning
            OrgChartToastSeverity.ERROR -> WeMadeColors.Error
        }
        val background = when (severity) {
            OrgChartToastSeverity.SUCCESS -> WeMadeColors.SuccessBg
            OrgChartToastSeverity.WARNING -> WeMadeColors.WarningBg
            OrgChartToastSeverity.ERROR -> WeMadeColors.ErrorBg
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = ClaySpacing.Md)
                .claySurface(
                    shape = ClayShapes.Chip,
                    background = background,
                    outline = tint,
                    offset = ClayOffset.Small,
                    borderWidth = ClayBorder.Medium
                )
                .clickable { onDismiss() }
                .padding(horizontal = ClaySpacing.Xl, vertical = ClaySpacing.Lg)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)
            ) {
                if (severity == OrgChartToastSeverity.SUCCESS) {
                    IconCheck(modifier = Modifier.size(16.dp), color = tint)
                }
                Text(text = message, color = tint, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            }
        }
    }
}

/** Dialog konfirmasi generik Org Chart. `destructive` menjadikan tombol konfirmasi merah (Danger). */
@Composable
internal fun OrgChartConfirmDialog(
    isOpen: Boolean,
    title: String,
    message: String,
    confirmLabel: String,
    destructive: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    if (!isOpen) return
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = WeMadeColors.OnSurface
            )
        },
        text = {
            Text(text = message, style = MaterialTheme.typography.bodyMedium, color = WeMadeColors.OnSurface)
        },
        confirmButton = {
            ClayButton(
                onClick = onConfirm,
                text = confirmLabel,
                style = if (destructive) ClayButtonStyle.Danger else ClayButtonStyle.Primary
            )
        },
        dismissButton = {
            ClayButton(onClick = onDismiss, text = "Batal", style = ClayButtonStyle.Ghost)
        },
        containerColor = WeMadeColors.Surface
    )
}

/** Konfirmasi sebelum "Pulihkan Contoh yang Hilang" pada tenant yang sudah berisi (aksi menambah data). */
@Composable
internal fun OrgChartRestoreConfirmDialog(isOpen: Boolean, onConfirm: () -> Unit, onDismiss: () -> Unit) =
    OrgChartConfirmDialog(
        isOpen = isOpen,
        title = "Pulihkan Contoh yang Hilang?",
        message = "Server akan menambahkan divisi dan staf contoh yang belum ada. Data yang sudah Anda " +
            "susun tidak dihapus, tetapi daftar bisa bertambah.",
        confirmLabel = "Ya, Pulihkan",
        destructive = false,
        onConfirm = onConfirm,
        onDismiss = onDismiss
    )
