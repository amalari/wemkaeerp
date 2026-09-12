-- ==============================================================================
-- WeMade ERP — Module development ledger & pricing engine
-- ==============================================================================
-- Two numbers the platform currently cannot answer: what a module costs to build,
-- and what a module is worth per month. Every subscription quote is guesswork until
-- both are recorded, and a monthly price agreed today is locked in for the length of
-- the contract — a bad guess is not correctable later.
--
-- The tables below record build effort so that (a) a price can be justified from
-- evidence rather than intuition, and (b) later estimates can be derived from what
-- comparable work actually took.
--
-- DESIGN NOTE — predictors vs outcomes:
--   Columns split into what is knowable BEFORE work starts (counts, clarity) and what
--   is only knowable AFTER (rework, revisions, defects). Only the former may ever feed
--   an estimate: at prediction time the latter do not exist yet. The feature columns are
--   frozen at estimated_at and are never corrected, even when they turn out wrong —
--   having guessed wrong IS the training signal. Late discoveries go to
--   discovered_scope_delta instead.
--
-- DESIGN NOTE — effort is measured in HOURS, never days:
--   A working day here is ~4 focused hours, not 8. If any layer stores days and another
--   converts at 8h/day, every figure doubles — and since price = hours x rate x margin,
--   that error is billed for the whole contract. lead_time_days exists but is a delivery
--   promise, not an effort measure; the two are never converted into each other.
--
-- These are the first PLATFORM-GLOBAL tables in the schema: they carry no tenant_id and
-- deliberately do not call apply_tenant_rls(). What a build cost us is our data, not the
-- factory's. Only module_customization_requests — a document the tenant itself owns — is
-- tenant-scoped, so our cost figures are separated from tenant-readable rows by table
-- boundary rather than by a permission check that someone could forget to write.
-- ==============================================================================

