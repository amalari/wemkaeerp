package com.eventverse.app.presentation.operator

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
import androidx.compose.ui.unit.sp
import com.eventverse.app.domain.sampling.SamplingOrder
import com.eventverse.app.domain.sampling.SamplingPipelineStage
import com.eventverse.app.presentation.designsystem.ClayBorder
import com.eventverse.app.presentation.designsystem.ClayShapes
import com.eventverse.app.presentation.designsystem.ClaySpacing
import com.eventverse.app.presentation.designsystem.clayFlat
import com.eventverse.app.presentation.theme.WeMadeColors

/**
 * Bahan kerja yang dibutuhkan operator di kartu "Sedang Dikerjakan", per meja. Meja tanpa bahan
 * khusus (Cuci, Setrika, QC, Kemas) tidak menampilkan apa-apa — kartunya tetap ringkas.
 */
@Composable
fun OperatorDeskDetails(order: SamplingOrder, stage: SamplingPipelineStage) {
    when (stage) {
        SamplingPipelineStage.MACHINE_KNITTING -> CamProgramReadOnly(order)
        SamplingPipelineStage.LINKING_ASSEMBLY -> LinkingDepositSummary(order)
        else -> Unit
    }
}

/** Instruksi mesin dari lembar Program CAM — dibaca operator rajut, tidak diubah di sini. */
@Composable
private fun CamProgramReadOnly(order: SamplingOrder) {
    val cam = order.stageInputFor(SamplingPipelineStage.CAM_PROGRAMMING)
    DetailBox(title = "PROGRAM CAM") {
        if (cam == null || cam.sections.isEmpty()) {
            Text(text = "Belum ada lembar Program CAM", fontSize = 11.sp, color = WeMadeColors.OnSurfaceMuted)
        } else {
            cam.sections.forEach { section ->
                Text(
                    text = "• ${section.section}: " + section.rows
                        .filter { it.isFilled }
                        .joinToString("; ") { "${it.label} : ${it.value}" },
                    fontSize = 11.sp,
                    color = WeMadeColors.OnSurface
                )
            }
        }
    }
}

/** Progres setoran linking: target, sudah disetor, sisa — plus riwayat setoran per operator. */
@Composable
private fun LinkingDepositSummary(order: SamplingOrder) {
    DetailBox(title = "SETORAN") {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            DepositFigure("Target", "${order.sampleQuantity} Pcs")
            DepositFigure("Disetor", "${order.totalFinishedDepositedQty} Pcs")
            DepositFigure("Sisa", "${order.remainingFinishingQty} Pcs")
        }
        order.finishingDeposits.forEach { dep ->
            Text(
                text = "• ${dep.depositDate} ${dep.operatorName.ifBlank { "Operator" }}: ${dep.qtyPcs} Pcs (${dep.weightKg} Kg)",
                fontSize = 11.sp,
                color = WeMadeColors.OnSurface
            )
        }
    }
}

@Composable
private fun DepositFigure(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, fontSize = 11.sp, color = WeMadeColors.OnSurfaceMuted)
        Text(text = value, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = WeMadeColors.OnSurface)
    }
}

@Composable
private fun DetailBox(title: String, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clayFlat(
                shape = ClayShapes.Chip,
                background = WeMadeColors.SurfaceMuted,
                outline = WeMadeColors.Outline,
                borderWidth = ClayBorder.Medium
            )
            .padding(ClaySpacing.Sm),
        verticalArrangement = Arrangement.spacedBy(ClaySpacing.Xxs)
    ) {
        Text(text = title, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = WeMadeColors.OnSurface)
        content()
    }
}
