package com.eventverse.app.domain.discovery

import com.eventverse.app.domain.pack.DomainPack
import com.eventverse.app.domain.pack.ModuleDefinition

/**
 * B4 — [HandoffGenerator]: mengubah pack data hasil handoff menjadi **kandidat PR** berisi pendaftaran
 * modul operasional (pola `GenerateSeedTopologyTool` — alat penulis, bukan aksi otomatis).
 *
 * Keluarannya berupa berkas teks yang ditinjau manusia sebelum diterapkan: migrasi Flyway
 * (`CREATE SCHEMA` per modul + tabel stub + `apply_tenant_rls_in` + grant `wemade_app` + entri
 * katalog), potongan `ModuleSchemaMap`, stub route ber-gerbang, dan catatan `ModuleScreenRegistry`.
 * Generator TIDAK menyentuh database maupun pohon sumber — menimpa kosakata platform diam-diam
 * melanggar plan §7.
 *
 * Deterministik: pack yang sama selalu menghasilkan keluaran byte-per-byte identik, sehingga
 * konsistensinya bisa dijaga test.
 */
class HandoffScaffoldGenerator {

    data class GeneratedFile(val path: String, val content: String)

    data class Scaffold(val packCode: String, val migrationVersion: Int, val files: List<GeneratedFile>)

    /**
     * Keluaran **dari spec** (kontrak §3.4): migrasi bertabel nyata, tabel+repository Exposed, route CRUD
     * fail-closed, dan test gerbang — bukan tabel stub. [packExpression] = ekspresi Kotlin yang menghasilkan
     * `DomainPack` modul ini (dipakai test gerbang yang digenerate; mis. `LayananPilotPack.pack`).
     * Batas v1 dan alasannya: lihat [com.eventverse.app.domain.discovery.handoff.SpecScaffoldGenerator].
     */
    fun generateFromSpec(
        spec: com.eventverse.app.domain.prototype.PrototypeSpec,
        module: ModuleDefinition,
        migrationVersion: Int,
        packExpression: String
    ): Scaffold = com.eventverse.app.domain.discovery.handoff.SpecScaffoldGenerator.generate(spec, module, migrationVersion, packExpression)

    fun generate(pack: DomainPack, migrationVersion: Int): Scaffold = Scaffold(
        packCode = pack.code.value,
        migrationVersion = migrationVersion,
        files = listOf(
            migrationFile(pack, migrationVersion),
            GeneratedFile(
                "server/src/main/kotlin/com/eventverse/app/infrastructure/ModuleSchemaMap.snippet.kt.txt",
                schemaMapSnippet(pack)
            ),
            GeneratedFile(
                "server/src/main/kotlin/com/eventverse/app/routes/${stubRouteFileName(pack.code.value)}",
                stubRouteSnippet(pack)
            ),
            GeneratedFile(
                "app/shared/src/commonMain/kotlin/com/eventverse/app/presentation/workspace/ModuleScreenRegistry.snippet.kt.txt",
                screenRegistryNote(pack)
            )
        ) + pack.modules.map { module -> catalogSnippetFile(pack, module) }
    )

