package com.eventverse.app.domain.crm

import com.eventverse.app.domain.orgchart.OrgNodeId
import com.eventverse.app.domain.tenant.TenantId
import kotlinx.datetime.Instant
import kotlin.jvm.JvmInline

@JvmInline
value class LeadActivityId(val value: String) {
    init {
        require(value.isNotBlank()) { "LeadActivityId cannot be blank" }
    }
}

/**
 * An activity/comment entry for a CRM Lead.
 * Tracks what sales reps did to engage or follow-up with the prospect.
 */
data class LeadActivity(
    val id: LeadActivityId,
    val tenantId: TenantId,
    val leadId: LeadId,
    val authorEmployeeId: OrgNodeId?,
    val authorName: String,
    val content: String,
    val createdAt: Instant
) {
    init {
        require(content.isNotBlank()) { "Activity content cannot be blank" }
        require(content.length <= 2000) { "Activity content must not exceed 2000 characters" }
    }
}

interface LeadActivityRepository {
    suspend fun findByLeadId(tenantId: TenantId, leadId: LeadId): List<LeadActivity>
    suspend fun countByLeadIds(tenantId: TenantId, leadIds: List<LeadId>): Map<LeadId, Int>
    suspend fun save(activity: LeadActivity): Result<LeadActivity>
}
