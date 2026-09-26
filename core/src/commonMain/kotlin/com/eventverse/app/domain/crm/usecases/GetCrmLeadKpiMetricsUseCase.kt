package com.eventverse.app.domain.crm.usecases

import com.eventverse.app.domain.crm.CrmLead
import com.eventverse.app.domain.crm.CrmLeadKpiMetrics
import com.eventverse.app.domain.crm.LeadStage
import kotlinx.datetime.Instant
import kotlin.time.Duration.Companion.hours

/**
 * Pure domain usecase to compute executive KPI metrics for CRM Sales.
 */
class GetCrmLeadKpiMetricsUseCase {
    operator fun invoke(leads: List<CrmLead>, now: Instant): CrmLeadKpiMetrics {
        val nonArchived = leads.filter { !it.isArchived }
        if (nonArchived.isEmpty()) {
            return CrmLeadKpiMetrics()
        }

        val totalPipeline = nonArchived.sumOf { it.estimatedValue?.amount ?: 0L }
        val activeLeads = nonArchived.filter { it.stage != LeadStage.UNQUALIFIED }
        val qualifiedCount = nonArchived.count { it.stage == LeadStage.QUALIFIED }

        val conversionRate = if (nonArchived.isNotEmpty()) {
            ((qualifiedCount.toDouble() / nonArchived.size.toDouble()) * 1000.0).toInt() / 10.0
        } else 0.0

        val twentyFourHoursAgo = now - 24.hours
        val followUpNeeded = activeLeads.count { lead ->
            val contactTime = lead.lastContactedAt ?: lead.createdAt
            contactTime < twentyFourHoursAgo || lead.activityCount == 0
        }

        return CrmLeadKpiMetrics(
            totalPipelineValue = totalPipeline,
            activeLeadsCount = activeLeads.size,
            qualifiedConversionRate = conversionRate,
            followUpNeededCount = followUpNeeded
        )
    }
}
