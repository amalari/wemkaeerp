package com.eventverse.app.presentation.sampling

import com.eventverse.app.domain.stageflow.IndustryStageTemplates
import com.eventverse.app.domain.stageflow.IndustryTemplateCode
import com.eventverse.app.domain.stageflow.StageCode
import com.eventverse.app.domain.stageflow.StageDefinition
import com.eventverse.app.infrastructure.api.NewStageDraft
import com.eventverse.app.infrastructure.api.StageFlowRemoteDataSource
import com.eventverse.app.infrastructure.api.TenantStageFlowView
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.*

/** Editor kerangka tahap (TRD-FLOW-001 3c): terjemahan niat layar → operasi API. */
class StageFlowEditorViewModelTest {

    private val embroidery = IndustryStageTemplates.stagesOf(IndustryTemplateCode.EMBROIDERY)
    private fun code(v: String) = StageCode(v)

    private class FakeRemote(var stages: List<StageDefinition>) : StageFlowRemoteDataSource {
        val calls = mutableListOf<String>()
        var failWith: String? = null
        private fun view() = failWith?.let { Result.failure<TenantStageFlowView>(IllegalStateException(it)) }
            ?: Result.success(TenantStageFlowView(IndustryTemplateCode.EMBROIDERY, stages))
        override suspend fun fetchTenantStages() = Result.success(stages)
        override suspend fun fetchTenantFlow() = view()
        override suspend fun addStage(draft: NewStageDraft) = view().also { calls += "add ${draft.code.value} after ${draft.after.value} desk=${draft.isOperatorDesk}" }
        override suspend fun removeStage(code: StageCode) = view().also { calls += "remove ${code.value}" }
        override suspend fun moveStage(code: StageCode, after: StageCode) = view().also { calls += "move ${code.value} after ${after.value}" }
    }

    private fun TestScope.vm(remote: FakeRemote, changed: MutableList<List<StageDefinition>> = mutableListOf()) =
        StageFlowEditorViewModel(remote, this, onChanged = { changed += it }).also { it.onEvent(StageFlowEditorUiEvent.Load); advanceUntilIdle() }

    @Test
    fun load_shouldExposeFlowAndNotifyBoard() = runTest(StandardTestDispatcher()) {
        val changed = mutableListOf<List<StageDefinition>>()
        val vm = vm(FakeRemote(embroidery), changed)
        assertEquals(IndustryTemplateCode.EMBROIDERY, vm.uiState.value.template)
        assertEquals(embroidery, changed.single())
    }

    @Test
    fun moves_shouldTranslateToInsertAfterAndNeverCrossAnchors() = runTest(StandardTestDispatcher()) {
        val remote = FakeRemote(embroidery)
        val vm = vm(remote)
        vm.onEvent(StageFlowEditorUiEvent.MoveUp(code("HOOPING"))); advanceUntilIdle()
        vm.onEvent(StageFlowEditorUiEvent.MoveDown(code("HOOPING"))); advanceUntilIdle()
        // Tahap kerja pertama tidak bisa naik melewati Penentuan Alur; kemas tidak bisa turun ke penyimpanan.
        vm.onEvent(StageFlowEditorUiEvent.MoveUp(code("DIGITIZING"))); advanceUntilIdle()
        vm.onEvent(StageFlowEditorUiEvent.MoveDown(code("PENGEMASAN"))); advanceUntilIdle()
        assertEquals(listOf("move HOOPING after FLOW_REVIEW", "move HOOPING after MACHINE_EMBROIDERY"), remote.calls)
    }

    @Test
    fun add_shouldDeriveUniqueCodeFromName() = runTest(StandardTestDispatcher()) {
        val remote = FakeRemote(embroidery)
        val vm = vm(remote)
        vm.onEvent(StageFlowEditorUiEvent.Add("Aplikasi Kain", "Aplikasi", code("MACHINE_EMBROIDERY"), isOperatorDesk = true)); advanceUntilIdle()
        vm.onEvent(StageFlowEditorUiEvent.Add("Hooping", "", code("DIGITIZING"), isOperatorDesk = false)); advanceUntilIdle()
        assertEquals(listOf("add APLIKASI_KAIN after MACHINE_EMBROIDERY desk=true", "add HOOPING_2 after DIGITIZING desk=false"), remote.calls)
    }

    @Test
    fun add_withoutLetters_shouldFailLocallyWithoutCallingServer() = runTest(StandardTestDispatcher()) {
        val remote = FakeRemote(embroidery)
        val vm = vm(remote)
        vm.onEvent(StageFlowEditorUiEvent.Add("  ! ", "", code("DIGITIZING"), true)); advanceUntilIdle()
        assertTrue(vm.uiState.value.isError)
        assertTrue(remote.calls.isEmpty())
    }

    @Test
    fun serverRejection_shouldBeShownAndKeepStages() = runTest(StandardTestDispatcher()) {
        val remote = FakeRemote(embroidery)
        val vm = vm(remote)
        remote.failWith = "Kerangka wajib punya satu tahap QC"
        vm.onEvent(StageFlowEditorUiEvent.Remove(code("QC_FINISHING"))); advanceUntilIdle()
        assertEquals("Kerangka wajib punya satu tahap QC", vm.uiState.value.message)
        assertTrue(vm.uiState.value.isError)
        assertEquals(embroidery, vm.uiState.value.stages)
    }

    @Test
    fun suggestStageCode_shouldNormaliseAndRejectEmpty() {
        assertEquals(code("SABLON_PLASTISOL"), suggestStageCode("sablon plastisol!", emptySet()))
        assertEquals(code("BORDIR"), suggestStageCode("123 Bordir", emptySet()))
        assertNull(suggestStageCode("9", emptySet()))
    }
}
