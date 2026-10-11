package com.eventverse.app.presentation.common

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

private val COLOR_HEX_REGEX = Regex("^#[0-9A-Fa-f]{6}$")

/**
 * Konversi dua arah antara `Color` Compose dan hex domain `#RRGGBB`
 * (mis. `SelectOption.colorHex`, `PhaseDefinition.colorHex`).
 *
 * Warna domain adalah **data tenant** — pengecualian sah aturan "tanpa literal warna di luar
 * WeMadeTheme.kt" (design-system-rules.md Kontrak 1); helper ini hanya menerjemahkan bentuknya,
 * bukan memutuskan warna.
 */

/** `#RRGGBB` → [Color]. Bentuk tak valid melempar [IllegalArgumentException] — fail loud, bukan fallback senyap. */
fun colorFromHex(hex: String): Color {
    require(COLOR_HEX_REGEX.matches(hex)) { "Color hex must be #RRGGBB: $hex" }
    val rgb = hex.removePrefix("#").toLong(16)
    return Color((0xFF000000L or rgb).toInt())
}

/** [Color] → `#RRGGBB` huruf besar; alpha dibuang karena domain menyimpan enam digit tanpa alpha. */
fun Color.toColorHex(): String {
    val rgb = toArgb() and 0x00FFFFFF
    return "#" + rgb.toString(16).padStart(6, '0').uppercase()
}
