package com.eventverse.app.presentation.operator

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.eventverse.app.domain.sampling.SamplingOrder
import com.eventverse.app.domain.stageflow.StageCode
import com.eventverse.app.presentation.designsystem.ClayButton
import com.eventverse.app.presentation.designsystem.ClayButtonStyle
import com.eventverse.app.presentation.designsystem.ClayCard
import com.eventverse.app.presentation.designsystem.ClaySpacing
import com.eventverse.app.presentation.theme.WeMadeColors

/**
 * Detail SPK baca-saja saat operator mengetuk kartu — referensi instruksi tanpa harus memulai
 * atau menyelesaikan pekerjaan. Isinya [SpkDetailPanel] yang sama dengan dialog kerja, jadi
 * operator melihat data yang identik di mana pun ia membukanya.
 */
@Composable
fun SpkDetailDialog(
    order: SamplingOrder,
    stage: StageCode,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        ClayCard(
            modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth(0.94f).heightIn(max = 720.dp),
            contentPadding = PaddingValues(ClaySpacing.Xl)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(ClaySpacing.Md)) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "SPK • ${order.spkNumber.value}",
                        style = MaterialTheme.typography.titleLarge,
                        color = WeMadeColors.OnSurface,
                        modifier = Modifier.weight(1f)
                    )
                    ClayButton(text = "Tutup", style = ClayButtonStyle.Secondary, onClick = onDismiss)
                }
                SpkDetailPanel(
                    order = order,
                    stage = stage,
                    modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())
                )
            }
        }
    }
}
