package com.eventverse.app.domain.pack

import com.eventverse.app.domain.i18n.LanguageCode
import com.eventverse.app.domain.i18n.LocalizedText
import com.eventverse.app.domain.i18n.PackLanguages

/**
 * Menambahkan terjemahan Inggris pack garment (TRD-I18N-002) **di atas** teks `id` yang tak disentuh — sehingga
 * keluaran bahasa `id` identik dengan sebelumnya. Kode yang tak punya terjemahan dibiarkan id-saja (tidak
 * dikarang); [missingTranslations] yang menunjukkannya. Klaim [PackLanguages] `en` hanya diberikan bila
 * validator kosong — klaim dibuktikan mesin, bukan niat.
 */
internal fun DomainPack.withGarmentEnglish(): DomainPack {
    fun LocalizedText.en(text: String?): LocalizedText = if (text == null) this else with(LanguageCode.EN, text)
    val translated = copy(
        phases = phases.map { it.withGarmentEnglish() },
        slots = slots.map { it.copy(displayNameText = it.displayNameText.en(GarmentEnglishNames.slots[it.code.value])) },
        sections = sections.map { it.copy(displayNameText = it.displayNameText.en(GarmentEnglishNames.sections[it.code.value])) },
        modules = modules.map { m ->
            val t = GarmentEnglishNames.modules[m.id.value]
            m.copy(displayNameText = m.displayNameText.en(t?.first), descriptionText = m.descriptionText?.en(t?.second))
        },
        actions = actions.map { it.copy(labelText = it.labelText.en(GarmentEnglishNames.actions[it.code.name])) },
        vocabularyText = vocabularyText.mapValues { (k, v) -> v.en(GarmentEnglishNames.vocabulary[k.name]) },
        portLabelTexts = portLabelTexts.mapValues { (k, v) -> v.en(GarmentEnglishLabels.ports[k]) },
        screenSuggestions = screenSuggestions.map { s ->
            val t = GarmentEnglishLabels.screens[s.moduleId.value]
            s.copy(titleText = s.titleText.en(t?.first), rationaleText = s.rationaleText?.en(t?.second))
        },
        roleHints = roleHints.map { it.copy(labelText = it.labelText.en(GarmentEnglishLabels.roles[it.word])) }
    )
    val claimed = translated.copy(languages = PackLanguages(setOf(LanguageCode.ID, LanguageCode.EN)))
    return if (claimed.missingTranslations().isEmpty()) claimed else translated
}

/** Fase garment dengan `en`. Dipakai di sumber (`GarmentPhases`) supaya node preset dan pack berbagi satu instans yang sama. */
internal fun PhaseDefinition.withGarmentEnglish(): PhaseDefinition {
    val t = GarmentEnglishNames.phases[code.value] ?: return this
    return copy(displayNameText = displayNameText.with(LanguageCode.EN, t.first), subtitleText = subtitleText?.with(LanguageCode.EN, t.second))
}
