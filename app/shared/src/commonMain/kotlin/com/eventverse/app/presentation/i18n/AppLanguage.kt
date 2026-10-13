package com.eventverse.app.presentation.i18n

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import com.eventverse.app.domain.i18n.LanguageCode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Bahasa aktif aplikasi, **satu sumber reaktif** ([language] adalah `StateFlow`). `App()` menyalurkannya ke
 * [LocalAppLanguage]; komposabel di luar `App()` boleh membaca [rememberActiveLanguage].
 */
class AppLanguage private constructor(
    initialLanguage: LanguageCode
) {
    private val _language = MutableStateFlow(initialLanguage)
    val language: StateFlow<LanguageCode> = _language.asStateFlow()

    /** Mengganti bahasa aktif dan menyimpannya di perangkat. */
    fun setLanguage(code: LanguageCode) {
        _language.value = code
        LanguagePreference.save(code)
    }

    companion object {
        // Tanpa `synchronized`/`@Volatile` (tidak ada di commonMain: JS/Wasm). Dibuat eager; hanya disentuh dari thread UI.
        private var instance: AppLanguage = AppLanguage(LanguagePreference.load() ?: LanguageCode.ID)

        fun getInstance(): AppLanguage = instance

        fun initialize(initialLanguage: LanguageCode) {
            instance = AppLanguage(initialLanguage)
        }
    }
}

/** Bahasa aktif lewat composition; disediakan `App()` dari [AppLanguage.language]. Bawaan `id`. */
val LocalAppLanguage: ProvidableCompositionLocal<LanguageCode> = compositionLocalOf { LanguageCode.ID }

@Composable
fun rememberAppLanguage(): LanguageCode = LocalAppLanguage.current
