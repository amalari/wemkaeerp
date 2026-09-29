package com.eventverse.app

import com.eventverse.app.domain.rbac.isScopeSupported

import com.eventverse.app.domain.rbac.isFoundation

import com.eventverse.app.domain.rbac.isOperational

import com.eventverse.app.domain.rbac.isGovernance

import com.eventverse.app.domain.rbac.isHierarchical

import com.eventverse.app.domain.rbac.isGlobalOnly


import com.eventverse.app.domain.rbac.supportedScopes

import com.eventverse.app.domain.rbac.kind

import com.eventverse.app.domain.rbac.scopeCapability

import com.eventverse.app.domain.rbac.iconKey

import com.eventverse.app.domain.rbac.description

import com.eventverse.app.domain.rbac.displayName

import com.eventverse.app.domain.rbac.name

import com.eventverse.app.domain.rbac.code

import com.eventverse.app.domain.rbac.BusinessModules

import com.eventverse.app.domain.pack.GarmentModules

import com.eventverse.app.domain.pipeline.TenantEntitlementGrants
import com.eventverse.app.domain.rbac.BusinessModule
import com.eventverse.app.domain.tenant.SubscriptionTier
import com.eventverse.app.domain.tenant.Tenant
import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.domain.tenant.TenantName
import com.eventverse.app.domain.tenant.TenantSlug
import com.eventverse.app.domain.tenant.TenantStatus
import com.eventverse.app.infrastructure.InMemoryTenantEntitlementRepository
import com.eventverse.app.infrastructure.InMemoryTenantPipelineRepository
import com.eventverse.app.infrastructure.InMemoryTenantRepository
import com.eventverse.app.shared.pipeline.TenantEntitlementGrantsCodec
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
 * `GET /api/tenant/entitlement` — jalur yang membuat pemutusan modul lewat billing benar-benar
 * terasa di dalam aplikasi.
 *
 * Terpisah dari [PipelineModuleApiTest] karena menjawab pertanyaan yang berbeda. Katalog pipeline
 * menjawab *"stasiun produksi apa yang bisa dipasang di kanvas"*; endpoint ini menjawab *"modul apa
 * saja — termasuk layar tata kelola — yang disambungkan ke pabrik ini"*. Modul tata kelola sengaja
 * tidak muncul di katalog pipeline, jadi tanpa endpoint ini klien tidak punya cara mengetahuinya.
 */
class TenantEntitlementApiTest {

    private val slug = "cv-berkah-makloon"
    private val tenantId = TenantId("ten-demo-cmt")

    private fun tenantRepo(tier: SubscriptionTier = SubscriptionTier.PRO): InMemoryTenantRepository {
        val repo = InMemoryTenantRepository()
        runBlocking {
            repo.save(
                Tenant(
                    id = tenantId,
                    slug = TenantSlug(slug),
                    name = TenantName("Tenant Uji Entitlement"),
                    status = TenantStatus.ACTIVE,
                    tier = tier
                )
            )
        }
        return repo
    }

    @Test
    fun getEntitlement_withNoStoredGrants_shouldReturnEveryBuiltInModule() = testApplication {
        application {
            module(
                tenantRepository = tenantRepo(),
                pipelineRepository = InMemoryTenantPipelineRepository(),
                entitlementRepository = InMemoryTenantEntitlementRepository()
            )
        }

        val response = client.get("/api/tenant/entitlement") { asTenant(slug) }
        assertEquals(HttpStatusCode.OK, response.status)

        val granted = TenantEntitlementGrantsCodec.decode(response.bodyAsText()).grantedModules
        assertNotNull(
            granted,
            "Server harus selalu mengirim daftar eksplisit; klien tidak boleh perlu tahu aturan tier " +
                "untuk memuluskan null menjadi 'semua'"
        )
        assertEquals(BusinessModules.entries.toSet(), granted)
    }

    @Test
    fun getEntitlement_shouldIncludeGovernanceModulesWhichThePipelineCatalogueOmits() = testApplication {
        application {
            module(
                tenantRepository = tenantRepo(),
                pipelineRepository = InMemoryTenantPipelineRepository(),
                entitlementRepository = InMemoryTenantEntitlementRepository()
            )
        }

        val granted = TenantEntitlementGrantsCodec
            .decode(client.get("/api/tenant/entitlement") { asTenant(slug) }.bodyAsText())
            .grantedModules
            .orEmpty()

        BusinessModules.governance.forEach { module ->
            assertTrue(module in granted, "${module.code} harus terbawa di entitlement")
        }
    }

    @Test
    fun getEntitlement_withNarrowedGrants_shouldReportTheRevokedModuleAsMissing() = testApplication {
        val entitlementRepo = InMemoryTenantEntitlementRepository()
        runBlocking {
            entitlementRepo.save(
                tenantId,
                TenantEntitlementGrants(
                    grantedModules = BusinessModules.entries.toSet() - GarmentModules.FACTORY_FLOW
                )
            )
        }

        application {
            module(
                tenantRepository = tenantRepo(),
                pipelineRepository = InMemoryTenantPipelineRepository(),
                entitlementRepository = entitlementRepo
            )
        }

        val granted = TenantEntitlementGrantsCodec
            .decode(client.get("/api/tenant/entitlement") { asTenant(slug) }.bodyAsText())
            .grantedModules
            .orEmpty()

        assertFalse(
            GarmentModules.FACTORY_FLOW in granted,
            "Modul yang diputus superadmin harus hilang dari jawaban, itulah yang mengosongkan menunya"
        )
        assertTrue(GarmentModules.ORG_CHART in granted)
    }

    @Test
    fun getEntitlement_withoutCredentials_shouldBeRefused() = testApplication {
        application {
            module(
                tenantRepository = tenantRepo(),
                pipelineRepository = InMemoryTenantPipelineRepository(),
                entitlementRepository = InMemoryTenantEntitlementRepository()
            )
        }

        // Rute di bawah /api/tenant/ tidak boleh menjawab tanpa token terverifikasi: daftar modul
        // sebuah pabrik adalah informasi komersial tentang pabrik itu.
        val response = client.get("/api/tenant/entitlement")
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun getEntitlement_targetingAnotherTenant_shouldBeRefused() = testApplication {
        application {
            module(
                tenantRepository = tenantRepo(),
                pipelineRepository = InMemoryTenantPipelineRepository(),
                entitlementRepository = InMemoryTenantEntitlementRepository()
            )
        }

        val response = client.get("/api/tenant/entitlement") {
            asTenantTargeting(tokenSlug = slug, targetSlug = "urbanwear-d2c")
        }

        assertEquals(HttpStatusCode.Forbidden, response.status)
    }
}
