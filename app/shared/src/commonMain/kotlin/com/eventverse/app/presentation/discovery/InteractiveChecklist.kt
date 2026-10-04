package com.eventverse.app.presentation.discovery

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.eventverse.app.presentation.designsystem.ClayBadge
import com.eventverse.app.presentation.designsystem.ClayCheckbox
import com.eventverse.app.presentation.designsystem.ClaySpacing
import com.eventverse.app.presentation.theme.WeMadeColors

/** Daftar periksa yang bisa dicentang; progres "n dari m selesai" ikut berubah. */
@Composable
fun InteractiveChecklist(state: InteractiveChecklistState, modifier: Modifier = Modifier) {
    val total = state.rows.size
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)) {
        ClayBadge(
            text = "${state.doneCount} dari $total selesai",
            tint = if (state.doneCount == total) WeMadeColors.Success else WeMadeColors.Primary,
            dot = true
        )
        state.rows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ClayCheckbox(checked = state.isDone(row), onCheckedChange = { state.toggle(row) })
                Text(row[state.config.labelField], style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
            }
        }
        state.message?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = WeMadeColors.Defect) }
    }
}
