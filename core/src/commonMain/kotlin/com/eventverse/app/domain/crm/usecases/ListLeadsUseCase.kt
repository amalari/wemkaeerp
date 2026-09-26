package com.eventverse.app.domain.crm.usecases

import com.eventverse.app.domain.crm.CrmLead
import com.eventverse.app.domain.crm.CrmLeadRepository
import com.eventverse.app.domain.crm.LeadScope
import com.eventverse.app.domain.orgchart.OrgNode
import com.eventverse.app.domain.orgchart.OrgNodeId
import com.eventverse.app.domain.rbac.DataScope
import com.eventverse.app.domain.tenant.TenantId

/**
 * Lists a tenant's active leads, restricted by [DataScope].
 *
 * [scope] is a required parameter with no default and no nullable fallback to
 * "unrestricted" — a nullable-defaulting scope is exactly how "forgot to filter" becomes a
 * data leak, the same lesson `OrgChartAccessGuard.kt`'s header documents.
 */
class ListLeadsUseCase(
    private val leadRepository: CrmLeadRepository
) {
    suspend operator fun invoke(
        tenantId: TenantId,
        scope: DataScope,
        employees: List<OrgNode>,
        viewerEmployeeId: OrgNodeId?,
        viewerDepartmentId: String?
    ): Result<List<CrmLead>> = runCatching {
        val reachIds = LeadScope.reachableOwnerIds(scope, employees, viewerEmployeeId, viewerDepartmentId)
        leadRepository.findActive(tenantId, reachIds)
    }
}
