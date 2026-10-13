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
        private var instance: AppLanguage? = null

        fun getInstance(): AppLanguage {
            return instance ?: synchronized(this) {
                instance ?: AppLanguage(LanguageCode.ID).also { instance = it }
            }
        }

        fun initialize(initialLanguage: LanguageCode) {
            instance = AppLanguage(initialLanguage)
        }
    }
}

val LocalAppLanguage = kotlinx.coroutines.flow.combine(
    AppLanguage.getInstance().language,
    AppLanguage.getInstance().language
) { lang, _ -> lang }
