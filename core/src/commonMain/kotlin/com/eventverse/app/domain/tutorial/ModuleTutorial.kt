package com.eventverse.app.domain.tutorial

import com.eventverse.app.domain.pack.ModuleId
import com.eventverse.app.domain.rbac.AccessLevel

/**
 * Kapan langkah coach mark boleh maju (TRD-TUT-002 FR-2).
 *
 * Uji Variabilitas: perilaku sistem — sama untuk semua tenant/industri, tidak diubah admin → konsep kode.
 * Default [Manual] menjaga tutorial lama berperilaku identik (kompatibel mundur, tanpa migrasi konten).
 */
sealed interface AdvanceTrigger {
    /** Perilaku lama: pengguna maju lewat tombol "Lanjut" di callout. */
    data object Manual : AdvanceTrigger

    /** Langkah maju ketika pengguna mengklik elemen yang di-anchor [anchorId]. Ditolak di dialog (FR-5). */
    data class UserClick(val anchorId: TutorialAnchorId) : AdvanceTrigger

    /** Langkah maju ketika bounds [anchorId] mulai ada di registry (elemen/state muncul di layar yang sama). */
    data class StateAppears(val anchorId: TutorialAnchorId) : AdvanceTrigger
}

/**
 * Satu langkah coach mark.
 *
 * @param anchor elemen yang disorot; `null` = callout di tengah tanpa sorotan (mis. elemen di dalam dialog).
 * @param screen modul tempat langkah ini tampil; `null` = layar tempat langkah sebelumnya tampil.
 * @param interactive `true` = lubang sorot tembus: klik pada target sampai ke elemen di bawahnya (TRD-TUT-002 FR-1).
 * @param advanceOn kapan langkah ini maju (FR-2). Default [AdvanceTrigger.Manual] ⇒ perilaku lama.
 */
data class TutorialStep(
    val title: String,
    val body: String,
    val anchor: TutorialAnchorId? = null,
    val screen: ModuleId? = null,
    val placement: CalloutPlacement = CalloutPlacement.BOTTOM,
    val interactive: Boolean = false,
    val advanceOn: AdvanceTrigger = AdvanceTrigger.Manual,
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
