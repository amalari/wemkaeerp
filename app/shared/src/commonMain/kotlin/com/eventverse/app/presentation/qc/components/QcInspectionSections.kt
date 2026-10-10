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
    modifier: Modifier = Modifier,
    isCompact: Boolean = false
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
                isCompact = isCompact,
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
    isCompact: Boolean,
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

    val blockModifier = Modifier
        .fillMaxWidth()
        .clayFlat(
            shape = ClayShapes.Tile,
            background = WeMadeColors.SurfaceMuted,
            outline = outline
        )
        .padding(horizontal = ClaySpacing.Lg, vertical = ClaySpacing.Md)

    if (isCompact) {
        QcPomInputBlockCompact(
            modifier = blockModifier,
            field = field,
            actualText = actualText,
            noteText = noteText,
            carriedCm = carriedCm,
            deviation = deviation,
            isWithinTolerance = isWithinTolerance,
            hasNote = hasNote,
            onActualChange = onActualChange,
            onNoteChange = onNoteChange
        )
        return
    }

    Row(
        modifier = blockModifier,
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
                text = "Target ${formatCm(field.targetCm)} cm  ·  ±1.0 cm",
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
            modifier = Modifier.width(POM_ACTUAL_WIDTH)
        )

        ClayTextField(
            value = noteText,
            onValueChange = onNoteChange,
            placeholder = "Catatan titik ini - kosongkan bila tidak ada temuan",
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

/**
 * Titik ukur versi layar sempit: dua baris — keterangan di atas, isian di bawah.
 *
 * Di lebar telepon, kolom label 230dp + kotak cm 96dp sudah menghabiskan seluruh lebar, dan
 * kolom catatan yang `weight(1f)` tersisa nol — yang terlihat di layar sebagai kotak abu-abu
 * kosong. Susunannya dibalik: baris atas murni keterangan (nama titik & targetnya di kiri,
 * status hasil ukur di kanan), baris bawah murni isian (angka cm lalu catatan).
 *
 * Memisahkan "yang dibaca" dari "yang diisi" seperti ini membuat jempol punya satu garis
 * mendatar berisi seluruh kotak yang perlu disentuh, dan mata punya satu garis di atasnya
 * berisi seluruh yang perlu dibaca — alih-alih keduanya berselang-seling tiga baris.
 */
@Composable
private fun QcPomInputBlockCompact(
    modifier: Modifier,
    field: QcPomField,
    actualText: String,
    noteText: String,
    carriedCm: Double?,
    deviation: Double?,
    isWithinTolerance: Boolean?,
    hasNote: Boolean,
    onActualChange: (String) -> Unit,
    onNoteChange: (String) -> Unit
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(ClaySpacing.Md)
    ) {
        // Baris keterangan. Badge status ditaruh di kanan atas, sebaris dengan nama titiknya,
        // karena status itu menerangkan titik ini — bukan langkah tersendiri yang perlu
        // barisnya sendiri di antara nama dan kotak isian.
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Md)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = field.label,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = WeMadeColors.OnSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Target ${formatCm(field.targetCm)} cm  ·  ±1.0 cm",
                    style = MaterialTheme.typography.labelSmall,
                    color = WeMadeColors.OnSurfaceMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            QcDeviationBadge(
                deviation = deviation,
                isWithinTolerance = isWithinTolerance,
                hasNote = hasNote,
                carriedCm = carriedCm
            )
        }

        // Baris isian. Kotak cm tetap selebar [POM_ACTUAL_WIDTH] seperti di layar lebar, jadi
        // seluruh angka tetap sejajar di satu garis vertikal dari titik ukur pertama sampai
        // terakhir — properti yang jadi alasan lebar tetap itu ada sejak awal.
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Md)
        ) {
            ClayTextField(
                value = actualText,
                onValueChange = onActualChange,
                placeholder = if (carriedCm != null) formatCm(carriedCm) else "cm",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                focusColor = if (isWithinTolerance == false) WeMadeColors.Error else WeMadeColors.Primary,
                modifier = Modifier.width(POM_ACTUAL_WIDTH)
            )

            ClayTextField(
                value = noteText,
                onValueChange = onNoteChange,
                // Placeholder panjang terpotong di lebar telepon dan tidak menjelaskan apa pun.
                placeholder = "Catatan temuan",
                focusColor = if (hasNote) WeMadeColors.Error else WeMadeColors.Primary,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/** Lebar kolom label agar seluruh kotak isian cm sejajar dari atas ke bawah. */
private val POM_LABEL_WIDTH = 230.dp

/** Lebar kotak angka cm — sama di kedua layout supaya kolomnya tetap sejajar. */
private val POM_ACTUAL_WIDTH = 96.dp

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
    val value = deviation ?: return "-"
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
