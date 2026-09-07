-- ==============================================================================
-- WeMade ERP — Multi-Tenant SaaS PostgreSQL Schema & Row-Level Security (RLS)
-- Phase 0: SaaS Foundation (Issue #11)
-- ==============================================================================

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- 1. Tenants Table
CREATE TABLE IF NOT EXISTS tenants (
    id VARCHAR(64) PRIMARY KEY,
    slug VARCHAR(30) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'TRIAL',
    tier VARCHAR(20) NOT NULL DEFAULT 'PRO',
    active_machine_count INT NOT NULL DEFAULT 0,
    settings JSONB NOT NULL DEFAULT '{"currency": "IDR", "timezone": "Asia/Jakarta", "workingHoursPerDay": 22.0}',
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_tenants_slug ON tenants(slug);
CREATE INDEX IF NOT EXISTS idx_tenants_status ON tenants(status);

-- 2. Users Table with Multi-Tenant Partitioning & Composite Index
CREATE TABLE IF NOT EXISTS users (
    id VARCHAR(64) PRIMARY KEY,
    tenant_id VARCHAR(64) REFERENCES tenants(id) ON DELETE CASCADE,
    username VARCHAR(50) NOT NULL,
    email VARCHAR(150) NOT NULL UNIQUE,
    role VARCHAR(50) NOT NULL,
    custom_permissions JSONB NOT NULL DEFAULT '[]',
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_tenant_username UNIQUE (tenant_id, username)
);

CREATE INDEX IF NOT EXISTS idx_users_tenant ON users(tenant_id);
CREATE INDEX IF NOT EXISTS idx_users_tenant_role ON users(tenant_id, role);

-- 3. Row-Level Security (RLS) Setup for Database-Level Data Isolation
ALTER TABLE users ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS tenant_isolation_users_policy ON users;

CREATE POLICY tenant_isolation_users_policy ON users
    AS PERMISSIVE
    FOR ALL
    TO PUBLIC
    USING (
        -- Platform superadmins bypass tenant filtering
        current_setting('app.current_user_role', true) = 'PLATFORM_SUPERADMIN'
        OR 
        tenant_id = current_setting('app.current_tenant_id', true)
    )
    WITH CHECK (
        current_setting('app.current_user_role', true) = 'PLATFORM_SUPERADMIN'
        OR 
        tenant_id = current_setting('app.current_tenant_id', true)
    );

-- 4. Template Function to apply RLS to any future business tables
CREATE OR REPLACE FUNCTION apply_tenant_rls(target_table_name TEXT)
RETURNS VOID AS $$
BEGIN
    EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY;', target_table_name);
    EXECUTE format('
        CREATE POLICY %I ON %I
        AS PERMISSIVE
        FOR ALL
        TO PUBLIC
        USING (
            current_setting(''app.current_user_role'', true) = ''PLATFORM_SUPERADMIN''
            OR tenant_id = current_setting(''app.current_tenant_id'', true)
        )
        WITH CHECK (
            current_setting(''app.current_user_role'', true) = ''PLATFORM_SUPERADMIN''
            OR tenant_id = current_setting(''app.current_tenant_id'', true)
        );
    ', target_table_name || '_tenant_isolation', target_table_name);
END;
$$ LANGUAGE plpgsql;
