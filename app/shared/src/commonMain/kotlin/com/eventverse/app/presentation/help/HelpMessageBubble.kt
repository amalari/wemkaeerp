package com.eventverse.app.presentation.help

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eventverse.app.domain.help.HelpAction
import com.eventverse.app.domain.help.usecases.HelpSuggestion
import com.eventverse.app.presentation.designsystem.ClayBorder
import com.eventverse.app.presentation.designsystem.ClayButton
import com.eventverse.app.presentation.designsystem.ClayButtonStyle
import com.eventverse.app.presentation.designsystem.ClayShapes
import com.eventverse.app.presentation.designsystem.ClaySpacing
import com.eventverse.app.presentation.designsystem.clayFlat
import com.eventverse.app.presentation.theme.WeMadeColors

/**
 * Satu gelembung chat. Pesan asisten yang membawa saran menampilkan tombol "Mulai tutorial" dan paling banyak
 * dua alternatif — tombol, bukan tautan, karena menjalankan tutorial bisa memindahkan layar.
 */
@Composable
internal fun HelpMessageBubble(message: HelpChatMessage, onStart: (HelpSuggestion) -> Unit, onAction: (HelpAction) -> Unit = {}) {
    val mine = message.role == HelpChatRole.USER
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
        Column(
            modifier = Modifier
                .widthIn(max = 340.dp)
                .clayFlat(
                    shape = ClayShapes.Tile,
                    background = if (mine) WeMadeColors.PrimaryContainer else WeMadeColors.Surface,
                    outline = if (mine) WeMadeColors.Primary else WeMadeColors.Outline,
                    borderWidth = ClayBorder.Medium
                )
                .padding(ClaySpacing.Lg),
            verticalArrangement = Arrangement.spacedBy(ClaySpacing.Md)
        ) {
            Text(message.text, fontSize = 13.sp, color = WeMadeColors.OnSurface)
            message.action?.let { a ->
                ClayButton(text = actionLabel(a), onClick = { onAction(a) }, style = ClayButtonStyle.Accent, fontSize = 12.sp)
            }
            message.suggestion?.let { s ->
                ClayButton(text = "Mulai tutorial: ${s.title}", onClick = { onStart(s) }, fontSize = 12.sp)
            }
            if (message.alternatives.isNotEmpty()) {
                Text("Panduan lain", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = WeMadeColors.OnSurfaceMuted)
                message.alternatives.forEach { alt ->
                    ClayButton(text = alt.title, onClick = { onStart(alt) }, style = ClayButtonStyle.Secondary, fontSize = 12.sp)
                }
            }
        }
    }
}

private fun actionLabel(action: HelpAction): String = when (action) {
    is HelpAction.PrefillLead -> "Isi form lead dari pesan ini"
}
