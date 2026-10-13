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
    sections.forEachIndexed { i, s -> add("sections[$i].displayName" to s.displayNameText) }
    modules.forEachIndexed { i, m ->
        add("modules[$i].displayName" to m.displayNameText)
        m.descriptionText?.let { add("modules[$i].description" to it) }
        m.menuLabelText?.let { add("modules[$i].menuLabel" to it) }
    }
    actions.forEachIndexed { i, a -> add("actions[$i].label" to a.labelText) }
    vocabularyText.forEach { (key, text) -> add("vocabulary.${key.name}" to text) }
    portLabelTexts.forEach { (port, text) -> add("portLabels.$port" to text) }
    screenSuggestions.forEachIndexed { i, s ->
        add("screenSuggestions[$i].title" to s.titleText)
        s.rationaleText?.let { add("screenSuggestions[$i].rationale" to it) }
    }
    roleHints.forEachIndexed { i, h -> add("roleHints[$i].label" to h.labelText) }
}

/** Selisih klaim [DomainPack.languages] terhadap isi. Kosong = pack lengkap untuk semua bahasa yang diklaimnya. */
fun DomainPack.missingTranslations(): List<MissingTranslation> =
    PackLanguageValidator.missing(languages, localizedTexts())
