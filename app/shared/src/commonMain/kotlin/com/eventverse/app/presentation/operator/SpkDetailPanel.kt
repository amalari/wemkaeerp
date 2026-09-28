package com.eventverse.app.presentation.operator

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eventverse.app.domain.sampling.FinishingPath
import com.eventverse.app.domain.sampling.SamplingOrder
import com.eventverse.app.domain.sampling.extractSizeColumns
import com.eventverse.app.domain.sampling.isSizeColumnActive
import com.eventverse.app.domain.stageflow.StageCode
import com.eventverse.app.presentation.designsystem.ClayBorder
import com.eventverse.app.presentation.designsystem.ClayShapes
import com.eventverse.app.presentation.designsystem.ClaySpacing
import com.eventverse.app.presentation.designsystem.ClayTag
import com.eventverse.app.presentation.designsystem.clayFlat
import com.eventverse.app.presentation.theme.WeMadeColors
import kotlinx.datetime.LocalDate

/**
 * Detail SPK lengkap yang ditampilkan di atas setiap dialog kerja meja operator (Lembar Kerja,
 * Setoran, QC) — identitas, tenggat, jalur finishing, dan catatan yang diisi tim sampling saat
 * SPK dibuat. Operator tidak menebak instruksi dari satu baris nomor SPK.
 *
 * Blok teknis paling bawah didelegasikan ke [OperatorDeskDetails] supaya isi per meja (Program
 * CAM di Rajut, progres setoran di Linking) punya satu sumber kebenaran dengan kartu kanban.
 */
@Composable
fun SpkDetailPanel(
    order: SamplingOrder,
    stage: StageCode,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clayFlat(
                shape = ClayShapes.Tile,
                background = WeMadeColors.SurfaceMuted,
                outline = WeMadeColors.Outline,
                borderWidth = ClayBorder.Medium
            )
            .padding(ClaySpacing.Sm),
        verticalArrangement = Arrangement.spacedBy(ClaySpacing.Xxs)
    ) {
        Text(text = "DETAIL SPK", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = WeMadeColors.OnSurface)

        SpkDetailRow("Klien", order.clientName)
        SpkDetailRow("Style", order.styleName)
        val sizeLabel = order.sizeLabel
        val sizeDesc = if (!sizeLabel.isNullOrBlank()) {
            val label = sizeLabel.trim()
            if (label.equals("ALL SIZE", ignoreCase = true)) "All Size" else "Size $label"
        } else {
            order.sizeMode.displayName
        }
        SpkDetailRow("Jumlah", "${order.sampleQuantity} Pcs • $sizeDesc")
        SpkDetailRow("Ukuran", if (!sizeLabel.isNullOrBlank()) sizeLabel else activeSizeLabels(order))
        SpkDetailRow("Deadline Program", formatSpkDate(order.deadlineProgram))
        SpkDetailRow("Deadline Finishing", formatSpkDate(order.deadlineFinishing))
        SpkDetailRow("Deadline Kirim", formatSpkDate(order.deadlineDelivery))

        Column(verticalArrangement = Arrangement.spacedBy(ClaySpacing.Xxs)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)
            ) {
                Text(
                    text = "Finishing",
                    modifier = Modifier.padding(top = 1.dp),
                    fontSize = 11.sp,
                    color = WeMadeColors.OnSurfaceMuted
                )
                ClayTag(
                    text = order.finishingPath.displayName,
                    tint = if (order.finishingPath == FinishingPath.MAKLOON_VENDOR) {
                        WeMadeColors.Accent
                    } else {
                        WeMadeColors.Success
                    }
                )
            }
            if (order.finishingPath == FinishingPath.MAKLOON_VENDOR) {
                val vendor = order.vendorInfo
                SpkDetailRow("Vendor", vendor.vendorName.ifBlank { "Belum ditentukan" })
                if (vendor.vendorPhone.isNotBlank()) SpkDetailRow("Kontak", vendor.vendorPhone)
                SpkDetailRow("Status", vendor.status.displayName)
            }
        }

        if (order.notes.isNotBlank()) SpkDetailRow("Catatan Sampling", order.notes)
        if (order.accNotes.isNotBlank()) SpkDetailRow("Catatan ACC Buyer", order.accNotes)

        OperatorDeskDetails(order = order, stage = stage)
    }
}

/** Kolom ukuran yang benar-benar aktif pada matriks; All Size tetap disebut eksplisit. */
private fun activeSizeLabels(order: SamplingOrder): String {
    val active = extractSizeColumns(order.sizeMatrix).filter { isSizeColumnActive(order.sizeMatrix, it) }
    return if (active.isEmpty()) "Mengikuti size chart bawaan" else active.joinToString(", ")
}

/** "25/09/2026", atau "—" bila tenggat belum ditetapkan tim sampling. */
private fun formatSpkDate(date: LocalDate?): String = date?.let { d ->
    "${d.dayOfMonth.toString().padStart(2, '0')}/${d.monthNumber.toString().padStart(2, '0')}/${d.year}"
} ?: "—"

@Composable
private fun SpkDetailRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            modifier = Modifier.weight(0.32f, fill = false),
            fontSize = 11.sp,
            color = WeMadeColors.OnSurfaceMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = value,
            modifier = Modifier.weight(1f),
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = WeMadeColors.OnSurface
        )
    }
}