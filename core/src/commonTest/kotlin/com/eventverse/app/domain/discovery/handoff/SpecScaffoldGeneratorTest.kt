package com.eventverse.app.domain.discovery.handoff

import com.eventverse.app.domain.pack.tenant.layanan.LayananPilotPack
import com.eventverse.app.domain.discovery.HandoffScaffoldGenerator
import com.eventverse.app.domain.discovery.WidgetKind
import com.eventverse.app.domain.pack.ModuleDefinition
import com.eventverse.app.domain.pack.ModuleId
import com.eventverse.app.domain.pack.ModuleSectionCode
import com.eventverse.app.domain.pack.SlotCode
import com.eventverse.app.domain.pack.DomainPackRegistry
import com.eventverse.app.domain.prototype.EntitySpec
import com.eventverse.app.domain.prototype.FieldSpec
import com.eventverse.app.domain.prototype.FieldType
import com.eventverse.app.domain.prototype.PrototypeContractSamples
import com.eventverse.app.domain.prototype.PrototypeSpec
import com.eventverse.app.domain.prototype.ScreenSpec
import com.eventverse.app.domain.prototype.TableConfig
import com.eventverse.app.domain.rbac.DataScope
import com.eventverse.app.domain.rbac.ModuleKind
import com.eventverse.app.domain.rbac.ScopeCapability
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * `generateFromSpec` (kontrak §3.4, butir C1–C2). Dites dengan **dua template**: pilot `layanan`
 * (delapan tipe field, termasuk FILE lampiran dan RELATION `rujukan`) dan fixture tiket servis dari
 * kontrak B0 (kunci berhuruf besar), sesuai Kontrak 6.
 */
class SpecScaffoldGeneratorTest {
    private val generator = HandoffScaffoldGenerator()
    private val packExpr = "com.eventverse.app.domain.pack.tenant.layanan.LayananPilotPack.pack"

    private fun pilot(version: Int = 90) = generator.generateFromSpec(LayananPilotPack.spec, LayananPilotPack.module, version, packExpr)
    private fun HandoffScaffoldGenerator.Scaffold.file(suffix: String) = files.single { it.path.endsWith(suffix) }.content

    private fun module(id: String) = ModuleDefinition(
        ModuleId(id), "Uji", "Modul uji", ModuleSectionCode("UTAMA"), ModuleKind.OPERATIONAL, "clipboard",
        ScopeCapability.GLOBAL_ONLY, setOf(DataScope.ALL_TENANT_DATA), slot = SlotCode("uji_slot")
    )

    private fun specWith(vararg fields: FieldSpec, entityId: String = "item") = PrototypeSpec(
        listOf(EntitySpec(entityId, "Item", fields.toList())),
        listOf(ScreenSpec("s", "S", WidgetKind.TABLE, entityId, table = TableConfig(listOf(fields.first().key))))
    )

    @AfterTest fun cleanRegistry() { DomainPackRegistry.unregister(LayananPilotPack.CODE) }

    // ---- bentuk keluaran & determinisme ---------------------------------------------------------

    @Test
    fun output_isByteForByteDeterministic_andHasAllSixFiles() {
        assertEquals(pilot(), pilot())
        assertEquals(
            listOf(
                "server/src/main/resources/db/migration/V90__layanan_change_request_module.sql",
                "server/src/main/kotlin/com/eventverse/app/infrastructure/tables/LayananChangeRequestTables.kt",
                "server/src/main/kotlin/com/eventverse/app/infrastructure/PostgresLayananChangeRequestRepository.kt",
                "server/src/main/kotlin/com/eventverse/app/routes/LayananChangeRequestRoutes.kt",
                "server/src/test/kotlin/com/eventverse/app/routes/LayananChangeRequestRoutesGateTest.kt",
                "docs/handoff/layanan_change_request/WIRING.md"
            ),
            pilot().files.map { it.path }
        )
    }

    // ---- migrasi: kolom nyata, RLS, grant, katalog ---------------------------------------------

