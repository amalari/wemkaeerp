package com.eventverse.app.presentation.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Sekelompok [ClayChoiceChip] dengan **pilihan ganda** — satu opsi bisa dipilih bersama opsi lain.
 *
 * Diangkat ke design system karena satu kontrol yang sama dipakai di tiga konteks (TRD-FIELD-003
 * FR-9): Form Blok, sel tabel inline, dan dialog detail kanban — ketiganya lewat `FieldInput`.
 * Komponen ini menjaga bentuknya terpusat alih-alih menyalin baris chip di tiap konteks
 * (Kontrak 4: Aturan Tiga Kali).
 *
 * Domain-blind seperti komponen bersama lain (Kontrak 6): ia menerima `List<String>`/`Set<String>`/
 * `Int?` dan lambda, dan tidak tahu apa itu `FieldSpec` atau `MULTI_SELECT`. Penerjemahan nilai sel
 * (JSON array kanonik) tetap di lapisan fitur.
 *
 * Pilihan dibedakan lewat isian & kedalaman pil ([ClayChoiceChip]), **bukan** oleh ketebalan outline
 * — ketebalan tetap (Kontrak 8). Bila [maxSelections] tercapai, opsi yang belum terpilih menjadi tidak
 * dapat ditekan; opsi terpilih tetap bisa dilepas.
 */
@Composable
fun ClayMultiChoiceChips(
    options: List<String>,
    selected: Set<String>,
    onToggle: (String) -> Unit,
    modifier: Modifier = Modifier,
    labelOf: (String) -> String = { it },
    maxSelections: Int? = null,
    enabled: Boolean = true
) {
    if (options.isEmpty()) return
    ClayFlowRow(modifier = modifier, spacing = ClaySpacing.Xs) {
        val atLimit = maxSelections != null && selected.size >= maxSelections
        options.forEach { option ->
            val isSelected = option in selected
            ClayChoiceChip(
                text = labelOf(option),
                selected = isSelected,
                onClick = { onToggle(option) },
                enabled = enabled && (isSelected || !atLimit)
            )
        }
    }
}
