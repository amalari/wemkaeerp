package com.eventverse.app.presentation.builder

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eventverse.app.presentation.designsystem.ClayBorder
import com.eventverse.app.presentation.designsystem.ClayButton
import com.eventverse.app.presentation.designsystem.ClayButtonStyle
import com.eventverse.app.presentation.designsystem.ClayShapes
import com.eventverse.app.presentation.designsystem.ClaySpacing
import com.eventverse.app.presentation.designsystem.clayFlat
import com.eventverse.app.presentation.theme.WeMadeColors

/**
 * Penampil brief developer yang sudah jadi (Markdown dari server) — dipakai Antrian Pembuatan. Berbeda dengan dialog ekspor
 * prototype, ia **tidak menyusun apa pun**: yang tampil persis snapshot beku, dengan opsi menyalin. Buta domain: hanya teks.
 */
@Composable
fun BriefViewerDialog(
    title: String,
    markdown: String?,
    error: String?,
    onDismissRequest: () -> Unit
) {
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = WeMadeColors.OnSurface) },
        text = {
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)) {
                when {
                    error != null -> Text(error, style = MaterialTheme.typography.bodySmall, color = WeMadeColors.Error)
                    markdown == null -> Text("Memuat brief...", style = MaterialTheme.typography.bodyMedium, color = WeMadeColors.Primary)
                    else -> Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 180.dp, max = 420.dp)
                            .clayFlat(
                                shape = ClayShapes.Card,
                                background = WeMadeColors.SurfaceMuted.copy(alpha = 0.5f),
                                outline = WeMadeColors.Outline.copy(alpha = 0.3f),
                                borderWidth = ClayBorder.Hairline
                            )
                            .padding(ClaySpacing.Md)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(text = markdown, fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = WeMadeColors.OnSurface)
                    }
                }
                if (copied) Text("Brief disalin ke clipboard.", style = MaterialTheme.typography.bodySmall, color = WeMadeColors.Success, fontWeight = FontWeight.Bold)
            }
        },
        confirmButton = {
            ClayButton(
                text = if (copied) "Tersalin" else "Salin ke Clipboard",
                style = ClayButtonStyle.Primary,
                enabled = markdown != null,
                onClick = { markdown?.let { clipboard.setText(AnnotatedString(it)); copied = true } }
            )
        },
        dismissButton = { ClayButton(text = "Tutup", style = ClayButtonStyle.Secondary, onClick = onDismissRequest) }
    )
}
