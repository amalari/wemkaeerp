package com.eventverse.app.presentation.pipeline

import com.eventverse.app.domain.pack.GarmentBlueprints

import com.eventverse.app.domain.pipeline.CustomTenantPipeline
import com.eventverse.app.domain.tenant.TenantId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Verifies the factory flow canvas renders the tenant's persisted topology from the server,
 * rather than the hardcoded preset it used to display.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FactoryFlowViewModelTenantDataTest {

    private val tenantSlug = "cv-berkah-makloon"
    private val tenantId = TenantId("ten-demo-cmt")

    private fun cmtPipelineWithCustomNames(): CustomTenantPipeline {
        val base = CustomTenantPipeline.fromPreset(tenantId, GarmentBlueprints.CMT_MAKLOON)
        val inventoryNode = base.nodes.first { it.moduleId == "inventory" }
        return base
            .renameNode(inventoryNode.nodeId, "Penerimaan Kain Titipan Buyer")
            .rename("Alur Operasional CV Berkah Makloon Jahit")
    }

    @Test
    fun loadTenantPipeline_shouldRenderPersistedNamesNotPresetDefaults() = runTest {
        val remote = FakePipelineRemoteDataSource(pipeline = cmtPipelineWithCustomNames())
        val viewModel = FactoryFlowViewModel(
            apiClient = remote,
            scope = CoroutineScope(StandardTestDispatcher(testScheduler))
        )

        viewModel.onEvent(FactoryFlowUiEvent.LoadTenantPipeline(tenantSlug))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertTrue(state.isTenantDataLoaded)
        assertFalse(state.isOfflineFallback)
        assertEquals("Alur Operasional CV Berkah Makloon Jahit", state.pipelineName)
        assertTrue(
            state.snapshot.nodes.any { it.title == "Penerimaan Kain Titipan Buyer" },
            "Kanvas harus memakai nama modul milik tenant"
        )
        assertEquals(GarmentBlueprints.CMT_MAKLOON, state.selectedPreset)
        assertTrue(remote.calls.contains("get"))
        assertTrue(remote.calls.contains("catalog"))
    }

    @Test
    fun loadTenantPipeline_shouldReflectBypassedModulesFromServer() = runTest {
        val remote = FakePipelineRemoteDataSource(pipeline = cmtPipelineWithCustomNames())
        val viewModel = FactoryFlowViewModel(
            apiClient = remote,
            scope = CoroutineScope(StandardTestDispatcher(testScheduler))
        )

        viewModel.onEvent(FactoryFlowUiEvent.LoadTenantPipeline(tenantSlug))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(2, state.bypassedCount, "CMT makloon mem-bypass pengadaan bahan baku")
        assertEquals(7, state.snapshot.activeModulesCount)
        assertTrue(state.filteredNodes.none { it.isBypassed })
    }

    @Test
    fun loadTenantPipeline_whenServerFails_shouldFallBackAndSaySo() = runTest {
        val remote = FakePipelineRemoteDataSource(
            pipeline = null,
            failure = IllegalStateException("Gagal memuat alur pabrik (HTTP 503)")
        )
        val viewModel = FactoryFlowViewModel(
            apiClient = remote,
            scope = CoroutineScope(StandardTestDispatcher(testScheduler))
        )

        viewModel.onEvent(FactoryFlowUiEvent.LoadTenantPipeline(tenantSlug))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        // A fallback must never be presented as the tenant's real configuration.
        assertTrue(state.isOfflineFallback)
        assertFalse(state.isTenantDataLoaded)
        assertNull(state.pipeline)
        assertNotNull(state.error)
        assertTrue(state.snapshot.nodes.isNotEmpty(), "Kanvas tetap terisi template agar tidak kosong")
    }

    @Test
    fun retry_afterFailure_shouldRecoverTenantData() = runTest {
        val remote = FakePipelineRemoteDataSource(
            pipeline = cmtPipelineWithCustomNames(),
            failure = IllegalStateException("jaringan terputus")
        )
        val viewModel = FactoryFlowViewModel(
            apiClient = remote,
            scope = CoroutineScope(StandardTestDispatcher(testScheduler))
        )

        viewModel.onEvent(FactoryFlowUiEvent.LoadTenantPipeline(tenantSlug))
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isOfflineFallback)

        remote.failure = null
        viewModel.onEvent(FactoryFlowUiEvent.Retry(tenantSlug))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.isTenantDataLoaded)
        assertNull(state.error)
    }

    @Test
    fun setModuleActive_shouldAdoptServerResultAsNewTruth() = runTest {
        val remote = FakePipelineRemoteDataSource(pipeline = cmtPipelineWithCustomNames())
        val viewModel = FactoryFlowViewModel(
            apiClient = remote,
            scope = CoroutineScope(StandardTestDispatcher(testScheduler))
        )
        viewModel.onEvent(FactoryFlowUiEvent.LoadTenantPipeline(tenantSlug))
        advanceUntilIdle()

        viewModel.onEvent(
            FactoryFlowUiEvent.SetModuleActive(tenantSlug, "operator_exec", false)
        )
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isSaving)
        assertNotNull(state.statusMessage)
        assertEquals(3, state.bypassedCount, "Modul yang dinonaktifkan harus ikut terhitung")
        assertTrue(remote.calls.contains("activation:operator_exec:false"))
    }

    @Test
    fun setModuleActive_whenPlanRejects_shouldSurfaceServerMessage() = runTest {
        val remote = FakePipelineRemoteDataSource(pipeline = cmtPipelineWithCustomNames())
        val viewModel = FactoryFlowViewModel(
            apiClient = remote,
            scope = CoroutineScope(StandardTestDispatcher(testScheduler))
        )
        viewModel.onEvent(FactoryFlowUiEvent.LoadTenantPipeline(tenantSlug))
        advanceUntilIdle()

        remote.failure = IllegalStateException(
            "Konfigurasi alur melebihi paket langganan: Paket STARTER hanya mengizinkan 5 modul aktif."
        )
        viewModel.onEvent(FactoryFlowUiEvent.SetModuleActive(tenantSlug, "inventory", true))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isSaving)
        assertTrue(state.error?.contains("paket langganan") == true, "Pesan: ${state.error}")
        // The previously loaded topology stays on screen instead of being wiped.
        assertTrue(state.isTenantDataLoaded)
    }

    @Test
    fun renameModule_shouldPersistAndCloseTheDialog() = runTest {
        val pipeline = cmtPipelineWithCustomNames()
        val remote = FakePipelineRemoteDataSource(pipeline = pipeline)
        val viewModel = FactoryFlowViewModel(
            apiClient = remote,
            scope = CoroutineScope(StandardTestDispatcher(testScheduler))
        )
        viewModel.onEvent(FactoryFlowUiEvent.LoadTenantPipeline(tenantSlug))
        advanceUntilIdle()

        val nodeToRename = viewModel.uiState.value.snapshot.nodes.first { it.title.contains("Kain") }
        viewModel.onEvent(FactoryFlowUiEvent.StartRenamingModule(nodeToRename))
        assertNotNull(viewModel.uiState.value.renamingNode)

        viewModel.onEvent(
            FactoryFlowUiEvent.RenameModule(tenantSlug, nodeToRename.id, "Gudang Kain Buyer Blok B")
        )
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNull(state.renamingNode, "Dialog harus tertutup setelah nama disimpan")
        assertTrue(state.snapshot.nodes.any { it.title == "Gudang Kain Buyer Blok B" })
    }

    @Test
    fun renameModule_withBlankName_shouldNotCallServer() = runTest {
        val remote = FakePipelineRemoteDataSource(pipeline = cmtPipelineWithCustomNames())
        val viewModel = FactoryFlowViewModel(
            apiClient = remote,
            scope = CoroutineScope(StandardTestDispatcher(testScheduler))
        )
        viewModel.onEvent(FactoryFlowUiEvent.LoadTenantPipeline(tenantSlug))
        advanceUntilIdle()
        val callsBefore = remote.calls.size

        viewModel.onEvent(FactoryFlowUiEvent.RenameModule(tenantSlug, "any-node", "   "))
        advanceUntilIdle()

        assertEquals(callsBefore, remote.calls.size, "Nama kosong tidak boleh dikirim ke server")
        assertNotNull(viewModel.uiState.value.error)
    }

    @Test
    fun resetToPreset_shouldReplaceTopologyWithPresetStandard() = runTest {
        val remote = FakePipelineRemoteDataSource(pipeline = cmtPipelineWithCustomNames())
        val viewModel = FactoryFlowViewModel(
            apiClient = remote,
            scope = CoroutineScope(StandardTestDispatcher(testScheduler))
        )
        viewModel.onEvent(FactoryFlowUiEvent.LoadTenantPipeline(tenantSlug))
        advanceUntilIdle()

        viewModel.onEvent(
            FactoryFlowUiEvent.ResetToPreset(tenantSlug, GarmentBlueprints.BRAND_D2C)
        )
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(GarmentBlueprints.BRAND_D2C, state.selectedPreset)
        assertTrue(state.isTenantDataLoaded)
        assertFalse(
            state.snapshot.nodes.any { it.title == "Penerimaan Kain Titipan Buyer" },
            "Reset harus menimpa kustomisasi sebelumnya"
        )
        assertTrue(remote.calls.contains("reset:brand_d2c"))
    }

    @Test
    fun selectScenario_shouldReprojectPersistedTopologyNotThePreset() = runTest {
        val remote = FakePipelineRemoteDataSource(pipeline = cmtPipelineWithCustomNames())
        val viewModel = FactoryFlowViewModel(
            apiClient = remote,
            scope = CoroutineScope(StandardTestDispatcher(testScheduler))
        )
        viewModel.onEvent(FactoryFlowUiEvent.LoadTenantPipeline(tenantSlug))
        advanceUntilIdle()

        viewModel.onEvent(
            FactoryFlowUiEvent.SelectScenario(
                com.eventverse.app.domain.pipeline.PipelineSimulationScenario.QC_FABRIC_DEFECT
            )
        )

        val state = viewModel.uiState.value
        assertTrue(
            state.snapshot.nodes.any { it.title == "Penerimaan Kain Titipan Buyer" },
            "Ganti skenario tidak boleh membuang nama modul milik tenant"
        )
        assertTrue(state.isTenantDataLoaded)
    }
}