    @Test
    fun migration_hasTypedColumns_notJsonbStub_andTenantIsolation() {
        val sql = pilot().file("_module.sql")
        listOf(
            "CREATE SCHEMA IF NOT EXISTS layanan_change_request;",
            "CREATE TABLE IF NOT EXISTS layanan_change_request.change_requests (",
            "tenant_id   VARCHAR(64) NOT NULL REFERENCES tenants(id),",
            "judul       TEXT NOT NULL CHECK (btrim(judul) <> ''),",
            "peminta     TEXT,",
            "prioritas   VARCHAR(120) CHECK (prioritas IN ('Rendah', 'Sedang', 'Tinggi')),",
            "status      VARCHAR(120) NOT NULL CHECK (status IN ('Baru', 'Ditinjau', 'Disetujui', 'Selesai')),",
            "perkiraan_jam NUMERIC(18,4),",
            "target_selesai DATE,",
            "mendesak    BOOLEAN NOT NULL DEFAULT FALSE,",
            "catatan     TEXT,",
            "lampiran    TEXT,",
            "SELECT apply_tenant_rls_in('layanan_change_request', 'change_requests');",
            "GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA layanan_change_request TO wemade_app;",
            "'mce-layanan_change_request', 'layanan_change_request', 'layanan_change_request',",
            "'GLOBAL_ONLY', 'PLANNED'"
        ).forEach { assertTrue(it in sql, "baris hilang: $it") }
        assertTrue("JSONB" !in sql, "tidak boleh tabel stub payload JSONB")
        // C7 (TRD-FIELD-001 FR-1, kriteria terima #1): kolom RELATION memancarkan VARCHAR(64) TANPA
        // `REFERENCES` — rujukan logis lintas schema dilindungi pagar J3, integritas dijaga saat tulis nilai.
        val relationLine = sql.lineSequence().first { it.trimStart().startsWith("rujukan ") }
        assertTrue("VARCHAR(64)" in relationLine, relationLine)
        assertFalse("REFERENCES" in relationLine, "RELATION tidak boleh FK fisik (pagar J3): $relationLine")
    }

    // ---- keamanan: spec = data tak tepercaya -----------------------------------------------------

    @Test
    fun hostileFieldKey_isNormalisedToSafeIdentifier_notInjectedIntoSql() {
        val scaffold = generator.generateFromSpec(
            specWith(FieldSpec("x'; DROP TABLE tenants;--", "Berbahaya", FieldType.TEXT)), module("uji_modul"), 91, packExpr
        )
        val sql = scaffold.file("_module.sql")
        assertTrue("x_drop_table_tenants" in sql)
        assertTrue("DROP TABLE tenants" !in sql)
    }

    @Test
    fun enumOptionWithQuote_isEscapedInSql_andKotlinLiteralEscapesDollarAndQuote() {
        val scaffold = generator.generateFromSpec(
            specWith(FieldSpec("tipe", "Tipe \"x\" \$y", FieldType.ENUM, listOf("O'Brien", "Biasa"))), module("uji_modul"), 91, packExpr
        )
        assertTrue("IN ('O''Brien', 'Biasa')" in scaffold.file("_module.sql"))
        val routes = scaffold.file("Routes.kt")
        assertTrue("Tipe \\\"x\\\" \\\$y" in routes, "label harus di-escape di literal Kotlin")
    }

    @Test
    fun unsafeNames_areRejected_failClosed() {
        fun gen(vararg f: FieldSpec, entity: String = "item") = generator.generateFromSpec(specWith(*f, entityId = entity), module("uji_modul"), 91, packExpr)
        assertEquals("Field 'order' adalah kata cadangan SQL; ganti kuncinya.", assertFailsWith<IllegalArgumentException> { gen(FieldSpec("order", "O", FieldType.TEXT)) }.message)
        assertTrue(assertFailsWith<IllegalArgumentException> { gen(FieldSpec("id", "I", FieldType.TEXT)) }.message!!.contains("kolom bawaan"))
        assertTrue(assertFailsWith<IllegalArgumentException> { gen(FieldSpec("Judul", "A", FieldType.TEXT), FieldSpec("judul", "B", FieldType.TEXT)) }.message!!.contains("ganda"))
        assertTrue(assertFailsWith<IllegalArgumentException> { gen(FieldSpec("123", "N", FieldType.TEXT)) }.message!!.contains("diawali huruf"))
        assertTrue(assertFailsWith<IllegalArgumentException> { generator.generateFromSpec(PrototypeSpec(emptyList(), emptyList()), module("uji_modul"), 91, packExpr) }.message!!.contains("tepat satu entitas"))
    }

    // ---- route: urutan gerbang & hak per operasi -------------------------------------------------

