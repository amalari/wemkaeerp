package com.eventverse.app.domain.discovery.handoff

/**
 * Test gerbang yang **ikut digenerate** bersama route (kontrak §3.4; Kontrak 7 tenant-variability):
 * peran tak berwenang harus 403, tanpa kredensial 401, tenant tanpa modul di pack-nya 404 — semuanya
 * berhenti di gerbang sebelum repository disentuh, sehingga tidak butuh Postgres (repository memori disuntikkan; pola `BuilderDraftBootstrapTest` — catatan: `VendorAccessApiTest` mengklaim hal sama tetapi memakai repository bawaan).
 * Jalur sukses (CRUD, RLS) butuh database dan diuji terpisah.
 */
internal object SpecRouteTestWriter {

    fun testFile(moduleId: String, t: SpecTable, packExpression: String): String {
        val cls = SpecNaming.pascal(t.schema) + "RoutesGateTest"
        val path = "/api/tenant/modules/" + t.schema + "/" + t.table
        return buildString {
            appendLine("package com.eventverse.app.routes")
            appendLine()
            listOf(
                "com.eventverse.app.asStaff", "com.eventverse.app.asTenant", "com.eventverse.app.domain.pack.DomainPackCode",
                "com.eventverse.app.domain.pack.DomainPackRegistry", "com.eventverse.app.domain.pack.GarmentDomainPack",
                "com.eventverse.app.domain.pack.ModuleId", "com.eventverse.app.domain.rbac.AccessLevel",
                "com.eventverse.app.domain.rbac.CustomRole", "com.eventverse.app.domain.rbac.DataScope",
                "com.eventverse.app.domain.rbac.ModuleAccessConfig", "com.eventverse.app.domain.rbac.RoleId",
                "com.eventverse.app.domain.tenant.SubscriptionTier", "com.eventverse.app.domain.tenant.Tenant",
                "com.eventverse.app.domain.tenant.TenantId", "com.eventverse.app.domain.tenant.TenantName",
                "com.eventverse.app.domain.tenant.TenantSlug", "com.eventverse.app.domain.tenant.TenantStatus",
                "com.eventverse.app.infrastructure.InMemoryModuleAssignmentRepository", "com.eventverse.app.infrastructure.InMemoryRoleRepository",
                "com.eventverse.app.infrastructure.InMemoryTenantEntitlementRepository", "com.eventverse.app.infrastructure.InMemoryTenantPipelineRepository",
                "com.eventverse.app.infrastructure.InMemoryTenantRepository", "com.eventverse.app.module",
                "io.ktor.client.request.delete", "io.ktor.client.request.get", "io.ktor.client.request.header", "io.ktor.client.request.post",
                "io.ktor.client.request.setBody", "io.ktor.http.ContentType", "io.ktor.http.HttpStatusCode", "io.ktor.http.contentType",
                "io.ktor.server.testing.ApplicationTestBuilder", "io.ktor.server.testing.testApplication",
                "kotlinx.coroutines.runBlocking", "kotlin.test.AfterTest", "kotlin.test.BeforeTest", "kotlin.test.Test", "kotlin.test.assertEquals", "kotlin.test.assertTrue"
            ).forEach { appendLine("import $it") }
            appendLine()
            appendLine("/** KANDIDAT PR (hasil generator) — gerbang RBAC modul ${SpecNaming.kString(moduleId)}. Tanpa Postgres. */")
            appendLine("class $cls {")
            appendLine("    private val pack = $packExpression")
            appendLine("    private val module = ModuleId(${SpecNaming.kString(moduleId)})")
            appendLine("    private val slug = \"gerbang-uji\"")
            appendLine("    private val tenantId = TenantId(\"ten-gerbang-uji\")")
            appendLine("    private val base = ${SpecNaming.kString(path)}")
            appendLine()
            appendLine("    @BeforeTest fun registerPack() { if (DomainPackRegistry.find(pack.code) == null) DomainPackRegistry.register(pack) }")
            appendLine("    @AfterTest fun unregisterPack() { DomainPackRegistry.unregister(pack.code) }")
            appendLine()
            appendLine("    private fun ApplicationTestBuilder.installApp(level: AccessLevel, tenantPack: DomainPackCode = pack.code) {")
            appendLine("        val tenants = InMemoryTenantRepository()")
            appendLine("        val roles = InMemoryRoleRepository()")
            appendLine("        runBlocking {")
            appendLine("            tenants.save(Tenant(tenantId, TenantSlug(slug), TenantName(\"Tenant Uji Gerbang\"), TenantStatus.ACTIVE, SubscriptionTier.PRO, domainPack = tenantPack))")
            appendLine("            roles.save(CustomRole(id = RoleId(\"role-uji\"), tenantId = tenantId, name = \"Staf Uji\", description = \"\", modulePermissions = mapOf(module to ModuleAccessConfig(level, DataScope.ALL_TENANT_DATA))))")
            appendLine("        }")
            appendLine("        // Repository memori untuk semua yang disentuh jalur gerbang — default `module()` memakai Postgres.")
            appendLine("        application {")
            appendLine("            module(")
            appendLine("                tenantRepository = tenants,")
            appendLine("                pipelineRepository = InMemoryTenantPipelineRepository(),")
            appendLine("                entitlementRepository = InMemoryTenantEntitlementRepository(),")
            appendLine("                roleRepository = roles,")
            appendLine("                moduleAssignmentRepository = InMemoryModuleAssignmentRepository()")
            appendLine("            )")
            appendLine("        }")
            appendLine("    }")
            appendLine()
            appendLine("    @Test fun `tanpa kredensial ditolak 401`() = testApplication {")
            appendLine("        installApp(AccessLevel.MANAGE)")
            appendLine("        assertEquals(HttpStatusCode.Unauthorized, client.get(base) { header(\"Host\", slug + \".wemakeerp.com\") }.status)")
            appendLine("    }")
            appendLine()
            appendLine("    @Test fun `akses ditutup menolak baca 403`() = testApplication {")
            appendLine("        installApp(AccessLevel.NONE)")
            appendLine("        assertEquals(HttpStatusCode.Forbidden, client.get(base) { asStaff(slug, customRoleId = \"role-uji\") }.status)")
            appendLine("    }")
            appendLine()
            appendLine("    @Test fun `hanya lihat menolak tambah 403 sebelum body dibaca`() = testApplication {")
            appendLine("        installApp(AccessLevel.VIEW)")
            appendLine("        val r = client.post(base) { asStaff(slug, customRoleId = \"role-uji\"); contentType(ContentType.Application.Json); setBody(\"bukan json\") }")
            appendLine("        assertEquals(HttpStatusCode.Forbidden, r.status)")
            appendLine("    }")
            appendLine()
            appendLine("    @Test fun `input dan kerja menolak hapus 403`() = testApplication {")
            appendLine("        installApp(AccessLevel.OPERATE)")
            appendLine("        assertEquals(HttpStatusCode.Forbidden, client.delete(base + \"/x\") { asStaff(slug, customRoleId = \"role-uji\") }.status)")
            appendLine("    }")
            appendLine()
            appendLine("    @Test fun `tenant tanpa modul di pack-nya tertolak walau owner`() = testApplication {")
            appendLine("        installApp(AccessLevel.MANAGE, tenantPack = GarmentDomainPack.CODE)")
            appendLine("        val status = client.get(base) { asTenant(slug) }.status")
            appendLine("        // 403 (RBAC: modul di luar pack tenant tak punya wewenang) atau 404 (lolos RBAC tapi modul tak ada di pack) — tidak boleh 200.")
            appendLine("        assertTrue(status == HttpStatusCode.Forbidden || status == HttpStatusCode.NotFound, \"seharusnya tertolak, dapat \" + status)")
            appendLine("    }")
            appendLine()
            appendLine("    @Test fun `modul tak dikenal proses ini ditolak 403 bukan error`() = testApplication {")
            appendLine("        // Tenant bawaan yang sah; pack modul ini tidak termuat di proses (mis. belum ada tenant yang memakainya).")
            appendLine("        installApp(AccessLevel.MANAGE, tenantPack = GarmentDomainPack.CODE)")
            appendLine("        DomainPackRegistry.unregister(pack.code)")
            appendLine("        assertEquals(HttpStatusCode.Forbidden, client.get(base) { asStaff(slug, customRoleId = \"role-uji\") }.status)")
            appendLine("    }")
            appendLine("}")
        }
    }
}
