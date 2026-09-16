package com.eventverse.app.domain.crm

import com.eventverse.app.domain.customfield.CustomAttributes
import com.eventverse.app.domain.moduledev.MoneyIdr
import com.eventverse.app.domain.orgchart.OrgNodeId
import com.eventverse.app.domain.tenant.TenantId
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate

/**
 * A tenant's own sales prospect for `BusinessModule.CRM_SALES`.
 *
 * NOT the same thing as `com.eventverse.app.domain.prospect.ProspectLead` — that entity is
 * WeMade's own platform-global sales funnel for factories considering becoming a tenant
 * (`ops` schema, no `tenantId` at all). This entity is one tenant's own customer/prospect
 * data. The different name and package exist specifically so the two are never confused.
 *
 * Immutable by default; mutation happens through the functions below, each producing a new
 * copy — the same pattern every other entity in this codebase (`Event`, `OrgNode`, ...) uses.
 */
data class CrmLead(
    val id: LeadId,
    val tenantId: TenantId,
    val brandName: BrandName = BrandName(""),
    val contactPerson: String = "",
    val whatsappNumber: WhatsappNumber? = null,
    val email: String = "",
    val stage: LeadStage = LeadStage.NEW_LEAD,
    val source: LeadSource = LeadSource.UNSPECIFIED,
    val estimatedPcs: Int? = null,
    val estimatedValue: MoneyIdr? = null,
    /** Drives `DataScope`. Null means unassigned — visible only to an unrestricted viewer. */
    val ownerEmployeeId: OrgNodeId? = null,
    val expectedCloseDate: LocalDate? = null,
    val productCategory: ProductCategory = ProductCategory.EMPTY,
    val lastContactedAt: Instant? = null,
    val customAttributes: CustomAttributes = CustomAttributes.EMPTY,
    val createdByUserId: String? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
    val archivedAt: Instant? = null,
    val activityCount: Int = 0
) {
    init {
        require(estimatedPcs == null || estimatedPcs >= 0) { "estimatedPcs cannot be negative" }
    }

    /** Display name prioritising brand name, then contact person, then phone number, with fallback to lead ID. */
    val title: String
        get() = if (brandName.value.isNotBlank()) brandName.value
        else if (contactPerson.isNotBlank()) contactPerson
        else if (whatsappNumber != null) whatsappNumber.localDisplay
        else "Prospek #${id.value.takeLast(6)}"

    val isArchived: Boolean get() = archivedAt != null

    fun rename(newBrandName: BrandName, now: Instant): CrmLead = copy(brandName = newBrandName, updatedAt = now)

    /** Refuses an illegal transition per [LeadStage.canTransitionTo] — the domain invariant. */
    fun transitionTo(newStage: LeadStage, now: Instant): Result<CrmLead> = runCatching {
        require(stage.canTransitionTo(newStage)) {
            "Tidak bisa memindahkan lead dari ${stage.displayName} ke ${newStage.displayName}"
        }
        copy(stage = newStage, updatedAt = now)
    }

    fun reassignOwner(newOwner: OrgNodeId?, now: Instant): CrmLead = copy(ownerEmployeeId = newOwner, updatedAt = now)

    fun withCustomAttributes(attrs: CustomAttributes, now: Instant): CrmLead =
        copy(customAttributes = attrs, updatedAt = now)

    fun archive(now: Instant): CrmLead = copy(archivedAt = now, updatedAt = now)
}
