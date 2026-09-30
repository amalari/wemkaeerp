package com.eventverse.app.presentation.tutorial

import com.eventverse.app.domain.tutorial.ModuleTutorial
import com.eventverse.app.domain.tutorial.TutorialStep
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Tutorial yang sedang berjalan dan langkah aktifnya. */
data class TutorialRun(val tutorial: ModuleTutorial, val stepIndex: Int) {
    val step: TutorialStep get() = tutorial.steps[stepIndex]
    val isFirst: Boolean get() = stepIndex == 0
    val isLast: Boolean get() = stepIndex == tutorial.steps.lastIndex
}

/**
 * Pemegang state coach mark (TRD-HELP-001 FR-3). Hanya urutan langkah — navigasi layar dan penantian anchor
 * dikerjakan `TutorialLayer`, sehingga kelas ini bisa diuji tanpa Compose. Satu controller per aplikasi: pemanggil
 * lain (daftar tutorial, AI chat helper) cukup memanggil [start].
 */
class TutorialController {
    private val state = MutableStateFlow<TutorialRun?>(null)
    val run: StateFlow<TutorialRun?> = state.asStateFlow()

    fun start(tutorial: ModuleTutorial, stepIndex: Int = 0) {
        state.value = TutorialRun(tutorial, stepIndex.coerceIn(0, tutorial.steps.lastIndex))
    }

    /** Maju satu langkah; di langkah terakhir berarti selesai. */
    fun next() {
        val current = state.value ?: return
        state.value = if (current.isLast) null else current.copy(stepIndex = current.stepIndex + 1)
    }

    fun back() {
        val current = state.value ?: return
        if (!current.isFirst) state.value = current.copy(stepIndex = current.stepIndex - 1)
    }

    fun dismiss() { state.value = null }
}
