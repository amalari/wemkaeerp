package com.eventverse.app.presentation.discovery.studio

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.eventverse.app.domain.discovery.WidgetKind
import com.eventverse.app.presentation.designsystem.ClayButton
import com.eventverse.app.presentation.designsystem.ClayButtonStyle
import com.eventverse.app.presentation.designsystem.ClayCard
import com.eventverse.app.presentation.designsystem.ClayChoiceChip
import com.eventverse.app.presentation.designsystem.ClayIconButton
import com.eventverse.app.presentation.designsystem.ClayOffset
import com.eventverse.app.presentation.designsystem.ClayShapes
import com.eventverse.app.presentation.designsystem.ClaySpacing
import com.eventverse.app.presentation.designsystem.ClayTextField
import com.eventverse.app.presentation.designsystem.IconTrash
import com.eventverse.app.presentation.theme.WeMadeColors

/**
 * Penyunting baris pola: kolom apa saja yang ada di layar, dan contoh isinya.
 *
 * Kerangka awalnya dipanen dari `WidgetRegistry` (`PrototypePatternUi.skeletonFrom`), jadi penyunting
 * ini hanya mengubah — bukan merancang dari nol. Karena itu tidak ada bentuk baku yang di-hardcode di
 * sini kecuali satu pengecualian yang **memang** semantik: kolom `Lebar` pada `CUSTOM_SCREEN` dipilih
 * lewat pil `penuh`/`separuh`, sebab renderer memasangkan blok berdasarkan nilai itu — salah ketik di
 * sini tidak memecahkan kompilasi, hanya mengubah tata letak pratinjau secara diam-diam.
 */
@Composable
fun PrototypeRowEditor(
    rows: List<PrototypeRowUi>,
    widgetCode: String,
    onChange: (List<PrototypeRowUi>) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(ClaySpacing.Md)) {
        Column(verticalArrangement = Arrangement.spacedBy(ClaySpacing.Xxs)) {
            Text("Baris pola", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(rowHint(widgetCode), style = MaterialTheme.typography.labelSmall, color = WeMadeColors.OnSurfaceMuted)
        }
        if (rows.isEmpty()) {
            Text(
                "Belum ada baris. Ambil kerangka dari modul pack di atas, atau tambah baris manual.",
                style = MaterialTheme.typography.bodySmall,
                color = WeMadeColors.OnSurfaceMuted
            )
        }
        rows.forEachIndexed { index, row ->
            RowCard(
                index = index,
                row = row,
                widgetCode = widgetCode,
                onRowChange = { onChange(rows.withRow(index, it)) },
                onRowDelete = { onChange(rows.withoutRow(index)) }
            )
        }
        ClayButton(
            text = "+ Baris",
            onClick = { onChange(rows + PrototypeRowUi(listOf(PrototypeFieldUi("Kolom", "Contoh isi")))) },
            style = ClayButtonStyle.Secondary
        )
    }
}

@Composable
private fun RowCard(
    index: Int,
    row: PrototypeRowUi,
    widgetCode: String,
    onRowChange: (PrototypeRowUi) -> Unit,
    onRowDelete: () -> Unit
) {
    ClayCard(
        modifier = Modifier.fillMaxWidth(),
        shape = ClayShapes.Tile,
        offset = ClayOffset.Small,
        contentPadding = PaddingValues(ClaySpacing.Lg)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Baris ${index + 1}",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = WeMadeColors.OnSurfaceMuted,
                modifier = Modifier.weight(1f)
            )
            ClayIconButton(onClick = onRowDelete, size = 26.dp) {
                IconTrash(modifier = Modifier.size(11.dp), color = WeMadeColors.Error)
            }
        }
        row.fields.forEachIndexed { fieldIndex, field ->
            FieldEditor(
                label = field.label,
                value = field.value,
                isWidthField = widgetCode == WidgetKind.CUSTOM_SCREEN.code && field.label == WIDTH_FIELD,
                onLabelChange = { onRowChange(row.withField(fieldIndex, field.copy(label = it))) },
                onValueChange = { onRowChange(row.withField(fieldIndex, field.copy(value = it))) },
                onDelete = { onRowChange(row.withoutField(fieldIndex)) }
            )
        }
        ClayButton(
            text = "+ Kolom pada baris ini",
            onClick = { onRowChange(row.copy(fields = row.fields + PrototypeFieldUi("Kolom", "Contoh isi"))) },
            style = ClayButtonStyle.Ghost
        )
    }
}

@Composable
private fun FieldEditor(
    label: String,
    value: String,
    isWidthField: Boolean,
    onLabelChange: (String) -> Unit,
    onValueChange: (String) -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ClayTextField(
            value = label,
            onValueChange = onLabelChange,
            label = "Kolom",
            modifier = Modifier.weight(1f)
        )
        if (isWidthField) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(ClaySpacing.Xxs)) {
                Text("Lebar blok", style = MaterialTheme.typography.labelSmall, color = WeMadeColors.OnSurfaceMuted)
                Row(horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Xs)) {
                    listOf("penuh", "separuh").forEach { option ->
                        ClayChoiceChip(
                            text = option,
                            selected = value == option,
                            onClick = { onValueChange(option) }
                        )
                    }
                }
            }
        } else {
            ClayTextField(
                value = value,
                onValueChange = onValueChange,
                label = "Contoh isi",
                modifier = Modifier.weight(1f)
            )
        }
        ClayIconButton(onClick = onDelete, size = 26.dp) {
            IconTrash(modifier = Modifier.size(11.dp), color = WeMadeColors.Error)
        }
    }
}

/** Nama kolom yang nilainya dibaca renderer untuk memasangkan blok `CUSTOM_SCREEN`. */
private const val WIDTH_FIELD = "Lebar"

private fun rowHint(widgetCode: String): String = when (WidgetKind.fromCode(widgetCode)) {
    WidgetKind.CUSTOM_SCREEN ->
        "Layar kustom menyusun blok: kolom `Blok` = nama blok, kolom `Lebar` = penuh/separuh. " +
            "Dua blok `separuh` berturut-turut dipasangkan berdampingan oleh renderer."
    WidgetKind.KANBAN -> "Satu baris = satu kolom papan; isian pertama adalah nama kolomnya."
    WidgetKind.FORM -> "Satu baris = satu formulir; setiap isian menjadi satu kolom masukan."
    WidgetKind.DASHBOARD -> "Satu baris = satu kartu angka; label jadi judul, isi jadi nilainya."
    WidgetKind.CHECKLIST -> "Satu baris = satu butir periksa."
    WidgetKind.TABLE, WidgetKind.PRINT -> "Satu baris = satu baris tabel; tiap isian jadi pasangan label-isi."
    null -> "Widget di luar kosakata tertutup - pilih salah satu kind di atas."
}

/** Ubah/daftar-buang yang tidak menggeser index tetangga; dipakai penyunting di atas. */
private fun List<PrototypeRowUi>.withRow(index: Int, row: PrototypeRowUi): List<PrototypeRowUi> =
    mapIndexed { i, existing -> if (i == index) row else existing }

private fun List<PrototypeRowUi>.withoutRow(index: Int): List<PrototypeRowUi> =
    filterIndexed { i, _ -> i != index }

private fun PrototypeRowUi.withField(index: Int, field: PrototypeFieldUi): PrototypeRowUi =
    copy(fields = fields.mapIndexed { i, existing -> if (i == index) field else existing })

private fun PrototypeRowUi.withoutField(index: Int): PrototypeRowUi =
    copy(fields = fields.filterIndexed { i, _ -> i != index })
