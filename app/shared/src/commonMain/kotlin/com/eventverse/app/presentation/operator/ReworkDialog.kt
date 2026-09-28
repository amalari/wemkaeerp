package com.eventverse.app.presentation.operator

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.eventverse.app.domain.pipeline.DefectLiability
import com.eventverse.app.domain.sampling.SamplingOrder
import com.eventverse.app.domain.sampling.SamplingPipelineStage
import com.eventverse.app.domain.sampling.reworkTargets
import com.eventverse.app.presentation.designsystem.ClayButton
import com.eventverse.app.presentation.designsystem.ClayButtonStyle
import com.eventverse.app.presentation.designsystem.ClayCard
import com.eventverse.app.presentation.designsystem.ClayChoiceChip
import com.eventverse.app.presentation.designsystem.ClayFlowRow
import com.eventverse.app.presentation.designsystem.ClaySpacing
import com.eventverse.app.presentation.designsystem.ClayTextField
import com.eventverse.app.presentation.theme.WeMadeColors

/**
 * Kirim balik SPK ke meja penyebab cacat (Kontrak 5 — DefectLiability & rework loop).
 *
 * Meja tujuan default = meja tepat sebelumnya, karena itu penyebab paling sering; penanggung
 * default = pabrik. Keduanya tetap wajib dilihat operator sebelum menekan kirim.
 */
@Composable
fun ReworkDialog(
    order: SamplingOrder,
    isSubmitting: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (target: SamplingPipelineStage, reason: String, liability: DefectLiability) -> Unit
) {
    val targets = order.reworkTargets
    var target by remember(order.id) { mutableStateOf(targets.lastOrNull()) }
    var reason by remember(order.id) { mutableStateOf("") }
    var liability by remember(order.id) { mutableStateOf(DefectLiability.FACTORY_WORKMANSHIP) }

    Dialog(onDismissRequest = onDismiss) {
        ClayCard(modifier = Modifier.widthIn(max = 520.dp), contentPadding = PaddingValues(ClaySpacing.Xl)) {
            Column(verticalArrangement = Arrangement.spacedBy(ClaySpacing.Md)) {
                Text(text = "Kirim Rework", style = MaterialTheme.typography.titleLarge, color = WeMadeColors.OnSurface)
                Text(
                    text = "${order.spkNumber.value} • ${order.clientName} • dari ${order.pipelineStage.displayName}",
                    fontSize = 12.sp,
                    color = WeMadeColors.OnSurfaceMuted
                )

                SectionLabel("Kirim ke bagian")
                ClayFlowRow {
                    targets.forEach { stage ->
                        ClayChoiceChip(text = stage.deskLabel, selected = target == stage, onClick = { target = stage })
                    }
                }

                SectionLabel("Alasan / cacat yang ditemukan")
                ClayTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    placeholder = "Mis. jahitan bahu kiri lepas",
                    singleLine = false,
                    modifier = Modifier.fillMaxWidth()
                )

                SectionLabel("Tanggung jawab")
                Column(verticalArrangement = Arrangement.spacedBy(ClaySpacing.Xs)) {
                    DefectLiability.entries.forEach { entry ->
                        ClayChoiceChip(
                            text = entry.displayName,
                            selected = liability == entry,
                            tint = WeMadeColors.Error,
                            onClick = { liability = entry }
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = ClaySpacing.Sm),
                    horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm, Alignment.End)
                ) {
                    ClayButton(text = "Batal", style = ClayButtonStyle.Ghost, onClick = onDismiss)
                    ClayButton(
                        text = "Kirim Rework",
                        style = ClayButtonStyle.Danger,
                        enabled = !isSubmitting && target != null && reason.isNotBlank(),
                        onClick = { target?.let { onConfirm(it, reason.trim(), liability) } }
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text = text, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = WeMadeColors.OnSurface)
}
