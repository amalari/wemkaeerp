package com.eventverse.app.presentation.traceability.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.eventverse.app.presentation.designsystem.*
import com.eventverse.app.presentation.theme.WeMadeColors
import com.eventverse.app.presentation.traceability.PanelTallyInput

/**
 * Form hitungan bundel di akhir shift.
 *
 * Yang membuat form ini berguna bukan kolom angkanya, melainkan pratinjau set lengkap di bawahnya:
 * "5 depan, 4 belakang, 10 lengan" tampak seperti 19 lembar, padahal hanya sanggup jadi 4 baju.
 * Menampilkan angka itu sambil mengetik memberi operator kesempatan mengoreksi sebelum bundel
 * terlanjur diikat dan ditaruh di meja QC.
 */
@Composable
fun BundleTallySheet(
    tallies: List<PanelTallyInput>,
    completeSets: Int,
    leftover: List<PanelTallyInput>,
    operatorName: String,
    shift: String,
    notes: String,
    isBusy: Boolean,
    onTallyChange: (PanelTallyInput, String) -> Unit,
    onOperatorChange: (String) -> Unit,
    onShiftChange: (String) -> Unit,
    onNotesChange: (String) -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier
) {
    ClayCard(modifier = modifier.fillMaxWidth()) {
        Text("Hitungan Lembar per Panel", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(ClaySpacing.Md))

        tallies.forEach { tally ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = ClaySpacing.Xs),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = tally.label,
                    modifier = Modifier.weight(1f, fill = false),
                    maxLines = 1
                )
                Spacer(Modifier.width(ClaySpacing.Md))
                ClayTextField(
                    value = tally.rawValue,
                    onValueChange = { onTallyChange(tally, it) },
                    placeholder = "0",
                    modifier = Modifier.width(110.dp)
                )
            }
        }

        Spacer(Modifier.height(ClaySpacing.Lg))
        CompleteSetsSummary(completeSets, leftover)

        Spacer(Modifier.height(ClaySpacing.Lg))
        ClayTextField(
            value = operatorName,
            onValueChange = onOperatorChange,
            label = "Operator",
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(ClaySpacing.Md))
        ClayTextField(
            value = shift,
            onValueChange = onShiftChange,
            label = "Shift / tanggal",
            placeholder = "mis. Malam, 19 Sep",
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(ClaySpacing.Md))
        ClayTextField(
            value = notes,
            onValueChange = onNotesChange,
            label = "Catatan",
            singleLine = false,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(ClaySpacing.Lg))
        ClayButton(
            text = if (isBusy) "Menyimpan…" else "Simpan Hitungan Bundel",
            onClick = onSave,
            enabled = !isBusy
        )
    }
}

@Composable
private fun CompleteSetsSummary(completeSets: Int, leftover: List<PanelTallyInput>) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        ClayBadge(
            text = "$completeSets set lengkap",
            tint = if (completeSets > 0) WeMadeColors.Success else WeMadeColors.Warning,
            dot = true
        )
    }
    if (leftover.isNotEmpty()) {
        Spacer(Modifier.height(ClaySpacing.Sm))
        // Sisa bukan cacat — hanya lembar yang belum berpasangan dan menunggu shift berikutnya.
        // Menyebutnya "sisa" alih-alih "kurang" mencegah operator membuangnya.
        Text(
            text = "Sisa menunggu pasangan: " +
                leftover.joinToString(", ") { "${it.panel.displayName} ${it.pieces - completeSets * it.piecesPerGarment}" },
            color = WeMadeColors.OnSurfaceMuted,
            fontWeight = FontWeight.Normal
        )
    }
}
