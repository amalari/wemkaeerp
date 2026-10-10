package com.eventverse.app.presentation.pipeline

import com.eventverse.app.domain.pack.GarmentBlueprints
import com.eventverse.app.domain.pipeline.CustomTenantPipeline
import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.infrastructure.api.PipelineRequestException
import com.eventverse.app.presentation.common.FriendlyErrors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** TRD-PLAT-012 Q2: galat muat Factory Flow dipisah per jenis dan tidak pernah menjadi preset. */
@OptIn(ExperimentalCoroutinesApi::class)
class FactoryFlowLoadStateTest {
    private val slug = "bordir-uji"
    private val tenant = TenantId("ten-bordir-uji")

    private fun viewModel(remote: FakePipelineRemoteDataSource, scope: TestScope) =
        FactoryFlowViewModel(apiClient = remote, scope = CoroutineScope(StandardTestDispatcher(scope.testScheduler)))

    private fun nonDefaultPipeline() = CustomTenantPipeline.fromPreset(tenant, GarmentBlueprints.CMT_MAKLOON)

    @Test
    fun fromFailure_http403_shouldBeAccessDeniedWithServerMessage() {
        val state = FactoryFlowLoadState.fromFailure(
            PipelineRequestException("memuat alur pabrik", 403, "Akses Alur Pabrik ditolak")
        )
        assertEquals(FactoryFlowLoadState.AccessDenied("Akses Alur Pabrik ditolak"), state)
    }

    @Test
    fun fromFailure_http403WithBlankBody_shouldUseDefaultMessage() {
        val state = FactoryFlowLoadState.fromFailure(PipelineRequestException("memuat alur pabrik", 403, " "))
        assertEquals(FactoryFlowLoadState.AccessDenied(FactoryFlowLoadState.DEFAULT_DENIED), state)
    }

    @Test
    fun fromFailure_http503AndNetwork_shouldBeFailedWithFriendlyText() {
        val gateway = FactoryFlowLoadState.fromFailure(PipelineRequestException("memuat alur pabrik", 503, "x"))
        assertEquals(FactoryFlowLoadState.Failed(FriendlyErrors.UNREACHABLE), gateway)
        val net = FactoryFlowLoadState.fromFailure(IllegalStateException("Connection refused"))
        assertEquals(FactoryFlowLoadState.Failed(FriendlyErrors.UNREACHABLE), net)
        val server500 = FactoryFlowLoadState.fromFailure(PipelineRequestException("memuat alur pabrik", 500, "boom"))
        assertIs<FactoryFlowLoadState.Failed>(server500)
    }

    @Test
    fun load_403_shouldBeAccessDeniedWithoutPreset() = runTest {
        val remote = FakePipelineRemoteDataSource(
            failure = PipelineRequestException("memuat alur pabrik", 403, "Ditolak")
        )
        val vm = viewModel(remote, this)
        vm.onEvent(FactoryFlowUiEvent.LoadTenantPipeline(slug))
        advanceUntilIdle()

        val state = vm.uiState.value
        assertIs<FactoryFlowLoadState.AccessDenied>(state.loadState)
        assertTrue(state.loadState.isBlocked)
        assertFalse(state.isOfflineFallback, "403 tidak boleh tampil sebagai preset")
        assertNull(state.pipeline)
        assertFalse(state.isLoading)
        assertFalse(remote.calls.contains("catalog"), "Tidak memanggil API sekunder setelah ditolak")
    }

    @Test
    fun load_networkFailure_shouldBeFailedThenRetryLoads() = runTest {
        val remote = FakePipelineRemoteDataSource(
            pipeline = nonDefaultPipeline(),
            failure = IllegalStateException("Failed to fetch")
        )
        val vm = viewModel(remote, this)
        vm.onEvent(FactoryFlowUiEvent.LoadTenantPipeline(slug))
        advanceUntilIdle()
        assertEquals(FactoryFlowLoadState.Failed(FriendlyErrors.UNREACHABLE), vm.uiState.value.loadState)
        assertFalse(vm.uiState.value.isOfflineFallback)

        remote.failure = null
        vm.onEvent(FactoryFlowUiEvent.Retry(slug))
        advanceUntilIdle()
        assertEquals(FactoryFlowLoadState.Loaded, vm.uiState.value.loadState)
        assertTrue(vm.uiState.value.isTenantDataLoaded)
    }

    @Test
    fun load_success_shouldBeLoadedWithNonDefaultTemplate() = runTest {
        val remote = FakePipelineRemoteDataSource(pipeline = nonDefaultPipeline())
        val vm = viewModel(remote, this)
        vm.onEvent(FactoryFlowUiEvent.LoadTenantPipeline(slug))
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(FactoryFlowLoadState.Loaded, state.loadState)
        assertEquals(GarmentBlueprints.CMT_MAKLOON, state.selectedPreset)
        assertFalse(state.loadState.isBlocked)
    }

    @Test
    fun reload_403AfterLoaded_shouldDropStaleTenantData() = runTest {
        val remote = FakePipelineRemoteDataSource(pipeline = nonDefaultPipeline())
        val vm = viewModel(remote, this)
        vm.onEvent(FactoryFlowUiEvent.LoadTenantPipeline(slug))
        advanceUntilIdle()
        remote.failure = PipelineRequestException("memuat alur pabrik", 403, "Ditolak")
        vm.onEvent(FactoryFlowUiEvent.Retry(slug))
        advanceUntilIdle()

        assertIs<FactoryFlowLoadState.AccessDenied>(vm.uiState.value.loadState)
        assertNull(vm.uiState.value.pipeline)
    }
}