    // -------------------------------------------------------------------------
    // 1. Migrasi Flyway — satu berkas untuk seluruh pack.
    // -------------------------------------------------------------------------
    private fun migrationFile(pack: DomainPack, version: Int): GeneratedFile {
        val sql = buildString {
            appendLine("-- ==============================================================================")
            appendLine("-- KANDIDAT PR — HASIL GENERATOR (HandoffScaffoldGenerator) — WAJIB REVIEW MANUSIA")
            appendLine("-- ==============================================================================")
            appendLine("-- Pack `${pack.code.value}`: ${pack.displayName}")
            appendLine("-- Diperankan modul: ${pack.modules.joinToString { it.id.value }}")
            appendLine("-- Sumber pola: V76 (schema per modul + grant), V64 (registrasi katalog), V77 (RLS).")
            appendLine("-- ==============================================================================")
            appendLine()
            pack.modules.forEach { module ->
                appendLine("-- ${module.id.value} — ${module.displayName}")
                appendLine("CREATE SCHEMA IF NOT EXISTS ${module.id.value};")
                appendLine()
                appendLine("CREATE TABLE IF NOT EXISTS ${module.id.value}.${module.id.value}_records (")
                appendLine("    id          VARCHAR(64) PRIMARY KEY,")
                appendLine("    tenant_id   VARCHAR(64) NOT NULL REFERENCES tenants(id),")
                appendLine("    title       VARCHAR(200) NOT NULL DEFAULT '',")
                appendLine("    payload     JSONB NOT NULL DEFAULT '{}'::jsonb, -- TODO(review): kolom nyata per fitur")
                appendLine("    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),")
                appendLine("    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()")
                appendLine(");")
                appendLine()
                appendLine("CREATE INDEX IF NOT EXISTS idx_${module.id.value}_records_tenant")
                appendLine("    ON ${module.id.value}.${module.id.value}_records(tenant_id, created_at);")
                appendLine()
                appendLine("SELECT apply_tenant_rls_in('${module.id.value}', '${module.id.value}_records');")
                appendLine()
                appendLine("GRANT USAGE ON SCHEMA ${module.id.value} TO wemade_app;")
                appendLine("GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA ${module.id.value} TO wemade_app;")
                appendLine("GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA ${module.id.value} TO wemade_app;")
                appendLine("ALTER DEFAULT PRIVILEGES IN SCHEMA ${module.id.value} GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO wemade_app;")
                appendLine("ALTER DEFAULT PRIVILEGES IN SCHEMA ${module.id.value} GRANT USAGE, SELECT ON SEQUENCES TO wemade_app;")
                appendLine()
            }
            appendLine("-- -----------------------------------------------------------------------------")
            appendLine("-- Registrasi katalog (pola V64). lifecycle PLANNED & tanpa harga: modul belum jadi.")
            appendLine("-- -----------------------------------------------------------------------------")
            pack.modules.forEach { module ->
                appendLine("INSERT INTO module_catalog_entries (")
                appendLine("    id, module_id, archetype_code, display_name, description, category_code,")
                appendLine("    scope_capability, lifecycle_status")
                appendLine(") VALUES (")
                appendLine("    'mce-${module.id.value}', '${module.id.value}', '${module.slot?.value ?: "custom_extension"}',")
                appendLine("    '${sqlQuote(module.displayName)}', '${sqlQuote(module.description)}',")
                appendLine("    '${module.section.value}', '${module.scopeCapability.name}', 'PLANNED'")
                appendLine(")")
                appendLine("ON CONFLICT (module_id) DO NOTHING;")
                appendLine()
            }
            appendLine("-- -----------------------------------------------------------------------------")
            appendLine("-- TODO(review): backfill wewenang (pola V64). Kunci untuk modul pack = module_id")
            appendLine("-- string; level per jabatan (Owner otomatis penuh) adalah keputusan bisnis, bukan")
            appendLine("-- keputusan generator:")
            appendLine("-- UPDATE custom_roles SET module_permissions = module_permissions ||")
            appendLine("--   jsonb_build_object('${pack.modules.firstOrNull()?.id?.value ?: "<module_id>"}',")
            appendLine("--   jsonb_build_object('level', 'MANAGE', 'scope', 'ALL_TENANT_DATA'))")
            appendLine("-- WHERE is_system_default = TRUE AND ...;")
            appendLine("-- TODO(review): backfill entitlement (`granted_custom_module_ids`) untuk tenant")
            appendLine("-- yang memakai skema granted eksplisit, agar tidak kehilangan modul baru.")
            appendLine("-- ==============================================================================")
        }
        return GeneratedFile(
            path = "server/src/main/resources/db/migration/V${version.toString().padStart(3, '0')}__register_${pack.code.value}_modules.sql",
            content = sql
        )
    }

    private fun sqlQuote(raw: String): String = raw.replace("'", "''")

