package com.eventverse.app.presentation.tutorial

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.eventverse.app.domain.tutorial.ModuleTutorial
import com.eventverse.app.presentation.designsystem.ClayBadge
import com.eventverse.app.presentation.designsystem.ClayCard
import com.eventverse.app.presentation.designsystem.ClayIconButton
import com.eventverse.app.presentation.designsystem.ClayOffset
import com.eventverse.app.presentation.designsystem.ClaySpacing
import com.eventverse.app.presentation.designsystem.IconClose
import com.eventverse.app.presentation.theme.WeMadeColors

/**
 * Daftar tutorial dari tombol Help (FR-4). [forScreen] = tutorial modul yang sedang dibuka; [others] = sisanya.
 * Keduanya sudah disaring wewenang oleh pemanggil — layar ini tidak memutuskan akses.
 */
@Composable
internal fun TutorialListSheet(
    forScreen: List<ModuleTutorial>,
    others: List<ModuleTutorial>,
    onStart: (ModuleTutorial) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        ClayCard(modifier = Modifier.width(460.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Panduan", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = WeMadeColors.OnSurface, modifier = Modifier.weight(1f))
                ClayIconButton(onClick = onDismiss) { IconClose(Modifier.size(12.dp), color = WeMadeColors.OnSurface) }
            }
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp).verticalScroll(rememberScrollState()).padding(top = ClaySpacing.Lg),
                verticalArrangement = Arrangement.spacedBy(ClaySpacing.Lg)
            ) {
                if (forScreen.isEmpty() && others.isEmpty()) {
                    Text("Belum ada panduan untuk akses Anda.", fontSize = 13.sp, color = WeMadeColors.OnSurfaceMuted)
                }
                TutorialSection("Untuk layar ini", forScreen, onStart)
                TutorialSection(if (forScreen.isEmpty()) "Semua panduan" else "Panduan lain", others, onStart)
            }
        }
    }
}

@Composable
private fun TutorialSection(title: String, tutorials: List<ModuleTutorial>, onStart: (ModuleTutorial) -> Unit) {
    if (tutorials.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(ClaySpacing.Md)) {
        Text(title, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = WeMadeColors.OnSurfaceMuted)
        tutorials.forEach { t ->
            ClayCard(
                modifier = Modifier.fillMaxWidth(),
                offset = ClayOffset.Small,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(ClaySpacing.Lg),
                onClick = { onStart(t) }
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(t.title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = WeMadeColors.OnSurface,
                        maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    androidx.compose.foundation.layout.Spacer(Modifier.width(ClaySpacing.Sm))
                    ClayBadge(text = "${t.steps.size} langkah", tint = WeMadeColors.Primary)
                }
                Text(t.summary, fontSize = 12.sp, color = WeMadeColors.OnSurfaceMuted, modifier = Modifier.padding(top = ClaySpacing.Xs))
            }
        }
    }
}
