package com.eventverse.app.infrastructure.tables

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

/**
 * Exposed mappings for the module development ledger (migration V13).
 *
 * Grouped in one file, unlike the one-table-per-file convention elsewhere, because these six
 * tables are a single migration and a single concept: they are always read and changed together,
 * and splitting them would scatter one schema across six files with no boundary between them.
 *
 * All but [ModuleCustomizationRequestsTable] are **platform-global**: they carry no `tenant_id`
 * and are not covered by RLS. What a build cost us is our data, not a factory's.
 *
 * Since V16 the cost-bearing tables live in the **`ops` schema**, which the tenant-scoped database
 * role cannot see at all. Two stay in `public` on purpose: the catalogue, because list prices are
 * customer-facing, and customisation requests, because a tenant owns them and reads them under RLS.
 */

object ModuleCatalogEntriesTable : Table("module_catalog_entries") {
    val id = varchar("id", 64)

    /** `BusinessModule.code` for built-ins; an arbitrary id for a tenant plugin. */
    val moduleId = varchar("module_id", 64).uniqueIndex()

    val archetypeCode = varchar("archetype_code", 32)
    val displayName = varchar("display_name", 150)
    val description = text("description")
    val categoryCode = varchar("category_code", 32).nullable()
    val scopeCapability = varchar("scope_capability", 16)
    val stockOwnership = varchar("stock_ownership", 32).nullable()
    val costingBehavior = varchar("costing_behavior", 32).nullable()
    val acceptedInputTypes = jsonbText("accepted_input_types")
    val producedOutputType = varchar("produced_output_type", 64).nullable()
    val isCustomPlugin = bool("is_custom_plugin")
    val originTenantId = varchar("origin_tenant_id", 64).references(TenantsTable.id).nullable()
    val lifecycleStatus = varchar("lifecycle_status", 16)
    val complexityTier = varchar("complexity_tier", 8).nullable()

    /** Subscription price per tenant per month for running this module. */
    val baseMonthlyPriceIdr = long("base_monthly_price_idr").nullable()

    val releasedAt = timestamp("released_at").nullable()

    override val primaryKey = PrimaryKey(id)
}

object ModuleSizingWeightsTable : Table("ops.module_sizing_weights") {
    val weightsVersion = varchar("weights_version", 16)
    val featureKey = varchar("feature_key", 64)
    val weight = decimal("weight", 6, 2)
    val calibrationNote = text("calibration_note").nullable()

    override val primaryKey = PrimaryKey(weightsVersion, featureKey)
}

object ModuleCustomizationRequestsTable : Table("module_customization_requests") {
    val id = varchar("id", 64)

    /** The one tenant-scoped table here; RLS applies. */
    val tenantId = varchar("tenant_id", 64).references(TenantsTable.id)

    val catalogEntryId = varchar("catalog_entry_id", 64)
        .references(ModuleCatalogEntriesTable.id).nullable()
    val title = varchar("title", 200)

    /** The customer's own words, stored verbatim. Never normalised on the way in. */
    val descriptionRaw = text("description_raw")

    val requestedByUserId = varchar("requested_by_user_id", 64).nullable()
    val requestedAt = timestamp("requested_at")
    val status = varchar("status", 20)
    val activeQuoteId = varchar("active_quote_id", 64).nullable()
    val decidedAt = timestamp("decided_at").nullable()
    val rejectionReason = text("rejection_reason").nullable()

    override val primaryKey = PrimaryKey(id)
}

object ModuleBuildRecordsTable : Table("ops.module_build_records") {
    val id = varchar("id", 64)
    val catalogEntryId = varchar("catalog_entry_id", 64).references(ModuleCatalogEntriesTable.id)
    val customizationRequestId = varchar("customization_request_id", 64)
        .references(ModuleCustomizationRequestsTable.id).nullable()
    val versionLabel = varchar("version_label", 32).nullable()
    val buildType = varchar("build_type", 20)
    val status = varchar("status", 20)

    // ---- Retrieval -----------------------------------------------------------
    val requirementText = text("requirement_text")
    val requirementSource = varchar("requirement_source", 20)
    val archetypeCode = varchar("archetype_code", 32)
    val embedding = jsonbText("embedding").nullable()
    val embeddingModel = varchar("embedding_model", 64).nullable()
    val embeddingVersion = varchar("embedding_version", 16).nullable()

    // ---- Size features, frozen at estimation ---------------------------------
    val featureVector = jsonbText("feature_vector")
    val entityCount = short("entity_count")
    val useCaseCount = short("use_case_count")
    val screenCount = short("screen_count")
    val apiEndpointCount = short("api_endpoint_count")
    val dbTableCount = short("db_table_count")
    val reportCount = short("report_count")
    val integrationCount = short("integration_count")
    val targetPlatformCount = short("target_platform_count")
    val affectedExistingModuleCount = short("affected_existing_module_count")
    val requiresCustomFormula = bool("requires_custom_formula")
    val requiresExternalIntegration = bool("requires_external_integration")
    val requiresRealtime = bool("requires_realtime")
    val requiresOfflineSync = bool("requires_offline_sync")
    val requiresFileUpload = bool("requires_file_upload")
    val requiresNewDesignComponent = bool("requires_new_design_component")
    val sizePoints = integer("size_points")
    val sizePointsWeightsVersion = varchar("size_points_weights_version", 16).nullable()

