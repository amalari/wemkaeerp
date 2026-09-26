-- ==============================================================================
-- WeMade ERP — CRM Leads (BusinessModule.CRM_SALES)
-- ==============================================================================
-- The first operational module to get real storage. Nine modules exist today only as a
-- route enum entry, an icon, a permission key, and two hardcoded sample rows in
-- ModuleWorkspaceScreen — this table is the first of them to actually hold the work the
-- module is supposed to do.
--
-- DESIGN — hybrid core entity + custom_attributes (NOT a fully generic monday.com-style
-- board): a strongly-typed CRM has real business logic (pipeline value SUM, forecast by
-- close date, DataScope ownership), and squeezing all of that through a JSONB blob would
-- make every query and constraint harder for no benefit — see the plan's "core vs custom"
-- rule. What genuinely varies per tenant (jenis sablon, detail kain, ukuran screen, ...)
-- lives in custom_attributes, keyed by custom_field_definitions.id (V20), never by name.
--
-- DESIGN NOTE — not the same "leads" as prospect_leads (V15): that table is WeMade's own
-- sales funnel for factories considering becoming a tenant (platform-global, ops schema,
-- no tenant_id at all). crm_leads is a tenant's OWN customers/prospects. Naming this table
-- differently (crm_leads, not leads) and labelling it "Prospek Sales" in the UI keeps
-- support from confusing the two.
-- ==============================================================================

CREATE TABLE IF NOT EXISTS crm_leads (
    id                     VARCHAR(64) PRIMARY KEY,
    tenant_id              VARCHAR(64) NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,

    brand_name             VARCHAR(150) NOT NULL,
    contact_person         VARCHAR(150) NOT NULL DEFAULT '',
    -- Stored already normalised to E.164 without a leading '+' (e.g. '6281234567890'), by
    -- WhatsappNumber in core — never normalised ad hoc in a route or a composable, so the
    -- server and every client build the exact same wa.me link.
    whatsapp_number        VARCHAR(20) NOT NULL DEFAULT '',

    -- Business enum, not a tenant-configurable stage table. Tenant-configurable stages need
    -- transition rules, value migration, and a reporting impact — that lands with kanban in
    -- a later phase. INQUIRY | TECHPACK_SPEC | QUOTATION_SENT | SAMPLE_APPROVAL |
    -- DEAL_DP_CONFIRMED | LOST
    stage                  VARCHAR(30) NOT NULL DEFAULT 'INQUIRY',
    source                 VARCHAR(50) NOT NULL DEFAULT '',

    estimated_pcs          INTEGER,
    -- IDR, integer rupiah (no fractional currency unit) — mirrors MoneyIdr in core.
    estimated_value_idr    BIGINT,

    -- Drives DataScope.OWN_DATA_ONLY / SUBORDINATE_DATA. A real column (not a JSONB key)
    -- so the scope predicate is indexed SQL, never a JSON probe. NULL means unassigned;
    -- visible only to ALL_TENANT_DATA callers, mirroring OrgChartDataReach's rule that a
    -- null department is visible only to an unrestricted viewer.
    owner_employee_id      VARCHAR(64) REFERENCES employees(id) ON DELETE RESTRICT,
    expected_close_date    DATE,

    -- Tenant-defined field values, keyed by custom_field_definitions.id. See V20 header.
    custom_attributes      JSONB NOT NULL DEFAULT '{}',

    created_by_user_id     VARCHAR(64) REFERENCES users(id) ON DELETE SET NULL,
    created_at             TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at             TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    archived_at            TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_crm_leads_tenant_active
    ON crm_leads(tenant_id, updated_at DESC) WHERE archived_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_crm_leads_owner
    ON crm_leads(tenant_id, owner_employee_id) WHERE archived_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_crm_leads_stage
    ON crm_leads(tenant_id, stage) WHERE archived_at IS NULL;

-- Equality filters against any custom field (e.g. "Sample Approved = true") without a
-- per-field expression index. Range/sort per custom field is left unindexed until a
-- specific field is hot enough to warrant CREATE INDEX CONCURRENTLY — additive, not a
-- schema change.
CREATE INDEX IF NOT EXISTS idx_crm_leads_custom_attributes_gin
    ON crm_leads USING GIN (custom_attributes jsonb_path_ops);

SELECT apply_tenant_rls('crm_leads');
