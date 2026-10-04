package com.eventverse.app.presentation.discovery

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.eventverse.app.presentation.designsystem.ClayFlowRow
import com.eventverse.app.presentation.designsystem.ClayShapes
import com.eventverse.app.presentation.designsystem.ClaySpacing
import com.eventverse.app.presentation.theme.WeMadeColors

/** Ubin dasbor; nilainya dihitung dari layar sumber, jadi bergerak saat data sumber berubah. */
@Composable
fun InteractiveDashboard(state: InteractiveDashboardState, modifier: Modifier = Modifier) {
    ClayFlowRow(modifier = modifier.fillMaxWidth()) {
        state.tiles().forEach { (label, value) ->
            Column(modifier = Modifier.background(WeMadeColors.PrimaryContainer, ClayShapes.Tile).padding(ClaySpacing.Md)) {
                Text(label, style = MaterialTheme.typography.labelSmall, color = WeMadeColors.OnSurfaceMuted)
                Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            }
        }
    }
}
