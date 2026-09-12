-- ==============================================================================
-- WeMade ERP — Prospect flow translation & price range
-- ==============================================================================
-- A prospective client describes how their factory works in their own words. That narrative is
-- translated into a chain of modules, split into "the catalogue already covers this" and "this has
-- to be built", and the gaps are estimated through the module development ledger (V13) to produce
-- a monthly price range.
--
-- DESIGN NOTE — a prospect is NOT a tenant:
--   These rows describe factories that may never become customers. Putting them in `tenants` would
--   contaminate every existing tenant query — RLS policies, entitlements, org chart, billing — with
--   rows for factories that do not exist. A placeholder TenantId is fine for assembling a pipeline
--   in memory, but that pipeline is stored as JSONB here and never in `tenant_pipelines`.
--
-- DESIGN NOTE — platform-global, no RLS:
--   Same reasoning as the ledger tables: there is no tenant to scope to, and the cost figures
--   behind a quoted range are ours. apply_tenant_rls() is deliberately not called.
--
-- DESIGN NOTE — history is kept, not overwritten:
--   Re-translating a narrative (better prompt, new model) writes a new translation row; re-pricing
--   writes a new estimate row. What we told a prospect, and on what basis, stays answerable after
--   the fact — the same rule the ledger applies to estimates and quotes.
-- ==============================================================================

-- ------------------------------------------------------------------------------
-- 1. Leads — who asked, and what they said
-- ------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS prospect_leads (
    id VARCHAR(64) PRIMARY KEY,
    company_name VARCHAR(150) NOT NULL,
    contact_name VARCHAR(150),
    contact_email VARCHAR(200),
    contact_phone VARCHAR(50),

    -- Stored verbatim, exactly like module_customization_requests.description_raw. This is the
    -- richest context the translator gets, and a tidied-up version of it cannot be un-tidied.
    narrative_raw TEXT NOT NULL,

    source VARCHAR(32) NOT NULL DEFAULT 'LANDING_PAGE',
    status VARCHAR(20) NOT NULL DEFAULT 'SUBMITTED',

    -- Set only if the prospect actually becomes a customer. The conversion flow itself is not
    -- built yet; the column exists so the link is recorded from the first conversion rather than
    -- reconstructed later from names and dates.
    converted_tenant_id VARCHAR(64) REFERENCES tenants(id) ON DELETE SET NULL,

    submitted_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_prospect_leads_status
    ON prospect_leads(status, submitted_at DESC);

-- ------------------------------------------------------------------------------
-- 2. Translations — what the model made of the narrative
-- ------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS prospect_flow_translations (
    id VARCHAR(64) PRIMARY KEY,
    lead_id VARCHAR(64) NOT NULL REFERENCES prospect_leads(id) ON DELETE CASCADE,

    -- '<model>/<prompt-version>'. Mirrors estimator_ref in the ledger, and for the same reason:
    -- once a real model replaces the keyword stub, quality has to be comparable across both in the
    -- same column rather than inferred from dates.
    translator_ref VARCHAR(64) NOT NULL,

    -- NULL means no preset matched. It must stay NULL rather than defaulting to FOB:
    -- GarmentBusinessPreset.fromCode() falls back to DEFAULT instead of returning null, so any
    -- unrecognised narrative would otherwise be silently filed as a full-package exporter.
    detected_preset VARCHAR(32),

    -- CustomTenantPipeline shape (nodes + edges), encoded by the pipeline codec.
    proposed_graph JSONB NOT NULL DEFAULT '{}',

    -- [{archetypeCode, title, description, sourceQuote}]
    -- sourceQuote is the fragment of the narrative that triggered the requirement. It is the only
    -- way a reviewer can check the model did not invent a need the client never mentioned.
    capability_requirements JSONB NOT NULL DEFAULT '[]',

    -- [{requirementIndex, kind: COVERED|GAP, moduleId?, buildId?}]
    coverage JSONB NOT NULL DEFAULT '[]',

    open_questions JSONB NOT NULL DEFAULT '[]',

    -- Cycles, incompatible ports, archetype codes the model made up. Recorded rather than thrown:
    -- a partly-wrong translation is still worth showing a reviewer, and hiding the warnings would
    -- make it look trustworthy.
    validation_warnings JSONB NOT NULL DEFAULT '[]',

    needs_human_review BOOLEAN NOT NULL DEFAULT TRUE,
    translated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_prospect_translations_lead
    ON prospect_flow_translations(lead_id, translated_at DESC);

-- ------------------------------------------------------------------------------
-- 3. Price estimates — the range, and whether it may be shown at all
-- ------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS prospect_price_estimates (
    id VARCHAR(64) PRIMARY KEY,
    lead_id VARCHAR(64) NOT NULL REFERENCES prospect_leads(id) ON DELETE CASCADE,
    translation_id VARCHAR(64) NOT NULL
        REFERENCES prospect_flow_translations(id) ON DELETE CASCADE,

    -- Modules the catalogue already covers, at list price. Exact, not a range.
    subscription_monthly_idr BIGINT NOT NULL DEFAULT 0,

    -- Sum over the gaps, priced from p50 and p90 hours respectively. NULL when any gap could not be
    -- estimated at all — see is_publishable.
    gap_low_monthly_idr BIGINT,
    gap_high_monthly_idr BIGINT,

    -- What the prospect is shown, already rounded OUTWARD (floor the low, ceil the high).
    -- Rounding to nearest could put the displayed ceiling below the real p90 price, and the final
    -- figure we send would then exceed a range the client has already seen.
    display_low_idr BIGINT,
    display_high_idr BIGINT,

    unpriceable_gap_count SMALLINT NOT NULL DEFAULT 0,

    -- 1 = the prospect funds the build alone. Deliberately conservative: the range must express
    -- uncertainty about HOURS only. Mixing in a guess about future resale would make it
    -- uninterpretable, and a price that later falls is a far better conversation than one that rises.
    expected_tenant_count SMALLINT NOT NULL DEFAULT 1,
    amortization_months SMALLINT NOT NULL DEFAULT 24,
    margin_percent NUMERIC(5,2) NOT NULL DEFAULT 0,
    pricing_model_version VARCHAR(16) NOT NULL DEFAULT 'v1',

    -- False while any gap is unestimated. A total assembled from only the priceable gaps is
    -- guaranteed to understate, so the honest answer is no number at all rather than a low one.
    is_publishable BOOLEAN NOT NULL DEFAULT FALSE,

    computed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_prospect_tenant_count_positive CHECK (expected_tenant_count > 0),
    CONSTRAINT chk_prospect_amortization_positive CHECK (amortization_months > 0),
    -- A displayed range must be a range. Catches an outward-rounding bug at write time rather than
    -- in front of a customer.
    CONSTRAINT chk_prospect_range_ordered CHECK (
        display_low_idr IS NULL
        OR display_high_idr IS NULL
        OR display_low_idr <= display_high_idr
    ),
    -- Publishable means there is something to publish.
    CONSTRAINT chk_prospect_publishable_has_range CHECK (
        is_publishable = FALSE
        OR (display_low_idr IS NOT NULL AND display_high_idr IS NOT NULL)
    )
);

CREATE INDEX IF NOT EXISTS idx_prospect_estimates_lead
    ON prospect_price_estimates(lead_id, computed_at DESC);
