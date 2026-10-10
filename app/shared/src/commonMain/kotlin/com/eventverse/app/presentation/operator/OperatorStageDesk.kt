package com.eventverse.app.presentation.operator

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eventverse.app.domain.sampling.OperatorDeskColumn
import com.eventverse.app.domain.sampling.SamplingOrder
import com.eventverse.app.domain.sampling.currentWork
import com.eventverse.app.presentation.designsystem.ClayBorder
import com.eventverse.app.presentation.designsystem.ClayBreakpoints
import com.eventverse.app.presentation.designsystem.ClayButton
import com.eventverse.app.presentation.designsystem.ClayButtonStyle
import com.eventverse.app.presentation.designsystem.ClayKanbanColumn
import com.eventverse.app.presentation.designsystem.ClayShapes
import com.eventverse.app.presentation.designsystem.ClaySpacing
import com.eventverse.app.presentation.designsystem.ClayTextField
import com.eventverse.app.presentation.designsystem.clayFlat
import com.eventverse.app.presentation.theme.WeMadeColors
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone

/**
 * Satu meja operator: kanban tiga kolom Antrian → Sedang Dikerjakan → Selesai (hari ini).
 *
 * Pada layar desktop (≥ 840dp), tiga kolom tampil sejajar berdampingan.
 * Pada layar smartphone (< 840dp), kanban tampil 1 per 1 dengan dukungan swipe geser
 * antar kolom via [HorizontalPager] dan segmented tab switcher di bagian atas.
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
    val finishLabel = board.stage.finishAction(board.frame)?.label
    val canRework = board.stage.hasEarlierDesk(board.frame)
    var detailOrder by remember { mutableStateOf<SamplingOrder?>(null) }
    detailOrder?.let { order ->
        SpkDetailDialog(order = order, stage = board.stage.code, onDismiss = { detailOrder = null })
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val isNarrow = maxWidth < ClayBreakpoints.MasterDetail

        Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(ClaySpacing.Md)) {
            // Header: responsif untuk HP (2 baris jika sempit, 1 baris jika lebar)
            if (isNarrow) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = board.stage.displayName,
                            style = MaterialTheme.typography.titleLarge,
                            color = WeMadeColors.OnSurface,
                            modifier = Modifier.weight(1f)
                        )
                        ClayButton(
                            text = "Riwayat (${board.history.size})",
                            style = ClayButtonStyle.Secondary,
                            onClick = onOpenHistory
                        )
                    }
                    ClayTextField(
                        value = operatorName,
                        onValueChange = onOperatorNameChange,
                        placeholder = "Nama operator",
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            } else {
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
            }

            // Papan Kanban: Di smartphone tampil 1 per 1 dengan swipe, di desktop 3 kolom sejajar
            if (isNarrow) {
                val columns = remember { OperatorDeskColumn.entries }
                val pagerState = rememberPagerState(initialPage = 0) { columns.size }
                val coroutineScope = rememberCoroutineScope()

                // Segmented Tab Switcher Antar Kolom Kanban di HP
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clayFlat(
                            shape = ClayShapes.Chip,
                            background = WeMadeColors.SurfaceMuted,
                            outline = WeMadeColors.Outline,
                            borderWidth = ClayBorder.Hairline
                        )
                        .padding(ClaySpacing.Xs),
                    horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Xs)
                ) {
                    columns.forEachIndexed { index, col ->
                        val count = when (col) {
                            OperatorDeskColumn.QUEUE -> board.queue.size
                            OperatorDeskColumn.IN_PROGRESS -> board.inProgress.size
                            OperatorDeskColumn.DONE -> board.doneToday.size
                        }
                        val isSelected = pagerState.currentPage == index
                        val activeStyle = when (col) {
                            OperatorDeskColumn.QUEUE -> ClayButtonStyle.Primary
                            OperatorDeskColumn.IN_PROGRESS -> ClayButtonStyle.Accent
                            OperatorDeskColumn.DONE -> ClayButtonStyle.Success
                        }

                        ClayButton(
                            text = "${col.displayName} ($count)",
                            style = if (isSelected) activeStyle else ClayButtonStyle.Ghost,
                            fontSize = 11.sp,
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp),
                            onClick = {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(index)
                                }
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    pageSpacing = ClaySpacing.Md
                ) { page ->
                    val col = columns[page]
                    OperatorDeskColumnContent(
                        column = col,
                        board = board,
                        isSubmitting = isSubmitting,
                        timeZone = timeZone,
                        finishLabel = finishLabel,
                        canRework = canRework,
                        onStart = onStart,
                        onRelease = onRelease,
                        onFinish = onFinish,
                        onRework = onRework,
                        onOpenDetail = { detailOrder = it },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Lg)
                ) {
                    OperatorDeskColumn.entries.forEach { col ->
                        OperatorDeskColumnContent(
                            column = col,
                            board = board,
                            isSubmitting = isSubmitting,
                            timeZone = timeZone,
                            finishLabel = finishLabel,
                            canRework = canRework,
                            onStart = onStart,
                            onRelease = onRelease,
                            onFinish = onFinish,
                            onRework = onRework,
                            onOpenDetail = { detailOrder = it },
                            modifier = Modifier.weight(1f).fillMaxHeight()
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OperatorDeskColumnContent(
    column: OperatorDeskColumn,
    board: OperatorDeskBoard,
    isSubmitting: Boolean,
    timeZone: TimeZone,
    finishLabel: String?,
    canRework: Boolean,
    onStart: (SamplingOrder) -> Unit,
    onRelease: (SamplingOrder) -> Unit,
    onFinish: (SamplingOrder) -> Unit,
    onRework: (SamplingOrder) -> Unit,
    onOpenDetail: (SamplingOrder) -> Unit,
    modifier: Modifier = Modifier
) {
    when (column) {
        OperatorDeskColumn.QUEUE -> {
            ClayKanbanColumn(
                title = OperatorDeskColumn.QUEUE.displayName,
                count = board.queue.size,
                tint = WeMadeColors.Primary,
                subtitle = "Menunggu diambil",
                emptyText = "Tidak ada SPK yang menunggu",
                modifier = modifier
            ) {
                items(board.queue, key = { it.id.value }) { order ->
                    OperatorDeskCard(order = order, statusLine = null, onClick = { onOpenDetail(order) }) {
                        ClayButton(text = "Mulai", enabled = !isSubmitting, onClick = { onStart(order) })
                    }
                }
            }
        }
        OperatorDeskColumn.IN_PROGRESS -> {
            ClayKanbanColumn(
                title = OperatorDeskColumn.IN_PROGRESS.displayName,
                count = board.inProgress.size,
                tint = WeMadeColors.Accent,
                subtitle = "Di tangan operator",
                emptyText = "Belum ada yang dikerjakan",
                modifier = modifier
            ) {
                items(board.inProgress, key = { it.id.value }) { order ->
                    val claim = order.currentWork
                    OperatorDeskCard(
                        order = order,
                        onClick = { onOpenDetail(order) },
                        statusLine = claim?.let { "${it.operatorName} · mulai ${formatDeskTime(it.startedAt, timeZone)}" },
                        details = { OperatorDeskDetails(order, board.stage.code) }
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
        }
        OperatorDeskColumn.DONE -> {
            ClayKanbanColumn(
                title = OperatorDeskColumn.DONE.displayName,
                count = board.doneToday.size,
                tint = WeMadeColors.Success,
                subtitle = "Diserahkan hari ini",
                emptyText = "Belum ada yang selesai hari ini",
                modifier = modifier
            ) {
                items(board.doneToday, key = { "${it.order.id.value}-${it.audit.at}" }) { handoff ->
                    OperatorDeskCard(
                        order = handoff.order,
                        onClick = { onOpenDetail(handoff.order) },
                        statusLine = "Ke ${handoff.order.deskLabelOf(handoff.audit.toCode)} · ${handoff.workerLabel}\n" +
                            handoff.timingLine(timeZone)
                    )
                }
            }
        }
    }
}

