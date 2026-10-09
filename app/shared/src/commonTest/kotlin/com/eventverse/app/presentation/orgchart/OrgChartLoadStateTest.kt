package com.eventverse.app.presentation.orgchart

import com.eventverse.app.domain.orgchart.Department
import com.eventverse.app.domain.orgchart.HierarchyLevel
import com.eventverse.app.domain.orgchart.OrgNode
import com.eventverse.app.domain.orgchart.OrgNodeId
import com.eventverse.app.domain.rbac.AccessLevel
import com.eventverse.app.domain.rbac.ModuleAccessConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** TRD-PLAT-010 T1: terjemahan hasil klien ke keadaan, tanpa jaringan dan tanpa data contoh. */
class OrgChartLoadStateTest {

    // Fixture non-garment (klinik), bukan sampel konveksi.
    private val poli = Department.createCustom(name = "Poli Umum", shortName = "Poli", colorHex = 0xFF2563EB)
    private val dokter = OrgNode(
        id = OrgNodeId("emp-dokter"),
        name = "dr. Sari",
        email = "sari@klinik.id",
        department = poli,
        level = HierarchyLevel.HEAD_OF_DEPARTMENT,
        roleTitle = "Dokter Jaga"
    )

    @Test
    fun from_emptySuccess_isEmptyNotLoaded() {
        val state = OrgChartLoadState.from(Result.success(emptyList()), Result.success(emptyList()))
        assertEquals(OrgChartLoadState.Empty, state)
    }

    @Test
    fun from_failureOnEitherSide_isFailedNotHalfData() {
        val deptFail = OrgChartLoadState.from(Result.failure(IllegalStateException("HTTP 500")), Result.success(listOf(dokter)))
        val empFail = OrgChartLoadState.from(Result.success(listOf(poli)), Result.failure(IllegalStateException("offline")))
        assertEquals(OrgChartLoadState.Failed("HTTP 500"), deptFail)
        assertEquals(OrgChartLoadState.Failed("offline"), empFail)
    }

    @Test
    fun from_onlyDepartmentsOrOnlyEmployees_isLoaded() {
        assertIs<OrgChartLoadState.Loaded>(OrgChartLoadState.from(Result.success(listOf(poli)), Result.success(emptyList())))
        assertIs<OrgChartLoadState.Loaded>(OrgChartLoadState.from(Result.success(emptyList()), Result.success(listOf(dokter))))
    }

    @Test
    fun isResolved_onlyForEmptyAndLoaded() {
        assertFalse(OrgChartLoadState.Loading.isResolved)
        assertFalse(OrgChartLoadState.Failed("x").isResolved)
        assertTrue(OrgChartLoadState.Empty.isResolved)
        assertTrue(OrgChartLoadState.Loaded(listOf(poli), emptyList()).isResolved)
    }

    @Test
    fun seedNone_withoutClient_startsEmptyWithNoSample() {
        val state = OrgChartViewModel(tenantSlug = "klinik-uji").uiState.value
        assertEquals(OrgChartLoadState.Empty, state.loadState)
        assertTrue(state.departments.isEmpty())
        assertTrue(state.employees.isEmpty())
        assertNull(state.selectedDepartment)
    }

    @Test
    fun seedGarmentSample_isLoadedWithSample() {
        val state = OrgChartViewModel(tenantSlug = "wemade-demo", seed = OrgChartSeed.GarmentSample).uiState.value
        assertIs<OrgChartLoadState.Loaded>(state.loadState)
        assertTrue(state.employees.isNotEmpty())
        assertTrue(state.departments.isNotEmpty())
    }

    @Test
    fun loader_loadedWithOnlyDepartments_keepsNoSampleEmployees() {
        val loader = OrgChartDataLoader(ModuleAccessConfig(AccessLevel.MANAGE), null, null)
        val initial = loader.initialState(OrgChartSeed.GarmentSample, hasClient = true)
        val applied = loader.apply(initial, OrgChartLoadState.Loaded(listOf(poli), emptyList()))
        assertTrue(applied.employees.isEmpty())
        assertEquals(listOf(poli), applied.departments)
        assertEquals(poli, applied.selectedDepartment)
    }

    @Test
    fun loader_empty_clearsEverythingIncludingSample() {
        val loader = OrgChartDataLoader(ModuleAccessConfig(AccessLevel.MANAGE), null, null)
        val initial = loader.initialState(OrgChartSeed.GarmentSample, hasClient = true)
        val applied = loader.apply(initial, OrgChartLoadState.Empty)
        assertTrue(applied.employees.isEmpty())
        assertTrue(applied.departments.isEmpty())
        assertNull(applied.selectedDepartment)
        assertEquals(OrgChartLoadState.Empty, applied.loadState)
    }
}
