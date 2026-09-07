-- ==============================================================================
-- Seed initial demo owner user for development & testing
-- ==============================================================================

INSERT INTO users (id, tenant_id, username, email, role, is_active)
VALUES ('usr-owner-001', 'ten-demo-001', 'achmad_owner', 'student.achmad@gmail.com', 'TENANT_ADMIN', true)
ON CONFLICT (email) DO NOTHING;
