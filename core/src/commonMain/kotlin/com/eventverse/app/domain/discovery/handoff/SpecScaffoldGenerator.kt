package com.eventverse.app.domain.discovery.handoff

import com.eventverse.app.domain.discovery.HandoffScaffoldGenerator
import com.eventverse.app.domain.pack.ModuleDefinition
import com.eventverse.app.domain.prototype.PrototypeSpec

/**
 * Merakit [HandoffScaffoldGenerator.Scaffold] dari [PrototypeSpec] (kontrak §3.4, butir C1–C2).
 *
 * **Batas v1 (fail-closed, ditolak dengan pesan, bukan diam-diam dipotong):** tepat **satu entitas** per
 * modul; maksimum satu migrasi per panggilan. Deterministik: masukan sama → keluaran identik byte per byte
 * (tidak ada waktu/acak). Keluaran = **kandidat PR untuk ditinjau manusia**; generator tidak menulis ke
 * database maupun pohon sumber.
 */
internal object SpecScaffoldGenerator {

    fun generate(spec: PrototypeSpec, module: ModuleDefinition, migrationVersion: Int, packExpression: String): HandoffScaffoldGenerator.Scaffold {
        require(migrationVersion > 0) { "Versi migrasi harus positif." }
        require(spec.entities.size == 1) { "Generator v1 mendukung tepat satu entitas per modul (spec punya ${spec.entities.size})." }
        require(packExpression.isNotBlank()) { "packExpression (ekspresi Kotlin pack modul untuk test gerbang) wajib diisi." }
        val table = SpecTable.of(module.id.value, spec.entities.single())
        val schema = module.id.value
        val pascal = SpecNaming.pascal(schema)
        fun file(path: String, content: String) = HandoffScaffoldGenerator.GeneratedFile(path, content)
        return HandoffScaffoldGenerator.Scaffold(
            packCode = schema,
            migrationVersion = migrationVersion,
            files = listOf(
                file("server/src/main/resources/db/migration/V${migrationVersion}__${schema}_module.sql", SpecMigrationWriter.write(module, listOf(table))),
                file("server/src/main/kotlin/com/eventverse/app/infrastructure/tables/${pascal}Tables.kt", SpecPostgresWriter.tableFile(table)),
                file("server/src/main/kotlin/com/eventverse/app/infrastructure/Postgres${pascal}Repository.kt", SpecPostgresWriter.repositoryFile(table)),
                file("server/src/main/kotlin/com/eventverse/app/routes/${pascal}Routes.kt", SpecRoutesWriter.routesFile(schema, table)),
                file("server/src/test/kotlin/com/eventverse/app/routes/${pascal}RoutesGateTest.kt", SpecRouteTestWriter.testFile(schema, table, packExpression)),
                file("docs/handoff/$schema/WIRING.md", wiring(schema, pascal, table))
            )
        )
    }

    /** Titik pendaftaran manual (anatomi `module-integration-rules.md` §5) — keputusan manusia, bukan generator. */
    private fun wiring(schema: String, pascal: String, t: SpecTable): String = buildString {
        val fn = SpecNaming.camel(schema) + "Routes"
        appendLine("# Pendaftaran modul `$schema` (kandidat — terapkan dan tinjau manual)")
        appendLine()
        appendLine("1. **`ModuleSchemaMap.byModule`** (server/.../infrastructure/ModuleSchemaMap.kt) — dijaga `ModuleSchemaOwnershipTest`:")
        appendLine("   `ModuleId(\"$schema\") to setOf(\"${t.table}\"),`")
        appendLine("2. **`RouteOwnership.moduleRoutes`** (server/.../routes/RouteOwnership.kt) — dijaga `RouteOwnershipTest`:")
        appendLine("   `\"/api/tenant/modules/$schema\" to RouteOwner.Module(ModuleId(\"$schema\")),`")
        appendLine("3. **`DomainRouteWiring.registerIn`** (server/.../routes/DomainRouteWiring.kt):")
        appendLine("   `$fn(Postgres${pascal}Repository(), roleRepo, assignmentRepo)`")
        appendLine("4. **Pack**: modul harus ada di pack tenant (pack data berprefiks `${schema.substringBefore('_')}_`, didaftarkan lewat `DomainPackRegistry.register`).")
        appendLine("5. **Wewenang jabatan** (`custom_roles.module_permissions`, kunci NAME `${t.entity.id.uppercase()}`→ lihat migrasi) dan entitlement/kuota: keputusan bisnis.")
        appendLine("6. Jalankan: `ModuleSchemaOwnershipTest`, `RouteOwnershipTest`, `RouteGateTest` (butuh Postgres) dan test gerbang yang ikut digenerate.")
    }
}
