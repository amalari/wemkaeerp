package com.eventverse.app.domain.pack

import com.eventverse.app.domain.i18n.LocalizedText

/**
 * Penyuntingan teks `id` yang **menjaga jembatan** TRD-I18N-002: `copy(description = …)` pada tipe yang punya
 * pasangan `xxxText` melanggar invarian (`xxxText.idText == xxx`). Fungsi di sini mengganti `String` lama **dan**
 * pasangannya sekaligus; terjemahan lama dibuang karena kedaluwarsa terhadap teks `id` yang baru.
 */
fun ModuleDefinition.withIdText(displayName: String = this.displayName, description: String = this.description): ModuleDefinition = copy(
    displayName = displayName, description = description,
    displayNameText = LocalizedText.of(displayName),
    descriptionText = description.takeIf { it.isNotBlank() }?.let { LocalizedText.of(it) }
)

fun ScreenSuggestion.withIdRationale(rationale: String?): ScreenSuggestion = copy(
    rationale = rationale,
    rationaleText = rationale?.takeIf { it.isNotBlank() }?.let { LocalizedText.of(it) }
)

/** Mengganti kosakata/label port (teks `id`) bersama pasangan dua bahasanya. */
fun DomainPack.withIdLabels(
    vocabulary: Map<VocabularyKey, String> = this.vocabulary,
    portLabels: Map<String, String> = this.portLabels
): DomainPack = copy(
    vocabulary = vocabulary, portLabels = portLabels,
    vocabularyText = vocabulary.mapValues { LocalizedText.of(it.value) },
    portLabelTexts = portLabels.mapValues { LocalizedText.of(it.value) }
)