-- ------------------------------------------------------------------------------
-- 1. Catalog — the sellable modules, moved out of the BusinessModule enum into data
-- ------------------------------------------------------------------------------
-- The enum cannot grow without a deploy, which makes tenant-commissioned plugins
-- impossible to list. module_id keeps the enum's code as its key so existing pipeline
-- rows (CustomPipelineNode.moduleId, granted_custom_module_ids) join without migration.
CREATE TABLE IF NOT EXISTS module_catalog_entries (
    id VARCHAR(64) PRIMARY KEY,
    module_id VARCHAR(64) NOT NULL UNIQUE,
    archetype_code VARCHAR(32) NOT NULL,
    display_name VARCHAR(150) NOT NULL,
    description TEXT NOT NULL DEFAULT '',
    category_code VARCHAR(32),
    scope_capability VARCHAR(16) NOT NULL DEFAULT 'GLOBAL_ONLY',
    stock_ownership VARCHAR(32),
    costing_behavior VARCHAR(32),
    accepted_input_types JSONB NOT NULL DEFAULT '[]',
    produced_output_type VARCHAR(64),
    is_custom_plugin BOOLEAN NOT NULL DEFAULT FALSE,
    -- The tenant a plugin was originally commissioned for. NULL means core product.
    -- Not an ownership claim: once built, a plugin may be offered to other tenants,
    -- which is exactly what makes expected_tenant_count below meaningful.
    origin_tenant_id VARCHAR(64) REFERENCES tenants(id) ON DELETE SET NULL,
    lifecycle_status VARCHAR(16) NOT NULL DEFAULT 'PLANNED',
    complexity_tier VARCHAR(8),
    -- Subscription price per tenant per month for running this module. The billing
    -- preview sums this across the modules a tenant actually has active.
    base_monthly_price_idr BIGINT,
    released_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_module_catalog_archetype
    ON module_catalog_entries(archetype_code);
CREATE INDEX IF NOT EXISTS idx_module_catalog_lifecycle
    ON module_catalog_entries(lifecycle_status);

-- ------------------------------------------------------------------------------
-- 2. Sizing weights — how countable features translate into size points
-- ------------------------------------------------------------------------------
-- A table rather than Kotlin constants, because these weights start as guesses and must
-- be recalibrated by regression once enough builds exist. Versioning them means an old
-- size_points value stays interpretable under the weights that produced it; without that,
-- recalibration would silently reinterpret every historical row.
CREATE TABLE IF NOT EXISTS module_sizing_weights (
    weights_version VARCHAR(16) NOT NULL,
    feature_key VARCHAR(64) NOT NULL,
    weight NUMERIC(6,2) NOT NULL,
    calibrated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    calibration_note TEXT,
    PRIMARY KEY (weights_version, feature_key)
);

-- ------------------------------------------------------------------------------
-- 3. Customization requests — the only tenant-owned table here (RLS applies)
-- ------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS module_customization_requests (
    id VARCHAR(64) PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    -- NULL when the tenant is asking for something no catalog module covers yet.
    catalog_entry_id VARCHAR(64) REFERENCES module_catalog_entries(id) ON DELETE SET NULL,
    title VARCHAR(200) NOT NULL,
    -- Stored verbatim, never cleaned up or summarised: this is the richest context an
    -- estimator (human or model) gets, and rewriting it destroys signal irrecoverably.
    description_raw TEXT NOT NULL,
    requested_by_user_id VARCHAR(64),
    requested_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    status VARCHAR(20) NOT NULL DEFAULT 'SUBMITTED',
    -- FK added at the bottom: quotes reference builds, builds reference requests, so the
    -- cycle cannot be closed by column definition order alone.
    active_quote_id VARCHAR(64),
    decided_at TIMESTAMP WITH TIME ZONE,
    rejection_reason TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_customization_requests_tenant_status
    ON module_customization_requests(tenant_id, status);

SELECT apply_tenant_rls('module_customization_requests');

-- ------------------------------------------------------------------------------
-- 4. Build records — one row per build attempt; the core of the ledger
-- ------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS module_build_records (
    id VARCHAR(64) PRIMARY KEY,
    catalog_entry_id VARCHAR(64) NOT NULL
        REFERENCES module_catalog_entries(id) ON DELETE CASCADE,
    customization_request_id VARCHAR(64)
        REFERENCES module_customization_requests(id) ON DELETE SET NULL,
    version_label VARCHAR(32),
    build_type VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ESTIMATING',

    -- ---- Block A: retrieval -------------------------------------------------
    requirement_text TEXT NOT NULL,
    requirement_source VARCHAR(20) NOT NULL DEFAULT 'TENANT_REQUEST',
    archetype_code VARCHAR(32) NOT NULL,
    -- Plain JSON array of floats. Not pgvector: at this row count cosine in Kotlin costs
    -- microseconds, and an extension would have to be provisioned in dev, CI and prod for
    -- a problem that does not exist yet. Moving to vector(N) later is one migration.
    embedding JSONB,
    -- Distances are only comparable within one embedding model. Mixing generations
    -- without this marker silently compares two different spaces, and the symptom
    -- (quietly poor neighbours) is very hard to trace back.
    embedding_model VARCHAR(64),
    embedding_version VARCHAR(16),

    -- ---- Block B: size features — FROZEN at estimated_at --------------------
    feature_vector JSONB NOT NULL DEFAULT '{}',
    entity_count SMALLINT NOT NULL DEFAULT 0,
    use_case_count SMALLINT NOT NULL DEFAULT 0,
    screen_count SMALLINT NOT NULL DEFAULT 0,
    api_endpoint_count SMALLINT NOT NULL DEFAULT 0,
    db_table_count SMALLINT NOT NULL DEFAULT 0,
    report_count SMALLINT NOT NULL DEFAULT 0,
    integration_count SMALLINT NOT NULL DEFAULT 0,
    target_platform_count SMALLINT NOT NULL DEFAULT 1,
    affected_existing_module_count SMALLINT NOT NULL DEFAULT 0,
    requires_custom_formula BOOLEAN NOT NULL DEFAULT FALSE,
    requires_external_integration BOOLEAN NOT NULL DEFAULT FALSE,
    requires_realtime BOOLEAN NOT NULL DEFAULT FALSE,
    requires_offline_sync BOOLEAN NOT NULL DEFAULT FALSE,
    requires_file_upload BOOLEAN NOT NULL DEFAULT FALSE,
    requires_new_design_component BOOLEAN NOT NULL DEFAULT FALSE,
    size_points INTEGER NOT NULL DEFAULT 0,
    size_points_weights_version VARCHAR(16),

    -- ---- Block C: normalisers ----------------------------------------------
    -- Without these, actual_hours mixes how big the work was with the conditions it was
    -- done under, and a model has no way to separate them — it will blame module
    -- complexity for what was really an unclear brief or an unfamiliar developer.
    requirement_clarity_score SMALLINT,
    builder_experience_level VARCHAR(16),
    was_rushed BOOLEAN NOT NULL DEFAULT FALSE,
    had_parallel_work BOOLEAN NOT NULL DEFAULT FALSE,

    -- ---- Block D: estimate — written once, never overwritten ---------------
    -- Overwriting these with actuals would destroy variance, which is the single most
    -- valuable thing a finished build has to teach.
    estimated_hours NUMERIC(8,2),
    estimated_hours_p90 NUMERIC(8,2),
    estimated_by VARCHAR(10),
    estimator_ref VARCHAR(64),
    estimate_confidence VARCHAR(10),
    retrieved_neighbor_ids JSONB NOT NULL DEFAULT '[]',
    nearest_neighbor_similarity NUMERIC(4,3),
    open_questions JSONB NOT NULL DEFAULT '[]',
    estimated_at TIMESTAMP WITH TIME ZONE,

    -- ---- Block E: actuals --------------------------------------------------
    actual_hours NUMERIC(8,2),
    rework_hours NUMERIC(8,2) NOT NULL DEFAULT 0,
    revision_round_count SMALLINT NOT NULL DEFAULT 0,
    post_release_defect_count SMALLINT NOT NULL DEFAULT 0,
    started_at TIMESTAMP WITH TIME ZONE,
    completed_at TIMESTAMP WITH TIME ZONE,
    -- Delivery promise to the client, NOT an effort measure. A 12-hour task can have a
    -- three-week lead time while waiting on a buyer's answer. Never use in costing.
    lead_time_days SMALLINT,
    discovered_scope_delta TEXT,
    -- LOGGED hours were recorded as work happened; RECONSTRUCTED were recalled from
    -- memory; IMPORTED rows carry features only and no hours at all. Evaluating model
    -- accuracy against reconstructed rows measures our own guesses, so they must stay
    -- separable.
    effort_source VARCHAR(16) NOT NULL DEFAULT 'LOGGED',
    -- Snapshot, not a lookup: raising rates next year must not retroactively change what
    -- an old build cost. Must be derived from REAL productive hours (~4/day), not a
    -- nominal 8-hour day, or every quote recovers only half the true cost.
    blended_hourly_rate_idr BIGINT,
    total_build_cost_idr BIGINT,
    git_ref VARCHAR(120),
    retrospective_notes TEXT,

    -- Generated so it cannot drift from its inputs or be forgotten on close.
    estimate_variance_percent NUMERIC(8,2) GENERATED ALWAYS AS (
        CASE
            WHEN estimated_hours IS NULL OR estimated_hours = 0 OR actual_hours IS NULL
                THEN NULL
            ELSE ROUND((actual_hours - estimated_hours) / estimated_hours * 100, 2)
        END
    ) STORED,

    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Retrieval filters by archetype before running kNN, so that a sewing build never
-- surfaces as a neighbour of a costing build merely for sharing vocabulary.
CREATE INDEX IF NOT EXISTS idx_build_records_archetype_status
    ON module_build_records(archetype_code, status);
CREATE INDEX IF NOT EXISTS idx_build_records_catalog
    ON module_build_records(catalog_entry_id);
-- Only rows with hours can contribute a productivity ratio.
CREATE INDEX IF NOT EXISTS idx_build_records_effort_source
    ON module_build_records(effort_source)
    WHERE actual_hours IS NOT NULL;

-- ------------------------------------------------------------------------------
-- 5. Effort entries — hours per role per phase
-- ------------------------------------------------------------------------------
-- Detail rather than one total, so the ledger can learn shape as well as size: that a
-- SEWING module is frontend-heavy on the canvas while COSTING_HPP is backend-heavy.
CREATE TABLE IF NOT EXISTS module_build_effort_entries (
    id VARCHAR(64) PRIMARY KEY,
    build_record_id VARCHAR(64) NOT NULL
        REFERENCES module_build_records(id) ON DELETE CASCADE,
    role_code VARCHAR(16) NOT NULL,
    phase_code VARCHAR(20) NOT NULL,
    -- Hours. Half-hour granularity in practice; there is no day-based path anywhere.
    hours NUMERIC(7,2) NOT NULL,
    -- Per row, not looked up: the rate in force when the work happened is part of what
    -- the work cost.
    hourly_rate_idr BIGINT NOT NULL,
    performed_by VARCHAR(64),
    note TEXT,
    logged_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_effort_hours_positive CHECK (hours > 0)
);

CREATE INDEX IF NOT EXISTS idx_effort_entries_build
    ON module_build_effort_entries(build_record_id);

-- ------------------------------------------------------------------------------
-- 6. Pricing quotes — every input to the formula, not just its output
-- ------------------------------------------------------------------------------
-- A price agreed today is billed for 24 months. If margin or rate lived only in code,
-- changing them would silently reinterpret prices already being charged and no one could
-- reconstruct how an old figure was reached.
CREATE TABLE IF NOT EXISTS module_pricing_quotes (
    id VARCHAR(64) PRIMARY KEY,
    catalog_entry_id VARCHAR(64) NOT NULL
        REFERENCES module_catalog_entries(id) ON DELETE CASCADE,
    build_record_id VARCHAR(64) REFERENCES module_build_records(id) ON DELETE SET NULL,
    -- Set when the quote is specific to one tenant; NULL for a catalog-wide price.
    -- No FK-driven RLS here: quotes live on the platform side by design.
    tenant_id VARCHAR(64) REFERENCES tenants(id) ON DELETE CASCADE,
    quote_status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',

    -- ---- formula inputs, snapshotted --------------------------------------
    -- p90 rather than the median: the monthly price is fixed once, and we absorb any
    -- overrun. Pricing from the midpoint loses money on half of all projects by design.
    basis_hours NUMERIC(8,2) NOT NULL,
    build_cost_idr BIGINT NOT NULL,
    -- 1 = exclusive to one tenant; higher spreads the build cost across the tenants
    -- expected to run it. The same build can price 3x apart on this column alone, which
    -- is precisely why it is recorded instead of decided in someone's head.
    expected_tenant_count SMALLINT NOT NULL DEFAULT 1,
    amortization_months SMALLINT NOT NULL DEFAULT 24,
    margin_percent NUMERIC(5,2) NOT NULL DEFAULT 0,
    monthly_maintenance_percent NUMERIC(5,2) NOT NULL DEFAULT 0,
    monthly_infra_cost_idr BIGINT NOT NULL DEFAULT 0,
    discount_percent NUMERIC(5,2) NOT NULL DEFAULT 0,
    pricing_model_version VARCHAR(16) NOT NULL DEFAULT 'v1',

    -- ---- results ----------------------------------------------------------
    one_time_fee_idr BIGINT NOT NULL DEFAULT 0,
    monthly_price_idr BIGINT NOT NULL,
    calculation_breakdown JSONB NOT NULL DEFAULT '{}',
    quoted_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_quote_amortization_positive CHECK (amortization_months > 0),
    CONSTRAINT chk_quote_tenant_count_positive CHECK (expected_tenant_count > 0)
);

CREATE INDEX IF NOT EXISTS idx_pricing_quotes_catalog_status
    ON module_pricing_quotes(catalog_entry_id, quote_status);
CREATE INDEX IF NOT EXISTS idx_pricing_quotes_tenant
    ON module_pricing_quotes(tenant_id)
    WHERE tenant_id IS NOT NULL;

-- ------------------------------------------------------------------------------
-- 7. Close the request -> quote cycle
-- ------------------------------------------------------------------------------
ALTER TABLE module_customization_requests
    DROP CONSTRAINT IF EXISTS fk_customization_active_quote;
ALTER TABLE module_customization_requests
    ADD CONSTRAINT fk_customization_active_quote
    FOREIGN KEY (active_quote_id)
    REFERENCES module_pricing_quotes(id) ON DELETE SET NULL;
