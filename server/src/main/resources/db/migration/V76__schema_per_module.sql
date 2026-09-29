-- ==============================================================================
-- WeMade ERP (Jalur B) — V76: Satu PostgreSQL schema per modul (TRD-PLAT-001 B8)
-- ==============================================================================
-- Batas modul kini terlihat di database: tabel modul pindah dari public ke schema bernama
-- persis kode modulnya (crm_sales, sampling_order, ...). Fitur ikut schema modul induknya.
-- Sumber daftar: server/.../infrastructure/ModuleSchemaMap.kt (dijaga ModuleSchemaOwnershipTest).
--
-- Tetap SATU database: foreign key, transaksi, JOIN lintas schema, dan RLS tidak berubah.
-- ALTER TABLE ... SET SCHEMA memindahkan index, constraint, sequence milik kolom, dan policy
-- RLS bersama tabelnya. Data dan id tidak disentuh.
--
-- Tabel platform (tenants, users, entitlement, audit, domain_packs, custom field, katalog modul)
-- tetap di public. Schema ops (V16) tidak berubah. search_path TIDAK diubah: setiap referensi
-- sesudah ini wajib berschema eksplisit.
-- ==============================================================================


-- org_chart
CREATE SCHEMA IF NOT EXISTS org_chart;
ALTER TABLE IF EXISTS public.departments SET SCHEMA org_chart;
ALTER TABLE IF EXISTS public.employees SET SCHEMA org_chart;

-- dynamic_rbac
CREATE SCHEMA IF NOT EXISTS dynamic_rbac;
ALTER TABLE IF EXISTS public.custom_roles SET SCHEMA dynamic_rbac;
ALTER TABLE IF EXISTS public.department_module_assignments SET SCHEMA dynamic_rbac;

-- factory_flow
CREATE SCHEMA IF NOT EXISTS factory_flow;
ALTER TABLE IF EXISTS public.tenant_pipelines SET SCHEMA factory_flow;
ALTER TABLE IF EXISTS public.tenant_locations SET SCHEMA factory_flow;
ALTER TABLE IF EXISTS public.tenant_location_settings SET SCHEMA factory_flow;
ALTER TABLE IF EXISTS public.tenant_flow_node_locations SET SCHEMA factory_flow;

-- sampling_order
CREATE SCHEMA IF NOT EXISTS sampling_order;
ALTER TABLE IF EXISTS public.sampling_orders SET SCHEMA sampling_order;
ALTER TABLE IF EXISTS public.sampling_knit_specs SET SCHEMA sampling_order;
ALTER TABLE IF EXISTS public.sampling_size_charts SET SCHEMA sampling_order;
ALTER TABLE IF EXISTS public.sampling_machine_programs SET SCHEMA sampling_order;
ALTER TABLE IF EXISTS public.sampling_yield_timings SET SCHEMA sampling_order;
ALTER TABLE IF EXISTS public.sampling_milestones SET SCHEMA sampling_order;
ALTER TABLE IF EXISTS public.sampling_finishing_deposits SET SCHEMA sampling_order;
ALTER TABLE IF EXISTS public.sampling_qc_inspections SET SCHEMA sampling_order;
ALTER TABLE IF EXISTS public.tenant_stage_flows SET SCHEMA sampling_order;
ALTER TABLE IF EXISTS public.tenant_optional_processes SET SCHEMA sampling_order;
ALTER TABLE IF EXISTS public.tenant_stage_phase_tags SET SCHEMA sampling_order;
ALTER TABLE IF EXISTS public.sample_storage_records SET SCHEMA sampling_order;

-- crm_sales
CREATE SCHEMA IF NOT EXISTS crm_sales;
ALTER TABLE IF EXISTS public.crm_leads SET SCHEMA crm_sales;
ALTER TABLE IF EXISTS public.crm_lead_activities SET SCHEMA crm_sales;
ALTER TABLE IF EXISTS public.crm_contacts SET SCHEMA crm_sales;
ALTER TABLE IF EXISTS public.deals SET SCHEMA crm_sales;
ALTER TABLE IF EXISTS public.deal_purchase_orders SET SCHEMA crm_sales;

