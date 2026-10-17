package com.eventverse.app.presentation.tutorial

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.eventverse.app.domain.pack.ModuleId
import com.eventverse.app.domain.tutorial.AdvanceTrigger
import com.eventverse.app.domain.tutorial.CalloutPlacement
import com.eventverse.app.domain.tutorial.ModuleTutorial
import com.eventverse.app.domain.tutorial.TutorialAnchorId
import com.eventverse.app.domain.tutorial.TutorialId
import com.eventverse.app.domain.tutorial.TutorialScope
import com.eventverse.app.domain.tutorial.TutorialStep
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** TRD-HELP-001 FR-3: urutan langkah coach mark dan penempatan callout yang tidak keluar layar. */
class TutorialControllerTest {

    // Fixture non-garment: modul pack e-learning fiktif — controller tidak peduli industri.
    private val tutorial = ModuleTutorial(
        id = TutorialId("grading_intro"),
        scope = TutorialScope.Module(ModuleId("elearning_grading")),
        title = "Menilai tugas",
        summary = "",
        steps = listOf(TutorialStep("Satu", "a"), TutorialStep("Dua", "b"), TutorialStep("Tiga", "c")),
    )

    @Test
    fun nextThroughLastStep_finishes() {
        val c = TutorialController()
        c.start(tutorial)
        c.next(); c.next()
        assertEquals(2, c.run.value?.stepIndex)
        c.next()
        assertNull(c.run.value, "Lanjut di langkah terakhir = selesai")
    }

    @Test
    fun backOnFirstStep_staysOnFirst() {
        val c = TutorialController()
        c.start(tutorial)
        c.back()
        assertEquals(0, c.run.value?.stepIndex)
    }

    @Test
    fun startBeyondLastStep_isClamped() {
        val c = TutorialController()
        c.start(tutorial, stepIndex = 9)
        assertEquals(2, c.run.value?.stepIndex)
    }

    @Test
    fun calloutBelowTargetNearBottom_isClampedInsideContainer() {
        val pos = calloutPosition(
            placement = CalloutPlacement.BOTTOM,
            target = Rect(100f, 700f, 200f, 780f),
            container = IntSize(1280, 800),
            callout = IntSize(340, 200),
            gap = 16f,
        )
        assertEquals(IntOffset(16, 584), pos)
    }

    @Test
    fun calloutWithoutTarget_isCentered() {
        val pos = calloutPosition(CalloutPlacement.BOTTOM, null, IntSize(1000, 800), IntSize(300, 200), 16f)
        assertEquals(IntOffset(350, 300), pos)
    }

    // --- TRD-TUT-002: notifyAction (FR-3) & AdvanceTrigger default (FR-2) ---

    @Test
    fun stepDefaults_preserveLegacyBehavior() {
        // Kompatibilitas mundur: tanpa field baru, langkah = Manual + tidak tembus (FR-2/FR-5).
        val step = TutorialStep("Judul", "Isi")
        assertFalse(step.interactive, "default interactive = false")
        assertIs<AdvanceTrigger.Manual>(step.advanceOn, "default advanceOn = Manual")
    }

    @Test
    fun notifyAction_onManualStep_isIgnored() {
        val c = TutorialController()
        c.start(tutorial) // langkah 0-2 semua Manual
        c.notifyAction(TutorialAnchorId("tombol_tambah"))
        assertEquals(0, c.run.value?.stepIndex, "Manual tidak boleh maju oleh notifyAction")
    }

    @Test
    fun notifyAction_matchingUserClick_advances() {
        val c = TutorialController()
        c.start(actionTutorial, stepIndex = 0) // langkah 0: UserClick("tombol_tambah")
        c.notifyAction(TutorialAnchorId("tombol_tambah"))
        assertEquals(1, c.run.value?.stepIndex, "UserClick yang cocok memajukan langkah")
    }

    @Test
    fun notifyAction_wrongAnchor_doesNotAdvance() {
        val c = TutorialController()
        c.start(actionTutorial, stepIndex = 0)
        c.notifyAction(TutorialAnchorId("anchor_lain"))
        assertEquals(0, c.run.value?.stepIndex, "anchor tak cocok tidak memajukan")
    }

    @Test
    fun notifyAction_onStateAppearsStep_isIgnored() {
        val c = TutorialController()
        c.start(actionTutorial, stepIndex = 2) // langkah 2: StateAppears
        c.notifyAction(TutorialAnchorId("panel_form"))
        assertEquals(2, c.run.value?.stepIndex, "StateAppears bukan UserClick — notifyAction diabaikan")
    }

    @Test
    fun notifyAction_whenNoRun_isSafeNoOp() {
        val c = TutorialController()
        c.notifyAction(TutorialAnchorId("tombol_tambah"))
        assertNull(c.run.value, "tanpa tutorial berjalan, notifyAction aman no-op")
    }

    // --- TRD-TUT-002 FR-5: degradasi jujur (fixture tabel FR-5 dibekukan Track 0) ---

    @Test
    fun degradationStepInDialog_mustBeManual() {
        // Hasil langkah adalah dialog → wajib Manual; StateAppears/UserClick ke anchor dialog dilarang.
        val dialogStep = TutorialStep(
            title = "Buka dialog", body = "b", anchor = TutorialAnchorId("tombol_buka"),
            interactive = false, advanceOn = AdvanceTrigger.Manual,
        )
        assertIs<AdvanceTrigger.Manual>(dialogStep.advanceOn, "langkah dialog wajib Manual (FR-5)")
    }

    @Test
    fun stepWithoutAnchor_defaultsToManualNotInteractive() {
        // target == null (tanpa anchor) → Manual + bukan tembus; scrim penuh berlaku.
        val step = TutorialStep(title = "Intro", body = "b", anchor = null)
        assertNull(step.anchor)
        assertFalse(step.interactive)
        assertIs<AdvanceTrigger.Manual>(step.advanceOn)
    }

    private companion object {
        val actionTutorial = ModuleTutorial(
            id = TutorialId("cara_tambah_data"),
            scope = TutorialScope.Module(ModuleId("elearning_grading")),
            title = "Menambah data",
            summary = "",
            steps = listOf(
                TutorialStep(
                    title = "Tekan Tambah", body = "b", anchor = TutorialAnchorId("tombol_tambah"),
                    interactive = true, advanceOn = AdvanceTrigger.UserClick(TutorialAnchorId("tombol_tambah")),
                ),
                TutorialStep(title = "Isi form", body = "b"),
                TutorialStep(
                    title = "Panel muncul", body = "c", anchor = TutorialAnchorId("panel_form"),
                    interactive = true, advanceOn = AdvanceTrigger.StateAppears(TutorialAnchorId("panel_form")),
                ),
            ),
        )
    }
}
