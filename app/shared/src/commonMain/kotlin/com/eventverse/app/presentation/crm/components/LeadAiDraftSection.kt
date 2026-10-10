package com.eventverse.app.presentation.crm.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eventverse.app.presentation.crm.leaddraft.LeadDraftUiState
import com.eventverse.app.presentation.designsystem.ClayBorder
import com.eventverse.app.presentation.designsystem.ClayButton
import com.eventverse.app.presentation.designsystem.ClayButtonStyle
import com.eventverse.app.presentation.designsystem.ClayShapes
import com.eventverse.app.presentation.designsystem.ClaySpacing
import com.eventverse.app.presentation.designsystem.ClayTextField
import com.eventverse.app.presentation.designsystem.clayFlat
import com.eventverse.app.presentation.theme.WeMadeColors

/**
 * Kotak "Tempel chat / catatan → Isi dengan AI" di atas form lead (TRD-HELP-002 FR-5).
 * Tidak tampil sama sekali sampai pengaturan termuat; tenant yang belum opt-in melihat ajakan aktivasi
 * hanya bila pemanggil boleh mengaktifkannya (CRM MANAGE).
 */
@Composable
internal fun LeadAiDraftSection(
    state: LeadDraftUiState,
    fieldLabel: (String) -> String,
    onTextChange: (String) -> Unit,
    onExtract: () -> Unit,
    onEnable: () -> Unit,
) {
    val enabled = state.enabled ?: return
    if (!enabled && !state.canManage) return

    Column(
        modifier = Modifier.fillMaxWidth()
            .clayFlat(shape = ClayShapes.Tile, background = WeMadeColors.PrimaryContainer, outline = WeMadeColors.Primary, borderWidth = ClayBorder.Medium)
            .padding(ClaySpacing.Lg),
        verticalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)
    ) {
        Text("Isi dengan AI", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = WeMadeColors.OnSurface)
        if (!enabled) {
            Text(
                "Belum aktif untuk pabrik ini. Saat aktif, nama pelanggan di teks yang ditempel dikirim ke penyedia AI; " +
                    "nomor HP dan email disamarkan dulu.",
                fontSize = 12.sp, color = WeMadeColors.OnSurfaceMuted
            )
            ClayButton(text = "Aktifkan untuk pabrik ini", onClick = onEnable, style = ClayButtonStyle.Secondary, fontSize = 12.sp)
        } else {
            ClayTextField(
                value = state.text,
                onValueChange = onTextChange,
                placeholder = "Tempel chat WhatsApp atau catatan pameran...",
                singleLine = false,
                modifier = Modifier.fillMaxWidth()
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Md)) {
                ClayButton(
                    text = if (state.isExtracting) "Membaca..." else "Isi dengan AI",
                    onClick = onExtract,
                    enabled = state.canExtract,
                    fontSize = 12.sp
                )
                Text("Periksa hasilnya sebelum menyimpan.", fontSize = 11.sp, color = WeMadeColors.OnSurfaceMuted, modifier = Modifier.weight(1f))
            }
            if (state.partial) {
                Text("AI tidak tersedia - hanya nomor, email, dan jumlah pcs yang terisi.", fontSize = 11.sp, color = WeMadeColors.Warning)
            }
            state.issues.forEach { issue ->
                Text("${fieldLabel(issue.field)}: ${issue.message}", fontSize = 11.sp, color = WeMadeColors.Warning, modifier = Modifier.padding(start = 2.dp))
            }
        }
        state.error?.let { Text(it, fontSize = 11.sp, color = WeMadeColors.Error) }
    }
}
