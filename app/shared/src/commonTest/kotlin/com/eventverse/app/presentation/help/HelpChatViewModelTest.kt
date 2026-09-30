package com.eventverse.app.presentation.help

import com.eventverse.app.domain.help.usecases.HelpResult
import com.eventverse.app.domain.help.usecases.HelpSuggestion
import com.eventverse.app.domain.pack.ModuleId
import com.eventverse.app.domain.tutorial.TutorialId
import com.eventverse.app.infrastructure.api.HelpGateway
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** TRD-HELP-001 FR-7: ViewModel chat hanya meneruskan & menyimpan; "Mulai tutorial" = efek, bukan panggilan langsung. */
class HelpChatViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val testScope = TestScope(dispatcher)

    // Fixture non-garment: modul pack e-learning — ViewModel tidak peduli industri.
    private val grading = ModuleId("elearning_grading")
    private val suggestion = HelpSuggestion(TutorialId("grading_intro"), "Menilai tugas", grading, 1)

    private class FakeGateway(var reply: Result<HelpResult>) : HelpGateway {
        val asked = mutableListOf<Pair<String, ModuleId?>>()
        override suspend fun ask(question: String, currentModule: ModuleId?): Result<HelpResult> {
            asked += question to currentModule
            return reply
        }
    }

    private fun ok(alternatives: Int = 0) = Result.success(HelpResult(
        "Buka tab Penilaian.", suggestion,
        List(alternatives) { HelpSuggestion(TutorialId("alt_$it"), "Alt $it", grading, 0) }, "deterministic/help-v1"))

    @Test
    fun send_appendsQuestionAndAnswer_withModuleContext() = testScope.runTest {
        val gateway = FakeGateway(ok(alternatives = 4))
        val vm = HelpChatViewModel(gateway, testScope)
        vm.onEvent(HelpChatUiEvent.UpdateDraft("  cara menilai tugas  "))
        vm.onEvent(HelpChatUiEvent.Send(grading))
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue(state.messages.map { it.role } == listOf(HelpChatRole.USER, HelpChatRole.ASSISTANT))
        assertEquals(listOf(HelpChatRole.USER, HelpChatRole.ASSISTANT), state.messages.map { it.role })
        assertEquals(suggestion, state.messages.last().suggestion)
        assertEquals(2, state.messages.last().alternatives.size, "alternatif dibatasi supaya gelembung tetap ringkas")
        assertEquals("", state.draft)
        assertFalse(state.isSending)
    }

    @Test
    fun blankDraft_isNotSent() = testScope.runTest {
        val gateway = FakeGateway(ok())
        val vm = HelpChatViewModel(gateway, testScope)
        vm.onEvent(HelpChatUiEvent.UpdateDraft("   "))
        vm.onEvent(HelpChatUiEvent.Send(null))
        advanceUntilIdle()
        assertTrue(gateway.asked.isEmpty())
        assertTrue(vm.uiState.value.messages.isEmpty())
    }

    @Test
    fun failure_restoresDraft_withoutDuplicatingHistory() = testScope.runTest {
        val gateway = FakeGateway(Result.failure(IllegalStateException("HTTP 500")))
        val vm = HelpChatViewModel(gateway, testScope)
        vm.onEvent(HelpChatUiEvent.UpdateDraft("cara menilai"))
        vm.onEvent(HelpChatUiEvent.Send(null))
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue(state.messages.isEmpty())
        assertEquals("cara menilai", state.draft)
        assertTrue(state.error?.contains("HTTP 500") == true)

        gateway.reply = ok()
        vm.onEvent(HelpChatUiEvent.Send(null))
        advanceUntilIdle()
        assertEquals(2, vm.uiState.value.messages.size)
        assertNull(vm.uiState.value.error)
    }

    @Test
    fun startSuggestion_emitsEffect() = testScope.runTest {
        val vm = HelpChatViewModel(FakeGateway(ok()), testScope)
        vm.onEvent(HelpChatUiEvent.StartSuggestion(suggestion))
        assertEquals(HelpChatUiEffect.StartTutorial(suggestion), vm.effects.first())
    }

    @Test
    fun draft_isCappedAtServerLimit() = testScope.runTest {
        val vm = HelpChatViewModel(FakeGateway(ok()), testScope)
        vm.onEvent(HelpChatUiEvent.UpdateDraft("a".repeat(900)))
        assertEquals(500, vm.uiState.value.draft.length)
    }
}
