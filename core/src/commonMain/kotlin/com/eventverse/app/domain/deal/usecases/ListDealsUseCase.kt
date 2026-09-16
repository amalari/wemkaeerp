package com.eventverse.app.domain.deal.usecases

import com.eventverse.app.domain.crm.LeadScope
import com.eventverse.app.domain.deal.Deal
import com.eventverse.app.domain.deal.DealRepository
import com.eventverse.app.domain.orgchart.OrgNode
import com.eventverse.app.domain.orgchart.OrgNodeId
import com.eventverse.app.domain.rbac.DataScope
import com.eventverse.app.domain.tenant.TenantId

/**
 * Lists active deals, filtered by the caller's `DataScope` reach — computed by the caller
 * (a route guard) via [LeadScope.reachableOwnerIds], the same explicit contract
 * [com.eventverse.app.domain.crm.usecases.ListLeadsUseCase] uses so a defaulted scope can
 * never silently become a data leak.
 */
class ListDealsUseCase(
    private val dealRepository: DealRepository
) {
    suspend operator fun invoke(
        tenantId: TenantId,
        scope: DataScope,
        employees: List<OrgNode>,
        viewerEmployeeId: OrgNodeId?,
        viewerDepartmentId: String?
    ): Result<List<Deal>> = runCatching {
        val reachIds = LeadScope.reachableOwnerIds(scope, employees, viewerEmployeeId, viewerDepartmentId)
        dealRepository.findActive(tenantId, reachIds).sortedByDescending { it.updatedAt }
    }
}
