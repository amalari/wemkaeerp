package com.eventverse.app.presentation.operator

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.eventverse.app.domain.sampling.OperatorDeskColumn
import com.eventverse.app.domain.sampling.SamplingOrder
import com.eventverse.app.domain.sampling.currentWork
import com.eventverse.app.domain.sampling.reworkTargets
import com.eventverse.app.presentation.designsystem.ClayBreakpoints
import com.eventverse.app.presentation.designsystem.ClayButton
import com.eventverse.app.presentation.designsystem.ClayButtonStyle
import com.eventverse.app.presentation.designsystem.ClayKanbanColumn
import com.eventverse.app.presentation.designsystem.ClaySpacing
import com.eventverse.app.presentation.designsystem.ClayTextField
import com.eventverse.app.presentation.theme.WeMadeColors
import kotlinx.datetime.TimeZone

/** Lebar kolom saat layar terlalu sempit untuk tiga kolom sejajar (Kontrak 12: clay makan ruang). */
private val NarrowColumnWidth = 324.dp

/**
 * Satu meja operator: kanban tiga kolom Antrian → Sedang Dikerjakan → Selesai (hari ini).
 *
 * Meja ini tidak tahu cara "menyelesaikan" SPK — [onFinish] diterima dari layar induk yang
 * memetakan [DeskFinishAction] per tahap ke dialog yang tepat.
 */
@Composable
fun OperatorStageDesk(
    board: OperatorDeskBoard,
    operatorName: String,
    onOperatorNameChange: (String) -> Unit,
    isSubmitting: Boolean,
    timeZone: TimeZone,
    onStart: (SamplingOrder) -> Unit,
    onRelease: (SamplingOrder) -> Unit,
    onFinish: (SamplingOrder) -> Unit,
    onRework: (SamplingOrder) -> Unit,
    onOpenHistory: () -> Unit,
    modifier: Modifier = Modifier
) {
    val finishLabel = board.stage.finishAction()?.label
    val canRework = board.stage.reworkTargets.isNotEmpty()

    Column(modifier = modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(ClaySpacing.Md)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = board.stage.displayName,
                style = MaterialTheme.typography.titleLarge,
                color = WeMadeColors.OnSurface,
                modifier = Modifier.weight(1f)
            )
            ClayTextField(
                value = operatorName,
                onValueChange = onOperatorNameChange,
                placeholder = "Nama operator",
                modifier = Modifier.width(220.dp)
            )
            ClayButton(
                text = "Riwayat (${board.history.size})",
                style = ClayButtonStyle.Secondary,
                onClick = onOpenHistory
            )
        }

        BoxWithConstraints(modifier = Modifier.fillMaxWidth().weight(1f)) {
            val isNarrow = maxWidth < ClayBreakpoints.MasterDetail
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (isNarrow) Modifier.horizontalScroll(rememberScrollState()) else Modifier),
                horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Lg)
            ) {
                val columnModifier = if (isNarrow) Modifier.width(NarrowColumnWidth) else Modifier.weight(1f)
                ClayKanbanColumn(
                    title = OperatorDeskColumn.QUEUE.displayName,
                    count = board.queue.size,
                    tint = WeMadeColors.Primary,
                    subtitle = "Menunggu diambil",
                    emptyText = "Tidak ada SPK yang menunggu",
                    modifier = columnModifier.fillMaxHeight()
                ) {
                    items(board.queue, key = { it.id.value }) { order ->
                        OperatorDeskCard(order = order, statusLine = null) {
                            ClayButton(text = "Mulai", enabled = !isSubmitting, onClick = { onStart(order) })
                        }
                    }
                }
                ClayKanbanColumn(
                    title = OperatorDeskColumn.IN_PROGRESS.displayName,
                    count = board.inProgress.size,
                    tint = WeMadeColors.Accent,
                    subtitle = "Di tangan operator",
                    emptyText = "Belum ada yang dikerjakan",
                    modifier = columnModifier.fillMaxHeight()
                ) {
                    items(board.inProgress, key = { it.id.value }) { order ->
                        val claim = order.currentWork
                        OperatorDeskCard(
                            order = order,
                            statusLine = claim?.let { "${it.operatorName} • mulai ${formatDeskTime(it.startedAt, timeZone)}" },
                            details = { OperatorDeskDetails(order, board.stage) }
                        ) {
                            ClayButton(
                                text = "Kembalikan",
                                style = ClayButtonStyle.Ghost,
                                enabled = !isSubmitting,
                                onClick = { onRelease(order) }
                            )
                            if (canRework) {
                                ClayButton(
                                    text = "Rework",
                                    style = ClayButtonStyle.Danger,
                                    enabled = !isSubmitting,
                                    onClick = { onRework(order) }
                                )
                            }
                            if (finishLabel != null) {
                                ClayButton(
                                    text = finishLabel,
                                    style = ClayButtonStyle.Success,
                                    enabled = !isSubmitting,
                                    onClick = { onFinish(order) }
                                )
                            }
                        }
                    }
                }
                ClayKanbanColumn(
                    title = OperatorDeskColumn.DONE.displayName,
                    count = board.doneToday.size,
                    tint = WeMadeColors.Success,
                    subtitle = "Diserahkan hari ini",
                    emptyText = "Belum ada yang selesai hari ini",
                    modifier = columnModifier.fillMaxHeight()
                ) {
                    items(board.doneToday, key = { "${it.order.id.value}-${it.audit.at}" }) { handoff ->
                        OperatorDeskCard(
                            order = handoff.order,
                            statusLine = "Ke ${handoff.audit.toStage.deskLabel} • " +
                                "${formatDeskTime(handoff.audit.at, timeZone)} • ${handoff.audit.actorEmail}"
                        )
                    }
                }
            }
        }
    }
}