-- master_data
CREATE SCHEMA IF NOT EXISTS master_data;
ALTER TABLE IF EXISTS public.material_code_sequences SET SCHEMA master_data;
ALTER TABLE IF EXISTS public.material_items SET SCHEMA master_data;
ALTER TABLE IF EXISTS public.material_prices SET SCHEMA master_data;
ALTER TABLE IF EXISTS public.material_price_policies SET SCHEMA master_data;

-- vendor_contacts
CREATE SCHEMA IF NOT EXISTS vendor_contacts;
ALTER TABLE IF EXISTS public.vendors SET SCHEMA vendor_contacts;
ALTER TABLE IF EXISTS public.vendor_assignments SET SCHEMA vendor_contacts;

-- tech_pack_bom
CREATE SCHEMA IF NOT EXISTS tech_pack_bom;
ALTER TABLE IF EXISTS public.tech_pack_style_sequences SET SCHEMA tech_pack_bom;
ALTER TABLE IF EXISTS public.tech_packs SET SCHEMA tech_pack_bom;
ALTER TABLE IF EXISTS public.tech_pack_bom_lines SET SCHEMA tech_pack_bom;
ALTER TABLE IF EXISTS public.tech_pack_labor_operations SET SCHEMA tech_pack_bom;
ALTER TABLE IF EXISTS public.tech_pack_size_yields SET SCHEMA tech_pack_bom;

-- costing_hpp
CREATE SCHEMA IF NOT EXISTS costing_hpp;
ALTER TABLE IF EXISTS public.costing_rate_cards SET SCHEMA costing_hpp;
ALTER TABLE IF EXISTS public.costing_sheets SET SCHEMA costing_hpp;
ALTER TABLE IF EXISTS public.costing_sheet_buckets SET SCHEMA costing_hpp;
ALTER TABLE IF EXISTS public.costing_product_benchmarks SET SCHEMA costing_hpp;

-- production_mrp
CREATE SCHEMA IF NOT EXISTS production_mrp;
ALTER TABLE IF EXISTS public.bulk_work_orders SET SCHEMA production_mrp;

-- operator_exec
CREATE SCHEMA IF NOT EXISTS operator_exec;
ALTER TABLE IF EXISTS public.work_cards SET SCHEMA operator_exec;
ALTER TABLE IF EXISTS public.work_deposits SET SCHEMA operator_exec;
ALTER TABLE IF EXISTS public.rework_tickets SET SCHEMA operator_exec;
ALTER TABLE IF EXISTS public.washing_batches SET SCHEMA operator_exec;
ALTER TABLE IF EXISTS public.washing_batch_items SET SCHEMA operator_exec;
ALTER TABLE IF EXISTS public.washing_batch_sort_outputs SET SCHEMA operator_exec;
ALTER TABLE IF EXISTS public.trace_work_orders SET SCHEMA operator_exec;
ALTER TABLE IF EXISTS public.trace_tenant_ordinals SET SCHEMA operator_exec;
ALTER TABLE IF EXISTS public.trace_containers SET SCHEMA operator_exec;
ALTER TABLE IF EXISTS public.trace_container_panel_tallies SET SCHEMA operator_exec;
ALTER TABLE IF EXISTS public.trace_container_links SET SCHEMA operator_exec;
ALTER TABLE IF EXISTS public.trace_allocations SET SCHEMA operator_exec;

-- fulfillment
CREATE SCHEMA IF NOT EXISTS fulfillment;
ALTER TABLE IF EXISTS public.fulfillment_transfers SET SCHEMA fulfillment;
ALTER TABLE IF EXISTS public.fulfillment_route_settings SET SCHEMA fulfillment;
ALTER TABLE IF EXISTS public.fulfillment_transfer_events SET SCHEMA fulfillment;
ALTER TABLE IF EXISTS public.surat_jalan_manifests SET SCHEMA fulfillment;
ALTER TABLE IF EXISTS public.surat_jalan_items SET SCHEMA fulfillment;

