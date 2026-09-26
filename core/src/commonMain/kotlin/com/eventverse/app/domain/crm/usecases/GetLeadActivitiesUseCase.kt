package com.eventverse.app.domain.crm.usecases

import com.eventverse.app.domain.crm.LeadActivity
import com.eventverse.app.domain.crm.LeadActivityRepository
import com.eventverse.app.domain.crm.LeadId
import com.eventverse.app.domain.tenant.TenantId

class GetLeadActivitiesUseCase(
    private val activityRepository: LeadActivityRepository
) {
    suspend operator fun invoke(
        tenantId: TenantId,
        leadId: LeadId
    ): Result<List<LeadActivity>> = runCatching {
        activityRepository.findByLeadId(tenantId, leadId)
    }
}
