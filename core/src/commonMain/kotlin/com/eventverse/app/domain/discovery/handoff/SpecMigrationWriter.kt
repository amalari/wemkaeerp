package com.eventverse.app.domain.discovery.handoff

import com.eventverse.app.domain.pack.ModuleDefinition

/**
 * Migrasi Flyway dari spec (kontrak §3.4): schema per modul, tabel berkolom bertipe nyata, RLS, grant,
 * dan entri katalog — pola V76 (schema+grant), V64 (katalog), V77 (RLS). Menggantikan tabel stub
 * `payload JSONB` untuk modul yang spec-nya diketahui.
 */
internal object SpecMigrationWriter {

    fun write(module: ModuleDefinition, tables: List<SpecTable>): String = buildString {
        val schema = module.id.value
        appendLine("-- ==============================================================================")
        appendLine("-- KANDIDAT PR — HASIL GENERATOR (HandoffScaffoldGenerator.generateFromSpec) — WAJIB REVIEW MANUSIA")
        appendLine("-- ==============================================================================")
        appendLine("-- Modul `$schema`: ${module.displayName}")
        appendLine("-- Sumber pola: V76 (schema per modul + grant), V64 (registrasi katalog), V77 (RLS).")
        appendLine("-- Setelah diterapkan file ini milik tim; perubahan berikutnya = migrasi baru, bukan menjalankan ulang generator.")
        appendLine("-- ==============================================================================")
        appendLine()
        appendLine("CREATE SCHEMA IF NOT EXISTS $schema;")
        appendLine()
        tables.forEach { t ->
            appendLine("CREATE TABLE IF NOT EXISTS ${t.qualified} (")
            appendLine("    id          VARCHAR(64) PRIMARY KEY,")
            appendLine("    tenant_id   VARCHAR(64) NOT NULL REFERENCES tenants(id),")
            t.columns.forEach { c -> appendLine("    ${c.name.padEnd(11)} ${c.sqlDefinition()},") }
            appendLine("    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),")
            appendLine("    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()")
            appendLine(");")
            appendLine()
            appendLine("CREATE INDEX IF NOT EXISTS idx_${t.table}_tenant ON ${t.qualified}(tenant_id, created_at);")
            appendLine()
            appendLine("SELECT apply_tenant_rls_in('$schema', '${t.table}');")
            appendLine()
        }
        appendLine("GRANT USAGE ON SCHEMA $schema TO wemade_app;")
        appendLine("GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA $schema TO wemade_app;")
        appendLine("GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA $schema TO wemade_app;")
        appendLine("ALTER DEFAULT PRIVILEGES IN SCHEMA $schema GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO wemade_app;")
        appendLine("ALTER DEFAULT PRIVILEGES IN SCHEMA $schema GRANT USAGE, SELECT ON SEQUENCES TO wemade_app;")
        appendLine()
        appendLine("-- Registrasi katalog (pola V64): PLANNED & tanpa harga — modul belum dijual.")
        appendLine("INSERT INTO module_catalog_entries (")
        appendLine("    id, module_id, archetype_code, display_name, description, category_code,")
        appendLine("    scope_capability, lifecycle_status")
        appendLine(") VALUES (")
        appendLine("    'mce-$schema', ${SpecNaming.sqlString(schema)}, ${SpecNaming.sqlString(module.slot?.value ?: "custom_extension")},")
        appendLine("    ${SpecNaming.sqlString(module.displayName)}, ${SpecNaming.sqlString(module.description)},")
        appendLine("    ${SpecNaming.sqlString(module.section.value)}, ${SpecNaming.sqlString(module.scopeCapability.name)}, 'PLANNED'")
        appendLine(")")
        appendLine("ON CONFLICT (module_id) DO NOTHING;")
        appendLine()
        appendLine("-- TODO(review): backfill wewenang per jabatan (pola V64) adalah keputusan bisnis, bukan keputusan generator.")
        appendLine("-- Kunci modul di `custom_roles.module_permissions` = NAME (${module.id.storedName}); katalog & node pipeline = code ($schema).")
        appendLine("-- Owner tenant (TENANT_ADMIN tanpa jabatan) otomatis MANAGE; jabatan lain TIDAK punya akses sampai diberi.")
    }
}
