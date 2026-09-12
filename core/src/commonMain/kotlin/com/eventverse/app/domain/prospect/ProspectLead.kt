package com.eventverse.app.domain.prospect

import com.eventverse.app.domain.tenant.TenantId
import kotlinx.datetime.Instant

/**
 * A factory that has described itself but is not a customer.
 *
 * **Deliberately not a `Tenant`.** A prospect has no slug, no users, no pipeline of its own and no
 * row to scope RLS against. Filing prospects in `tenants` would put factories that do not exist
 * into every tenant query in the system — entitlements, org chart, billing — and most of them never
 * become customers.
 *
 * A placeholder [TenantId] is used when assembling a proposed pipeline **in memory** (see
 * [com.eventverse.app.domain.pipeline.CustomTenantPipeline]); that pipeline is stored as JSON on the
 * translation and never reaches `tenant_pipelines`.
 */
data class ProspectLead(
    val id: ProspectLeadId,
    val companyName: String,
    /** The prospect's own words, stored verbatim and never normalised on the way in. */
    val narrativeRaw: String,
    val contactName: String? = null,
    val contactEmail: String? = null,
    val contactPhone: String? = null,
    val source: LeadSource = LeadSource.LANDING_PAGE,
    val status: LeadStatus = LeadStatus.SUBMITTED,
    val convertedTenantId: TenantId? = null,
    val submittedAt: Instant? = null
) {
    init {
        require(companyName.isNotBlank()) { "companyName cannot be blank" }
        require(companyName.length <= 150) { "companyName must be at most 150 characters" }
        require(narrativeRaw.isNotBlank()) {
            "narrativeRaw cannot be blank: it is the only input the translator has"
        }
    }

    /**
     * The placeholder tenant identity used to assemble a proposed pipeline.
     *
     * `TenantId` validates only length (3..64) with no format rule, so this is legal — but it names
     * no row in `tenants`, and passing it to a tenant-scoped repository would fail on the foreign
     * key. Keep it inside in-memory assembly.
     */
    val placeholderTenantId: TenantId get() = TenantId("prospect-${id.value}".take(64))

    fun markTranslated(): ProspectLead = copy(status = LeadStatus.TRANSLATED)

    /** Where a lead lands whenever the ledger cannot price every gap. Not an error state. */
    fun markNeedsReview(): ProspectLead = copy(status = LeadStatus.NEEDS_REVIEW)

    fun markQuoted(): ProspectLead = copy(status = LeadStatus.QUOTED)

    fun reject(): ProspectLead = copy(status = LeadStatus.REJECTED)

    fun convertTo(tenantId: TenantId): ProspectLead =
        copy(status = LeadStatus.CONVERTED, convertedTenantId = tenantId)
}
