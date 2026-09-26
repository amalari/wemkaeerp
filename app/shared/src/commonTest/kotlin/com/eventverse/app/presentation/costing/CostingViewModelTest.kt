package com.eventverse.app.presentation.costing

import com.eventverse.app.domain.contracts.CostingCalculationResult
import com.eventverse.app.domain.costing.*
import com.eventverse.app.domain.pipeline.CostingBehavior
import com.eventverse.app.domain.pipeline.FlowHealthStatus
import com.eventverse.app.domain.rbac.*
import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.infrastructure.api.CostingRemoteDataSource
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
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class CostingViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private lateinit var fakeRemote: FakeCostingRemoteDataSource

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeRemote = FakeCostingRemoteDataSource()
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun viewModel_withoutViewCostingMarginPermission_marginAndSellingPriceHidden() = testScope.runTest {
        // Operator with OPERATE access level but roleTitle is "Operator Jahit" (not Sales, not Manage/Owner)
        val operatorDecision = AccessDecision(
            config = ModuleAccessConfig(level = AccessLevel.OPERATE),
            source = AccessSource.ROLE,
            fromRole = ModuleAccessConfig(level = AccessLevel.OPERATE),
            fromDepartment = ModuleAccessConfig(level = AccessLevel.NONE)
        )
        val operatorPersona = TestingPersona(
            userId = "u-op-1",
            name = "Budi Operator",
            tenantId = TenantId("berkah-garment"),
            tenantSlug = "berkah-garment",
            departmentId = "dept-1",
            departmentName = "Produksi",
            roleId = RoleId("role-op"),
            roleTitle = "Operator Jahit",
            isOwnerOrSuperAdmin = false
        )

        val vm = CostingViewModel(
            tenantSlug = "berkah-garment",
            decision = operatorDecision,
            persona = operatorPersona,
            remoteDataSource = fakeRemote,
            scope = testScope
        )
        testScheduler.advanceUntilIdle()

        // Assert margin is hidden and canApprove is false
        assertFalse(vm.showMargin)
        assertFalse(vm.uiState.value.showMargin)
        assertFalse(vm.canApprove)
        assertFalse(vm.uiState.value.canApprove)
    }

    @Test
    fun viewModel_salesRole_canViewMargin_butCannotApprove() = testScope.runTest {
        // Sales Representative with VIEW access level
        val salesDecision = AccessDecision(
            config = ModuleAccessConfig(level = AccessLevel.VIEW),
            source = AccessSource.ROLE,
            fromRole = ModuleAccessConfig(level = AccessLevel.VIEW),
            fromDepartment = ModuleAccessConfig(level = AccessLevel.NONE)
        )
        val salesPersona = TestingPersona(
            userId = "u-sales-1",
            name = "Siti Sales",
            tenantId = TenantId("berkah-garment"),
            tenantSlug = "berkah-garment",
            departmentId = "dept-sales",
            departmentName = "Penjualan",
            roleId = RoleId("role-sales"),
            roleTitle = "Sales Representative",
            isOwnerOrSuperAdmin = false
        )

        val vm = CostingViewModel(
            tenantSlug = "berkah-garment",
            decision = salesDecision,
            persona = salesPersona,
            remoteDataSource = fakeRemote,
            scope = testScope
        )
        testScheduler.advanceUntilIdle()

        // Assert canApprove is false while showMargin is true
        assertFalse(vm.canApprove)
        assertFalse(vm.uiState.value.canApprove)
        assertTrue(vm.showMargin)
        assertTrue(vm.uiState.value.showMargin)

        // Attempt approve should be denied client-side and set error status message
        vm.onEvent(CostingUiEvent.Approve("costing-1"))
        testScheduler.advanceUntilIdle()

        assertEquals(0, fakeRemote.approvedSheetIds.size)
        assertNotNull(vm.uiState.value.statusMessage)
        assertTrue(vm.uiState.value.statusMessage!!.contains("Akses ditolak: Anda tidak memiliki wewenang APPROVE_COSTING"))
        assertTrue(vm.uiState.value.isErrorMessage)
    }

    @Test
    fun viewModel_withApproveCostingPermission_canApproveIsTrue_andCallsApprove() = testScope.runTest {
        // Factory Manager with MANAGE level
        val managerDecision = AccessDecision(
            config = ModuleAccessConfig(level = AccessLevel.MANAGE),
            source = AccessSource.ROLE,
            fromRole = ModuleAccessConfig(level = AccessLevel.MANAGE),
            fromDepartment = ModuleAccessConfig(level = AccessLevel.NONE)
        )
        val managerPersona = TestingPersona(
            userId = "u-mgr-1",
            name = "Pak Bambang",
            tenantId = TenantId("berkah-garment"),
            tenantSlug = "berkah-garment",
            departmentId = "dept-prod",
            departmentName = "Pabrik",
            roleId = RoleId("role-mgr"),
            roleTitle = "Factory Manager",
            isOwnerOrSuperAdmin = false
        )

        val vm = CostingViewModel(
            tenantSlug = "berkah-garment",
            decision = managerDecision,
            persona = managerPersona,
            remoteDataSource = fakeRemote,
            scope = testScope
        )
        testScheduler.advanceUntilIdle()

        assertTrue(vm.canApprove)
        assertTrue(vm.uiState.value.canApprove)
        assertTrue(vm.showMargin)
        assertTrue(vm.uiState.value.showMargin)

        // Execute approve
        vm.onEvent(CostingUiEvent.Approve("costing-1"))
        testScheduler.advanceUntilIdle()

        assertEquals(listOf("costing-1"), fakeRemote.approvedSheetIds)
        assertNotNull(vm.uiState.value.statusMessage)
        assertTrue(vm.uiState.value.statusMessage!!.contains("berhasil disetujui"))
        assertFalse(vm.uiState.value.isErrorMessage)
    }

    @Test
    fun viewModel_loadInitialData_populatesSheetsAndTelemetry() = testScope.runTest {
        val vm = CostingViewModel(
            tenantSlug = "berkah-garment",
            remoteDataSource = fakeRemote,
            scope = testScope
        )
        testScheduler.advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(1, state.sheets.size)
        assertEquals("costing-1", state.selectedSheet?.id?.value)
        val telemetry = state.telemetry
        assertNotNull(telemetry)
        assertEquals(1, telemetry.pendingSheetCount)
        assertEquals(FlowHealthStatus.HEALTHY, telemetry.healthStatus)
    }
}

