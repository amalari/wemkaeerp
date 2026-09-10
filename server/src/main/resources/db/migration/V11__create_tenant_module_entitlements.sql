-- ==============================================================================
-- WeMade ERP — Per-tenant module entitlements
-- ==============================================================================
-- The subscription tier decides how MANY modules a tenant may run, but not WHICH
-- custom plugin modules it is licensed for: a plugin is provisioned to one specific
-- factory, so that grant cannot be derived from the plan.
--
-- Without a durable grant, a tenant that installs a custom module has every later
-- edit to its own pipeline rejected, because the entitlement check rebuilt from the
-- tier no longer knows the plugin is allowed.
-- ==============================================================================

CREATE TABLE IF NOT EXISTS tenant_module_entitlements (
    tenant_id VARCHAR(64) PRIMARY KEY REFERENCES tenants(id) ON DELETE CASCADE,
    -- NULL means "all built-in modules the plan tier grants by default".
    granted_modules JSONB,
    granted_custom_module_ids JSONB NOT NULL DEFAULT '[]',
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Apply Multi-Tenant Row-Level Security (RLS)
SELECT apply_tenant_rls('tenant_module_entitlements');

-- The seeded D2C demo tenant runs a tenant-only screen-printing/embroidery plugin
-- (see V10). Record that grant so the tenant can keep editing its own flow.
INSERT INTO tenant_module_entitlements (tenant_id, granted_modules, granted_custom_module_ids)
VALUES ('ten-demo-d2c', NULL, '["sablon_bordir_custom"]')
ON CONFLICT (tenant_id) DO UPDATE SET
    granted_custom_module_ids = EXCLUDED.granted_custom_module_ids,
    updated_at = CURRENT_TIMESTAMP;
