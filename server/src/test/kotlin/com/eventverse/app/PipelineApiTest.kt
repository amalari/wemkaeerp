package com.eventverse.app

import com.eventverse.app.domain.pipeline.GarmentBusinessPreset
import com.eventverse.app.domain.tenant.*
import com.eventverse.app.infrastructure.InMemoryTenantPipelineRepository
import com.eventverse.app.infrastructure.InMemoryTenantRepository
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class PipelineApiTest {

    private val tenantSlug = "wemade-demo"
    private val tenantId = TenantId("ten-demo-001")

    private fun setupTestTenantRepo(): InMemoryTenantRepository {
        val repo = InMemoryTenantRepository()
        runBlocking {
            repo.save(
                Tenant(
                    id = tenantId,
                    slug = TenantSlug(tenantSlug),
                    name = TenantName("PT WeMade Demo"),
                    status = TenantStatus.ACTIVE,
                    tier = SubscriptionTier.PRO,
                    businessPreset = GarmentBusinessPreset.FOB_FULL_PACKAGE
                )
            )
        }
        return repo
    }

    @Test
    fun getPipeline_withValidTenant_shouldReturn200AndDefaultPipeline() = testApplication {
        val tenantRepo = setupTestTenantRepo()
        val pipeRepo = InMemoryTenantPipelineRepository()

        application {
            module(
                tenantRepository = tenantRepo,
                pipelineRepository = pipeRepo
            )
        }

        val res = client.get("/api/tenant/pipeline") {
            header("X-Tenant-Slug", tenantSlug)
        }

        assertEquals(HttpStatusCode.OK, res.status)
        val body = res.bodyAsText()
        assertTrue(body.contains("\"pipelineName\":"))
        assertTrue(body.contains("\"nodes\":"))
        assertTrue(body.contains("\"edges\":"))
    }

    @Test
    fun putPipeline_withValidCustomTopologies_shouldReturn200AndPersist() = testApplication {
        val tenantRepo = setupTestTenantRepo()
        val pipeRepo = InMemoryTenantPipelineRepository()

        application {
            module(
                tenantRepository = tenantRepo,
                pipelineRepository = pipeRepo
            )
        }

        // 1. Fetch current
        val initialRes = client.get("/api/tenant/pipeline") {
            header("X-Tenant-Slug", tenantSlug)
        }
        val initialBody = initialRes.bodyAsText()

        // 2. Modify name
        val modifiedBody = initialBody.replace("Alur Operasional", "Alur Khusus Modifikasi")
        val putRes = client.put("/api/tenant/pipeline") {
            header("X-Tenant-Slug", tenantSlug)
            contentType(ContentType.Application.Json)
            setBody(modifiedBody)
        }

        assertEquals(HttpStatusCode.OK, putRes.status)
        val putBody = putRes.bodyAsText()
        assertTrue(putBody.contains("Alur Khusus Modifikasi"))

        // 3. Verify get returns modified
        val getRes = client.get("/api/tenant/pipeline") {
            header("X-Tenant-Slug", tenantSlug)
        }
        assertTrue(getRes.bodyAsText().contains("Alur Khusus Modifikasi"))
    }

    @Test
    fun resetPipeline_shouldRestoreSpecifiedPreset() = testApplication {
        val tenantRepo = setupTestTenantRepo()
        val pipeRepo = InMemoryTenantPipelineRepository()

        application {
            module(
                tenantRepository = tenantRepo,
                pipelineRepository = pipeRepo
            )
        }

        val resetRes = client.post("/api/tenant/pipeline/reset") {
            header("X-Tenant-Slug", tenantSlug)
            contentType(ContentType.Application.Json)
            setBody("{\"preset\":\"brand_d2c\"}")
        }

        assertEquals(HttpStatusCode.OK, resetRes.status)
        val body = resetRes.bodyAsText()
        assertTrue(body.contains("brand_d2c"))
    }
}
