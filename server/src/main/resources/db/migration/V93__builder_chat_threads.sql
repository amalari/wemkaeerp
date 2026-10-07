-- PLAN-builder-interview-chat Fase A: utas chat per modul + pesan pertanyaan follow-up.
--   module_id : NULL = utas "Semua" (tanpa filter modul); terisi = kode modul pack (data, bukan enum).
--   kind      : TEXT biasa | QUESTION (pesan AGENT yang membawa pertanyaan follow-up).
--   questions : JSON array [{id, question, answer?}] — hanya bermakna untuk kind = QUESTION.
-- Aditif dan nullable/berbawaan: pesan lama tetap terbaca persis seperti sebelumnya (utas Semua, TEXT).
ALTER TABLE builder.chat_messages ADD COLUMN IF NOT EXISTS module_id VARCHAR(80) NULL;
ALTER TABLE builder.chat_messages ADD COLUMN IF NOT EXISTS kind      VARCHAR(16) NOT NULL DEFAULT 'TEXT';
ALTER TABLE builder.chat_messages ADD COLUMN IF NOT EXISTS questions TEXT        NOT NULL DEFAULT '[]';

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chat_messages_kind_check') THEN
        ALTER TABLE builder.chat_messages
            ADD CONSTRAINT chat_messages_kind_check CHECK (kind IN ('TEXT', 'QUESTION'));
        ALTER TABLE builder.chat_messages
            ADD CONSTRAINT chat_messages_question_agent_check CHECK (kind = 'TEXT' OR role = 'AGENT');
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_builder_chat_messages_thread
    ON builder.chat_messages (conversation_id, module_id, created_at);
