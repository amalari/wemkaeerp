package com.eventverse.app.routes

import com.eventverse.app.asStaff
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
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.post
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
 * B3 (PLAN-i18n Fase 1, Track B): rute Fulfillment menjawab error ber-**kode stabil**,
 * bukan lagi teks bebas — supaya klien bisa menerjemahkannya (Track C4).
 *
 * Yang diuji adalah *kontrak mesin* (`code`), bukan pesan manusianya.
 */
class FulfillmentErrorCodeTest {

    private val tenants = InMemoryTenantRepository()
    private val roles = InMemoryRoleRepository()
    private val languages = InMemoryLanguagePreferenceRepository()

    private data class Env(val slug: String, val tenantId: TenantId, val suffix: String)

    private suspend fun registerTenant(suffix: String, access: AccessLevel?): Env {
        val env = Env("fulfillment-code-$suffix", TenantId("ten-fulfillment-code-$suffix"), suffix)
        tenants.save(
            Tenant(
                env.tenantId, TenantSlug(env.slug), TenantName("FC $suffix"), TenantStatus.ACTIVE,
                SubscriptionTier.PRO, businessPreset = GarmentBlueprints.CMT_MAKLOON
            )
        )
        if (access != null) {
            roles.save(
                CustomRole(
                    id = RoleId("role-ful-$access-$suffix"),
                    tenantId = env.tenantId,
                    name = "Fulfillment $access",
                    description = "Test",
                    modulePermissions = mapOf(GarmentModules.FULFILLMENT to ModuleAccessConfig(access))
                )
            )
        }
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

    @Test
    fun forbiddenResponse_carriesStableCode() = boot {
        // Peran OPERATE: gerbang grup lolos (butuh OPERATE pada PATCH), tetapi reopen butuh MANAGE
        // → 403 dari handler, dan handler itu kini membawa kode stabil.
        val env = registerTenant("denied", AccessLevel.OPERATE)
        val response = client.patch("/api/tenant/fulfillment/cartons/ctn-any/reopen") {
            asStaff(env.slug, "role-ful-OPERATE-denied")
            contentType(ContentType.Application.Json)
            setBody("""{"reason":"Salah ukuran","actor":"tester"}""")
        }
        assertEquals(403, response.status.value)
        assertTrue(response.bodyAsText().contains("\"code\":\"FORBIDDEN\""), response.bodyAsText())
    }

    @Test
    fun validationResponse_carriesStableCode() = boot {
        val env = registerTenant("validation", AccessLevel.OPERATE)
        val response = client.post("/api/tenant/fulfillment/cartons") {
            asStaff(env.slug, "role-ful-OPERATE-validation")
            contentType(ContentType.Application.Json)
            setBody("""{"notes":"tanpa productionRef"}""")
        }
        assertEquals(400, response.status.value)
        assertTrue(response.bodyAsText().contains("\"code\":\"VALIDATION_ERROR\""), response.bodyAsText())
    }

    @Test
    fun invalidBodyResponse_carriesStableCode() = boot {
        val env = registerTenant("badbody", AccessLevel.OPERATE)
        val response = client.post("/api/tenant/fulfillment/cartons") {
            asStaff(env.slug, "role-ful-OPERATE-badbody")
            contentType(ContentType.Application.Json)
            setBody("bukan-json")
        }
        assertEquals(400, response.status.value)
        assertTrue(response.bodyAsText().contains("\"code\":\"INVALID_BODY\""), response.bodyAsText())
    }
}
