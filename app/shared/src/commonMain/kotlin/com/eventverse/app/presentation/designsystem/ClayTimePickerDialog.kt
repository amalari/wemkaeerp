package com.eventverse.app.presentation.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.eventverse.app.presentation.theme.WeMadeColors

/**
 * Dialog pemilih jam Clay (jam 00-23, menit 00-59) berbentuk stepper.
 *
 * Sengaja bukan `TimePicker` Material: komponen itu berbeda perilaku/bentuk antar target (dial di Android, input di
 * web) dan mewarnai ulang di luar token Clay. Stepper tombol Clay berperilaku identik di 5 target, bisa diketuk
 * tanpa keyboard, dan nilainya selalu sah (melingkar, tak ada teks setengah ketik).
 *
 * Buta domain: hanya `Int`. [onConfirm] menerima jam dan menit sah; [onBack] kembali ke langkah sebelumnya.
 */
@Composable
fun ClayTimePickerDialog(
    initialHour: Int,
    initialMinute: Int,
    title: String = "Pilih Jam",
    onDismiss: () -> Unit,
    onBack: (() -> Unit)? = null,
    onConfirm: (hour: Int, minute: Int) -> Unit
) {
    var hour by remember { mutableStateOf(initialHour.coerceIn(0, HOURS_PER_DAY - 1)) }
    var minute by remember { mutableStateOf(initialMinute.coerceIn(0, MINUTES_PER_HOUR - 1)) }

    Dialog(onDismissRequest = onDismiss) {
        ClayCard(
            modifier = Modifier.fillMaxWidth(0.95f).widthIn(max = 360.dp),
            shape = ClayShapes.Panel,
            contentPadding = PaddingValues(ClaySpacing.Lg)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(ClaySpacing.Md)
            ) {
                Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = WeMadeColors.OnSurface)
                Text(
                    text = formatClock(hour, minute),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = WeMadeColors.Primary
                )
                StepperRow("Jam", listOf("-" to -1, "+" to 1)) { hour = stepCyclic(hour, it, HOURS_PER_DAY) }
                StepperRow("Menit", listOf("-5" to -5, "-" to -1, "+" to 1, "+5" to 5)) {
                    minute = stepCyclic(minute, it, MINUTES_PER_HOUR)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ClayButton(
                        text = if (onBack != null) "Kembali" else "Batal",
                        style = ClayButtonStyle.Ghost,
                        onClick = { if (onBack != null) onBack() else onDismiss() }
                    )
                    ClayButton(text = "Pilih", style = ClayButtonStyle.Primary, onClick = { onConfirm(hour, minute) })
                }
            }
        }
    }
}

/** Satu baris label + deretan tombol langkah; [onStep] menerima selisih. */
@Composable
private fun StepperRow(label: String, steps: List<Pair<String, Int>>, onStep: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = WeMadeColors.OnSurfaceMuted)
        Row(horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Xs)) {
            steps.forEach { (text, delta) ->
                ClayButton(text = text, style = ClayButtonStyle.Secondary, onClick = { onStep(delta) })
            }
        }
    }
}