    @Test
    fun routes_gateRunsBeforeBodyIsRead_andLevelsMatchTheGovernanceTable() {
        val routes = pilot().file("LayananChangeRequestRoutes.kt")
        assertTrue("/api/tenant/modules/layanan_change_request/change_requests" in routes, "di bawah /api/tenant agar tercakup RouteGateTest")
        assertTrue("tenant.pack.module(MODULE) == null" in routes, "modul harus ada di pack tenant")
        assertTrue(routes.indexOf("requireModuleAccess(MODULE") in 0 until routes.indexOf("tenant.pack.module(MODULE)"), "RBAC (403) sebelum cek pack (404)")
        assertTrue(routes.indexOf("DomainPackRegistry.moduleDefinition(MODULE) == null") in 0 until routes.indexOf("moduleDecision("), "modul tak dikenal = 403, bukan error saat menghitung keputusan")
        val post = routes.substringAfter("        post {").substringBefore("        put(")
        assertTrue(post.indexOf("authorized(AccessLevel.OPERATE)") in 0 until post.indexOf("bodyValues()"), "gerbang sebelum body dibaca")
        assertTrue("authorized(AccessLevel.MANAGE)" in routes.substringAfter("delete(\"/{id}\")"))
        assertTrue("authorized(AccessLevel.VIEW)" in routes.substringAfter("route(").substringBefore("post {"))
        assertTrue("PrototypeReducer.reduce(SPEC" in routes, "validasi memakai spec yang sama dengan prototype")
        assertTrue("DATE_FIELDS" in routes && "LocalDate.parse" in routes)
    }

    @Test
    fun generatedGateTest_coversUnauthorizedRoles_andUsesGivenPack() {
        val test = pilot().file("RoutesGateTest.kt")
        listOf("Forbidden", "Unauthorized", "NotFound").forEach { assertTrue(it in test, "kasus hilang: $it") }
        assertTrue("private val pack = $packExpr" in test)
        assertTrue("DomainPackRegistry.register(pack)" in test)
    }

    // ---- template kedua (non-pilot, kunci berhuruf besar) ----------------------------------------

    @Test
    fun secondTemplate_ticketFixtureFromContract_generatesValidNaming() {
        val entity = PrototypeContractSamples.ticketEntity
        val scaffold = generator.generateFromSpec(
            PrototypeSpec(listOf(entity), emptyList()), module("layanan_tiket_servis"), 92, packExpr
        )
        val sql = scaffold.file("_module.sql")
        assertTrue("CREATE TABLE IF NOT EXISTS layanan_tiket_servis.tikets (" in sql)
        assertTrue("judul       TEXT NOT NULL" in sql && "status      VARCHAR(120)" in sql)
        assertTrue("Tikets" !in sql && "PostgresLayananTiketServisRepository.kt" in scaffold.files.joinToString { it.path })
    }

    @Test
    fun repository_listOrdering_isTotal_soTiedTimestampsCannotReorderRows() {
        val repo = pilot().file("PostgresLayananChangeRequestRepository.kt")
        assertTrue("orderBy(T.createdAt to SortOrder.ASC, T.id to SortOrder.ASC)" in repo, "urutan harus total (created_at lalu id)")
    }

    @Test
    fun versionMustBePositive_andPackExpressionRequired() {
        assertFailsWith<IllegalArgumentException> { generator.generateFromSpec(LayananPilotPack.spec, LayananPilotPack.module, 0, packExpr) }
        assertFailsWith<IllegalArgumentException> { generator.generateFromSpec(LayananPilotPack.spec, LayananPilotPack.module, 90, " ") }
    }

    // ---- pack pilot sendiri ---------------------------------------------------------------------

    @Test
    fun pilotPack_isRegistrableAsDataPack_andSpecMatchesItsModule() {
        DomainPackRegistry.register(LayananPilotPack.pack)
        assertEquals(LayananPilotPack.module, DomainPackRegistry.moduleDefinition(LayananPilotPack.CHANGE_REQUEST))
        assertEquals(
            setOf(FieldType.TEXT, FieldType.NUMBER, FieldType.DATE, FieldType.ENUM, FieldType.BOOL, FieldType.FILE, FieldType.RELATION),
            LayananPilotPack.entity.fields.map { it.type }.toSet()
        )
    }
}