-- invoicing
CREATE SCHEMA IF NOT EXISTS invoicing;
ALTER TABLE IF EXISTS public.invoice_number_sequences SET SCHEMA invoicing;
ALTER TABLE IF EXISTS public.invoice_issuer_profiles SET SCHEMA invoicing;
ALTER TABLE IF EXISTS public.invoice_templates SET SCHEMA invoicing;
ALTER TABLE IF EXISTS public.invoices SET SCHEMA invoicing;
ALTER TABLE IF EXISTS public.invoice_lines SET SCHEMA invoicing;
ALTER TABLE IF EXISTS public.invoice_payments SET SCHEMA invoicing;

-- ------------------------------------------------------------------------------
-- Hak akses role tenant (wemade_app, V16). V16 hanya mengatur public; tanpa blok ini
-- koneksi RLS kehilangan akses ke setiap tabel yang dipindah, dan tabel baru di schema
-- modul tidak otomatis ter-grant.
-- ------------------------------------------------------------------------------
GRANT USAGE ON SCHEMA org_chart, dynamic_rbac, factory_flow, sampling_order, crm_sales, master_data, vendor_contacts, tech_pack_bom, costing_hpp, production_mrp, operator_exec, fulfillment, invoicing TO wemade_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA org_chart, dynamic_rbac, factory_flow, sampling_order, crm_sales, master_data, vendor_contacts, tech_pack_bom, costing_hpp, production_mrp, operator_exec, fulfillment, invoicing TO wemade_app;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA org_chart, dynamic_rbac, factory_flow, sampling_order, crm_sales, master_data, vendor_contacts, tech_pack_bom, costing_hpp, production_mrp, operator_exec, fulfillment, invoicing TO wemade_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA org_chart GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO wemade_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA org_chart GRANT USAGE, SELECT ON SEQUENCES TO wemade_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA dynamic_rbac GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO wemade_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA dynamic_rbac GRANT USAGE, SELECT ON SEQUENCES TO wemade_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA factory_flow GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO wemade_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA factory_flow GRANT USAGE, SELECT ON SEQUENCES TO wemade_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA sampling_order GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO wemade_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA sampling_order GRANT USAGE, SELECT ON SEQUENCES TO wemade_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA crm_sales GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO wemade_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA crm_sales GRANT USAGE, SELECT ON SEQUENCES TO wemade_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA master_data GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO wemade_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA master_data GRANT USAGE, SELECT ON SEQUENCES TO wemade_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA vendor_contacts GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO wemade_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA vendor_contacts GRANT USAGE, SELECT ON SEQUENCES TO wemade_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA tech_pack_bom GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO wemade_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA tech_pack_bom GRANT USAGE, SELECT ON SEQUENCES TO wemade_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA costing_hpp GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO wemade_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA costing_hpp GRANT USAGE, SELECT ON SEQUENCES TO wemade_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA production_mrp GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO wemade_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA production_mrp GRANT USAGE, SELECT ON SEQUENCES TO wemade_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA operator_exec GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO wemade_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA operator_exec GRANT USAGE, SELECT ON SEQUENCES TO wemade_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA fulfillment GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO wemade_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA fulfillment GRANT USAGE, SELECT ON SEQUENCES TO wemade_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA invoicing GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO wemade_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA invoicing GRANT USAGE, SELECT ON SEQUENCES TO wemade_app;

-- ------------------------------------------------------------------------------
-- apply_tenant_rls (V1) memakai format('%I', nama) dan tidak bisa menerima schema.tabel.
-- Mulai V76 migrasi memakai versi schema-aware ini untuk tabel modul baru.
-- ------------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION apply_tenant_rls_in(schema_name text, target_table_name text) RETURNS void AS $$
BEGIN
    -- Kebijakan identik dengan apply_tenant_rls (V1); hanya nama tabelnya kini berschema.
    EXECUTE format('ALTER TABLE %I.%I ENABLE ROW LEVEL SECURITY;', schema_name, target_table_name);
    EXECUTE format('
        CREATE POLICY %I ON %I.%I
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
    ', target_table_name || '_tenant_isolation', schema_name, target_table_name);
END;
$$ LANGUAGE plpgsql;
