package com.eventverse.app.routes

import com.eventverse.app.domain.pack.tenant.layanan.LayananPilotPack
import com.eventverse.app.asStaff
import com.eventverse.app.asTenant
import com.eventverse.app.domain.pack.DomainPackCode
import com.eventverse.app.domain.pack.DomainPackRegistry
import com.eventverse.app.domain.pack.GarmentDomainPack
import com.eventverse.app.domain.pack.ModuleId
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
import com.eventverse.app.infrastructure.InMemoryModuleAssignmentRepository
import com.eventverse.app.infrastructure.InMemoryRoleRepository
import com.eventverse.app.infrastructure.InMemoryTenantEntitlementRepository
import com.eventverse.app.infrastructure.InMemoryTenantPipelineRepository
import com.eventverse.app.infrastructure.InMemoryTenantRepository
import com.eventverse.app.module
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** KANDIDAT PR (hasil generator) — gerbang RBAC modul "layanan_change_request". Tanpa Postgres. */
class LayananChangeRequestRoutesGateTest {
    private val pack = LayananPilotPack.pack
    private val module = ModuleId("layanan_change_request")
    private val slug = "gerbang-uji"
    private val tenantId = TenantId("ten-gerbang-uji")
    private val base = "/api/tenant/modules/layanan_change_request/change_requests"

    @BeforeTest fun registerPack() { if (DomainPackRegistry.find(pack.code) == null) DomainPackRegistry.register(pack) }
    @AfterTest fun unregisterPack() { DomainPackRegistry.unregister(pack.code) }

    private fun ApplicationTestBuilder.installApp(level: AccessLevel, tenantPack: DomainPackCode = pack.code) {
        val tenants = InMemoryTenantRepository()
        val roles = InMemoryRoleRepository()
        runBlocking {
            tenants.save(Tenant(tenantId, TenantSlug(slug), TenantName("Tenant Uji Gerbang"), TenantStatus.ACTIVE, SubscriptionTier.PRO, domainPack = tenantPack))
            roles.save(CustomRole(id = RoleId("role-uji"), tenantId = tenantId, name = "Staf Uji", description = "", modulePermissions = mapOf(module to ModuleAccessConfig(level, DataScope.ALL_TENANT_DATA))))
        }
        // Repository memori untuk semua yang disentuh jalur gerbang — default `module()` memakai Postgres.
        application {
            module(
                tenantRepository = tenants,
                pipelineRepository = InMemoryTenantPipelineRepository(),
                entitlementRepository = InMemoryTenantEntitlementRepository(),
                roleRepository = roles,
                moduleAssignmentRepository = InMemoryModuleAssignmentRepository()
            )
        }
    }

    @Test fun `tanpa kredensial ditolak 401`() = testApplication {
        installApp(AccessLevel.MANAGE)
        assertEquals(HttpStatusCode.Unauthorized, client.get(base) { header("Host", slug + ".wemakeerp.com") }.status)
    }

    @Test fun `akses ditutup menolak baca 403`() = testApplication {
        installApp(AccessLevel.NONE)
        assertEquals(HttpStatusCode.Forbidden, client.get(base) { asStaff(slug, customRoleId = "role-uji") }.status)
    }

    @Test fun `hanya lihat menolak tambah 403 sebelum body dibaca`() = testApplication {
        installApp(AccessLevel.VIEW)
        val r = client.post(base) { asStaff(slug, customRoleId = "role-uji"); contentType(ContentType.Application.Json); setBody("bukan json") }
        assertEquals(HttpStatusCode.Forbidden, r.status)
    }

    @Test fun `input dan kerja menolak hapus 403`() = testApplication {
        installApp(AccessLevel.OPERATE)
        assertEquals(HttpStatusCode.Forbidden, client.delete(base + "/x") { asStaff(slug, customRoleId = "role-uji") }.status)
    }

    @Test fun `tenant tanpa modul di pack-nya tertolak walau owner`() = testApplication {
        installApp(AccessLevel.MANAGE, tenantPack = GarmentDomainPack.CODE)
        val status = client.get(base) { asTenant(slug) }.status
        // 403 (RBAC: modul di luar pack tenant tak punya wewenang) atau 404 (lolos RBAC tapi modul tak ada di pack) — tidak boleh 200.
        assertTrue(status == HttpStatusCode.Forbidden || status == HttpStatusCode.NotFound, "seharusnya tertolak, dapat " + status)
    }

    @Test fun `modul tak dikenal proses ini ditolak 403 bukan error`() = testApplication {
        // Tenant bawaan yang sah; pack modul ini tidak termuat di proses (mis. belum ada tenant yang memakainya).
        installApp(AccessLevel.MANAGE, tenantPack = GarmentDomainPack.CODE)
        DomainPackRegistry.unregister(pack.code)
        assertEquals(HttpStatusCode.Forbidden, client.get(base) { asStaff(slug, customRoleId = "role-uji") }.status)
    }
}
