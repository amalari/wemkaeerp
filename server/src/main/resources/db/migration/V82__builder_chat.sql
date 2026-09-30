-- ==============================================================================
-- WeMade ERP (Jalur B) — V82: Builder chat tersimpan (PLAN-builder-console M1)
-- ==============================================================================
-- Dua tabel di schema builder (milik tenant, RLS pola V76/V81):
--   builder.conversations — satu percakapan per tenant (UNIQUE tenant_id).
--   builder.chat_messages — pesan USER/AGENT/SYSTEM. Patch usulan = dokumen draf
--     penuh (draft_json) di pesan AGENT, BELUM diterapkan sampai manusia menekan
--     "Terapkan" (maka applied_draft_id terisi). Agent tidak pernah menulis draf.
-- CHECK menegakkan invarian domain (BuilderChat.kt):
--   role terbatas; pesan ber-role agent boleh membawa patch; applied_draft_id
--   hanya sah pada pesan agent.
-- ==============================================================================

CREATE TABLE IF NOT EXISTS builder.conversations (
    id         VARCHAR(80) PRIMARY KEY,
    tenant_id  VARCHAR(64) NOT NULL REFERENCES public.tenants(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (tenant_id)
);

CREATE TABLE IF NOT EXISTS builder.chat_messages (
    id                VARCHAR(120) PRIMARY KEY,
    conversation_id   VARCHAR(80)  NOT NULL REFERENCES builder.conversations(id) ON DELETE CASCADE,
    tenant_id         VARCHAR(64)  NOT NULL REFERENCES public.tenants(id),
    role              VARCHAR(16)  NOT NULL CHECK (role IN ('USER','AGENT','SYSTEM')),
    text              TEXT         NOT NULL,
    proposed_draft    TEXT         NULL,
    proposed_summary  TEXT         NOT NULL DEFAULT '[]',
    applied_draft_id  VARCHAR(64)  NULL,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    -- Patch usulan hanya pada pesan agent; taut penerapan hanya pada pesan agent.
    CHECK (role = 'AGENT' OR proposed_draft IS NULL),
    CHECK (role = 'AGENT' OR applied_draft_id IS NULL)
);

CREATE INDEX IF NOT EXISTS idx_builder_chat_messages_conv
    ON builder.chat_messages (conversation_id, created_at);

GRANT USAGE ON SCHEMA builder TO wemade_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON builder.conversations TO wemade_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON builder.chat_messages TO wemade_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA builder GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO wemade_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA builder GRANT USAGE, SELECT ON SEQUENCES TO wemade_app;
SELECT apply_tenant_rls_in('builder', 'conversations');
SELECT apply_tenant_rls_in('builder', 'chat_messages');
