package com.eventverse.app.domain.pack

import com.eventverse.app.domain.i18n.LocalizedText
import com.eventverse.app.domain.i18n.MissingTranslation
import com.eventverse.app.domain.i18n.PackLanguageValidator

/**
 * Semua [LocalizedText] pack ini beserta jalurnya (TRD-I18N-002). **Satu tempat** yang diperluas tiap tahap
 * Strangler Fig (P1 fase, P2 slot, P3 modul, P4 usulan layar, P5 peran/template, P6 tutorial/wawancara):
 * tahap yang menambah tipe `LocalizedText` wajib menambahkan jalurnya di sini, sehingga validator kelengkapan
 * ([missingTranslations]) otomatis ikut memeriksanya.
 */
fun DomainPack.localizedTexts(): List<Pair<String, LocalizedText>> = buildList {
    phases.forEachIndexed { i, p ->
        add("phases[$i].displayName" to p.displayNameText)
        p.subtitleText?.let { add("phases[$i].subtitle" to it) }
    }
    slots.forEachIndexed { i, s -> add("slots[$i].displayName" to s.displayNameText) }
}

/** Selisih klaim [DomainPack.languages] terhadap isi. Kosong = pack lengkap untuk semua bahasa yang diklaimnya. */
fun DomainPack.missingTranslations(): List<MissingTranslation> =
    PackLanguageValidator.missing(languages, localizedTexts())
