package com.eventverse.app

import com.eventverse.app.domain.blueprint.Blueprint

import com.eventverse.app.domain.pack.GarmentBlueprints

import com.eventverse.app.domain.pipeline.CustomTenantPipeline
import com.eventverse.app.domain.pipeline.DynamicModuleDescriptor
import com.eventverse.app.domain.pipeline.ModuleArchetype
import com.eventverse.app.domain.tenant.*
import com.eventverse.app.infrastructure.InMemoryAuditLogRepository
import com.eventverse.app.infrastructure.InMemoryTenantEntitlementRepository
import com.eventverse.app.infrastructure.InMemoryTenantPipelineRepository
import com.eventverse.app.infrastructure.InMemoryTenantRepository
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * End-to-end coverage of the platform-administration API: guard (superadmin only), viewing
 * a tenant's entitlement, changing it, changing the subscription tier, and the audit trail
 * every write is supposed to leave behind.
 */
class AdminApiTest {

    private val targetSlug = "pabrik-admin-test"
    private val targetTenantId = TenantId("ten-admin-test")
    private val otherTenantSlug = "cv-berkah-makloon"

    private fun tenantRepoWith(
        tier: SubscriptionTier = SubscriptionTier.PRO,
        preset: Blueprint = GarmentBlueprints.FOB_FULL_PACKAGE
    ): InMemoryTenantRepository {
        val repo = InMemoryTenantRepository()
        runBlocking {
            repo.save(
                Tenant(
                    id = targetTenantId,
                    slug = TenantSlug(targetSlug),
                    name = TenantName("PT Uji Admin"),
                    status = TenantStatus.ACTIVE,
                    tier = tier,
                    businessPreset = preset
                )
            )
        }
        return repo
    }

    private class Fixture(
        val tenantRepo: InMemoryTenantRepository,
        val pipeRepo: InMemoryTenantPipelineRepository = InMemoryTenantPipelineRepository(),
        val entitlementRepo: InMemoryTenantEntitlementRepository = InMemoryTenantEntitlementRepository(),
        val auditRepo: InMemoryAuditLogRepository = InMemoryAuditLogRepository()
    )

    private fun ApplicationTestBuilder.installModule(fixture: Fixture) {
        application {
            module(
                tenantRepository = fixture.tenantRepo,
                pipelineRepository = fixture.pipeRepo,
                entitlementRepository = fixture.entitlementRepo,
                auditLogRepository = fixture.auditRepo
            )
        }
    }

    // -------------------------------------------------------------------
    // Guard: only an authenticated platform superadmin may reach /api/admin.
    // -------------------------------------------------------------------

