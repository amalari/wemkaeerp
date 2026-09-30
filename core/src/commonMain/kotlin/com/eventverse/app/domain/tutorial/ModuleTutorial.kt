package com.eventverse.app.domain.tutorial

import com.eventverse.app.domain.pack.ModuleId
import com.eventverse.app.domain.rbac.AccessLevel

/**
 * Satu langkah coach mark.
 *
 * @param anchor elemen yang disorot; `null` = callout di tengah tanpa sorotan (mis. elemen di dalam dialog).
 * @param screen modul tempat langkah ini tampil; `null` = layar tempat langkah sebelumnya tampil.
 */
data class TutorialStep(
    val title: String,
    val body: String,
    val anchor: TutorialAnchorId? = null,
    val screen: ModuleId? = null,
    val placement: CalloutPlacement = CalloutPlacement.BOTTOM,
) {
    init {
        require(title.isNotBlank()) { "Judul langkah tutorial kosong" }
        require(body.isNotBlank()) { "Isi langkah '$title' kosong" }
    }
}

/**
 * Tutorial yang ditulis developer dan dikirim per rilis bersama pack-nya (TRD-HELP-001 §0.4).
 *
 * Template vs salinan: tutorial **tidak** disalin ke tenant dan **tidak** membeku — tenant selalu membaca versi rilis
 * yang berjalan, karena tutorial harus cocok dengan UI rilis itu. [sampleQuestions] & [keywords] adalah bahan
 * pencocok pertanyaan AI helper, bukan teks tampil.
 */
data class ModuleTutorial(
    val id: TutorialId,
    val scope: TutorialScope,
    val title: String,
    val summary: String,
    val steps: List<TutorialStep>,
    val sampleQuestions: List<String> = emptyList(),
    val keywords: List<String> = emptyList(),
    val requiredLevel: AccessLevel = AccessLevel.VIEW,
) {
    init {
        require(title.isNotBlank()) { "Judul tutorial ${id.value} kosong" }
        require(steps.isNotEmpty()) { "Tutorial ${id.value} tanpa langkah" }
        require(requiredLevel != AccessLevel.NONE) { "Tutorial ${id.value}: requiredLevel NONE membuka tutorial untuk semua orang" }
    }

    /** Modul yang dituju, bila tutorial ini tutorial modul. */
    val moduleId: ModuleId? get() = (scope as? TutorialScope.Module)?.moduleId
}
