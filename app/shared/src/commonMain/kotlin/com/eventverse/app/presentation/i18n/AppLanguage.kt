package com.eventverse.app.presentation.i18n

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import com.eventverse.app.domain.i18n.LanguageCode

val LocalAppLanguage: ProvidableCompositionLocal<LanguageCode> = compositionLocalOf {
    LanguageCode.ID
}

class LanguageManager private constructor(
    initialLanguage: LanguageCode
) {
    private var _language: LanguageCode = initialLanguage
    val language: LanguageCode get() = _language

    fun setLanguage(code: LanguageCode) {
        _language = code
        onLanguageChanged?.invoke(code)
    }

    var onLanguageChanged: ((LanguageCode) -> Unit)? = null

    companion object {
        @Volatile
        private var instance: LanguageManager? = null

        fun getInstance(): LanguageManager {
            return instance ?: synchronized(this) {
                instance ?: LanguageManager(LanguageCode.ID).also { instance = it }
            }
        }

        fun initialize(initialLanguage: LanguageCode) {
            instance = LanguageManager(initialLanguage)
        }
    }
}

@Composable
fun rememberAppLanguage(): LanguageCode {
    return LocalAppLanguage.current
}
