package com.eventverse.app

import com.eventverse.app.domain.auth.Role
import com.eventverse.app.domain.i18n.LanguageCode
import com.eventverse.app.domain.pack.DomainPackCode
import com.eventverse.app.domain.pack.DomainPackRegistry
import com.eventverse.app.domain.pack.GarmentDomainPack
import com.eventverse.app.domain.tenant.SubscriptionTier
import com.eventverse.app.domain.tenant.Tenant
import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.domain.tenant.TenantName
import com.eventverse.app.domain.tenant.TenantSlug
import com.eventverse.app.domain.tenant.TenantStatus
import com.eventverse.app.infrastructure.DatabaseFactory
import com.eventverse.app.infrastructure.InMemoryAuditLogRepository
import com.eventverse.app.infrastructure.InMemoryDepartmentRepository
import com.eventverse.app.infrastructure.InMemoryDomainPackRepository
import com.eventverse.app.infrastructure.InMemoryEmployeeRepository
import com.eventverse.app.infrastructure.InMemoryModuleAssignmentRepository
import com.eventverse.app.infrastructure.InMemoryRoleRepository
import com.eventverse.app.infrastructure.InMemoryTenantEntitlementRepository
import com.eventverse.app.infrastructure.InMemoryTenantPipelineRepository
import com.eventverse.app.infrastructure.InMemoryTenantRepository
import com.eventverse.app.shared.json.JsonParser
import com.eventverse.app.shared.pack.DomainPackCodec
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * TRD-I18N-003 Track B: `menuLabel` modul (dua bahasa) lewat API tanpa kehilangan data, pack lama tanpa kunci itu
 * dilayani byte-identik, bahasa tak dikenal ditolak 400, laporan terjemahan memuat path `modules[i].menuLabel`,
 * tulis tetap fail-closed. Pack `klinikbi` non-garment (Kontrak 6).
 */
class DomainPackMenuLabelApiTest {

    private val code = "klinikbi"
    private val tenantSlug = "klinik-bi-uji"

    @AfterTest
    fun cleanup() = DomainPackRegistry.unregister(DomainPackCode(code))

    private fun ApplicationTestBuilder.boot() {
        DatabaseFactory.init()
        val tenants = InMemoryTenantRepository().also { repo ->
            runBlocking {
                repo.save(Tenant(TenantId("ten-bi"), TenantSlug(tenantSlug), TenantName(tenantSlug), TenantStatus.ACTIVE, SubscriptionTier.PRO,
                    domainPack = GarmentDomainPack.CODE))
            }
        }
        application { module(tenantRepository = tenants, pipelineRepository = InMemoryTenantPipelineRepository(),
            entitlementRepository = InMemoryTenantEntitlementRepository(), roleRepository = InMemoryRoleRepository(),
            moduleAssignmentRepository = InMemoryModuleAssignmentRepository(), departmentRepository = InMemoryDepartmentRepository(),
            employeeRepository = InMemoryEmployeeRepository(), auditLogRepository = InMemoryAuditLogRepository(),
            domainPackRepository = InMemoryDomainPackRepository()) }
    }

    private suspend fun ApplicationTestBuilder.publish(json: String) {
        assertEquals(HttpStatusCode.OK, client.put("/api/admin/domain-packs/$code") { asSuperadmin(); setBody(json) }.status)
        assertEquals(HttpStatusCode.OK, client.post("/api/admin/domain-packs/$code/lock") { asSuperadmin() }.status)
        assertEquals(HttpStatusCode.OK, client.put("/api/admin/tenants/$tenantSlug/domain-pack") { asSuperadmin(); setBody("""{"code":"$code"}""") }.status)
    }

    @Test
    fun menuLabel_roundTripsThroughPutLockAssignAndTenantRead() = testApplication {
        boot()
        val pack = I18nPackFixtures.pack(code, menuLabel = true)
        publish(DomainPackCodec.encodeToString(pack))
        val served = DomainPackCodec.decode(client.get("/api/tenant/pack") { asTenant(tenantSlug) }.bodyAsText())
        assertEquals(pack, served)
        val antrean = served.modules.last()
        assertEquals("Queue", antrean.menuLabelText?.textOrNull(LanguageCode.EN))
        assertEquals("Antrean", antrean.menuLabelText?.textOrNull(LanguageCode.ID))
    }

    @Test
    fun legacyPackWithoutMenuLabel_isServedByteIdentical_withoutMenuLabelKey() = testApplication {
        boot()
        val json = DomainPackCodec.encodeToString(I18nPackFixtures.pack(code, menuLabel = false))
        // Modul bersama org_chart dari garment kini membawa menuLabel (TRD-I18N-003 Track A); yang diuji = modul milik pack uji.
        assertNull(DomainPackCodec.decode(json).modules.last().menuLabelText)
        publish(json)
        val body = client.get("/api/tenant/pack") { asTenant(tenantSlug) }.bodyAsText()
        assertEquals(json, body)
        assertNull(DomainPackCodec.decode(body).modules.last().menuLabelText)
    }

    @Test
    fun unknownLanguageInMenuLabel_isRejected400_andNothingStored() = testApplication {
        boot()
        val json = DomainPackCodec.encodeToString(I18nPackFixtures.pack(code, menuLabel = true))
        val bad = json.replace("\"en\":\"Queue\"", "\"fr\":\"Queue\"")
        assertTrue(bad != json, "fixture harus memuat menuLabel en")
        assertEquals(HttpStatusCode.BadRequest, client.put("/api/admin/domain-packs/$code") { asSuperadmin(); setBody(bad) }.status)
        assertEquals(HttpStatusCode.NotFound, client.get("/api/admin/domain-packs/$code/translation-report") { asSuperadmin() }.status)
    }

    @Test
    fun translationReport_listsMenuLabelPath_whenEnMissing_andEmptyWhenComplete() = testApplication {
        boot()
        client.put("/api/admin/domain-packs/$code") { asSuperadmin(); setBody(DomainPackCodec.encodeToString(I18nPackFixtures.pack(code, menuLabel = true, complete = false))) }
        val partial = JsonParser.parseObject(client.get("/api/admin/domain-packs/$code/translation-report") { asSuperadmin() }.bodyAsText())
        val paths = partial.objectArray("missing").map { it.string("path") to it.string("language") }
        assertTrue(("modules[1].menuLabel" to "en") in paths, paths.toString())

        client.put("/api/admin/domain-packs/$code") { asSuperadmin(); setBody(DomainPackCodec.encodeToString(I18nPackFixtures.pack(code, menuLabel = true))) }
        val complete = JsonParser.parseObject(client.get("/api/admin/domain-packs/$code/translation-report") { asSuperadmin() }.bodyAsText())
        assertTrue(complete.array("missing").isEmpty())
    }

    @Test
    fun menuLabelWrite_byUnauthorizedRoleIs403_andWithoutTokenIs401() = testApplication {
        boot()
        val json = DomainPackCodec.encodeToString(I18nPackFixtures.pack(code, menuLabel = true))
        assertEquals(HttpStatusCode.Forbidden, client.put("/api/admin/domain-packs/$code") { asTenant(tenantSlug, Role.TENANT_ADMIN); setBody(json) }.status)
        assertEquals(HttpStatusCode.Unauthorized, client.put("/api/admin/domain-packs/$code") { setBody(json) }.status)
        assertEquals(HttpStatusCode.NotFound, client.get("/api/admin/domain-packs/$code/translation-report") { asSuperadmin() }.status)
    }
}