    // -------------------------------------------------------------------------
    // 2. Potongan ModuleSchemaMap.byModule.
    // -------------------------------------------------------------------------
    private fun schemaMapSnippet(pack: DomainPack): String = buildString {
        appendLine("-- KANDIDAT PR: tambahkan ke `ModuleSchemaMap.byModule` (server/.../ModuleSchemaMap.kt).")
        appendLine("-- Dijaga `ModuleSchemaOwnershipTest`: entri wajib cocok dengan schema nyata setelah migrasi.")
        pack.modules.forEach { module ->
            appendLine("ModuleId(\"${module.id.value}\") to setOf(\"${module.id.value}_records\"),")
        }
    }

    // -------------------------------------------------------------------------
    // 3. Stub route ber-gerbang — pola ModuleAccessGuard (fail-closed).
    // -------------------------------------------------------------------------
    private fun stubRouteFileName(packCode: String): String =
        packCode.replaceFirstChar { it.uppercase() } + "StubRoutes.snippet.kt.txt"

    private fun stubRouteSnippet(pack: DomainPack): String = buildString {
        appendLine("// KANDIDAT PR: stub route untuk modul pack `${pack.code.value}`.")
        appendLine("// Daftarkan di ServerRouteWiring SETELAH tabel & katalog nyata. Gerbang WAJIB")
        appendLine("// fail-closed: keputusan RBAC yang tidak bisa dihitung = 403 (Kontrak 7).")
        appendLine("//")
        pack.modules.forEach { module ->
            appendLine("// fun Route.${module.id.value.replace("-", "_")}StubRoutes() = route(\"/api/modules/${module.id.value}\") {")
            appendLine("//     get {")
            appendLine("//         val decision = moduleDecision(call, ModuleId(\"${module.id.value}\"), AccessLevel.VIEW)")
            appendLine("//             ?: return@get call.respond(HttpStatusCode.Forbidden)")
            appendLine("//         // TODO(review): query nyata ke ${module.id.value}.${module.id.value}_records")
            appendLine("//         call.respondText(\"[]\", ContentType.Application.Json)")
            appendLine("//     }")
            appendLine("// }")
            appendLine("//")
        }
    }

    // -------------------------------------------------------------------------
    // 4. Catatan ModuleScreenRegistry — tanpa entri, modul memakai layar generik.
    // -------------------------------------------------------------------------
    private fun screenRegistryNote(pack: DomainPack): String = buildString {
        appendLine("// KANDIDAT PR (opsional): layar khusus modul pack `${pack.code.value}`.")
        appendLine("// Modul pack TANPA entri di ModuleScreenRegistry sudah otomatis memakai layar kerja")
        appendLine("// generik (GenericModuleRoute) — jangan tambahkan entri sebelum layar kustomnya ada.")
        appendLine("//")
        pack.modules.forEach { module ->
            appendLine("// ${module.id.value} to { c -> /* TODO: layar kustom ${module.displayName} */ },")
        }
    }

    // -------------------------------------------------------------------------
    // 5. Satu berkas review per modul — ringkasan keputusan yang wajib diperiksa.
    // -------------------------------------------------------------------------
    private fun catalogSnippetFile(pack: DomainPack, module: ModuleDefinition) = GeneratedFile(
        path = "docs/handoff/${pack.code.value}/${module.id.value}.catalog.md",
        content = buildString {
            appendLine("# Kandidat katalog: ${module.id.value}")
            appendLine()
            appendLine("- Pack: ${pack.code.value} (${pack.displayName})")
            appendLine("- Slot: ${module.slot?.value ?: "(tanpa slot / kustom)"}")
            appendLine("- Seksi: ${module.section.value}")
            appendLine("- Scope capability: ${module.scopeCapability.name}")
            appendLine("- Nama tampilan: ${module.displayName}")
            appendLine("- Deskripsi: ${module.description}")
            appendLine()
            appendLine("Review manusia: cek Uji Variabilitas (tenant-variability-rules.md Kontrak 1) —")
            appendLine("modul pack boleh dijadikan modul hanya bila benar-benar dijual & di-RBAC; kalau")
            appendLine("hanya tahap/proses/stasiun, ia cukup sebagai data di pack (Kontrak 2).")
        }
    )
}
