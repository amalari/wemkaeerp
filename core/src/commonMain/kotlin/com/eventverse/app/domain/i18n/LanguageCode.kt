package com.eventverse.app.domain.i18n

import kotlin.jvm.JvmInline

@JvmInline
value class LanguageCode(val code: String) {
    init {
        require(code.equals("id", ignoreCase = true) || code.equals("en", ignoreCase = true)) {
            "LanguageCode '$code' is not supported. Supported: id, en"
        }
    }

    companion object {
        val ID = LanguageCode("id")
        val EN = LanguageCode("en")

        private val SUPPORTED = setOf("id", "en")

        fun parse(value: String?): LanguageCode? {
            if (value == null) return null
            val normalized = value.lowercase().trim()
            return if (normalized in SUPPORTED) LanguageCode(normalized) else null
        }

        fun parseOrThrow(value: String): LanguageCode {
            return parse(value) ?: error("Unsupported language: '$value'. Supported: id, en")
        }
    }

    val displayName: String
        get() = when (code) {
            "id" -> "Indonesia"
            "en" -> "English"
            else -> code
        }

    val nativeName: String
        get() = when (code) {
            "id" -> "Bahasa Indonesia"
            "en" -> "English"
            else -> code
        }
}
