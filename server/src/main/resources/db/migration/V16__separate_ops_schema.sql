-- ==============================================================================
-- WeMade ERP — Move platform-internal tables into an `ops` schema
-- ==============================================================================
-- The prospect assessment endpoint is unauthenticated and takes free text from the open internet.
-- Before it existed, nothing outside the tenant session touched this database. Now something does,
-- and what it can reach if it is ever abused should be a decision rather than an accident.
--
-- The line drawn here is **what reveals our cost**:
--
--   ops     — build hours, hourly rates, build cost, margins, the estimation weights, and the
--             prospect funnel. None of it is a tenant's data and none of it should ever be
--             reachable from a tenant-facing request.
--   public  — everything a tenant legitimately reads or owns, including the module catalogue
--             (list prices are customer-facing) and customisation requests (tenant-owned, RLS).
--
-- WHY A SCHEMA AND NOT A SECOND DATABASE:
--   Four of these tables carry foreign keys to `tenants`, and the monthly billing preview has to
--   read the tenant's pipeline and the catalogue together in one query. Splitting databases would
--   trade referential integrity and single-transaction writes for isolation we do not need at this
--   volume — on figures that are money. A schema keeps the keys, the joins and the transactions,
--   and still lets a role be locked out.
--
-- WHAT THIS MIGRATION DOES AND DOES NOT GIVE YOU:
--   Moving the tables is only half. The boundary becomes real when the server connects as
--   `wemade_app` for tenant-scoped work and as the owner for platform work. The role and its grants
--   are created here so that switch is a configuration change, not another migration.
-- ==============================================================================

CREATE SCHEMA IF NOT EXISTS ops;

-- ------------------------------------------------------------------------------
-- 1. Move the cost-bearing tables
-- ------------------------------------------------------------------------------
-- Cross-schema foreign keys keep working: this is one database, and the constraints move with the
-- tables. `module_catalog_entries` and `module_customization_requests` deliberately stay in public.
ALTER TABLE IF EXISTS module_build_records        SET SCHEMA ops;
ALTER TABLE IF EXISTS module_build_effort_entries SET SCHEMA ops;
ALTER TABLE IF EXISTS module_sizing_weights       SET SCHEMA ops;
ALTER TABLE IF EXISTS module_pricing_quotes       SET SCHEMA ops;

-- ------------------------------------------------------------------------------
-- 2. Move the prospect funnel
-- ------------------------------------------------------------------------------
-- Prospects are not tenants and their assessments carry our gap pricing. The public endpoint that
-- writes them runs without a tenant context, so it is platform work and belongs on the platform
-- connection.
ALTER TABLE IF EXISTS prospect_leads             SET SCHEMA ops;
ALTER TABLE IF EXISTS prospect_flow_translations SET SCHEMA ops;
ALTER TABLE IF EXISTS prospect_price_estimates   SET SCHEMA ops;

-- ------------------------------------------------------------------------------
-- 3. The one customer-facing fact inside a cost table
-- ------------------------------------------------------------------------------
-- A tenant's monthly bill includes the accepted quotes for work built for them. The quote row also
-- holds build cost, basis hours and margin — which the billing path has no business seeing. The
-- view exposes the result and nothing that produced it, so the tenant-facing connection can compute
-- a bill without being able to read what it cost us.
CREATE OR REPLACE VIEW public.tenant_billable_quotes AS
SELECT
    id,
    tenant_id,
    catalog_entry_id,
    quote_status,
    monthly_price_idr,
    one_time_fee_idr,
    quoted_at
FROM ops.module_pricing_quotes;

COMMENT ON VIEW public.tenant_billable_quotes IS
    'Customer-facing columns of ops.module_pricing_quotes. Deliberately omits build_cost_idr, '
    'basis_hours and margin_percent so a tenant-scoped connection cannot read our cost.';

-- ------------------------------------------------------------------------------
-- 4. The tenant-facing role
-- ------------------------------------------------------------------------------
-- Created without LOGIN on purpose: granting it a password is a deployment decision, and a role
-- that cannot log in yet cannot be forgotten in a config file either.
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'wemade_app') THEN
        CREATE ROLE wemade_app NOLOGIN;
    END IF;
END
$$;

GRANT USAGE ON SCHEMA public TO wemade_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO wemade_app;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO wemade_app;

ALTER DEFAULT PRIVILEGES IN SCHEMA public
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO wemade_app;

-- The whole point: no USAGE on ops means every table inside it is invisible, not merely unreadable.
REVOKE ALL ON SCHEMA ops FROM wemade_app;
REVOKE ALL ON ALL TABLES IN SCHEMA ops FROM wemade_app;

-- Except through the view, which reads ops under the view owner's rights rather than the caller's.
GRANT SELECT ON public.tenant_billable_quotes TO wemade_app;
