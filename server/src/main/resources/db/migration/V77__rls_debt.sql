-- ==============================================================================
-- WeMade ERP (Jalur B) — V77: Bayar utang RLS (TRD-PLAT-001 B8, ledger RLS_DEBT)
-- ==============================================================================
-- Sepuluh tabel ber-tenant_id dibuat tanpa row level security (ditemukan saat B8 memindah
-- tabel ke schema modul). Kebijakannya identik dengan tabel tenant lain: baris hanya terbaca/
-- tertulis bila tenant_id = app.current_tenant_id, yang di-set DatabaseFactory.dbQuery(tenantId)
-- per transaksi. Koneksi owner (dbQuery tanpa tenant: login, /api/admin, platform) tidak
-- terpengaruh karena pemilik tabel melewati RLS (tidak FORCE).
-- ==============================================================================

SELECT apply_tenant_rls_in('factory_flow', 'tenant_locations');
SELECT apply_tenant_rls_in('factory_flow', 'tenant_location_settings');
SELECT apply_tenant_rls_in('factory_flow', 'tenant_flow_node_locations');
SELECT apply_tenant_rls_in('fulfillment', 'surat_jalan_manifests');
SELECT apply_tenant_rls_in('operator_exec', 'rework_tickets');
SELECT apply_tenant_rls_in('operator_exec', 'trace_tenant_ordinals');
SELECT apply_tenant_rls_in('operator_exec', 'work_cards');
SELECT apply_tenant_rls_in('operator_exec', 'work_deposits');
SELECT apply_tenant_rls_in('sampling_order', 'sample_storage_records');
SELECT apply_tenant_rls_in('sampling_order', 'tenant_optional_processes');
