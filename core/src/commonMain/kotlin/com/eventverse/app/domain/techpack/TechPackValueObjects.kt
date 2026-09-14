package com.eventverse.app.domain.techpack

import kotlin.jvm.JvmInline

@JvmInline
value class TechPackId(val value: String) {
    init {
        require(value.isNotBlank()) { "TechPackId tidak boleh kosong" }
        require(value.length <= 64) { "TechPackId maksimal 64 karakter" }
    }
}

@JvmInline
value class StyleCode(val value: String) {
    init {
        val trimmed = value.trim()
        require(trimmed.isNotBlank()) { "StyleCode tidak boleh kosong" }
        require(trimmed.length <= 32) { "StyleCode maksimal 32 karakter" }
        require(STYLE_CODE_REGEX.matches(trimmed)) {
            "StyleCode harus berupa huruf/angka/dash/slash diawali alfanumerik: $value"
        }
    }

    companion object {
        private val STYLE_CODE_REGEX = Regex("^[A-Za-z0-9][A-Za-z0-9\\-_/.]*$")
    }
}

enum class TechPackStatus(val displayName: String, val isEditable: Boolean) {
    DRAFT("Draf Penyusunan", isEditable = true),
    RELEASED("Dirilis ke Produksi", isEditable = false),
    SUPERSEDED("Digantikan Versi Baru", isEditable = false),
    ARCHIVED("Diarsipkan", isEditable = false);

    companion object {
        fun fromCode(code: String?): TechPackStatus? =
            entries.firstOrNull { it.name.equals(code, ignoreCase = true) }
    }
}
