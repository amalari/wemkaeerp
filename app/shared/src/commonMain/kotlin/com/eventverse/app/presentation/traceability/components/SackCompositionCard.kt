package com.eventverse.app.presentation.traceability.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.eventverse.app.domain.traceability.PanelRequirement
import com.eventverse.app.domain.traceability.TraceCodec
import com.eventverse.app.domain.traceability.TraceContainer
import com.eventverse.app.presentation.designsystem.*
import com.eventverse.app.presentation.theme.WeMadeColors

/**
 * Penutupan karung: memilih bundel induk, mengisi jumlah dan timbangan.
 *
 * Selisihnya ditampilkan **sebelum** karung ditutup, bukan sesudah. Angka susut yang baru muncul di
 * laporan keesokan hari tidak bisa ditindaklanjuti siapa pun; angka yang muncul saat karung masih
 * terbuka di depan operator masih bisa dijelaskan — atau dihitung ulang.
 */
@Composable
fun SackCompositionCard(
    sizeLabel: String,
    bundles: List<TraceContainer>,
    selectedCodes: Set<String>,
    requirements: List<PanelRequirement>,
    selectedSets: Int,
    declaredPcs: String,
    weightKg: String,
    previewShrinkage: Int,
    isBusy: Boolean,
    onToggleBundle: (String) -> Unit,
    onDeclaredPcsChange: (String) -> Unit,
    onWeightChange: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    ClayCard(modifier = modifier.fillMaxWidth()) {
        Text("Isi Karung - Size $sizeLabel", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(ClaySpacing.Xs))
        Text(
            "Hanya bundel size $sizeLabel yang sudah dihitung dan belum dituang ke karung lain.",
            color = WeMadeColors.OnSurfaceMuted
        )
        Spacer(Modifier.height(ClaySpacing.Md))

        if (bundles.isEmpty()) {
            Text(
                "Belum ada bundel siap tuang untuk size ini.",
                color = WeMadeColors.Warning
            )
        }

        bundles.forEach { bundle ->
            val isSelected = bundle.code.value in selectedCodes
            ClayActionSurface(
                onClick = { onToggleBundle(bundle.code.value) },
                selected = isSelected,
                modifier = Modifier.fillMaxWidth().padding(vertical = ClaySpacing.Xxs)
            ) {
                Column(modifier = Modifier.weight(1f, fill = false)) {
                    Text(TraceCodec.grouped(bundle.code), maxLines = 1)
                    Text(
                        "${bundle.operatorName.ifBlank { "-" }} · ${bundle.shift.value.ifBlank { "-" }}",
                        color = WeMadeColors.OnSurfaceMuted,
                        maxLines = 1
                    )
                }
                Spacer(Modifier.width(ClaySpacing.Sm))
                ClayBadge(
                    text = "${bundle.completeSets(requirements)} set",
                    tint = if (isSelected) WeMadeColors.Primary else WeMadeColors.OnSurfaceMuted
                )
            }
        }

        Spacer(Modifier.height(ClaySpacing.Lg))
        Row(horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Md)) {
            ClayTextField(
                value = declaredPcs,
                onValueChange = onDeclaredPcsChange,
                label = "Isi karung (pcs)",
                modifier = Modifier.weight(1f)
            )
            ClayTextField(
                value = weightKg,
                onValueChange = onWeightChange,
                label = "Timbangan (kg)",
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(ClaySpacing.Lg))
        ShrinkagePreview(selectedSets, declaredPcs, previewShrinkage)

        Spacer(Modifier.height(ClaySpacing.Lg))
        ClayButton(
            text = if (isBusy) "Menutup..." else "Tutup Karung",
            onClick = onClose,
            enabled = !isBusy && selectedCodes.isNotEmpty()
        )
    }
}

@Composable
private fun ShrinkagePreview(selectedSets: Int, declaredPcs: String, shrinkage: Int) {
    val pcs = declaredPcs.trim().toIntOrNull()
    Row(verticalAlignment = Alignment.CenterVertically) {
        ClayBadge(text = "$selectedSets set dipilih", tint = WeMadeColors.Info)
        if (pcs != null && pcs > 0) {
            Spacer(Modifier.width(ClaySpacing.Md))
            ClayBadge(
                text = when {
                    shrinkage > 0 -> "Susut $shrinkage pcs"
                    shrinkage < 0 -> "Lebih ${-shrinkage} pcs"
                    else -> "Angka cocok"
                },
                tint = when {
                    shrinkage == 0 -> WeMadeColors.Success
                    shrinkage > 0 -> WeMadeColors.Warning
                    // Isi melebihi yang tercatat masuk hampir selalu berarti ada bundel yang lupa
                    // di-scan — itu kekeliruan pencatatan, bukan kabar baik.
                    else -> WeMadeColors.Defect
                },
                dot = true
            )
        }
    }
}
