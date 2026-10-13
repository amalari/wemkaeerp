package com.eventverse.app.presentation.i18n

import com.eventverse.app.domain.i18n.LanguageCode
import com.eventverse.app.infrastructure.storage.PlatformLocalStorage

/**
 * Pilihan bahasa pengguna di penyimpanan lokal perangkat (TRD-I18N-001 F1). Hanya konvenien per perangkat;
 * pilihan per akun di server (`users.preferred_language`) adalah pekerjaan terpisah.
 *
 * Nilai tersimpan yang tak dikenal **dibuang** dan hasilnya `null` (pemanggil memakai bahasa dasar `id`) —
 * tidak ditafsirkan sebagai bahasa lain.
 */
object LanguagePreference {
    const val STORAGE_KEY = "wemade.language"

    /** Murni, bisa diuji: `null` untuk kosong atau kode yang tidak didukung. */
    fun parseStored(raw: String?): LanguageCode? = LanguageCode.parse(raw)

    fun load(): LanguageCode? {
        val raw = PlatformLocalStorage.getItem(STORAGE_KEY)
        val parsed = parseStored(raw)
        if (raw != null && parsed == null) PlatformLocalStorage.removeItem(STORAGE_KEY)
        return parsed
    }

    fun save(language: LanguageCode) = PlatformLocalStorage.setItem(STORAGE_KEY, language.code)
}
