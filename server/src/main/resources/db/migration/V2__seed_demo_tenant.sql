-- ==============================================================================
-- Seed initial demo tenant for development & testing
-- ==============================================================================

INSERT INTO tenants (id, slug, name, status, tier, active_machine_count)
VALUES ('ten-demo-001', 'wemade-demo', 'PT WeMade Convection Demo', 'ACTIVE', 'PRO', 10)
ON CONFLICT (id) DO NOTHING;
