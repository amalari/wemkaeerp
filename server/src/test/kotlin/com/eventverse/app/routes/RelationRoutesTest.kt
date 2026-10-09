package com.eventverse.app.routes

import com.eventverse.app.asStaff
import com.eventverse.app.asSuperadminActingAs
import com.eventverse.app.domain.discovery.handoff.InMemoryPrototypeRowRepository
import com.eventverse.app.domain.discovery.handoff.PrototypeRowRepository
import com.eventverse.app.domain.pack.DomainPackRegistry
import com.eventverse.app.domain.pack.GarmentModules
import com.eventverse.app.domain.pack.tenant.layanan.LayananPilotPack
import com.eventverse.app.domain.prototype.PrototypeRow
import com.eventverse.app.domain.rbac.AccessLevel
import com.eventverse.app.domain.rbac.CustomRole
import com.eventverse.app.domain.rbac.DataScope
import com.eventverse.app.domain.rbac.ModuleAccessConfig
import com.eventverse.app.domain.rbac.RoleId
import com.eventverse.app.domain.tenant.SubscriptionTier
import com.eventverse.app.domain.tenant.Tenant
import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.domain.tenant.TenantName
import com.eventverse.app.domain.tenant.TenantSlug
import com.eventverse.app.domain.tenant.TenantStatus
import com.eventverse.app.infrastructure.InMemoryCrmLeadRepository
import com.eventverse.app.infrastructure.InMemoryModuleAssignmentRepository
import com.eventverse.app.infrastructure.InMemoryRoleRepository
import com.eventverse.app.infrastructure.InMemoryTenantEntitlementRepository
import com.eventverse.app.infrastructure.InMemoryTenantPipelineRepository
import com.eventverse.app.infrastructure.InMemoryTenantRepository
import com.eventverse.app.module
import com.eventverse.app.shared.json.JsonParser
import com.eventverse.app.shared.json.JsonValue
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Gerbang & kontrak route opsi rujukan tipe field RELATION (C7, TRD-FIELD-001 FR-4) — tanpa Postgres.
 * Fokus: urutan gerbang (RBAC paling awal, SEBELUM parameter dibaca), 403 lintas modul dua arah,
 * 404 modul di luar pack tenant, dan LIMIT 20. "Pemegang field" di sini = CRM (`crm_sales`);
 * "modul target" = modul yang diminta di `?module=`.
 */
class RelationRoutesTest {
    private val slug = "relation-uji"
    private val tenantId = TenantId("ten-relation-uji")
    private val roleId = "role-relation"
    private val base = "/api/tenant/relation-options"

    private fun ApplicationTestBuilder.installApp(
        permissions: Map<com.eventverse.app.domain.pack.ModuleId, ModuleAccessConfig>,
        rows: Map<String, PrototypeRowRepository> = emptyMap()
    ) {
        val tenants = InMemoryTenantRepository()
        val roles = InMemoryRoleRepository()
        runBlocking {
            tenants.save(Tenant(tenantId, TenantSlug(slug), TenantName("Tenant Uji Relation"), TenantStatus.ACTIVE, SubscriptionTier.PRO))
            roles.save(CustomRole(id = RoleId(roleId), tenantId = tenantId, name = "Staf Uji", description = "", modulePermissions = permissions))
        }
        application {
            module(
                tenantRepository = tenants,
                pipelineRepository = InMemoryTenantPipelineRepository(),
                entitlementRepository = InMemoryTenantEntitlementRepository(),
                roleRepository = roles,
                moduleAssignmentRepository = InMemoryModuleAssignmentRepository(),
                crmLeadRepository = InMemoryCrmLeadRepository(),
                fieldFileRecordRows = rows
            )
        }
    }

    private fun rows(module: String, count: Int): Map<String, PrototypeRowRepository> =
        mapOf(module to InMemoryPrototypeRowRepository().apply {
            runBlocking {
                repeat(count) { i -> save(tenantId, PrototypeRow("rec-$i", mapOf("nama" to "Pelanggan $i"))) }
            }
        })

    private fun viewOnly(module: com.eventverse.app.domain.pack.ModuleId) =
        mapOf(module to ModuleAccessConfig(AccessLevel.VIEW, DataScope.ALL_TENANT_DATA))

