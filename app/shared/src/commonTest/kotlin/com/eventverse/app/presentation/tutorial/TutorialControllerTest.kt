package com.eventverse.app.presentation.tutorial

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.eventverse.app.domain.pack.ModuleId
import com.eventverse.app.domain.tutorial.CalloutPlacement
import com.eventverse.app.domain.tutorial.ModuleTutorial
import com.eventverse.app.domain.tutorial.TutorialId
import com.eventverse.app.domain.tutorial.TutorialScope
import com.eventverse.app.domain.tutorial.TutorialStep
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

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
}
