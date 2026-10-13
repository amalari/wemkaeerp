package com.eventverse.app.domain.pack

import com.eventverse.app.domain.i18n.LocalizedText
import com.eventverse.app.domain.i18n.PackLanguages

/**
 * Pack ini **tanpa terjemahan**: semua [LocalizedText] kembali hanya berisi teks `id` (field `String` lama) dan
 * [DomainPack.languages] = [PackLanguages.ID_ONLY]. Dipakai untuk membandingkan dokumen **di luar terjemahan**
 * (mis. [matchesShipped]: draf lama yang tersimpan sebelum pack bawaan punya `en` tetap identik dengan pack bawaan).
 * Satu tempat yang diperluas tiap tahap Strangler Fig bersama [localizedTexts].
 */
fun DomainPack.withoutTranslations(): DomainPack = copy(
    phases = phases.map { it.withoutTranslations() },
    slots = slots.map { it.copy(displayNameText = LocalizedText.of(it.displayName)) },
    sections = sections.map { it.copy(displayNameText = LocalizedText.of(it.displayName)) },
    modules = modules.map { it.withoutTranslations() },
    actions = actions.map { it.copy(labelText = LocalizedText.of(it.label)) },
    vocabularyText = vocabulary.mapValues { LocalizedText.of(it.value) },
    portLabelTexts = portLabels.mapValues { LocalizedText.of(it.value) },
    screenSuggestions = screenSuggestions.map { it.withoutTranslations() },
    roleHints = roleHints.map { it.copy(labelText = LocalizedText.of(it.label)) },
    languages = PackLanguages.ID_ONLY
)

internal fun PhaseDefinition.withoutTranslations(): PhaseDefinition = copy(
    displayNameText = LocalizedText.of(displayName),
    subtitleText = subtitle.takeIf { it.isNotBlank() }?.let { LocalizedText.of(it) }
)

internal fun ModuleDefinition.withoutTranslations(): ModuleDefinition = copy(
    displayNameText = LocalizedText.of(displayName),
    descriptionText = description.takeIf { it.isNotBlank() }?.let { LocalizedText.of(it) }
)

internal fun ScreenSuggestion.withoutTranslations(): ScreenSuggestion = copy(
    titleText = LocalizedText.of(title),
    rationaleText = rationale?.takeIf { it.isNotBlank() }?.let { LocalizedText.of(it) }
)