    // ---- Normalisers ---------------------------------------------------------
    val requirementClarityScore = short("requirement_clarity_score").nullable()
    val builderExperienceLevel = varchar("builder_experience_level", 16).nullable()
    val wasRushed = bool("was_rushed")
    val hadParallelWork = bool("had_parallel_work")

    // ---- Estimate, write-once ------------------------------------------------
    val estimatedHours = decimal("estimated_hours", 8, 2).nullable()
    val estimatedHoursP90 = decimal("estimated_hours_p90", 8, 2).nullable()
    val estimatedBy = varchar("estimated_by", 10).nullable()
    val estimatorRef = varchar("estimator_ref", 64).nullable()
    val estimateConfidence = varchar("estimate_confidence", 10).nullable()
    val retrievedNeighborIds = jsonbText("retrieved_neighbor_ids")
    val nearestNeighborSimilarity = decimal("nearest_neighbor_similarity", 4, 3).nullable()
    val openQuestions = jsonbText("open_questions")
    val estimatedAt = timestamp("estimated_at").nullable()

    // ---- Actuals -------------------------------------------------------------
    val actualHours = decimal("actual_hours", 8, 2).nullable()
    val reworkHours = decimal("rework_hours", 8, 2)
    val revisionRoundCount = short("revision_round_count")
    val postReleaseDefectCount = short("post_release_defect_count")
    val startedAt = timestamp("started_at").nullable()
    val completedAt = timestamp("completed_at").nullable()

    /** Delivery promise to the client. Never an effort measure, never used in costing. */
    val leadTimeDays = short("lead_time_days").nullable()

    val discoveredScopeDelta = text("discovered_scope_delta").nullable()
    val effortSource = varchar("effort_source", 16)
    val blendedHourlyRateIdr = long("blended_hourly_rate_idr").nullable()
    val totalBuildCostIdr = long("total_build_cost_idr").nullable()
    val gitRef = varchar("git_ref", 120).nullable()
    val retrospectiveNotes = text("retrospective_notes").nullable()

    /**
     * Computed by PostgreSQL as a stored generated column.
     *
     * Declared here so it can be read back, and never assigned on insert or update — the database
     * would reject the write. Keeping the arithmetic in the schema is what stops it drifting from
     * its inputs or being skipped when a build is closed in a hurry.
     */
    val estimateVariancePercent = decimal("estimate_variance_percent", 8, 2).nullable()

    override val primaryKey = PrimaryKey(id)
}

object ModuleBuildEffortEntriesTable : Table("ops.module_build_effort_entries") {
    val id = varchar("id", 64)
    val buildRecordId = varchar("build_record_id", 64).references(ModuleBuildRecordsTable.id)
    val roleCode = varchar("role_code", 16)
    val phaseCode = varchar("phase_code", 20)

    /** Hours. There is no day-based column anywhere in this schema by design. */
    val hours = decimal("hours", 7, 2)

    /** Snapshotted per row, so raising rates later cannot restate what past work cost. */
    val hourlyRateIdr = long("hourly_rate_idr")

    val performedBy = varchar("performed_by", 64).nullable()
    val note = text("note").nullable()
    val loggedAt = timestamp("logged_at")

    override val primaryKey = PrimaryKey(id)
}

object ModulePricingQuotesTable : Table("ops.module_pricing_quotes") {
    val id = varchar("id", 64)
    val catalogEntryId = varchar("catalog_entry_id", 64).references(ModuleCatalogEntriesTable.id)
    val buildRecordId = varchar("build_record_id", 64)
        .references(ModuleBuildRecordsTable.id).nullable()
    val tenantId = varchar("tenant_id", 64).references(TenantsTable.id).nullable()
    val quoteStatus = varchar("quote_status", 16)

    // ---- Inputs, snapshotted so an old price stays reproducible ---------------
    val basisHours = decimal("basis_hours", 8, 2)
    val buildCostIdr = long("build_cost_idr")
    val expectedTenantCount = short("expected_tenant_count")
    val amortizationMonths = short("amortization_months")
    val marginPercent = decimal("margin_percent", 5, 2)
    val monthlyMaintenancePercent = decimal("monthly_maintenance_percent", 5, 2)
    val monthlyInfraCostIdr = long("monthly_infra_cost_idr")
    val discountPercent = decimal("discount_percent", 5, 2)
    val pricingModelVersion = varchar("pricing_model_version", 16)

    // ---- Results -------------------------------------------------------------
    val oneTimeFeeIdr = long("one_time_fee_idr")
    val monthlyPriceIdr = long("monthly_price_idr")
    val calculationBreakdown = jsonbText("calculation_breakdown")
    val quotedAt = timestamp("quoted_at")

    override val primaryKey = PrimaryKey(id)
}
