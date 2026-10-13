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
    descriptionText = description.takeIf { it.isNotBlank() }?.let { LocalizedText.of(it) },
    // Judul menu bukan bagian identitas pack (draf lama tak punya): dibuang agar draf lama tetap identik dengan pack bawaan.
    menuLabelText = null
)

internal fun ScreenSuggestion.withoutTranslations(): ScreenSuggestion = copy(
    titleText = LocalizedText.of(title),
    rationaleText = rationale?.takeIf { it.isNotBlank() }?.let { LocalizedText.of(it) }
)

internal fun SlotDefinition.withoutTranslations(): SlotDefinition = copy(displayNameText = LocalizedText.of(displayName))

/**
 * Definisi modul [this] sama dengan milik platform [platform] **di luar terjemahan**: draf/pack lama yang menyalin
 * modul bersama sebelum pack bawaan punya `en` tetap sama; yang membawa terjemahan wajib identik dengan milik
 * platform (terjemahan tulisan sendiri = penulisan ulang, ditolak).
 */
internal fun ModuleDefinition.samePlatformDefinitionAs(platform: ModuleDefinition): Boolean =
    withoutTranslations() == platform.withoutTranslations() && (this == withoutTranslations() || this == platform)

internal fun SlotDefinition.samePlatformDefinitionAs(platform: SlotDefinition): Boolean =
    withoutTranslations() == platform.withoutTranslations() && (this == withoutTranslations() || this == platform)
