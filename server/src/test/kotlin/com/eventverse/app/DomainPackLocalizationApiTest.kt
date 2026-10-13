package com.eventverse.app

import com.eventverse.app.domain.auth.Role
import com.eventverse.app.domain.i18n.LanguageCode
import com.eventverse.app.domain.pack.DomainPackCode
import com.eventverse.app.domain.pack.DomainPackRegistry
import com.eventverse.app.domain.pack.GarmentDomainPack
import com.eventverse.app.domain.pack.missingTranslations
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
import kotlin.test.assertTrue

/**
 * TRD-I18N-002 Track B: pack dua bahasa lewat API + persistensi tanpa kehilangan data, pack lama (id saja)
 * terbaca identik, kode bahasa tak dikenal ditolak (bukan diterima senyap), tulis tetap fail-closed (403), dan
 * laporan kelengkapan terjemahan fail-closed. Pack `klinikbi` sengaja non-garment (Kontrak 6).
 */
class DomainPackLocalizationApiTest {

    private val code = "klinikbi"
    private val tenantSlug = "klinik-bi-uji"

    @AfterTest
    fun cleanup() = DomainPackRegistry.unregister(DomainPackCode(code))

    private fun ApplicationTestBuilder.boot(packs: InMemoryDomainPackRepository = InMemoryDomainPackRepository()) {
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
            domainPackRepository = packs) }
    }

    @Test
    fun bilingualPack_roundTripsThroughApiAndTenantRead_withoutLosingEnglish() = testApplication {
        boot()
        val pack = I18nPackFixtures.pack(code)
        val put = client.put("/api/admin/domain-packs/$code") { asSuperadmin(); setBody(DomainPackCodec.encodeToString(pack)) }
        assertEquals(HttpStatusCode.OK, put.status, put.bodyAsText())
        assertEquals(listOf("en", "id"), JsonParser.parseObject(put.bodyAsText()).stringArray("languages"))
        assertEquals(HttpStatusCode.OK, client.post("/api/admin/domain-packs/$code/lock") { asSuperadmin() }.status)
        assertEquals(HttpStatusCode.OK, client.put("/api/admin/tenants/$tenantSlug/domain-pack") { asSuperadmin(); setBody("""{"code":"$code"}""") }.status)

        val served = DomainPackCodec.decode(client.get("/api/tenant/pack") { asTenant(tenantSlug) }.bodyAsText())
        assertEquals(pack, served)
        assertEquals("1. Service", served.phases.single().displayNameText.textOrNull(LanguageCode.EN))
        assertEquals("Cashier", served.slots.last().displayNameText.textOrNull(LanguageCode.EN))
        assertTrue(served.missingTranslations().isEmpty())
    }

    @Test
    fun legacyIdOnlyPack_isServedByteIdentical_withNoLanguagesKey() = testApplication {
        boot()
        val legacy = I18nPackFixtures.pack(code, languages = setOf("id"))
        val json = DomainPackCodec.encodeToString(legacy)
        assertFalse(json.contains("\"languages\""), "pack id-saja ditulis identik dengan format lama")
        assertEquals(HttpStatusCode.OK, client.put("/api/admin/domain-packs/$code") { asSuperadmin(); setBody(json) }.status)
        client.post("/api/admin/domain-packs/$code/lock") { asSuperadmin() }
        client.put("/api/admin/tenants/$tenantSlug/domain-pack") { asSuperadmin(); setBody("""{"code":"$code"}""") }
        assertEquals(json, client.get("/api/tenant/pack") { asTenant(tenantSlug) }.bodyAsText())
    }

    @Test
    fun unknownLanguageCode_isRejected400_notAcceptedSilently() = testApplication {
        boot()
        val json = DomainPackCodec.encodeToString(I18nPackFixtures.pack(code))
        val withFrenchClaim = json.replace("\"languages\":[\"en\",\"id\"]", "\"languages\":[\"fr\",\"id\"]")
        assertTrue(withFrenchClaim != json, "fixture harus memuat klaim languages")
        assertEquals(HttpStatusCode.BadRequest, client.put("/api/admin/domain-packs/$code") { asSuperadmin(); setBody(withFrenchClaim) }.status)
        val withFrenchText = json.replace("\"en\":\"1. Service\"", "\"fr\":\"1. Service\"")
        assertTrue(withFrenchText != json, "fixture harus memuat teks en fase")
        assertEquals(HttpStatusCode.BadRequest, client.put("/api/admin/domain-packs/$code") { asSuperadmin(); setBody(withFrenchText) }.status)
        // Tidak ada yang tersimpan dari percobaan ditolak.
        assertEquals(HttpStatusCode.NotFound, client.get("/api/admin/domain-packs/$code/translation-report") { asSuperadmin() }.status)
    }

    @Test
    fun unauthorizedRole_isForbidden403_onBilingualWrite_andOnTranslationReport() = testApplication {
        boot()
        val json = DomainPackCodec.encodeToString(I18nPackFixtures.pack(code))
        assertEquals(HttpStatusCode.Forbidden, client.put("/api/admin/domain-packs/$code") { asTenant(tenantSlug, Role.TENANT_ADMIN); setBody(json) }.status)
        assertEquals(HttpStatusCode.Forbidden, client.put("/api/admin/domain-packs/$code") { asTenant(tenantSlug, Role.OPERATOR); setBody(json) }.status)
        client.put("/api/admin/domain-packs/$code") { asSuperadmin(); setBody(json) }
        assertEquals(HttpStatusCode.Forbidden, client.get("/api/admin/domain-packs/$code/translation-report") { asTenant(tenantSlug, Role.TENANT_ADMIN) }.status)
        assertEquals(HttpStatusCode.Unauthorized, client.get("/api/admin/domain-packs/$code/translation-report").status)
    }

    @Test
    fun translationReport_listsMissingPathsPerLanguage_andIsEmptyWhenComplete() = testApplication {
        boot()
        // Klaim en, tetapi slot kasir tanpa en.
        client.put("/api/admin/domain-packs/$code") { asSuperadmin(); setBody(DomainPackCodec.encodeToString(I18nPackFixtures.pack(code, complete = false))) }
        val partial = JsonParser.parseObject(client.get("/api/admin/domain-packs/$code/translation-report") { asSuperadmin() }.bodyAsText())
        assertEquals(code, partial.string("code"))
        assertEquals(listOf("en", "id"), partial.stringArray("claimed"))
        assertEquals(listOf("slots[1].displayName" to "en"), partial.objectArray("missing").map { it.string("path") to it.string("language") })
        assertEquals(1L, partial.obj("countByLanguage")?.long("en"))

        // Revisi lengkap = versi baru; laporan terbaru kosong, versi lama tetap bisa diminta.
        client.put("/api/admin/domain-packs/$code") { asSuperadmin(); setBody(DomainPackCodec.encodeToString(I18nPackFixtures.pack(code))) }
        val complete = JsonParser.parseObject(client.get("/api/admin/domain-packs/$code/translation-report") { asSuperadmin() }.bodyAsText())
        assertTrue(complete.array("missing").isEmpty())
        assertEquals(HttpStatusCode.BadRequest, client.get("/api/admin/domain-packs/$code/translation-report?version=abc") { asSuperadmin() }.status)
        assertEquals(HttpStatusCode.NotFound, client.get("/api/admin/domain-packs/$code/translation-report?version=99") { asSuperadmin() }.status)
    }

    @Test
    fun idOnlyPack_reportsNothingMissing() = testApplication {
        boot()
        client.put("/api/admin/domain-packs/$code") { asSuperadmin(); setBody(DomainPackCodec.encodeToString(I18nPackFixtures.pack(code, languages = setOf("id")))) }
        val report = JsonParser.parseObject(client.get("/api/admin/domain-packs/$code/translation-report") { asSuperadmin() }.bodyAsText())
        assertEquals(listOf("id"), report.stringArray("claimed"))
        assertTrue(report.array("missing").isEmpty())
    }
}
