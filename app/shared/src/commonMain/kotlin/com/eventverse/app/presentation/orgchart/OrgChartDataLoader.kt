package com.eventverse.app.presentation.orgchart

import com.eventverse.app.domain.orgchart.Department
import com.eventverse.app.domain.orgchart.HierarchyLevel
import com.eventverse.app.domain.orgchart.OrgChartVisibility
import com.eventverse.app.domain.orgchart.OrgNode
import com.eventverse.app.domain.orgchart.OrgNodeId
import com.eventverse.app.domain.rbac.DataScope
import com.eventverse.app.domain.rbac.ModuleAccessConfig
import com.eventverse.app.infrastructure.api.OrgChartApiClient
import kotlinx.coroutines.CancellationException

/**
 * Pemuatan data Org Chart (TRD-PLAT-010 T1): keadaan awal per [OrgChartSeed], membaca server menjadi
 * [OrgChartLoadState], dan menerapkan keadaan itu ke [OrgChartUiState]. Murni terhadap `ViewModel`
 * (tanpa coroutine scope, tanpa state sendiri) sehingga bisa diuji tanpa jaringan.
 *
 * Aturan yang ditegakkan di sini: data live MENANG PENUH. Tidak ada kondisi `isNotEmpty()` yang menahan
 * data contoh, tidak ada `catch` senyap; respons sukses kosong = [OrgChartLoadState.Empty], galat = [OrgChartLoadState.Failed].
 */
