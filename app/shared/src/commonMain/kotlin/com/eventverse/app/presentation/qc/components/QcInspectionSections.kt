package com.eventverse.app.presentation.qc.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.eventverse.app.presentation.designsystem.ClayBadge
import com.eventverse.app.presentation.designsystem.ClayShapes
import com.eventverse.app.presentation.designsystem.ClaySpacing
import com.eventverse.app.presentation.designsystem.ClayTextField
import com.eventverse.app.presentation.designsystem.clayFlat
import com.eventverse.app.presentation.qc.QcInspectionFormState
import com.eventverse.app.presentation.qc.QcPomField
import com.eventverse.app.presentation.theme.WeMadeColors

/**
 * Daftar titik ukur untuk satu pcs. Inilah seluruh isi lembar — tidak ada bagian lain.
 */
@Composable
fun QcPomChecklist(
    form: QcInspectionFormState,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(ClaySpacing.Md)
    ) {
        form.pomFields.forEach { field ->
            QcPomInputRow(
                field = field,
                actualText = form.actualOf(field.key),
                noteText = form.noteOf(field.key),
                carriedCm = form.carriedValueOf(field.key)?.actualCm,
                isRequired = field.key in form.requiredKeys,
                onActualChange = { form.setActual(field.key, it) },
                onNoteChange = { form.setNote(field.key, it) }
            )
        }
    }
}

/**
 * Satu titik ukur: angka dan catatannya menempel jadi satu blok.
 *
 * Catatan ditaruh di barisnya sendiri, bukan dikumpulkan di bawah lembar, karena "bahu kiri
 * bergelombang" hanya berarti kalau jelas ia menempel pada Lebar Bahu. Kolom catatan global
 * memaksa petugas mengetik ulang nama titiknya di dalam kalimat — dan itu yang biasanya
 * terlewat saat sedang buru-buru.
 */
@Composable
private fun QcPomInputRow(
    field: QcPomField,
    actualText: String,
    noteText: String,
    carriedCm: Double?,
    isRequired: Boolean,
    onActualChange: (String) -> Unit,
    onNoteChange: (String) -> Unit
) {
    val actual = actualText.toDoubleOrNull()
    val deviation = actual?.let { it - field.targetCm }
    val isWithinTolerance = deviation?.let { kotlin.math.abs(it) <= 1.0 }
    val hasNote = noteText.isNotBlank()

    val outline = when {
        hasNote || isWithinTolerance == false -> WeMadeColors.Error
        isWithinTolerance == true -> WeMadeColors.Success
        // Titik yang tidak diminta diukur ulang tidak boleh tampak seperti pekerjaan tertinggal.
        !isRequired -> WeMadeColors.Border
        else -> WeMadeColors.Border
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clayFlat(
                shape = ClayShapes.Tile,
                background = WeMadeColors.SurfaceMuted,
                outline = outline
            )
            .padding(horizontal = ClaySpacing.Lg, vertical = ClaySpacing.Md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Md)
    ) {
        // Lebar tetap, bukan weight: 9 kolom angka yang harus diisi berurutan jauh lebih cepat
        // dibaca kalau semuanya sejajar di satu garis vertikal. Label sepanjang apa pun tidak
        // boleh menggeser kolom cm ke kanan.
        Column(modifier = Modifier.width(POM_LABEL_WIDTH)) {
            Text(
                text = field.label,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = WeMadeColors.OnSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "Target ${formatCm(field.targetCm)} cm  •  ±1.0 cm",
                style = MaterialTheme.typography.labelSmall,
                color = WeMadeColors.OnSurfaceMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        ClayTextField(
            value = actualText,
            onValueChange = onActualChange,
            placeholder = if (carriedCm != null) formatCm(carriedCm) else "cm",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            focusColor = if (isWithinTolerance == false) WeMadeColors.Error else WeMadeColors.Primary,
            modifier = Modifier.width(96.dp)
        )

        ClayTextField(
            value = noteText,
            onValueChange = onNoteChange,
            placeholder = "Catatan titik ini — kosongkan bila tidak ada temuan",
            focusColor = if (hasNote) WeMadeColors.Error else WeMadeColors.Primary,
            modifier = Modifier.weight(1f)
        )

        QcDeviationBadge(
            deviation = deviation,
            isWithinTolerance = isWithinTolerance,
            hasNote = hasNote,
            carriedCm = carriedCm
        )
    }
}

/** Lebar kolom label agar seluruh kotak isian cm sejajar dari atas ke bawah. */
private val POM_LABEL_WIDTH = 230.dp

@Composable
private fun QcDeviationBadge(deviation: Double?, isWithinTolerance: Boolean?, hasNote: Boolean, carriedCm: Double? = null) {
    when {
        // Catatan mengalahkan angka: petugas melihat sesuatu yang meteran tidak menangkap.
        hasNote -> ClayBadge(text = "Ada catatan", tint = WeMadeColors.Error, dot = true)
        // Belum diukur bukan "sesuai" — itu keadaan yang justru menahan submit.
        isWithinTolerance == null && carriedCm != null ->
            ClayBadge(text = "Dibawa dari sebelumnya", tint = WeMadeColors.Info)
        isWithinTolerance == null -> ClayBadge(text = "Belum diukur", tint = WeMadeColors.OnSurfaceMuted)
        isWithinTolerance -> ClayBadge(text = "Sesuai ${signed(deviation)}", tint = WeMadeColors.Success)
        else -> ClayBadge(text = "Deviasi ${signed(deviation)}", tint = WeMadeColors.Error, dot = true)
    }
}

private fun signed(deviation: Double?): String {
    val value = deviation ?: return "—"
    val prefix = if (value >= 0) "+" else "-"
    return "$prefix${formatCm(kotlin.math.abs(value))} cm"
}

/** Satu desimal sudah cukup untuk meteran kain; dua desimal hanya menambah lebar kolom. */
internal fun formatCm(value: Double): String {
    val rounded = kotlin.math.round(value * 10.0) / 10.0
    val whole = rounded.toInt()
    val tenth = kotlin.math.round(kotlin.math.abs(rounded - whole) * 10.0).toInt()
    return "$whole.$tenth"
}
