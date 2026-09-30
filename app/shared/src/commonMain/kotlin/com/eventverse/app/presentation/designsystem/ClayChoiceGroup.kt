package com.eventverse.app.presentation.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.eventverse.app.presentation.theme.WeMadeColors

/**
 * Sekelompok [ClayChoiceChip] berlabel dengan **pilihan tunggal** — pola yang muncul berulang kali
 * secara identik (label kecil di atas, baris pil yang melipat, satu nilai terpilih) di RBAC, alur
 * tahap sampling, dan Studio Discovery. Diangkat ke design system setelah muncul ketiga kalinya
 * (Kontrak 4: Aturan Tiga Kali).
 *
 * Domain-blind seperti komponen bersama lain: ia menerima `String` apa adanya dan tidak tahu apakah
 * `"QUALITY_CONTROL"` itu modul, widget, atau pack. Nilai dipetakan ke label oleh [labelOf] supaya
 * pemanggil tidak perlu menyalin daftar label di dua tempat.
 *
 * Pemilihan dibedakan lewat isian & kedalaman pil (`ClayChoiceChip`), **bukan** oleh ketebalan
 * outline — ketebalan tetap (Kontrak 8).
 */
@Composable
fun ClayChoiceGroup(
    label: String,
    options: List<String>,
    selected: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    labelOf: (String) -> String = { it },
    hint: String? = null,
    enabled: Boolean = true
) {
    if (options.isEmpty()) return
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(ClaySpacing.Xs)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = WeMadeColors.OnSurfaceMuted
        )
        ClayFlowRow {
            options.forEach { option ->
                ClayChoiceChip(
                    text = labelOf(option),
                    selected = option == selected,
                    enabled = enabled,
                    onClick = { onSelect(option) }
                )
            }
        }
        hint?.let {
            Text(it, style = MaterialTheme.typography.labelSmall, color = WeMadeColors.OnSurfaceMuted)
        }
    }
}
