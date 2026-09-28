package com.eventverse.app.presentation.sampling

import com.eventverse.app.domain.pipeline.DefectLiability
import com.eventverse.app.domain.sampling.*
import com.eventverse.app.domain.sampling.toStageCode
import com.eventverse.app.domain.stageflow.StageCode
import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.infrastructure.api.SamplingRemoteDataSource
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
class SamplingDraftAutosaverTest {
    private val now = Instant.parse("2026-09-27T08:00:00Z")
    private val order = SamplingOrder(
        id = SamplingOrderId("smp_draft"),
        tenantId = TenantId("demo-tenant"),
        spkNumber = SpkNumber("SPK-SMP-0051"),
        clientName = "Morfeen Studio",
        styleName = "Oversized Knit Hoodie",
        status = SamplingStatus.IN_PROGRESS,
        stageCode = SamplingPipelineStage.CAM_PROGRAMMING.toStageCode(),
        createdAt = now,
        updatedAt = now
    )

    private fun sections(value: String) =
        listOf(StageInputSection(section = "Badan", rows = listOf(StageInputRow("Tensi", value))))

    private fun TestScope.autosaver(remote: FakeRemote, state: MutableStateFlow<SamplingUiState>) =
        SamplingDraftAutosaver("demo", remote, this, state, debounceMillis = 1_000)

    @Test
    fun `draft change when typing burst should save only last content once`() = runTest {
        val remote = FakeRemote()
        val state = MutableStateFlow(SamplingUiState(orders = listOf(order)))
        val saver = autosaver(remote, state)

        saver.onDraftChanged(order.id, SamplingPipelineStage.CAM_PROGRAMMING.toStageCode(), sections("1"))
        advanceTimeBy(500)
        saver.onDraftChanged(order.id, SamplingPipelineStage.CAM_PROGRAMMING.toStageCode(), sections("12"))
        advanceTimeBy(1_001)
        runCurrent()

        assertEquals(1, remote.saved.size)
        assertEquals(sections("12"), remote.saved.single().stageInputFor(SamplingPipelineStage.CAM_PROGRAMMING)?.sections)
        assertEquals(DraftSaveStatus.Saved, state.value.draftSave.statusFor(order.id))
        // Autosave tidak boleh memicu toast maupun men-disable tombol.
        assertEquals(null, state.value.statusMessage)
        assertEquals(false, state.value.isSubmitting)
    }

    @Test
    fun `draft change when content equals saved input should not send request`() = runTest {
        val remote = FakeRemote()
        val saved = order.fillStageInput(SamplingPipelineStage.CAM_PROGRAMMING, sections("5"), now)
        val state = MutableStateFlow(SamplingUiState(orders = listOf(saved)))

        autosaver(remote, state).onDraftChanged(saved.id, SamplingPipelineStage.CAM_PROGRAMMING.toStageCode(), sections("5"))
        advanceTimeBy(2_000)
        runCurrent()

        assertEquals(0, remote.saved.size)
    }

    @Test
    fun `cancel pending when stage advance starts should drop debounced draft`() = runTest {
        val remote = FakeRemote()
        val state = MutableStateFlow(SamplingUiState(orders = listOf(order)))
        val saver = autosaver(remote, state)

        saver.onDraftChanged(order.id, SamplingPipelineStage.CAM_PROGRAMMING.toStageCode(), sections("9"))
        saver.cancelPending()
        advanceTimeBy(2_000)
        runCurrent()

        assertEquals(0, remote.saved.size)
    }

    @Test
    fun `draft save when remote fails should expose failed status for that order only`() = runTest {
        val remote = FakeRemote(fail = true)
        val state = MutableStateFlow(SamplingUiState(orders = listOf(order)))

        autosaver(remote, state).onDraftChanged(order.id, SamplingPipelineStage.CAM_PROGRAMMING.toStageCode(), sections("3"))
        advanceTimeBy(1_001)
        runCurrent()

        assertIs<DraftSaveStatus.Failed>(state.value.draftSave.statusFor(order.id))
        assertEquals(DraftSaveStatus.Idle, state.value.draftSave.statusFor(SamplingOrderId("lain")))
    }

    private class FakeRemote(private val fail: Boolean = false) : SamplingRemoteDataSource {
        val saved = mutableListOf<SamplingOrder>()

        override suspend fun saveOrder(tenantSlug: String, order: SamplingOrder): Result<SamplingOrder> =
            if (fail) Result.failure(IllegalStateException("offline")) else Result.success(order.also { saved += it })

        private fun unused(): Nothing = error("tidak dipakai di test ini")
        override suspend fun getOrders(tenantSlug: String, status: SamplingStatus?) = unused()
        override suspend fun getOrderDetail(tenantSlug: String, orderId: String) = unused()
        override suspend fun createOrder(
            tenantSlug: String, clientName: String, styleName: String, sizeMode: SizeMode, useFactoryPreset: Boolean
        ) = unused()
        override suspend fun updateTechnicalSpec(tenantSlug: String, order: SamplingOrder) = unused()
        override suspend fun toggleMilestone(tenantSlug: String, orderId: String, step: MilestoneStep, isCompleted: Boolean) = unused()
        override suspend fun approveOrder(tenantSlug: String, orderId: String, isApproved: Boolean, accNotes: String) = unused()
        override suspend fun advanceStage(
            tenantSlug: String, orderId: String, targetStage: StageCode, stageInputs: List<StageWorkInput>
        ) = unused()
        override suspend fun addFinishingDeposit(tenantSlug: String, orderId: String, deposit: FinishingDeposit) = unused()
        override suspend fun assignMakloonVendor(tenantSlug: String, orderId: String, info: MakloonVendorInfo) = unused()
        override suspend fun confirmVendorReturn(tenantSlug: String, orderId: String, returnedAt: LocalDate?) = unused()
        override suspend fun submitQcInspection(tenantSlug: String, orderId: String, report: QcInspectionReport) = unused()
        override suspend fun requestRevision(tenantSlug: String, orderId: String, notes: String) = unused()
        override suspend fun startStageWork(tenantSlug: String, orderId: String, operatorName: String) = unused()
        override suspend fun releaseStageWork(tenantSlug: String, orderId: String) = unused()
        override suspend fun sendBackForRework(
            tenantSlug: String, orderId: String, target: StageCode, reason: String, liability: DefectLiability
        ) = unused()
    }
}
