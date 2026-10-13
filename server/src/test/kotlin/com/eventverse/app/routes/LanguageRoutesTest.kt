package com.eventverse.app.routes

import com.eventverse.app.asStaff
import com.eventverse.app.asTenant
import com.eventverse.app.domain.auth.UserId
import com.eventverse.app.domain.i18n.LanguageCode
import com.eventverse.app.domain.pack.GarmentBlueprints
import com.eventverse.app.domain.pack.GarmentModules
import com.eventverse.app.domain.rbac.AccessLevel
import com.eventverse.app.domain.rbac.CustomRole
import com.eventverse.app.domain.rbac.ModuleAccessConfig
import com.eventverse.app.domain.rbac.RoleId
import com.eventverse.app.domain.tenant.SubscriptionTier
import com.eventverse.app.domain.tenant.Tenant
import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.domain.tenant.TenantName
import com.eventverse.app.domain.tenant.TenantSlug
import com.eventverse.app.domain.tenant.TenantStatus
import com.eventverse.app.infrastructure.InMemoryLanguagePreferenceRepository
import com.eventverse.app.infrastructure.InMemoryRoleRepository
import com.eventverse.app.infrastructure.InMemoryTenantRepository
import com.eventverse.app.module
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * B2/B6 (PLAN-i18n Fase 1, Track B): preferensi bahasa dua tingkat + gerbang fail-closed.
 *
 * - `PUT /api/me/language` — pilihan pribadi, tanpa wewenang modul; bahasa tak dikenal → 400.
 * - `PUT /api/tenant/language` — bawaan pabrik, gerbang `MANAGE` tata kelola; peran tak
 *   berwenang → 403 fail-closed.
 *
 * Memakai repository in-memory (tenant/jabatan/bahasa) sehingga tak butuh seed DB; booting
 * `module(...)` tetap menjalankan migrasi Flyway di DB scratch (bukti V103 terpasang).
 */
class LanguageRoutesTest {

    private val tenants = InMemoryTenantRepository()
    private val roles = InMemoryRoleRepository()
    private val languages = InMemoryLanguagePreferenceRepository()

    private data class Env(val slug: String, val tenantId: TenantId, val suffix: String)

    private suspend fun registerTenant(suffix: String): Env {
        val env = Env("i18n-$suffix", TenantId("ten-i18n-$suffix"), suffix)
        tenants.save(
            Tenant(
                env.tenantId, TenantSlug(env.slug), TenantName("I18n $suffix"), TenantStatus.ACTIVE,
                SubscriptionTier.PRO, businessPreset = GarmentBlueprints.CMT_MAKLOON
            )
        )
        // Jabatan tanpa akses tata kelola (DYNAMIC_RBAC).
        roles.save(
            CustomRole(
                id = RoleId("role-gudang-$suffix"),
                tenantId = env.tenantId,
                name = "Staf Gudang",
                description = "Master data saja",
                modulePermissions = mapOf(GarmentModules.MASTER_DATA to ModuleAccessConfig(AccessLevel.MANAGE))
            )
        )
        return env
    }

    private fun boot(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        application {
            module(
                tenantRepository = tenants,
                roleRepository = roles,
                languagePreferenceRepository = languages
            )
        }
        startApplication()
        block()
    }

    // -------------------------------------------------------------------------
    // B2: PUT /api/me/language
    // -------------------------------------------------------------------------

    @Test
    fun putMyLanguage_valid_returns200_andPersists() = boot {
        val env = registerTenant("me-ok")
        val response = client.put("/api/me/language") {
            asStaff(env.slug, "role-gudang-me-ok")
            contentType(ContentType.Application.Json)
            setBody("""{"language":"en"}""")
        }
        assertEquals(200, response.status.value)
        assertTrue(response.bodyAsText().contains("\"language\":\"en\""), response.bodyAsText())
        // Tersimpan pada pengguna pemanggil.
        val stored = languages.findUserLanguage(UserId("usr-test-staff-role-gudang-me-ok"), env.tenantId)
        assertEquals(LanguageCode.EN, stored)
    }

    @Test
    fun putMyLanguage_unknownLanguage_returns400_withCode() = boot {
        val env = registerTenant("me-bad")
        val response = client.put("/api/me/language") {
            asStaff(env.slug, "role-gudang-me-bad")
            contentType(ContentType.Application.Json)
            setBody("""{"language":"fr"}""")
        }
        assertEquals(400, response.status.value, "Bahasa tak dikenal harus 400")
        assertTrue(
            response.bodyAsText().contains("\"code\":\"UNKNOWN_LANGUAGE\""),
            "Respons harus membawa kode stabil UNKNOWN_LANGUAGE: ${response.bodyAsText()}"
        )
    }

    @Test
    fun putMyLanguage_missingField_returns400_withCode() = boot {
        val env = registerTenant("me-missing")
        val response = client.put("/api/me/language") {
            asStaff(env.slug, "role-gudang-me-missing")
            contentType(ContentType.Application.Json)
            setBody("""{}""")
        }
        assertEquals(400, response.status.value)
        assertTrue(response.bodyAsText().contains("\"code\":\"MISSING_LANGUAGE\""), response.bodyAsText())
    }

    // -------------------------------------------------------------------------
    // B2/B6: PUT /api/tenant/language (gerbang fail-closed)
    // -------------------------------------------------------------------------

    @Test
    fun putTenantLanguage_roleWithoutManage_returns403() = boot {
        val env = registerTenant("tenant-denied")
        val response = client.put("/api/tenant/language") {
            asStaff(env.slug, "role-gudang-tenant-denied")
            contentType(ContentType.Application.Json)
            setBody("""{"language":"en"}""")
        }
        assertEquals(403, response.status.value, "Tanpa MANAGE tata kelola harus 403 fail-closed")
        assertTrue(response.bodyAsText().contains("\"code\":\"FORBIDDEN\""), response.bodyAsText())
    }

    @Test
    fun putTenantLanguage_owner_returns200_andPersists() = boot {
        val env = registerTenant("tenant-ok")
        // Owner (TENANT_ADMIN tanpa jabatan kustom) melewati matriks → MANAGE.
        val response = client.put("/api/tenant/language") {
            asTenant(env.slug)
            contentType(ContentType.Application.Json)
            setBody("""{"language":"en"}""")
        }
        assertEquals(200, response.status.value, response.bodyAsText())
        assertEquals(LanguageCode.EN, languages.findTenantLanguage(env.tenantId))
    }

    @Test
    fun putTenantLanguage_unknownLanguage_returns400_evenForOwner() = boot {
        val env = registerTenant("tenant-bad")
        val response = client.put("/api/tenant/language") {
            asTenant(env.slug)
            contentType(ContentType.Application.Json)
            setBody("""{"language":"de"}""")
        }
        assertEquals(400, response.status.value)
        assertTrue(response.bodyAsText().contains("\"code\":\"UNKNOWN_LANGUAGE\""), response.bodyAsText())
    }
}
