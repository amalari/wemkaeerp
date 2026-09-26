package com.eventverse.app.domain.crm

import com.eventverse.app.domain.orgchart.OrgNode
import com.eventverse.app.domain.orgchart.OrgNodeId
import com.eventverse.app.domain.orgchart.SubordinateResolver
import com.eventverse.app.domain.rbac.DataScope

/**
 * Filters a list of [CrmLead] by [DataScope] — the CRM equivalent of
 * [com.eventverse.app.domain.orgchart.OrgChartVisibility.visibleTo], sharing the same
 * [SubordinateResolver] reach computation so "bawahan" cannot mean two different things in
 * two different modules.
 *
 * Pure domain service: no I/O. Run twice, same as the org chart precedent — once
 * authoritatively on the server (in the `WHERE owner_employee_id IN (...)` predicate the
 * repository builds from this same reach set) and once on the client so the screen is
 * immediately consistent with what the server will actually return.
 */
object LeadScope {

    /**
     * @param employees every employee of one tenant, used only to compute the viewer's reach
     * @param leads the leads to filter
     */
    fun visibleTo(
        leads: List<CrmLead>,
        scope: DataScope,
        employees: List<OrgNode>,
        viewerEmployeeId: OrgNodeId?,
        viewerDepartmentId: String?
    ): List<CrmLead> = when (scope) {
        DataScope.ALL_TENANT_DATA -> leads

        DataScope.SUBORDINATE_DATA -> {
            val reachIds = SubordinateResolver.reachableEmployeeIds(employees, viewerEmployeeId, viewerDepartmentId)
            // A lead with no owner is visible only to an unrestricted (ALL_TENANT_DATA) caller —
            // mirroring OrgChartDataReach.allowsDepartment's rule that a null department is
            // visible only to an unrestricted viewer.
            leads.filter { it.ownerEmployeeId != null && it.ownerEmployeeId in reachIds }
        }

        DataScope.OWN_DATA_ONLY ->
            viewerEmployeeId?.let { id -> leads.filter { it.ownerEmployeeId == id } }.orEmpty()
    }

    /** The reach set alone, for repositories that push the predicate into SQL instead of filtering in memory. */
    fun reachableOwnerIds(
        scope: DataScope,
        employees: List<OrgNode>,
        viewerEmployeeId: OrgNodeId?,
        viewerDepartmentId: String?
    ): Set<OrgNodeId>? = when (scope) {
        DataScope.ALL_TENANT_DATA -> null // null = unrestricted, no predicate needed
        DataScope.SUBORDINATE_DATA -> SubordinateResolver.reachableEmployeeIds(employees, viewerEmployeeId, viewerDepartmentId)
        DataScope.OWN_DATA_ONLY -> viewerEmployeeId?.let { setOf(it) } ?: emptySet()
    }
}
