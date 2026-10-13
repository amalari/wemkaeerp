package com.eventverse.app.presentation.i18n

import com.eventverse.app.domain.i18n.LanguageCode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AppLanguage private constructor(
    initialLanguage: LanguageCode
) {
    private val _language = MutableStateFlow(initialLanguage)
    val language: StateFlow<LanguageCode> = _language.asStateFlow()

    fun setLanguage(code: LanguageCode) {
        _language.value = code
    }

    companion object {
        // Tanpa `synchronized` (tidak ada di commonMain: JS/Wasm). Dibuat eager; state bahasa hanya disentuh dari thread UI.
        private var instance: AppLanguage = AppLanguage(LanguageCode.ID)

        fun getInstance(): AppLanguage = instance

        fun initialize(initialLanguage: LanguageCode) {
            instance = AppLanguage(initialLanguage)
        }
    }
}

val LocalAppLanguage = kotlinx.coroutines.flow.combine(
    AppLanguage.getInstance().language,
    AppLanguage.getInstance().language
) { lang, _ -> lang }
