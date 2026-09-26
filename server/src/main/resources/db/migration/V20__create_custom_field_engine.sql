-- ==============================================================================
-- WeMade ERP — Custom Field Engine (shared across all operational modules)
-- ==============================================================================
-- The nine operational modules (CRM Sales, Sampling Order, Inventory, Tech Pack BOM,
-- Costing HPP, Production MRP, Operator Exec, Quality Control, Fulfillment) each need a
-- small amount of tenant-defined extensibility on top of a strongly-typed core entity —
-- the same pattern Salesforce, Odoo, Jira, and Shopify Metafields use.
--
-- This is deliberately ONE shared engine, not one per module. `owner_resource` is the
-- same string space already used by `module_catalog_entries.module_id`,
-- `tenant_pipelines.graph_data` node `moduleId`, and `tenant_module_entitlements
-- .granted_custom_module_ids` (see V11) — so a tenant's custom plugin module gets custom
-- fields for free, with no additional migration, once it exists.
--
-- Values are NOT stored here. `custom_field_definitions` is schema/metadata only; each
-- consuming entity (e.g. `crm_leads`) carries its own `custom_attributes JSONB` column
-- keyed by `custom_field_definitions.id` — never by name, since name is admin-editable.
-- See V21 for the first consumer.
-- ==============================================================================

CREATE TABLE IF NOT EXISTS custom_field_definitions (
    id                  VARCHAR(64) PRIMARY KEY,
    tenant_id           VARCHAR(64) NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,

    -- Module code string, e.g. 'crm_sales', 'quality_control', or a tenant plugin id like
    -- 'sablon_bordir_custom'. Deliberately NOT an FK to module_catalog_entries: that table
    -- is seed data (V14) and a board for a standard module must not be un-creatable on an
    -- environment where the seed hasn't run. Validated in the domain layer instead.
    owner_resource      VARCHAR(64) NOT NULL,

    field_key           VARCHAR(64) NOT NULL,   -- slugified from the first label, immutable after create
    label               VARCHAR(120) NOT NULL,  -- freely renamable; display only
    field_type          VARCHAR(32) NOT NULL,   -- TEXT | LONG_TEXT | NUMBER | SINGLE_SELECT | DATE | CHECKBOX | USER_REF
    config              JSONB NOT NULL DEFAULT '{}',   -- options (select), decimals/currency (number), maxCount (user_ref)
    position            DOUBLE PRECISION NOT NULL,
    is_required         BOOLEAN NOT NULL DEFAULT FALSE,
    -- Stamped when is_required flips to true. Required is validated on WRITE, never on
    -- READ: an item created before this timestamp is never retroactively invalid, and can
    -- still be edited on other fields. See CustomFieldValidation.kt.
    required_since      TIMESTAMPTZ,
    default_value        JSONB,
    is_system           BOOLEAN NOT NULL DEFAULT FALSE,  -- provisioned by seed; admin may rename but not archive
    created_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    -- Soft archive, never hard delete: an archived field's key stays in every row's JSONB
    -- blob, so restoring the field restores every value. Odoo-style pattern already used
    -- by employees/departments (V5/V6).
    archived_at         TIMESTAMPTZ
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_custom_field_definitions_key
    ON custom_field_definitions(tenant_id, owner_resource, field_key) WHERE archived_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_custom_field_definitions_resource
    ON custom_field_definitions(tenant_id, owner_resource, position) WHERE archived_at IS NULL;

-- Referential-integrity side table for USER_REF (people) custom fields, and later
-- RELATION fields once a second board/entity exists. Written in the same transaction as
-- the owning entity's custom_attributes blob — never diverging, since the blob alone
-- cannot enforce "you cannot assign a lead to a deleted employee".
--
-- `owner_resource` + `owner_record_id` identify which entity's row this link belongs to
-- (e.g. 'crm_leads' + a crm_leads.id) without a hard FK to any one entity table, since
-- this table is shared across modules the same way custom_field_definitions is.
CREATE TABLE IF NOT EXISTS custom_field_links (
    tenant_id           VARCHAR(64) NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    owner_resource      VARCHAR(64) NOT NULL,
    owner_record_id     VARCHAR(64) NOT NULL,
    field_id            VARCHAR(64) NOT NULL REFERENCES custom_field_definitions(id) ON DELETE CASCADE,
    ordinal             SMALLINT NOT NULL DEFAULT 0,
    target_employee_id  VARCHAR(64) NOT NULL REFERENCES employees(id) ON DELETE RESTRICT,
    PRIMARY KEY (owner_resource, owner_record_id, field_id, ordinal)
);

CREATE INDEX IF NOT EXISTS idx_custom_field_links_employee
    ON custom_field_links(tenant_id, target_employee_id);

SELECT apply_tenant_rls('custom_field_definitions');
SELECT apply_tenant_rls('custom_field_links');
