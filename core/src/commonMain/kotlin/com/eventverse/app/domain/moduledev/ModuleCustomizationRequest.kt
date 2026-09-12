package com.eventverse.app.domain.moduledev

import com.eventverse.app.domain.tenant.TenantId
import kotlinx.datetime.Instant

/**
 * A factory asking for something the product does not do yet.
 *
 * The only tenant-owned entity in this package, and the only one whose table carries RLS. Our
 * build costs and hourly rates live on platform-global tables that have no `tenant_id` at all, so
 * the separation between "what we charge" and "what it cost us" is a table boundary rather than a
 * permission check somebody has to remember to write.
 */
data class ModuleCustomizationRequest(
    val id: CustomizationRequestId,
    val tenantId: TenantId,
    val title: String,
    /**
     * The request in the customer's own words, stored verbatim.
     *
     * Never cleaned up, summarised or rewritten. It is the richest context any estimator gets —
     * human or model — and tidying it destroys signal that cannot be recovered afterwards.
     */
    val descriptionRaw: String,
    /** Null when nothing in the catalogue covers the request yet. */
    val catalogEntryId: ModuleCatalogEntryId? = null,
    val requestedByUserId: String? = null,
    val requestedAt: Instant? = null,
    val status: CustomizationRequestStatus = CustomizationRequestStatus.SUBMITTED,
    val activeQuoteId: QuoteId? = null,
    val decidedAt: Instant? = null,
    val rejectionReason: String? = null
) {
    init {
        require(title.isNotBlank()) { "title cannot be blank" }
        require(title.length <= 200) { "title must be at most 200 characters" }
        require(descriptionRaw.isNotBlank()) { "descriptionRaw cannot be blank" }
    }

    fun markEstimating(): ModuleCustomizationRequest =
        copy(status = CustomizationRequestStatus.ESTIMATING)

    /**
     * Attaches the quote the customer should respond to.
     *
     * Only one quote is active at a time; re-quoting supersedes the previous offer rather than
     * leaving two live prices for the same request.
     */
    fun attachQuote(quoteId: QuoteId): ModuleCustomizationRequest =
        copy(status = CustomizationRequestStatus.QUOTED, activeQuoteId = quoteId)

    fun approve(at: Instant): ModuleCustomizationRequest {
        check(activeQuoteId != null) {
            "Request ${id.value} cannot be approved before a quote is attached"
        }
        return copy(status = CustomizationRequestStatus.APPROVED, decidedAt = at)
    }

    fun reject(reason: String, at: Instant): ModuleCustomizationRequest = copy(
        status = CustomizationRequestStatus.REJECTED,
        rejectionReason = reason,
        decidedAt = at
    )

    fun markInProgress(): ModuleCustomizationRequest =
        copy(status = CustomizationRequestStatus.IN_PROGRESS)

    fun markDelivered(): ModuleCustomizationRequest =
        copy(status = CustomizationRequestStatus.DELIVERED)
}
