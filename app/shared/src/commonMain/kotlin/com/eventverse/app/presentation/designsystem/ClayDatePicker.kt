package com.eventverse.app.presentation.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Pemilih tanggal Clay. Buta domain: menerima dan mengembalikan **string** `TTTT-BB-HH` (ISO, mis. `2026-10-08`),
 * atau string kosong bila belum dipilih. Pemanggil yang menafsirkan nilainya, bukan komponen ini.
 *
 * **Tanda tangan ini dibekukan oleh A0 Irisan 1** (`PLAN-field-component-gaps.md` §2): Track B mengisi badan dan
 * perilakunya, Track C dan pemakai lain hanya bergantung pada tanda tangan ini. Mengubahnya = minta Track B,
 * jangan ditambal di pemakai.
 *
 * Kontrak nilai:
 * - [onValueChange] hanya dipanggil dengan string kosong atau tanggal kalender yang sah — bukan teks setengah ketik.
 * - [value] yang bukan tanggal sah ditampilkan apa adanya dan ditandai galat, tidak diam-diam dikosongkan.
 *
 * **A0: badan sementara** — mendelegasikan ke [ClayTextField] sehingga perilakunya sama dengan kolom teks
 * `TTTT-BB-HH` yang dipakai sekarang. Ini *belum* date picker; Track B menggantinya.
 */
@Composable
fun ClayDatePicker(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isError: Boolean = false
) {
    ClayTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        label = label,
        placeholder = "TTTT-BB-HH",
        enabled = enabled,
        isError = isError
    )
}