    @Test fun `tanpa kredensial ditolak 401`() = testApplication {
        installApp(viewOnly(GarmentModules.QUALITY_CONTROL))
        assertEquals(HttpStatusCode.Unauthorized, client.get("$base?module=quality_control&entity=x") { header("Host", "$slug.wemakeerp.com") }.status)
    }

    @Test fun `403 peran tanpa VIEW di modul target`() = testApplication {
        // VIEW di modul pemegang (CRM) TIDAK memberi VIEW di modul target — inti keputusan #2.
        installApp(viewOnly(GarmentModules.CRM_SALES))
        val r = client.get("$base?module=quality_control&entity=x") { asStaff(slug, roleId) }
        assertEquals(HttpStatusCode.Forbidden, r.status)
    }

    @Test fun `403 SEBELUM parameter entity dibaca`() = testApplication {
        // Tanpa `entity` sama sekali: bila handler membaca/memvalidasi parameter dulu, ia akan 400.
        // 403 membuktikan gerbang RBAC modul target berjalan paling awal (Kontrak 7).
        installApp(viewOnly(GarmentModules.CRM_SALES))
        val r = client.get("$base?module=quality_control") { asStaff(slug, roleId) }
        assertEquals(HttpStatusCode.Forbidden, r.status)
    }

    @Test fun `403 modul tak dikenal`() = testApplication {
        installApp(viewOnly(GarmentModules.CRM_SALES))
        val r = client.get("$base?module=modul_hantu&entity=x") { asStaff(slug, roleId) }
        assertEquals(HttpStatusCode.Forbidden, r.status)
    }

    @Test fun `pemanggil berwenang di pemegang tapi tak berwenang di target tetap 403`() = testApplication {
        // Wewenang OPERATE di modul pemegang (CRM) tetap tidak membuka modul target (quality_control).
        installApp(mapOf(GarmentModules.CRM_SALES to ModuleAccessConfig(AccessLevel.MANAGE, DataScope.ALL_TENANT_DATA)))
        val r = client.get("$base?module=quality_control&entity=x") { asStaff(slug, roleId) }
        assertEquals(HttpStatusCode.Forbidden, r.status)
    }

    @Test fun `404 bila modul target tak ada di pack tenant`() = testApplication {
        val register = DomainPackRegistry.find(LayananPilotPack.CODE) == null
        if (register) DomainPackRegistry.register(LayananPilotPack.pack)
        try {
            // Modul dikenal proses ini (pack terdaftar) & lolos gerbang RBAC (superadmin melewati matriks
            // wewenang + entitlement), TAPI tenant memakai pack garment → bukan bagian pack tenant → 404.
            installApp(viewOnly(LayananPilotPack.CHANGE_REQUEST))
            val r = client.get("$base?module=layanan_change_request&entity=x") { asSuperadminActingAs(slug) }
            assertEquals(HttpStatusCode.NotFound, r.status)
        } finally {
            if (register) DomainPackRegistry.unregister(LayananPilotPack.CODE)
        }
    }

    @Test fun `sukses mengembalikan paling banyak 20 opsi`() = testApplication {
        installApp(viewOnly(GarmentModules.QUALITY_CONTROL), rows("quality_control", 25))
        val r = client.get("$base?module=quality_control&entity=inspeksi") { asStaff(slug, roleId) }
        assertEquals(HttpStatusCode.OK, r.status)
        val items = JsonParser.parseArray(r.bodyAsText())
        assertEquals(20, items.size, "LIMIT 20 ditegakkan")
        assertTrue(items.all { it is JsonValue.Obj && it.string("id") != null && it.string("label") != null })
    }

    @Test fun `query menyaring opsi berdasarkan label`() = testApplication {
        installApp(viewOnly(GarmentModules.QUALITY_CONTROL), rows("quality_control", 25))
        val r = client.get("$base?module=quality_control&entity=inspeksi&q=Pelanggan%207") { asStaff(slug, roleId) }
        assertEquals(HttpStatusCode.OK, r.status)
        val items = JsonParser.parseArray(r.bodyAsText())
        assertTrue(items.isNotEmpty() && items.size <= 20)
        assertTrue(items.all { (it as JsonValue.Obj).string("label").orEmpty().contains("Pelanggan 7") })
    }
}
