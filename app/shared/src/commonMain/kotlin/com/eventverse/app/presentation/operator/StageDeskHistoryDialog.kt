package com.eventverse.app.presentation.operator

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.eventverse.app.presentation.designsystem.ClayBorder
import com.eventverse.app.presentation.designsystem.ClayButton
import com.eventverse.app.presentation.designsystem.ClayButtonStyle
import com.eventverse.app.presentation.designsystem.ClayCard
import com.eventverse.app.presentation.designsystem.ClayChoiceChip
import com.eventverse.app.presentation.designsystem.ClayShapes
import com.eventverse.app.presentation.designsystem.ClaySpacing
import com.eventverse.app.presentation.designsystem.clayFlat
import com.eventverse.app.presentation.theme.WeMadeColors
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime

private enum class HistoryRange(val label: String, val days: Int?) {
    TODAY("Hari ini", 0),
    WEEK("7 hari", 6),
    ALL("Semua", null)
}

/**
 * Riwayat semua SPK yang pernah diserahkan dari meja ini. Tidak ada tabel riwayat terpisah:
 * ini dibaca langsung dari `stageHistory` setiap SPK — sumber yang sama dengan audit tahap.
 */
@Composable
fun StageDeskHistoryDialog(
    board: OperatorDeskBoard,
    today: LocalDate,
    timeZone: TimeZone,
    onDismiss: () -> Unit
) {
    var range by remember { mutableStateOf(HistoryRange.WEEK) }
    val rows = remember(board.activity, range, today) {
        val days = range.days ?: return@remember board.activity
        val from = today.minus(DatePeriod(days = days))
        board.activity.filter { it.audit.at.toLocalDateTime(timeZone).date >= from }
    }

    Dialog(onDismissRequest = onDismiss) {
        ClayCard(modifier = Modifier.widthIn(max = 640.dp), contentPadding = PaddingValues(ClaySpacing.Xl)) {
            Column(verticalArrangement = Arrangement.spacedBy(ClaySpacing.Md)) {
                Text(
                    text = "Riwayat ${board.stage.displayName}",
                    style = MaterialTheme.typography.titleLarge,
                    color = WeMadeColors.OnSurface
                )
                Row(horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)) {
                    HistoryRange.entries.forEach { entry ->
                        ClayChoiceChip(text = entry.label, selected = range == entry, onClick = { range = entry })
                    }
                }
                if (rows.isEmpty()) {
                    Text(text = "Belum ada kejadian di meja ini pada rentang ini.", fontSize = 12.sp, color = WeMadeColors.OnSurfaceMuted)
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 440.dp),
                        verticalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)
                    ) {
                        items(rows, key = { "${it.order.id.value}-${it.audit.at}" }) { HistoryRow(it, timeZone) }
                    }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    ClayButton(text = "Tutup", style = ClayButtonStyle.Secondary, onClick = onDismiss)
                }
            }
        }
    }
}

@Composable
private fun HistoryRow(handoff: DeskHandoff, timeZone: TimeZone) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clayFlat(
                shape = ClayShapes.Chip,
                background = if (handoff.audit.isRework) WeMadeColors.ErrorBg else WeMadeColors.SurfaceMuted,
                outline = if (handoff.audit.isRework) WeMadeColors.Error else WeMadeColors.Outline,
                borderWidth = ClayBorder.Medium
            )
            .padding(ClaySpacing.Md),
        horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "${handoff.order.spkNumber.value} • ${handoff.order.clientName}",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = WeMadeColors.OnSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${handoff.eventLabel} • ${handoff.workerLabel}",
                fontSize = 11.sp,
                color = WeMadeColors.OnSurfaceMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(text = handoff.timingLine(timeZone), fontSize = 11.sp, color = WeMadeColors.OnSurface)
        }
        Text(
            text = handoff.workMinutes?.let(::formatDeskDuration) ?: "–",
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = WeMadeColors.OnSurface
        )
    }
}
