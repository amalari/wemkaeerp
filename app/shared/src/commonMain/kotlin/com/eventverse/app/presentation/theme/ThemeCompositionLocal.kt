package com.eventverse.app.presentation.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import com.eventverse.app.domain.pack.PackTheme

/**
 * Tema visual pack yang aktif (TRD-PLAT-013 FR-T5-2), disalurkan ke subtree yang butuh.
 *
 * Nilainya berasal dari `DomainPack.theme`, yang **nullable** untuk pack lama. Local ini selalu
 * menyajikan [PackTheme] non-null: `null` dari pack di-fallback-kan ke `PackTheme()` (CLAY) oleh
 * [ProvidePackTheme], sehingga pemakai bisa membacanya tanpa null-check dan pack lama tetap jalan.
 */
val LocalPackTheme = compositionLocalOf { PackTheme() }

/**
 * Menyediakan [theme] ke subtree [content]. `null` (pack tanpa konfigurasi tema) di-resolve ke
 * `PackTheme()` bawaan — CLAY — sesuai FR-T1-3 dan jaminan "null theme tidak crash".
 */
@Composable
fun ProvidePackTheme(theme: PackTheme?, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalPackTheme provides (theme ?: PackTheme()), content = content)
}
