package com.eventverse.app.presentation.i18n

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import com.eventverse.app.domain.i18n.LanguageCode

val LocalAppLanguage: ProvidableCompositionLocal<LanguageCode> = compositionLocalOf {
    LanguageCode.ID
}

class AppLanguage(
    initialLanguage: LanguageCode
) {
    private var _language: LanguageCode = initialLanguage

    val language: LanguageCode get() = _language

    fun setLanguage(code: LanguageCode) {
        _language = code
        LanguagePreference.save(code)
    }

    companion object {
        private var instance: AppLanguage = AppLanguage(LanguagePreference.load() ?: LanguageCode.ID)

        fun getInstance(): AppLanguage = instance
    }
}

@Composable
fun rememberAppLanguage(): LanguageCode {
    return LocalAppLanguage.current
}
