-- ==============================================================================
-- WeMade ERP — Platform audit trail
-- ==============================================================================
-- A platform superadmin can act as any tenant to change what modules it runs or which
-- subscription plan it is on. That capability only stays legitimate for as long as every
-- use of it leaves a trail an operator (or, eventually, the affected factory) can review.
-- ==============================================================================

CREATE TABLE IF NOT EXISTS audit_logs (
    id VARCHAR(64) PRIMARY KEY,
    actor_user_id VARCHAR(64) NOT NULL,
    actor_role VARCHAR(30) NOT NULL,
    -- Named tenant_id (not target_tenant_id) so apply_tenant_rls(), which hardcodes that
    -- column name, works the same way it does for every other tenant-scoped table here.
    tenant_id VARCHAR(64) NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    action VARCHAR(60) NOT NULL,
    summary TEXT NOT NULL,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_audit_logs_tenant_occurred
    ON audit_logs(tenant_id, occurred_at DESC);

-- Apply Multi-Tenant Row-Level Security (RLS), consistent with every other tenant-scoped
-- table — even though only the platform-admin surface reads and writes it today.
SELECT apply_tenant_rls('audit_logs');
