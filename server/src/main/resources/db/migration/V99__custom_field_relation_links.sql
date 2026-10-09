-- ==============================================================================
-- WeMade ERP (Jalur B) — V99: tautan tipe field RELATION (C7, TRD-FIELD-001 Track B)
-- ==============================================================================
-- Tabel link BARU untuk custom field bertipe RELATION (padanan `custom_field_links` milik
-- USER_REF, yang tetap tidak disentuh — strangler). Baris rujukan hidup di schema modul
-- PEMEGANG field (`crm_sales`), sedangkan modul TARGET tetap pemilik satu-satunya record-nya.
--
-- FR-1: rujukan = id baris target (string), TIDAK ADA foreign key ke schema modul lain
-- (pagar J3/TRD-PLAT-004 P4). Satu-satunya FK adalah ke tabel platform di `public`
-- (`tenants`, `custom_field_definitions`) — bukan kopling ke schema modul.
-- Keberadaan target diverifikasi saat tulis lewat RelationTargetResolver (fail-closed).
--
-- Pola V76 (schema per modul) + V90 (RLS schema-aware, grant). Dibuat additive: rollback = drop tabel.
-- ==============================================================================

CREATE TABLE IF NOT EXISTS crm_sales.custom_field_relation_links (
    tenant_id           VARCHAR(64) NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    owner_resource      VARCHAR(64) NOT NULL,
    owner_record_id     VARCHAR(64) NOT NULL,
    field_id            VARCHAR(64) NOT NULL REFERENCES custom_field_definitions(id) ON DELETE CASCADE,
    ordinal             SMALLINT NOT NULL DEFAULT 0,
    target_resource     VARCHAR(64) NOT NULL,
    target_record_id    VARCHAR(64) NOT NULL,
    PRIMARY KEY (owner_resource, owner_record_id, field_id, ordinal)
);

-- Lookup opsi & validasi tulis selalu ber-key (tenant, target): jaga p95 <= 300 ms (NFR).
CREATE INDEX IF NOT EXISTS idx_custom_field_relation_links_target
    ON crm_sales.custom_field_relation_links(tenant_id, target_resource, target_record_id);

SELECT apply_tenant_rls_in('crm_sales', 'custom_field_relation_links');

GRANT SELECT, INSERT, UPDATE, DELETE ON crm_sales.custom_field_relation_links TO wemade_app;
