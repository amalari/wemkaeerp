package com.eventverse.app.presentation.rbac.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eventverse.app.domain.sampling.SamplingPipelineStage
import com.eventverse.app.domain.sampling.isOperatorDesk
import com.eventverse.app.presentation.designsystem.ClayChoiceChip
import com.eventverse.app.presentation.designsystem.ClayFlowRow
import com.eventverse.app.presentation.designsystem.ClayTag
import com.eventverse.app.presentation.theme.WeMadeColors
import com.eventverse.app.presentation.operator.deskLabel

/**
 * Pemilih meja lantai produksi (Rajut sampai Kemas) pada penugasan modul `OPERATOR_EXEC`.
 *
 * `selected == null` berarti tanpa batasan — seluruh meja; itulah kondisi penugasan lama, jadi
 * chip "Semua Meja" adalah keadaan awal penugasan yang belum pernah disetel. Mematikan chip
 * terakhir sama artinya dengan membuka semuanya, sehingga penugasan tidak pernah bisa mengunci
 * divisi keluar dari seluruh layar tanpa sisa pintu kembali.
 *
 * Komponen fitur, bukan design system: ia tahu domain meja dan menerjemahkannya menjadi chip
 * netral berisi teks.
 */
@Composable
fun OperatorDeskAccessPicker(
    selected: Set<String>?,
    onSelectionChange: (Set<String>?) -> Unit
) {
    val allDesks = SamplingPipelineStage.entries.filter { it.isOperatorDesk }
    val allCodes = allDesks.map { it.name }.toSet()

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Meja Lantai Produksi",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = WeMadeColors.OnSurface
            )
            ClayTag(
                text = if (selected == null) "Semua meja" else "${selected.size} meja",
                tint = WeMadeColors.Info,
                fontSize = 10.sp
            )
        }
        Spacer(modifier = Modifier.height(5.dp))

        ClayFlowRow {
            ClayChoiceChip(
                text = "Semua Meja",
                selected = selected == null,
                onClick = { onSelectionChange(null) }
            )
            allDesks.forEach { stage ->
                val base = selected ?: allCodes
                ClayChoiceChip(
                    text = stage.deskLabel,
                    selected = stage.name in base,
                    onClick = {
                        val next = if (stage.name in base) base - stage.name else base + stage.name
                        onSelectionChange(next.takeIf { it.isNotEmpty() && it != allCodes })
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Meja yang tidak dipilih tidak muncul bagi divisi ini di layar Lantai Produksi.",
            fontSize = 10.5.sp,
            color = WeMadeColors.OnSurfaceMuted
        )
    }
}