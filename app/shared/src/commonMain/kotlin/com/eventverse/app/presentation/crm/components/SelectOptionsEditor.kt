package com.eventverse.app.presentation.crm.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eventverse.app.domain.customfield.FieldKey
import com.eventverse.app.domain.prototype.SelectOption
import com.eventverse.app.domain.prototype.SelectOptionId
import com.eventverse.app.presentation.common.colorFromHex
import com.eventverse.app.presentation.common.toColorHex
import com.eventverse.app.presentation.designsystem.ClayBorder
import com.eventverse.app.presentation.designsystem.ClayShapes
import com.eventverse.app.presentation.designsystem.ClaySpacing
import com.eventverse.app.presentation.designsystem.ClayTextField
import com.eventverse.app.presentation.designsystem.IconClose
import com.eventverse.app.presentation.designsystem.clayFlat
import com.eventverse.app.presentation.theme.WeMadeColors

/**
 * Baris draf opsi di editor "Tambah kolom" — belum [SelectOption] karena labelnya boleh kosong
 * sementara (dipakai saat baris baru ditambah). Konversi ke opsi sah terjadi hanya di
 * [buildSelectOptions] saat kolom disimpan.
 */
data class SelectOptionDraft(val label: String, val colorHex: String)

/**
 * Palet warna opsi pilihan tunggal/ganda — **token peran** [WeMadeColors], bukan literal baru
 * (design-system-rules.md Kontrak 1/2). Hex tersimpan diturunkan dari token lewat
 * [com.eventverse.app.presentation.common.toColorHex], jadi swatch yang dilihat admin selalu
 * identik dengan warna yang tersimpan di config.
 */
val SelectOptionPalette: List<Color> = listOf(
    WeMadeColors.Primary,
    WeMadeColors.Accent,
    WeMadeColors.Success,
    WeMadeColors.Warning,
    WeMadeColors.Error,
    WeMadeColors.Purple,
    WeMadeColors.Teal,
    WeMadeColors.Info
)

/** Warna untuk draf baru: token palet pertama yang belum terpakai; habis, berputar dari awal. */
fun nextDraftColor(existing: List<SelectOptionDraft>): SelectOptionDraft {
    val used = existing.map { it.colorHex }.toSet()
    val color = SelectOptionPalette.firstOrNull { it.toColorHex() !in used }
        ?: SelectOptionPalette[existing.size % SelectOptionPalette.size]
    return SelectOptionDraft(label = "", colorHex = color.toColorHex())
}

/** Minimal satu opsi berlabel — syarat render "Tambah" di dialog (opsi tanpa label tidak tersimpan). */
fun hasActiveDraft(options: List<SelectOptionDraft>): Boolean = options.any { it.label.isNotBlank() }

/**
 * Draf → opsi domain [SelectOption]. Baris berlabel kosong dilewati (pola
 * [com.eventverse.app.presentation.designsystem.ClayTagInput]); id di-slug dari label lewat
 * [FieldKey.fromLabel] dan di-dedup dalam satu build, sehingga dua opsi berlabel sama tetap
 * punya id berbeda. [SelectOption] menolak hex tak sah — fail loud, bukan fallback senyap.
 */
fun buildSelectOptions(drafts: List<SelectOptionDraft>): List<SelectOption> {
    val usedIds = mutableSetOf<String>()
    return drafts.filter { it.label.isNotBlank() }.map { draft ->
        val key = FieldKey.fromLabel(draft.label.trim(), usedIds)
        usedIds += key.value
        SelectOption(
            id = SelectOptionId(key.value),
            label = draft.label.trim(),
            colorHex = draft.colorHex
        )
    }
}

/**
 * Editor opsi berwarna untuk field pilihan tunggal ([com.eventverse.app.domain.prototype.FieldType.ENUM])
 * dan pilihan ganda (`MULTI_SELECT`) di dialog "Tambah kolom" — follow-up #1
 * `PLAN-unify-field-vocabulary.md` §3a. Tiap baris: swatch warna (dropdown palet token), label,
 * dan tombol hapus; tombol "+ Tambah pilihan" menambah baris dengan warna palet berikutnya.
 *
 * Feature-local di `crm/components` (bukan `designsystem/`): pemanggilnya satu dialog hari ini —
 * Aturan Tiga Kali; naikkan bersama layar "Kelola pilihan" bila polanya terulang.
 */
@Composable
fun SelectOptionsEditor(
    options: List<SelectOptionDraft>,
    onOptionsChange: (List<SelectOptionDraft>) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(ClaySpacing.Xs)) {
        options.forEachIndexed { index, draft ->
            SelectOptionRow(
                draft = draft,
                onLabelChange = { label ->
                    onOptionsChange(options.toMutableList().also { it[index] = draft.copy(label = label) })
                },
                onColorChange = { colorHex ->
                    onOptionsChange(options.toMutableList().also { it[index] = draft.copy(colorHex = colorHex) })
                },
                onRemove = { onOptionsChange(options.filterIndexed { i, _ -> i != index }) }
            )
        }
        Text(
            text = "+ Tambah pilihan",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = WeMadeColors.Primary,
            modifier = Modifier
                .padding(top = ClaySpacing.Xs)
                .clickable { onOptionsChange(options + nextDraftColor(options)) }
        )
    }
}

@Composable
private fun SelectOptionRow(
    draft: SelectOptionDraft,
    onLabelChange: (String) -> Unit,
    onColorChange: (String) -> Unit,
    onRemove: () -> Unit
) {
    var paletteExpanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Xs)
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clayFlat(
                    shape = ClayShapes.Chip,
                    background = colorFromHex(draft.colorHex),
                    outline = WeMadeColors.Outline,
                    borderWidth = ClayBorder.Medium
                )
                .clickable { paletteExpanded = true }
        ) {
            DropdownMenu(expanded = paletteExpanded, onDismissRequest = { paletteExpanded = false }) {
                SelectOptionPalette.forEach { color ->
                    val hex = color.toColorHex()
                    DropdownMenuItem(
                        text = {
                            Box(
                                modifier = Modifier
                                    .size(18.dp)
                                    .clayFlat(
                                        shape = ClayShapes.Chip,
                                        background = color,
                                        outline = if (hex == draft.colorHex) WeMadeColors.Primary else WeMadeColors.Outline,
                                        borderWidth = ClayBorder.Medium
                                    )
                            )
                        },
                        onClick = {
                            onColorChange(hex)
                            paletteExpanded = false
                        }
                    )
                }
            }
        }

        ClayTextField(
            value = draft.label,
            onValueChange = onLabelChange,
            placeholder = "mis. Satin",
            modifier = Modifier.weight(1f)
        )

        Box(
            modifier = Modifier
                .size(22.dp)
                .clickable(onClick = onRemove),
            contentAlignment = Alignment.Center
        ) {
            IconClose(
                modifier = Modifier.size(12.dp),
                color = WeMadeColors.OnSurfaceMuted
            )
        }
    }
}