class FakeCostingRemoteDataSource : CostingRemoteDataSource {
    val approvedSheetIds = mutableListOf<String>()

    private val sampleSheet = CostingSheet(
        id = CostingSheetId("costing-1"),
        tenantId = TenantId("berkah-garment"),
        number = CostingNumber("HPP-2026-0001"),
        techPackId = "tp-polo-01",
        orderQuantity = 1000L,
        behavior = CostingBehavior.FULL_PACKAGE_COGS,
        status = CostingSheetStatus.PENDING_APPROVAL,
        pricingAsOf = Clock.System.now(),
        createdAt = Clock.System.now(),
        updatedAt = Clock.System.now()
    )

    override suspend fun getSheets(
        tenantSlug: String,
        techPackId: String?,
        status: CostingSheetStatus?
    ): Result<List<CostingSheet>> = Result.success(listOf(sampleSheet))

    override suspend fun getSheet(tenantSlug: String, id: String): Result<CostingSheet> =
        Result.success(sampleSheet)

    override suspend fun createDraft(
        tenantSlug: String,
        techPackId: String,
        orderQuantity: Long,
        behavior: CostingBehavior,
        overrides: Map<String, String>
    ): Result<CostingSheet> = Result.success(sampleSheet)

    override suspend fun calculate(
        tenantSlug: String,
        id: String,
        nodeParams: Map<String, String>
    ): Result<CostingSheet> = Result.success(sampleSheet)

    override suspend fun reprice(
        tenantSlug: String,
        id: String,
        pricingAsOf: kotlinx.datetime.Instant?
    ): Result<CostingSheet> = Result.success(sampleSheet)

    override suspend fun overrideParameters(
        tenantSlug: String,
        id: String,
        overrides: Map<String, String>
    ): Result<CostingSheet> = Result.success(sampleSheet)

    override suspend fun submitForApproval(
        tenantSlug: String,
        id: String
    ): Result<CostingSheet> = Result.success(sampleSheet.copy(status = CostingSheetStatus.PENDING_APPROVAL))

    override suspend fun approve(tenantSlug: String, id: String): Result<CostingSheet> {
        approvedSheetIds.add(id)
        val snapshot = CostingSnapshot(
            snapshotId = "snap-$id",
            approvedAt = Clock.System.now(),
            approvedByUserId = "u-mgr-1",
            result = CostingCalculationResult(
                costingId = id,
                tenantId = TenantId(tenantSlug),
                techPackId = sampleSheet.techPackId,
                orderQuantity = sampleSheet.orderQuantity,
                behavior = sampleSheet.behavior,
                calculatedAt = Clock.System.now()
            ),
            inputFingerprint = "fp-$id"
        )
        return Result.success(sampleSheet.copy(status = CostingSheetStatus.APPROVED, approvedSnapshot = snapshot))
    }

    override suspend fun reject(
        tenantSlug: String,
        id: String,
        reason: String
    ): Result<CostingSheet> = Result.success(sampleSheet.copy(status = CostingSheetStatus.REJECTED, rejectionReason = reason))

    override suspend fun revise(tenantSlug: String, id: String): Result<CostingSheet> =
        Result.success(sampleSheet.copy(status = CostingSheetStatus.DRAFT))

    override suspend fun getActiveRateCard(
        tenantSlug: String,
        behavior: CostingBehavior
    ): Result<CostingRateCard> = Result.failure(NoSuchElementException("No rate card"))

    override suspend fun updateRateCard(
        tenantSlug: String,
        card: CostingRateCard
    ): Result<CostingRateCard> = Result.success(card)

    override suspend fun getTelemetry(tenantSlug: String): Result<CostingNodeTelemetry> =
        Result.success(
            CostingNodeTelemetry(
                pendingSheetCount = 1,
                overdueApprovalCount = 0,
                avgApprovalCycleHours = 12.5,
                healthStatus = FlowHealthStatus.HEALTHY
            )
        )
}
