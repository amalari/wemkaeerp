package com.eventverse.app.domain.crm

import com.eventverse.app.domain.crm.usecases.GetCrmLeadKpiMetricsUseCase
import com.eventverse.app.domain.moduledev.MoneyIdr
import com.eventverse.app.domain.tenant.TenantId
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class GetCrmLeadKpiMetricsUseCaseTest {

    private val now = Instant.parse("2026-03-01T12:00:00Z")

    private fun sampleLead(
        id: String,
        stage: LeadStage,
        amount: Long? = null,
        productCategory: String? = null,
        lastContactedAt: Instant? = null,
        activityCount: Int = 1
    ) = CrmLead(
        id = LeadId(id),
        tenantId = TenantId("ten-demo-001"),
        brandName = BrandName("Brand $id"),
        stage = stage,
        estimatedValue = amount?.let { MoneyIdr(it) },
        productCategory = ProductCategory(productCategory ?: ""),
        lastContactedAt = lastContactedAt,
        activityCount = activityCount,
        createdAt = now,
        updatedAt = now
    )

    @Test
    fun invoke_calculatesMetricsAccurately() {
        val useCase = GetCrmLeadKpiMetricsUseCase()
        val leads = listOf(
            sampleLead("1", LeadStage.NEW_LEAD, 50_000_000L, "Kemeja Tactical", now, activityCount = 1),
            sampleLead("2", LeadStage.QUALIFIED, 150_000_000L, "Jaket Bomber", now, activityCount = 2),
            sampleLead("3", LeadStage.UNQUALIFIED, 20_000_000L, "Kaos Polos", null, activityCount = 0),
            sampleLead("4", LeadStage.QUALIFIED, 100_000_000L, "Seragam Kerja", now, activityCount = 1)
        )

        val metrics = useCase(leads, now)

        // Total Pipeline Value: 50m + 150m + 20m + 100m = 320m
        assertEquals(320_000_000L, metrics.totalPipelineValue)
        // Active Leads: NEW_LEAD + QUALIFIED = 3
        assertEquals(3, metrics.activeLeadsCount)
        // Qualified Conversion Rate: 2 qualified / 4 non-archived = 50%
        assertEquals(50.0, metrics.qualifiedConversionRate)
    }

    @Test
    fun invoke_countsFollowUpNeededForLeadsWithoutContactOrActivity() {
        val useCase = GetCrmLeadKpiMetricsUseCase()
        val leads = listOf(
            sampleLead("1", LeadStage.NEW_LEAD, 50_000_000L, "Kemeja", null, activityCount = 0), // Needs follow up
            sampleLead("2", LeadStage.QUALIFIED, 100_000_000L, "Jaket", now, activityCount = 2)  // Contacted recently
        )

        val metrics = useCase(leads, now)
        assertEquals(1, metrics.followUpNeededCount)
        assertEquals(150_000_000L, metrics.totalPipelineValue)
        assertEquals(2, metrics.activeLeadsCount)
        assertEquals(50.0, metrics.qualifiedConversionRate)
    }

    @Test
    fun invoke_handlesEmptyLeadsGracefully() {
        val useCase = GetCrmLeadKpiMetricsUseCase()
        val emptyList = emptyList<CrmLead>()
        val metrics = useCase(emptyList, now)

        assertEquals(0L, metrics.totalPipelineValue)
        assertEquals(0, metrics.activeLeadsCount)
        assertEquals(0.0, metrics.qualifiedConversionRate)
        assertEquals(0, metrics.followUpNeededCount)
    }
}
