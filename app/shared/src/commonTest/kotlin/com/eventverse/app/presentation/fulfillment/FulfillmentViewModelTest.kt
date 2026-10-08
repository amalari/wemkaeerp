package com.eventverse.app.presentation.fulfillment

import com.eventverse.app.domain.fulfillment.FulfillmentRouteConfig
import com.eventverse.app.domain.fulfillment.HandoverMode
import com.eventverse.app.domain.fulfillment.HandoverProof
import com.eventverse.app.domain.fulfillment.HandoverRoute
import com.eventverse.app.domain.fulfillment.HandoverRouteCode
import com.eventverse.app.domain.fulfillment.HandoverRouteSetting
import com.eventverse.app.domain.fulfillment.HandoverRouteSettingsView
import com.eventverse.app.domain.fulfillment.InternalTransfer
import com.eventverse.app.domain.fulfillment.SackRoute
import com.eventverse.app.domain.fulfillment.SackTransferId
import com.eventverse.app.domain.fulfillment.SackTransferStatus
import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.domain.traceability.TraceCode
import com.eventverse.app.infrastructure.api.FulfillmentTransferRemoteDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Clock
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class FulfillmentViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun non_garment_tenant_loads_data_driven_routes() = testScope.runTest {
        val digitizingRoute = HandoverRoute(
            code = HandoverRouteCode("DIGITIZING_TO_HOOPING"),
            label = "Digitizing ke Pembidangan",
            sortOrder = 0,
            active = true
        )
        val hoopingRoute = HandoverRoute(
            code = HandoverRouteCode("HOOPING_TO_EMBROIDERY"),
            label = "Pembidangan ke Bordir",
            sortOrder = 1,
            active = true
        )
        val settingsView = HandoverRouteSettingsView(
            tenantId = TenantId("bordir-uji"),
            settings = listOf(
                HandoverRouteSetting(digitizingRoute, HandoverMode.DIRECT, isExplicit = true),
                HandoverRouteSetting(hoopingRoute, HandoverMode.ADMIN_HUB, isExplicit = true)
            )
        )
        val fakeDataSource = FakeFulfillmentRemoteDataSource(initialView = settingsView)
        val viewModel = FulfillmentViewModel(
            tenantSlug = "bordir-uji",
            remoteDataSource = fakeDataSource,
            scope = testScope
        )

        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(2, state.effectiveRoutes.size)
        assertEquals("DIGITIZING_TO_HOOPING", state.effectiveRoutes[0].route.code.value)
        assertEquals(HandoverMode.DIRECT, state.effectiveRoutes[0].mode)
        assertEquals("HOOPING_TO_EMBROIDERY", state.effectiveRoutes[1].route.code.value)
        assertEquals(HandoverMode.ADMIN_HUB, state.effectiveRoutes[1].mode)

        // hasAdminHubRoute bernilai true karena ada satu rute ADMIN_HUB
        assertTrue(state.hasAdminHubRoute)

        // Bundel (wadah terbuka / bukan karung tertutup) hanya boleh diantar lewat rute DIRECT
        val openContainerRoutes = state.routesAccepting(isClosedSack = false)
        assertEquals(1, openContainerRoutes.size)
        assertEquals("DIGITIZING_TO_HOOPING", openContainerRoutes[0].route.code.value)
    }

    @Test
    fun empty_routes_tenant_state_handles_cleanly() = testScope.runTest {
        val emptyView = HandoverRouteSettingsView(TenantId("tenant-kosong"), emptyList())
        val fakeDataSource = FakeFulfillmentRemoteDataSource(initialView = emptyView)
        val viewModel = FulfillmentViewModel(
            tenantSlug = "tenant-kosong",
            remoteDataSource = fakeDataSource,
            scope = testScope
        )

        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.effectiveRoutes.isEmpty())
        assertTrue(state.routesAccepting(isClosedSack = true).isEmpty())
        assertFalse(state.hasAdminHubRoute)
    }

    @Test
    fun submit_transfer_with_non_garment_route_submits_properly() = testScope.runTest {
        val customRoute = HandoverRoute(
            code = HandoverRouteCode("DIGITIZING_TO_HOOPING"),
            label = "Digitizing ke Pembidangan",
            sortOrder = 0,
            active = true
        )
        val settingsView = HandoverRouteSettingsView(
            tenantId = TenantId("bordir-uji"),
            settings = listOf(HandoverRouteSetting(customRoute, HandoverMode.DIRECT, isExplicit = true))
        )
        val fakeDataSource = FakeFulfillmentRemoteDataSource(initialView = settingsView)
        val viewModel = FulfillmentViewModel(
            tenantSlug = "bordir-uji",
            remoteDataSource = fakeDataSource,
            scope = testScope
        )

        testScheduler.advanceUntilIdle()

        viewModel.onEvent(
            FulfillmentUiEvent.SubmitTransfer(
                sackPayload = "W1SK010030000001",
                routeCode = HandoverRouteCode("DIGITIZING_TO_HOOPING"),
                dispatchWeightKg = null,
                dispatchScalePhotoKey = null,
                requestedBy = "Operator Bordir",
                declaredPcs = 50
            )
        )
        testScheduler.advanceUntilIdle()

        assertEquals("DIGITIZING_TO_HOOPING", fakeDataSource.lastSubmittedRouteCode?.value)
        assertEquals("W1SK010030000001", fakeDataSource.lastSubmittedSack)
        assertEquals("Operator Bordir", fakeDataSource.lastSubmittedRequestedBy)
    }

    @Test
    fun update_route_modes_updates_view_model_and_reloads() = testScope.runTest {
        val hoopingRoute = HandoverRoute(
            code = HandoverRouteCode("HOOPING_TO_EMBROIDERY"),
            label = "Pembidangan ke Bordir",
            sortOrder = 0,
            active = true
        )
        val settingsView = HandoverRouteSettingsView(
            tenantId = TenantId("bordir-uji"),
            settings = listOf(HandoverRouteSetting(hoopingRoute, HandoverMode.ADMIN_HUB, isExplicit = true))
        )
        val fakeDataSource = FakeFulfillmentRemoteDataSource(initialView = settingsView)
        val viewModel = FulfillmentViewModel(
            tenantSlug = "bordir-uji",
            remoteDataSource = fakeDataSource,
            scope = testScope
        )

        testScheduler.advanceUntilIdle()
        assertTrue(viewModel.uiState.value.hasAdminHubRoute)

        // Ubah mode rute menjadi DIRECT
        viewModel.onEvent(
            FulfillmentUiEvent.UpdateRouteModes(
                modes = mapOf(HandoverRouteCode("HOOPING_TO_EMBROIDERY") to HandoverMode.DIRECT)
            )
        )
        testScheduler.advanceUntilIdle()

        assertEquals(
            HandoverMode.DIRECT,
            fakeDataSource.lastUpdatedModes?.get(HandoverRouteCode("HOOPING_TO_EMBROIDERY"))
        )
        // Setelah diperbarui menjadi DIRECT, hasAdminHubRoute menjadi false
        assertFalse(viewModel.uiState.value.hasAdminHubRoute)
    }

    private class FakeFulfillmentRemoteDataSource(
        var initialView: HandoverRouteSettingsView? = null,
        var transfersList: List<InternalTransfer> = emptyList()
    ) : FulfillmentTransferRemoteDataSource {
        var lastSubmittedRouteCode: HandoverRouteCode? = null
        var lastSubmittedSack: String? = null
        var lastSubmittedRequestedBy: String? = null
        var lastUpdatedModes: Map<HandoverRouteCode, HandoverMode>? = null

        override suspend fun transfers(tenantSlug: String): Result<List<InternalTransfer>> =
            Result.success(transfersList)

        override suspend fun routeSettings(tenantSlug: String): Result<FulfillmentRouteConfig> =
            Result.success(FulfillmentRouteConfig(TenantId(tenantSlug)))

        override suspend fun routeSettingsView(tenantSlug: String): Result<HandoverRouteSettingsView> =
            Result.success(initialView ?: HandoverRouteSettingsView(TenantId(tenantSlug), emptyList()))

        override suspend fun updateRouteModes(
            tenantSlug: String,
            modes: Map<HandoverRouteCode, HandoverMode>
        ): Result<Unit> {
            lastUpdatedModes = modes
            initialView?.let { view ->
                val updated = view.settings.map { s ->
                    val newMode = modes[s.route.code] ?: s.mode
                    s.copy(mode = newMode, isExplicit = s.route.code in modes)
                }
                initialView = view.copy(settings = updated)
            }
            return Result.success(Unit)
        }

        override suspend fun routes(tenantSlug: String): Result<List<HandoverRoute>> =
            Result.success(initialView?.settings?.map { it.route } ?: emptyList())

        override suspend fun updateRoutes(tenantSlug: String, routes: List<HandoverRoute>): Result<Unit> =
            Result.success(Unit)

        override suspend fun submit(
            tenantSlug: String,
            sackPayload: String,
            routeCode: HandoverRouteCode,
            dispatchWeightKg: String?,
            dispatchScalePhotoKey: String?,
            requestedBy: String,
            notes: String,
            declaredPcs: Int?
        ): Result<InternalTransfer> {
            lastSubmittedRouteCode = routeCode
            lastSubmittedSack = sackPayload
            lastSubmittedRequestedBy = requestedBy
            val created = InternalTransfer(
                id = SackTransferId("TR-FAKE-1"),
                tenantId = TenantId(tenantSlug),
                sackCode = TraceCode(sackPayload),
                route = routeCode,
                handoverMode = initialView?.settings?.firstOrNull { it.route.code == routeCode }?.mode ?: HandoverMode.DIRECT,
                status = SackTransferStatus.DIANTAR,
                declaredPcs = declaredPcs ?: 10,
                sizeLabel = "M",
                colorway = "NAVY",
                requestedBy = requestedBy,
                requestedAt = Clock.System.now(),
                createdAt = Clock.System.now(),
                updatedAt = Clock.System.now()
            )
            transfersList = listOf(created) + transfersList
            return Result.success(created)
        }

        override suspend fun approve(
            tenantSlug: String,
            transferId: String,
            approverName: String,
            signatureKey: String
        ): Result<InternalTransfer> = Result.failure(NotImplementedError())

        override suspend fun reject(
            tenantSlug: String,
            transferId: String,
            reason: String,
            approverName: String
        ): Result<InternalTransfer> = Result.failure(NotImplementedError())

        override suspend fun resubmit(
            tenantSlug: String,
            transferId: String,
            dispatchWeightKg: String,
            dispatchScalePhotoKey: String,
            requestedBy: String
        ): Result<InternalTransfer> = Result.failure(NotImplementedError())

        override suspend fun receive(
            tenantSlug: String,
            transferId: String,
            proof: HandoverProof,
            receivedWeightKg: String?,
            receivedPcs: Int?,
            recordedBy: String
        ): Result<InternalTransfer> = Result.failure(NotImplementedError())

        override suspend fun uploadEvidence(
            tenantSlug: String,
            fileName: String,
            contentType: String,
            bytes: ByteArray
        ): Result<String> = Result.success("key-evidence-fake")
    }
}
