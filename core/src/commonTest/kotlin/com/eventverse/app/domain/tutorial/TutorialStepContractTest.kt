package com.eventverse.app.domain.tutorial

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * TRD-TUT-002 Track 0/A: kontrak `AdvanceTrigger` + field `TutorialStep.interactive`/`advanceOn`.
 * Default wajib mempertahankan perilaku lama (Manual, tidak tembus) — kompatibel mundur tanpa migrasi konten.
 */
class TutorialStepContractTest {

    @Test
    fun stepDefaults_preserveLegacyBehavior() {
        val step = TutorialStep(title = "Judul", body = "Isi")
        assertFalse(step.interactive, "default interactive = false")
        assertIs<AdvanceTrigger.Manual>(step.advanceOn, "default advanceOn = Manual")
        assertEquals(CalloutPlacement.BOTTOM, step.placement, "placement lama tidak berubah")
    }

    @Test
    fun userClick_carriesAnchor() {
        val anchor = TutorialAnchorId("tombol_tambah")
        val trigger = AdvanceTrigger.UserClick(anchor)
        assertEquals(anchor, trigger.anchorId)
    }

    @Test
    fun stateAppears_carriesAnchor() {
        val anchor = TutorialAnchorId("panel_form")
        val trigger = AdvanceTrigger.StateAppears(anchor)
        assertEquals(anchor, trigger.anchorId)
    }

    @Test
    fun interactiveActStep_setsBothFields() {
        val step = TutorialStep(
            title = "Tekan Tambah", body = "b", anchor = TutorialAnchorId("tombol_tambah"),
            interactive = true, advanceOn = AdvanceTrigger.UserClick(TutorialAnchorId("tombol_tambah")),
        )
        assertTrue(step.interactive)
        val trigger = assertIs<AdvanceTrigger.UserClick>(step.advanceOn)
        assertEquals(step.anchor, trigger.anchorId, "act step: anchor & trigger anchor konsisten")
    }
}
