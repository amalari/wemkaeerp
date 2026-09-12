package com.eventverse.app.infrastructure.tables

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

/**
 * Exposed mappings for the prospect flow (migration V15).
 *
 * Platform-global: no `tenant_id`, no RLS. A prospect is not a tenant, and the cost figures behind
 * a quoted range are ours.
 *
 * Since V16 these live in the **`ops` schema**. The public assessment endpoint writes them, but it
 * runs with no tenant context at all — that is platform work, so it takes the platform connection.
 */

object ProspectLeadsTable : Table("ops.prospect_leads") {
    val id = varchar("id", 64)
    val companyName = varchar("company_name", 150)
    val contactName = varchar("contact_name", 150).nullable()
    val contactEmail = varchar("contact_email", 200).nullable()
    val contactPhone = varchar("contact_phone", 50).nullable()

    /** The prospect's own words, stored verbatim. */
    val narrativeRaw = text("narrative_raw")

    /** Named `leadSource` because Exposed's `ColumnSet` already declares a `source` member. */
    val leadSource = varchar("source", 32)
    val status = varchar("status", 20)

    /** Set only once a prospect actually becomes a customer. */
    val convertedTenantId = varchar("converted_tenant_id", 64).references(TenantsTable.id).nullable()

    val submittedAt = timestamp("submitted_at")

    override val primaryKey = PrimaryKey(id)
}

object ProspectFlowTranslationsTable : Table("ops.prospect_flow_translations") {
    val id = varchar("id", 64)
    val leadId = varchar("lead_id", 64).references(ProspectLeadsTable.id)
    val translatorRef = varchar("translator_ref", 64)

    /**
     * Null means no preset matched, and it must be allowed to stay null — defaulting it would file
     * every unrecognised factory as a full-package exporter.
     */
    val detectedPreset = varchar("detected_preset", 32).nullable()

    val proposedGraph = jsonbText("proposed_graph")
    val capabilityRequirements = jsonbText("capability_requirements")
    val coverage = jsonbText("coverage")
    val openQuestions = jsonbText("open_questions")
    val validationWarnings = jsonbText("validation_warnings")
    val needsHumanReview = bool("needs_human_review")
    val translatedAt = timestamp("translated_at")

    override val primaryKey = PrimaryKey(id)
}

object ProspectPriceEstimatesTable : Table("ops.prospect_price_estimates") {
    val id = varchar("id", 64)
    val leadId = varchar("lead_id", 64).references(ProspectLeadsTable.id)
    val translationId = varchar("translation_id", 64).references(ProspectFlowTranslationsTable.id)

    val subscriptionMonthlyIdr = long("subscription_monthly_idr")
    val gapLowMonthlyIdr = long("gap_low_monthly_idr").nullable()
    val gapHighMonthlyIdr = long("gap_high_monthly_idr").nullable()

    /** Already rounded outward. Recomputing them on read would risk a different rounding. */
    val displayLowIdr = long("display_low_idr").nullable()
    val displayHighIdr = long("display_high_idr").nullable()

    val unpriceableGapCount = short("unpriceable_gap_count")
    val expectedTenantCount = short("expected_tenant_count")
    val amortizationMonths = short("amortization_months")
    val marginPercent = decimal("margin_percent", 5, 2)
    val pricingModelVersion = varchar("pricing_model_version", 16)
    val isPublishable = bool("is_publishable")
    val computedAt = timestamp("computed_at")

    override val primaryKey = PrimaryKey(id)
}
