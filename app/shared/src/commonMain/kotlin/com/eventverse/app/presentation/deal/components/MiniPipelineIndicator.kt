package com.eventverse.app.presentation.deal.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eventverse.app.domain.deal.DealStage
import com.eventverse.app.presentation.designsystem.*
import com.eventverse.app.presentation.theme.WeMadeColors

/**
 * Stepper milestone 4 tahap alur pesanan konveksi:
 * Qualify -> Sampling -> Produksi -> Pelunasan
 *
 * Mendukung visualisasi status:
 * - Sukses (Centang Hijau)
 * - Berjalan / Aktif (Panah Kuning/Oranye)
 * - Gugur / Batal / Lost (Silang Merah ✕ pada step terkait)
 * - Belum Dimulai (Lingkaran abu-abu)
 */
@Composable
fun MiniPipelineIndicator(stage: DealStage, modifier: Modifier = Modifier) {
    val isLost = stage == DealStage.LOST
    val isWon = stage == DealStage.WON
    val inProd = stage == DealStage.IN_PRODUCTION || isWon

    val qualifyDone = stage != DealStage.OPEN
    val qualifyActive = stage == DealStage.OPEN
    val samplingDone = inProd || isLost
    val samplingActive = stage == DealStage.PO_RECEIVED
    val prodDone = isWon
    val prodActive = stage == DealStage.IN_PRODUCTION
    val prodFailed = isLost
    val settlementDone = isWon

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Step 1: Qualify
        MilestoneStep(
            iconState = when {
                qualifyDone -> StepIconState.DONE
                qualifyActive -> StepIconState.ACTIVE
                else -> StepIconState.PENDING
            },
            label = "Qualify",
            labelColor = if (qualifyDone || qualifyActive) WeMadeColors.OnSurface else WeMadeColors.OnSurfaceMuted
        )

        // Connector 1 -> 2 (Qualify -> Sampling)
        Box(modifier = Modifier.weight(1f).padding(bottom = 16.dp), contentAlignment = Alignment.Center) {
            val c1Color = if (qualifyDone) WeMadeColors.Success else WeMadeColors.Border
            StepperConnector(color = c1Color, modifier = Modifier.fillMaxWidth())
        }

        // Step 2: Sampling
        MilestoneStep(
            iconState = when {
                samplingDone -> StepIconState.DONE
                samplingActive -> StepIconState.ACTIVE
                else -> StepIconState.PENDING
            },
            label = "Sampling",
            labelColor = if (samplingDone || samplingActive) WeMadeColors.OnSurface else WeMadeColors.OnSurfaceMuted
        )

        // Connector 2 -> 3 (Sampling -> Produksi)
        Box(modifier = Modifier.weight(1f).padding(bottom = 16.dp), contentAlignment = Alignment.Center) {
            val c2Color = when {
                prodFailed -> WeMadeColors.Error
                samplingDone -> WeMadeColors.Success
                else -> WeMadeColors.Border
            }
            StepperConnector(color = c2Color, modifier = Modifier.fillMaxWidth())
        }

        // Step 3: Produksi
        MilestoneStep(
            iconState = when {
                prodFailed -> StepIconState.FAILED
                prodDone -> StepIconState.DONE
                prodActive -> StepIconState.ACTIVE
                else -> StepIconState.PENDING
            },
            label = "Produksi",
            labelColor = when {
                prodFailed -> WeMadeColors.Error
                prodDone || prodActive -> WeMadeColors.OnSurface
                else -> WeMadeColors.OnSurfaceMuted
            }
        )

        // Connector 3 -> 4 (Produksi -> Pelunasan)
        Box(modifier = Modifier.weight(1f).padding(bottom = 16.dp), contentAlignment = Alignment.Center) {
            StepperConnector(
                color = if (settlementDone) WeMadeColors.Success else WeMadeColors.Border,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Step 4: Pelunasan
        MilestoneStep(
            iconState = if (settlementDone) StepIconState.DONE else StepIconState.PENDING,
            label = "Pelunasan",
            labelColor = if (settlementDone) WeMadeColors.OnSurface else WeMadeColors.OnSurfaceMuted
        )
    }
}

private enum class StepIconState { DONE, ACTIVE, FAILED, PENDING }

@Composable
private fun MilestoneStep(
    iconState: StepIconState,
    label: String,
    labelColor: Color
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(48.dp)
    ) {
        when (iconState) {
            StepIconState.DONE -> {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(WeMadeColors.Success)
                        .border(ClayBorder.Medium, WeMadeColors.Outline, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    IconCheck(modifier = Modifier.size(12.dp), color = Color.White)
                }
            }
            StepIconState.ACTIVE -> {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFF59E0B))
                        .border(ClayBorder.Medium, WeMadeColors.Outline, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    IconArrowForward(modifier = Modifier.size(12.dp), color = Color.White)
                }
            }
            StepIconState.FAILED -> {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(WeMadeColors.Error)
                        .border(ClayBorder.Medium, WeMadeColors.Outline, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    IconClose(modifier = Modifier.size(12.dp), color = Color.White)
                }
            }
            StepIconState.PENDING -> {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(WeMadeColors.Surface)
                        .border(ClayBorder.Medium, WeMadeColors.Border, CircleShape)
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            color = labelColor,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}

@Composable
private fun StepperConnector(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.height(24.dp)) {
        val w = size.width
        val midY = size.height * 0.5f
        val stroke = 2.dp.toPx()

        drawLine(
            color = color,
            start = Offset(0f, midY),
            end = Offset(w - 5.dp.toPx(), midY),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )

        val arrowSize = 5.dp.toPx()
        val endX = w - 3.dp.toPx()
        val path = Path().apply {
            moveTo(endX, midY)
            lineTo(endX - arrowSize, midY - arrowSize * 0.7f)
            moveTo(endX, midY)
            lineTo(endX - arrowSize, midY + arrowSize * 0.7f)
        }
        drawPath(path, color = color, style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}
