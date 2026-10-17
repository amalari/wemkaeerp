-- =============================================================================
-- V105: Tutorial storage for RAG and help system
-- Tutorials are platform-level (pack-level), stored in public schema
-- This enables RAG vector search and admin editing in the future
-- =============================================================================

CREATE TABLE IF NOT EXISTS tutorials (
    id              VARCHAR(64) PRIMARY KEY,
    pack_code       VARCHAR(40) NOT NULL,
    scope_type      VARCHAR(20) NOT NULL, -- 'module' or 'surface'
    scope_code      VARCHAR(40),          -- module_id or surface_code
    title           VARCHAR(200) NOT NULL,
    summary         TEXT NOT NULL,
    required_level  VARCHAR(20) NOT NULL DEFAULT 'VIEW',
    keywords        TEXT[] NOT NULL,      -- array of keywords for matching
    steps           JSONB NOT NULL,      -- array of {title, description, anchor, screen, placement}
    sample_questions TEXT[] NOT NULL,      -- array of example questions
    created_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Index for pack-level queries
CREATE INDEX IF NOT EXISTS idx_tutorials_pack_code ON tutorials (pack_code);

-- Index for module/surface queries
CREATE INDEX IF NOT EXISTS idx_tutorials_scope ON tutorials (scope_type, scope_code);

-- Index for keyword search (GIN for array)
CREATE INDEX IF NOT EXISTS idx_tutorials_keywords_gin ON tutorials USING GIN (keywords);

-- Unique constraint: one tutorial per module/surface
CREATE UNIQUE INDEX IF NOT EXISTS idx_tutorials_unique_module
    ON tutorials (pack_code, scope_type, scope_code)
    WHERE scope_type = 'module';

GRANT SELECT, INSERT, UPDATE, DELETE ON tutorials TO wemade_app;
