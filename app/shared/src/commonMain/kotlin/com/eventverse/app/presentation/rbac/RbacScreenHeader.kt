package com.eventverse.app.presentation.rbac

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eventverse.app.presentation.designsystem.ClayButton
import com.eventverse.app.presentation.designsystem.ClayButtonStyle
import com.eventverse.app.presentation.designsystem.ClayFlowRow
import com.eventverse.app.presentation.designsystem.ClayShapes
import com.eventverse.app.presentation.designsystem.ClaySpacing
import com.eventverse.app.presentation.designsystem.ClayTag
import com.eventverse.app.presentation.designsystem.clayFlat
import com.eventverse.app.presentation.theme.WeMadeColors

/**
 * Kepala layar Hak Akses. Disusun dengan [ClayFlowRow] supaya judul dan toolbar membungkus ke baris
 * berikutnya di lebar sempit (Kontrak 13), bukan `Row(SpaceBetween)` yang mengosongkan sisa lebar.
 */
@Composable
internal fun RbacScreenHeader(chips: List<RbacHeaderChip>, onBackToLogin: () -> Unit) {
    ClayFlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Lg),
        spacing = ClaySpacing.Md
    ) {
        Column {
            ClayFlowRow(spacing = ClaySpacing.Sm) {
                Text(
                    text = "Pengaturan Hak Akses & Jabatan Pabrik",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = WeMadeColors.OnSurface
                )
                ClayTag(text = "Dynamic Module RBAC", tint = WeMadeColors.Primary, fontSize = 11.sp)
            }
            Spacer(modifier = Modifier.height(ClaySpacing.Xs))
            Text(
                text = "Kelola struktur peran dan batasan modul konveksi tanpa terminologi teknis yang rumit.",
                style = MaterialTheme.typography.bodyMedium,
                color = WeMadeColors.OnSurfaceMuted
            )
        }

        ClayFlowRow(spacing = ClaySpacing.Md) {
            chips.forEach { HeaderStatChip(label = it.label, value = "${it.value}") }
            ClayButton(
                text = "Ke Halaman Login",
                onClick = onBackToLogin,
                style = ClayButtonStyle.Ghost,
                fontSize = 12.sp,
                maxLines = 1,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 7.dp)
            )
        }
    }
}

@Composable
private fun HeaderStatChip(label: String, value: String) {
    Box(
        modifier = Modifier
            .clayFlat(shape = ClayShapes.Chip, background = WeMadeColors.Surface, outline = WeMadeColors.Border)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(text = value, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = WeMadeColors.PrimaryDark)
            Text(text = label, fontSize = 11.sp, color = WeMadeColors.OnSurfaceMuted, maxLines = 1)
        }
    }
}
