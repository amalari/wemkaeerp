package com.eventverse.app.presentation.discovery.fields

import com.eventverse.app.domain.prototype.MultiSelectValues

/**
 * Penyusunan nilai tipe `MULTI_SELECT` (TRD-FIELD-003 Track C, FR-2) sebagai fungsi **murni** common
 * Kotlin, terpisah dari `FieldInput` supaya bisa diuji tanpa Compose.
 *
 * Kontrol chip memanggil [multiSelectToggleValue] setiap kali sebuah opsi di-toggle; hasilnya selalu
 * string JSON array **kanonik** lewat [MultiSelectValues.encode] — urut mengikuti `options`, tanpa
 * duplikat, elemen di luar `options` dibuang, dan tanpa pilihan = `""`. Dengan begitu penyusunan nilai
 * UI identik dengan yang ditegakkan codec/reducer (satu sumber aturan).
 */
fun multiSelectToggleValue(current: String, option: String, options: List<String>): String {
    val selected = MultiSelectValues.parse(current).orEmpty().toSet()
    val next = if (option in selected) selected - option else selected + option
    return MultiSelectValues.encode(next, options)
}