internal class OrgChartDataLoader(
    private val access: ModuleAccessConfig,
    private val viewerDepartmentId: String?,
    private val viewerEmployeeId: String?
) {
    val isScoped: Boolean
        get() = access.scope != DataScope.ALL_TENANT_DATA && !viewerDepartmentId.isNullOrBlank()

    fun matchDepartment(depts: List<Department>, query: String?): Department? {
        if (query.isNullOrBlank()) return null
        return depts.find { it.id.value.equals(query, ignoreCase = true) }
            ?: depts.find { it.code.equals(query, ignoreCase = true) }
            ?: depts.find { query.contains(it.code, ignoreCase = true) || it.code.contains(query, ignoreCase = true) }
            ?: depts.find { it.displayName.contains(query, ignoreCase = true) || query.contains(it.displayName, ignoreCase = true) }
    }

    fun filterByScope(nodes: List<OrgNode>, deptId: String?): List<OrgNode> {
        if (!isScoped) return nodes
        return OrgChartVisibility.visibleTo(
            nodes = nodes,
            scope = access.scope,
            viewerEmployeeId = viewerEmployeeId?.let { OrgNodeId(it) },
            viewerDepartmentId = deptId ?: viewerDepartmentId
        )
    }

    /** Keadaan awal. [OrgChartSeed.None]: tanpa data; menunggu server bila [hasClient], selain itu langsung kosong. */
    fun initialState(seed: OrgChartSeed, hasClient: Boolean): OrgChartUiState = when (seed) {
        OrgChartSeed.None -> OrgChartUiState(
            departments = emptyList(),
            selectedDepartment = null,
            isDepartmentLocked = isScoped,
            isCreatingNew = access.canWrite,
            loadState = if (hasClient) OrgChartLoadState.Loading else OrgChartLoadState.Empty
        )
        OrgChartSeed.GarmentSample -> garmentSampleState()
    }

    private fun garmentSampleState(): OrgChartUiState {
        val sampleList = OrgNode.createSampleEmployees()
        val defaultDepts = Department.defaultPresets()
        val matchedDept = matchDepartment(defaultDepts, viewerDepartmentId)
        val targetDept = matchedDept ?: defaultDepts.firstOrNull { it.id.value == "dept-sales" } ?: defaultDepts.firstOrNull() ?: Department.SALES

        val scopedSampleList = filterByScope(sampleList, targetDept.id.value)
        val initialSuperior = resolveDefaultSuperior(scopedSampleList, targetDept, HierarchyLevel.STAFF_OPERATOR)
        val initialSelectedEmpId = if (!access.canWrite) {
            val viewerNode = if (viewerEmployeeId != null) scopedSampleList.find { it.id.value == viewerEmployeeId } else null
            viewerNode?.id?.value
                ?: scopedSampleList.find { it.department?.id == targetDept.id && it.level == HierarchyLevel.HEAD_OF_DEPARTMENT }?.id?.value
                ?: scopedSampleList.find { it.department?.id == targetDept.id }?.id?.value
                ?: scopedSampleList.firstOrNull()?.id?.value
        } else {
            null
        }
        val departments = if (isScoped) listOf(targetDept) else defaultDepts
        return OrgChartUiState(
            employees = scopedSampleList,
            departments = departments,
            selectedDepartment = targetDept,
            isDepartmentLocked = isScoped,
            isCreatingNew = access.canWrite,
            selectedEmployeeId = initialSelectedEmpId,
            selectedReportsToId = initialSuperior,
            loadState = OrgChartLoadState.Loaded(departments, scopedSampleList)
        )
    }

    /** Membaca divisi dan karyawan; pengecualian non-pembatalan menjadi [OrgChartLoadState.Failed], bukan data lain. */
    suspend fun fetch(client: OrgChartApiClient, tenantSlug: String): OrgChartLoadState = try {
        OrgChartLoadState.from(client.getDepartments(tenantSlug), client.getEmployees(tenantSlug))
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        OrgChartLoadState.Failed(e.message?.takeIf { it.isNotBlank() } ?: "Gagal memuat struktur organisasi.")
    }

    fun beginLoading(state: OrgChartUiState): OrgChartUiState = state.copy(loadState = OrgChartLoadState.Loading)

    /** Menerapkan hasil [loaded] ke [state]. Data live menang penuh; tidak ada sisa data contoh. */
    fun apply(state: OrgChartUiState, loaded: OrgChartLoadState): OrgChartUiState = when (loaded) {
        OrgChartLoadState.Loading, is OrgChartLoadState.Failed -> state.copy(loadState = loaded)
        OrgChartLoadState.Empty -> state.copy(
            departments = emptyList(),
            employees = emptyList(),
            selectedDepartment = null,
            selectedEmployeeId = null,
            selectedReportsToId = null,
            isDepartmentLocked = isScoped,
            isCreatingNew = access.canWrite,
            loadState = loaded
        )
        is OrgChartLoadState.Loaded -> applyLoaded(state, loaded)
    }

    private fun applyLoaded(state: OrgChartUiState, loaded: OrgChartLoadState.Loaded): OrgChartUiState {
        val liveDepts = loaded.departments
        val matchedLiveDept = matchDepartment(liveDepts, viewerDepartmentId)
        val firstLoad = state.loadState !is OrgChartLoadState.Loaded
        val activeLiveDept = when {
            state.selectedDepartment != null ->
                liveDepts.find { it.id == state.selectedDepartment.id } ?: matchedLiveDept ?: liveDepts.firstOrNull()
            // Pemuatan ulang: null berarti pengguna sedang di tampilan Direksi; pertahankan.
            !firstLoad -> null
            else -> matchedLiveDept ?: liveDepts.firstOrNull()
        }

        val effectiveLiveEmps = filterByScope(loaded.employees, activeLiveDept?.id?.value)
        val effectiveLiveDepts = if (isScoped && activeLiveDept != null) listOf(activeLiveDept) else liveDepts

        val validSuperior = if (state.selectedReportsToId != null && effectiveLiveEmps.any { it.id.value == state.selectedReportsToId }) {
            state.selectedReportsToId
        } else if (activeLiveDept != null) {
            resolveDefaultSuperior(effectiveLiveEmps, activeLiveDept, state.selectedLevel)
        } else {
            null
        }

        val effectiveSelectedEmpId = if (!access.canWrite) {
            if (state.selectedEmployeeId != null && effectiveLiveEmps.any { it.id.value == state.selectedEmployeeId }) {
                state.selectedEmployeeId
            } else if (activeLiveDept == null) {
                effectiveLiveEmps.find { it.level == HierarchyLevel.EXECUTIVE }?.id?.value
                    ?: effectiveLiveEmps.firstOrNull()?.id?.value
            } else {
                val viewerNode = if (viewerEmployeeId != null) effectiveLiveEmps.find { it.id.value == viewerEmployeeId } else null
                viewerNode?.id?.value
                    ?: effectiveLiveEmps.find { it.department?.id == activeLiveDept.id && it.level == HierarchyLevel.HEAD_OF_DEPARTMENT }?.id?.value
                    ?: effectiveLiveEmps.find { it.department?.id == activeLiveDept.id }?.id?.value
                    ?: effectiveLiveEmps.firstOrNull()?.id?.value
            }
        } else {
            state.selectedEmployeeId?.takeIf { id -> effectiveLiveEmps.any { it.id.value == id } }
        }

        return state.copy(
            departments = effectiveLiveDepts,
            employees = effectiveLiveEmps,
            selectedDepartment = activeLiveDept,
            isDepartmentLocked = isScoped,
            isCreatingNew = access.canWrite,
            selectedEmployeeId = effectiveSelectedEmpId,
            selectedReportsToId = validSuperior,
            loadState = loaded
        )
    }
}
