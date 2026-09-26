package com.eventverse.app.domain.crm.usecases

import com.eventverse.app.domain.crm.LeadActivity
import com.eventverse.app.domain.crm.LeadActivityId
import com.eventverse.app.domain.crm.LeadActivityRepository
import com.eventverse.app.domain.crm.LeadId
import com.eventverse.app.domain.orgchart.OrgNodeId
import com.eventverse.app.domain.tenant.TenantId
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

class AddLeadActivityUseCase(
    private val activityRepository: LeadActivityRepository
) {
    suspend operator fun invoke(
        tenantId: TenantId,
        leadId: LeadId,
        authorEmployeeId: OrgNodeId?,
        authorName: String,
        content: String,
        activityId: LeadActivityId = LeadActivityId("act-${tenantId.value}-${Clock.System.now().toEpochMilliseconds()}"),
        createdAt: Instant = Clock.System.now()
    ): Result<LeadActivity> = runCatching {
        val activity = LeadActivity(
            id = activityId,
            tenantId = tenantId,
            leadId = leadId,
            authorEmployeeId = authorEmployeeId,
            authorName = authorName.ifBlank { "Sales" },
            content = content.trim(),
            createdAt = createdAt
        )
        activityRepository.save(activity).getOrThrow()
    }
}
