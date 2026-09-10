-- ==============================================================================
-- WeMade ERP — Multi-Tenant Schema for Custom Pipeline Workflow (Issue #21)
-- Stores custom tenant pipeline topologies, dynamic nodes, edges, and formula params
-- ==============================================================================

CREATE TABLE IF NOT EXISTS tenant_pipelines (
    id VARCHAR(64) PRIMARY KEY,
    tenant_id VARCHAR(64) REFERENCES tenants(id) ON DELETE CASCADE,
    pipeline_name VARCHAR(100) NOT NULL,
    base_preset VARCHAR(50),
    graph_data JSONB NOT NULL DEFAULT '{}',
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_tenant_pipeline UNIQUE (tenant_id)
);

CREATE INDEX IF NOT EXISTS idx_tenant_pipelines_tenant ON tenant_pipelines(tenant_id);

-- Apply Multi-Tenant Row-Level Security (RLS)
SELECT apply_tenant_rls('tenant_pipelines');