    @Test
    fun adminRoute_withoutCredentials_shouldReturn401() = testApplication {
        installModule(Fixture(tenantRepoWith()))

        val response = client.get("/api/admin/tenants/$targetSlug")

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun adminRoute_asTenantBoundUser_shouldReturn403() = testApplication {
        installModule(Fixture(tenantRepoWith()))

        // Authenticated, but as an ordinary tenant account — not a platform superadmin.
        val response = client.get("/api/admin/tenants/$targetSlug") {
            asTenant(otherTenantSlug)
        }

        assertEquals(HttpStatusCode.Forbidden, response.status)
        assertTrue(response.bodyAsText().contains("platform superadmin"))
    }

    @Test
    fun adminRoute_asSuperadmin_shouldNotRequireATenantHeader() = testApplication {
        // Admin routes address their target via the {slug} path parameter, not
        // X-Tenant-Slug — the plugin must not demand tenant-context resolution here.
        installModule(Fixture(tenantRepoWith()))

        val response = client.get("/api/admin/tenants/$targetSlug") { asSuperadmin() }

        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun adminRoute_forUnknownTenantSlug_shouldReturn404() = testApplication {
        installModule(Fixture(tenantRepoWith()))

        val response = client.get("/api/admin/tenants/tenant-yang-tidak-ada") { asSuperadmin() }

        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    // -------------------------------------------------------------------
    // GET the admin view.
    // -------------------------------------------------------------------

    @Test
    fun getAdminView_shouldReflectTenantIdentityPlanAndModules() = testApplication {
        installModule(Fixture(tenantRepoWith(tier = SubscriptionTier.PRO)))

        val response = client.get("/api/admin/tenants/$targetSlug") { asSuperadmin() }
        assertEquals(HttpStatusCode.OK, response.status)

        val body = response.bodyAsText()
        assertTrue(body.contains("\"slug\":\"$targetSlug\""))
        assertTrue(body.contains("\"tier\":\"PRO\""))
        assertTrue(body.contains("\"modules\":["))
        assertTrue(body.contains("\"grantedCustomModuleIds\":[]"))
    }

    // -------------------------------------------------------------------
    // PUT entitlement.
    // -------------------------------------------------------------------

    @Test
    fun putEntitlement_shouldNarrowGrantedModulesAndPersistIt() = testApplication {
        val fixture = Fixture(tenantRepoWith())
        installModule(fixture)

        val response = client.put("/api/admin/tenants/$targetSlug/entitlement") {
            asSuperadmin()
            contentType(ContentType.Application.Json)
            setBody("""{"grantedModules":["CRM_SALES","OPERATOR_EXEC"],"grantedCustomModuleIds":[]}""")
        }

        assertEquals(HttpStatusCode.OK, response.status, response.bodyAsText())

        val stored = runBlocking { fixture.entitlementRepo.findByTenantId(targetTenantId) }
        assertNotNull(stored)
        assertEquals(2, stored.grantedModules?.size)
    }

    @Test
    fun putEntitlement_thatBreaksTheRunningPipeline_shouldBeRejectedWithoutPersisting() = testApplication {
        val fixture = Fixture(tenantRepoWith())
        installModule(fixture)
        // Seed a pipeline with all nine FOB modules active.
        runBlocking {
            fixture.pipeRepo.save(
                CustomTenantPipeline.fromPreset(targetTenantId, GarmentBlueprints.FOB_FULL_PACKAGE)
            )
        }

        val response = client.put("/api/admin/tenants/$targetSlug/entitlement") {
            asSuperadmin()
            contentType(ContentType.Application.Json)
            setBody("""{"grantedModules":["CRM_SALES"],"grantedCustomModuleIds":[]}""")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertTrue(runBlocking { fixture.entitlementRepo.findByTenantId(targetTenantId) } == null)
    }

    @Test
    fun putEntitlement_shouldRecordAnAuditEntry() = testApplication {
        val fixture = Fixture(tenantRepoWith())
        installModule(fixture)

        client.put("/api/admin/tenants/$targetSlug/entitlement") {
            asSuperadmin()
            contentType(ContentType.Application.Json)
            setBody("""{"grantedModules":null,"grantedCustomModuleIds":["sablon_bordir_custom"]}""")
        }

        val entries = runBlocking { fixture.auditRepo.findByTenant(targetTenantId) }
        assertEquals(1, entries.size)
        val entry = entries.single()
        assertEquals("tenant_entitlement_updated", entry.action.code)
        assertTrue(entry.summary.contains(targetSlug))
        assertEquals(com.eventverse.app.domain.auth.Role.PLATFORM_SUPERADMIN, entry.actorRole)
    }

    // -------------------------------------------------------------------
    // PUT tier.
    // -------------------------------------------------------------------

    @Test
    fun putTier_shouldChangeTheStoredTenantTier() = testApplication {
        val fixture = Fixture(tenantRepoWith(tier = SubscriptionTier.STARTER))
        installModule(fixture)

        val response = client.put("/api/admin/tenants/$targetSlug/tier") {
            asSuperadmin()
            contentType(ContentType.Application.Json)
            setBody("""{"tier":"ENTERPRISE"}""")
        }

        assertEquals(HttpStatusCode.OK, response.status, response.bodyAsText())
        assertEquals(
            SubscriptionTier.ENTERPRISE,
            runBlocking { fixture.tenantRepo.findById(targetTenantId) }?.tier
        )
    }

    @Test
    fun putTier_withInvalidTierName_shouldReturn400() = testApplication {
        installModule(Fixture(tenantRepoWith()))

        val response = client.put("/api/admin/tenants/$targetSlug/tier") {
            asSuperadmin()
            contentType(ContentType.Application.Json)
            setBody("""{"tier":"NOT_A_REAL_TIER"}""")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun putTier_downgradeThatBreaksAnActiveCustomPlugin_shouldBeRejected() = testApplication {
        val fixture = Fixture(tenantRepoWith(
            tier = SubscriptionTier.ENTERPRISE,
            preset = GarmentBlueprints.BRAND_D2C
        ))
        installModule(fixture)

        // Install a custom plugin the way an Enterprise tenant legitimately would.
        val installResponse = client.post("/api/tenant/pipeline/modules/custom") {
            asTenant(targetSlug)
            contentType(ContentType.Application.Json)
            setBody(
                """{"moduleId":"sablon_bordir_custom","name":"Sablon & Bordir",
                   "archetype":"finishing"}"""
            )
        }
        assertEquals(HttpStatusCode.Created, installResponse.status, installResponse.bodyAsText())

        val response = client.put("/api/admin/tenants/$targetSlug/tier") {
            asSuperadmin()
            contentType(ContentType.Application.Json)
            setBody("""{"tier":"PRO"}""")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertTrue(response.bodyAsText().contains("tidak valid"))
        // Refused, so the tenant must still be on its original plan.
        assertEquals(
            SubscriptionTier.ENTERPRISE,
            runBlocking { fixture.tenantRepo.findById(targetTenantId) }?.tier
        )
    }

    @Test
    fun putTier_shouldRecordAnAuditEntryNamingBothTiers() = testApplication {
        val fixture = Fixture(tenantRepoWith(tier = SubscriptionTier.STARTER))
        installModule(fixture)

        client.put("/api/admin/tenants/$targetSlug/tier") {
            asSuperadmin()
            contentType(ContentType.Application.Json)
            setBody("""{"tier":"ENTERPRISE"}""")
        }

        val entry = runBlocking { fixture.auditRepo.findByTenant(targetTenantId) }.single()
        assertEquals("tenant_tier_updated", entry.action.code)
        assertTrue(entry.summary.contains("STARTER"))
        assertTrue(entry.summary.contains("ENTERPRISE"))
    }

    // -------------------------------------------------------------------
    // GET audit-log.
    // -------------------------------------------------------------------

    @Test
    fun getAuditLog_shouldListEntriesNewestFirst() = testApplication {
        val fixture = Fixture(tenantRepoWith(tier = SubscriptionTier.STARTER))
        installModule(fixture)

        client.put("/api/admin/tenants/$targetSlug/tier") {
            asSuperadmin()
            contentType(ContentType.Application.Json)
            setBody("""{"tier":"PRO"}""")
        }
        client.put("/api/admin/tenants/$targetSlug/entitlement") {
            asSuperadmin()
            contentType(ContentType.Application.Json)
            setBody("""{"grantedModules":null,"grantedCustomModuleIds":[]}""")
        }

        val response = client.get("/api/admin/tenants/$targetSlug/audit-log") { asSuperadmin() }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        val tierIndex = body.indexOf("tenant_tier_updated")
        val entitlementIndex = body.indexOf("tenant_entitlement_updated")
        assertTrue(tierIndex >= 0 && entitlementIndex >= 0)
        assertTrue(
            entitlementIndex < tierIndex,
            "Entri terbaru (entitlement) harus muncul lebih dulu dari entri lama (tier)"
        )
    }

    @Test
    fun getAuditLog_forTenantWithNoHistory_shouldReturnEmptyArray() = testApplication {
        installModule(Fixture(tenantRepoWith()))

        val response = client.get("/api/admin/tenants/$targetSlug/audit-log") { asSuperadmin() }

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("[]", response.bodyAsText())
    }

    @Test
    fun tenantAdminApi_shouldNeverBeReachableThroughTheTenantFacingEndpoint() = testApplication {
        // A tenant-bound account authenticated for its own workspace must not be able to
        // read or write another tenant's admin data via the ordinary tenant routes either.
        val fixture = Fixture(tenantRepoWith())
        installModule(fixture)

        val response = client.get("/api/tenant/pipeline/modules") {
            asTenantTargeting(tokenSlug = otherTenantSlug, targetSlug = targetSlug)
        }

        assertEquals(HttpStatusCode.Forbidden, response.status)
    }
}
