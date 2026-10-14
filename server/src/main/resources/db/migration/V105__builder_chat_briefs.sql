-- TRD-BLDR-001 Track B: pesan BRIEF per modul pada utas chat Builder.
--   features     : JSON array teks (butir fitur) — hanya bermakna untuk kind = BRIEF.
--   stages       : JSON array [{name, optional?, reworkTo?}] (sketsa Tahap) — hanya BRIEF.
--   basis_ref    : jejak basis brief (hash narasi + fitur tautan + versi agent) untuk idempotensi — hanya BRIEF.
--   brief_source : AI | DETERMINISTIC — hanya BRIEF.
-- Aditif dan berbawaan: pesan lama tetap terbaca persis seperti sebelumnya.
ALTER TABLE builder.chat_messages ADD COLUMN IF NOT EXISTS features     TEXT        NOT NULL DEFAULT '[]';
ALTER TABLE builder.chat_messages ADD COLUMN IF NOT EXISTS stages       TEXT        NOT NULL DEFAULT '[]';
ALTER TABLE builder.chat_messages ADD COLUMN IF NOT EXISTS basis_ref    VARCHAR(80) NULL;
ALTER TABLE builder.chat_messages ADD COLUMN IF NOT EXISTS brief_source VARCHAR(16) NULL;

ALTER TABLE builder.chat_messages DROP CONSTRAINT IF EXISTS chat_messages_kind_check;
ALTER TABLE builder.chat_messages
    ADD CONSTRAINT chat_messages_kind_check CHECK (kind IN ('TEXT', 'QUESTION', 'BRIEF'));

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chat_messages_brief_shape_check') THEN
        ALTER TABLE builder.chat_messages
            ADD CONSTRAINT chat_messages_brief_shape_check CHECK (
                kind <> 'BRIEF'
                OR (role = 'AGENT' AND module_id IS NOT NULL AND brief_source IS NOT NULL AND basis_ref IS NOT NULL));
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_builder_chat_messages_brief
    ON builder.chat_messages (conversation_id, module_id, created_at) WHERE kind = 'BRIEF';
