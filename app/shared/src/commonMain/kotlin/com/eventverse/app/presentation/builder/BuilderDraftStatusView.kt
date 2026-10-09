package com.eventverse.app.presentation.builder

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

/** Tampilan untuk keadaan draf selain [BuilderDraftState.Loaded]: memuat, kosong (tanpa draf), atau galat. */
@Composable
fun BuilderDraftStatusView(state: BuilderDraftState, modifier: Modifier = Modifier) {
    val typography = rememberClayTypography()
    when (state) {
        is BuilderDraftState.Loaded -> Unit
        BuilderDraftState.Loading ->
            Text("Memuat draf...", modifier = modifier, style = typography.bodyMedium, color = WeMadeColors.OnSurfaceMuted)
        BuilderDraftState.Empty -> ClayCard(modifier = modifier.fillMaxWidth(), containerColor = WeMadeColors.SurfaceMuted) {
            Column(verticalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)) {
                Text(
                    "Draf kerja belum tersedia untuk jenis usaha ini.",
                    style = typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = WeMadeColors.OnSurface
                )
                Text(
                    "Draf kerja saat ini hanya dibuat otomatis untuk jenis usaha yang sudah punya blueprint bawaan.",
                    style = typography.bodySmall,
                    color = WeMadeColors.OnSurfaceMuted
                )
            }
        }
        is BuilderDraftState.Failed ->
            Text("Draf kerja gagal dimuat: ${state.message}", modifier = modifier, style = typography.bodyMedium, color = WeMadeColors.Error)
    }
}
