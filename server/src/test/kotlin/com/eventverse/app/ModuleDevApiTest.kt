package com.eventverse.app

import com.eventverse.app.domain.moduledev.BuildType
import com.eventverse.app.domain.moduledev.EffortSource
import com.eventverse.app.domain.moduledev.ModuleBuildId
import com.eventverse.app.domain.moduledev.ModuleBuildRecord
import com.eventverse.app.domain.moduledev.ModuleCatalogEntry
import com.eventverse.app.domain.moduledev.ModuleCatalogEntryId
import com.eventverse.app.domain.moduledev.ModuleLifecycleStatus
import com.eventverse.app.domain.moduledev.MoneyIdr
import com.eventverse.app.domain.moduledev.SizePoints
import com.eventverse.app.domain.moduledev.WorkHours
import com.eventverse.app.domain.pipeline.CustomPipelineNode
import com.eventverse.app.domain.pipeline.CustomTenantPipeline
import com.eventverse.app.domain.pipeline.ModuleArchetype
import com.eventverse.app.domain.tenant.SubscriptionTier
import com.eventverse.app.domain.tenant.Tenant
import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.domain.tenant.TenantName
import com.eventverse.app.domain.tenant.TenantSlug
import com.eventverse.app.domain.tenant.TenantStatus
import com.eventverse.app.infrastructure.InMemoryAuditLogRepository
import com.eventverse.app.infrastructure.InMemoryModuleBuildRepository
import com.eventverse.app.infrastructure.InMemoryModuleCatalogRepository
import com.eventverse.app.infrastructure.InMemoryModuleCustomizationRequestRepository
import com.eventverse.app.infrastructure.InMemoryModulePricingQuoteRepository
import com.eventverse.app.infrastructure.InMemorySizingWeightsRepository
import com.eventverse.app.infrastructure.InMemoryTenantEntitlementRepository
import com.eventverse.app.infrastructure.InMemoryTenantPipelineRepository
import com.eventverse.app.infrastructure.InMemoryTenantRepository
import com.eventverse.app.infrastructure.LexicalEmbeddingProvider
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * End-to-end coverage of the module development ledger over HTTP.
 *
 * Walks the whole path the plan describes: a factory asks for a customisation, the work is
 * estimated from comparable past builds, hours are logged, the build is closed, a price is issued,
 * and the tenant's monthly bill reflects it.
 *
 * Two properties matter more than the happy path and are asserted explicitly:
 *
 *  - an unrelated request must be **refused**, not priced cheaply;
 *  - no tenant-facing response may leak what the work cost us.
 */
class ModuleDevApiTest {

    private val tenantSlug = "cv-berkah-makloon"
    private val tenantId = TenantId("ten-demo-cmt")
    private val qcEntryId = ModuleCatalogEntryId("mce-quality-control")

    private val qcEntry = ModuleCatalogEntry(
        id = qcEntryId,
        moduleId = "quality_control",
        archetypeCode = "quality_control",
        displayName = "Inspeksi QC & Defect",
        lifecycleStatus = ModuleLifecycleStatus.RELEASED,
        baseMonthlyPriceIdr = MoneyIdr(400_000)
    )

    private val inventoryEntry = ModuleCatalogEntry(
        id = ModuleCatalogEntryId("mce-inventory"),
        moduleId = "inventory",
        archetypeCode = "raw_material",
        displayName = "Bahan Baku & Stok Kain",
        lifecycleStatus = ModuleLifecycleStatus.RELEASED,
        baseMonthlyPriceIdr = MoneyIdr(400_000)
    )

    private class Fixture(
        val tenantRepo: InMemoryTenantRepository,
        val pipeRepo: InMemoryTenantPipelineRepository = InMemoryTenantPipelineRepository(),
        val entitlementRepo: InMemoryTenantEntitlementRepository = InMemoryTenantEntitlementRepository(),
        val auditRepo: InMemoryAuditLogRepository = InMemoryAuditLogRepository(),
        val catalogRepo: InMemoryModuleCatalogRepository = InMemoryModuleCatalogRepository(),
        val buildRepo: InMemoryModuleBuildRepository = InMemoryModuleBuildRepository(),
        val quoteRepo: InMemoryModulePricingQuoteRepository = InMemoryModulePricingQuoteRepository(),
        val requestRepo: InMemoryModuleCustomizationRequestRepository =
            InMemoryModuleCustomizationRequestRepository(),
        val weightsRepo: InMemorySizingWeightsRepository = InMemorySizingWeightsRepository()
    )

    private fun fixture(
        history: List<ModuleBuildRecord> = emptyList(),
        activeModules: List<Pair<String, Boolean>> = emptyList()
    ): Fixture {
        val tenantRepo = InMemoryTenantRepository()
        val catalogRepo = InMemoryModuleCatalogRepository(listOf(qcEntry, inventoryEntry))
        val buildRepo = InMemoryModuleBuildRepository()
        val pipeRepo = InMemoryTenantPipelineRepository()

        runBlocking {
            tenantRepo.save(
                Tenant(
                    id = tenantId,
                    slug = TenantSlug(tenantSlug),
                    name = TenantName("CV Berkah Makloon"),
                    status = TenantStatus.ACTIVE,
                    tier = SubscriptionTier.ENTERPRISE
                )
            )
            history.forEach { buildRepo.save(it) }
            if (activeModules.isNotEmpty()) {
                pipeRepo.save(
                    CustomTenantPipeline(
                        tenantId = tenantId,
                        pipelineName = "Alur CMT",
                        baseStarterPreset = null,
                        nodes = activeModules.mapIndexed { index, (moduleId, bypassed) ->
                            CustomPipelineNode(
                                nodeId = "node-$moduleId",
                                moduleId = moduleId,
                                customDisplayName = moduleId,
                                archetype = ModuleArchetype.forModuleCode(moduleId),
                                isBypassed = bypassed,
                                stepOrderIndex = index
                            )
                        },
                        edges = emptyList()
                    )
                )
            }
        }

        return Fixture(
            tenantRepo = tenantRepo,
            pipeRepo = pipeRepo,
            catalogRepo = catalogRepo,
            buildRepo = buildRepo
        )
    }

    private fun ApplicationTestBuilder.installModule(fixture: Fixture) {
        application {
            module(
                tenantRepository = fixture.tenantRepo,
                pipelineRepository = fixture.pipeRepo,
                entitlementRepository = fixture.entitlementRepo,
                auditLogRepository = fixture.auditRepo,
                moduleCatalogRepository = fixture.catalogRepo,
                moduleBuildRepository = fixture.buildRepo,
                modulePricingQuoteRepository = fixture.quoteRepo,
                moduleCustomizationRequestRepository = fixture.requestRepo,
                sizingWeightsRepository = fixture.weightsRepo,
                // The real retriever, so the similarity gate is genuinely exercised.
                embeddingProvider = LexicalEmbeddingProvider()
            )
        }
    }

    /** Past QC work with recorded hours, embedded the same way the server will embed a new request. */
    private fun qcHistory(): List<ModuleBuildRecord> = runBlocking {
        val provider = LexicalEmbeddingProvider()
        listOf(
            Triple("B-019", "rekam inspeksi cacat kain per karton lalu ekspor laporan inspeksi" to 46, 58.0),
            Triple("B-007", "upload foto sample cacat kain untuk approval buyer inspeksi" to 31, 44.0),
            Triple("B-012", "cetak laporan inspeksi per karton berlogo tenant" to 24, 26.0)
        ).map { (id, textAndSize, hours) ->
            val (text, size) = textAndSize
            ModuleBuildRecord(
                id = ModuleBuildId(id),
                catalogEntryId = qcEntryId,
                buildType = BuildType.CUSTOMIZATION,
                archetypeCode = "quality_control",
                requirementText = text,
                sizePoints = SizePoints(size),
                actualHours = WorkHours(hours),
                effortSource = EffortSource.LOGGED,
                embedding = provider.embed(text)
            )
        }
    }

    private val qcFeaturesJson = """
        "features": {
          "entityCount": 2, "useCaseCount": 4, "screenCount": 2, "apiEndpointCount": 5,
          "dbTableCount": 2, "reportCount": 1, "targetPlatformCount": 3,
          "affectedExistingModuleCount": 1, "requiresFileUpload": true
        }
    """.trimIndent()

    // ---- Guard ---------------------------------------------------------------

    @Test
    fun moduleDevAdminRoute_withoutCredentials_shouldReturn401() = testApplication {
        installModule(fixture())
        assertEquals(
            HttpStatusCode.Unauthorized,
            client.get("/api/admin/module-dev/catalog").status
        )
    }

    @Test
    fun moduleDevAdminRoute_asTenantBoundUser_shouldBeRefused() = testApplication {
        installModule(fixture())
        val response = client.get("/api/admin/module-dev/catalog") { asTenant(tenantSlug) }
        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    // ---- The full path -------------------------------------------------------

    @Test
    fun fullFlow_request_estimate_logEffort_complete_quote_shouldReachTheTenantBill() =
        testApplication {
            val fixture = fixture(
                history = qcHistory(),
                activeModules = listOf("quality_control" to false)
            )
            installModule(fixture)

            // 1. The factory asks for something, in its own words.
            val submitted = client.post("/api/tenant/customization-requests") {
                asTenant(tenantSlug)
                setBody(
                    """
                    {"requestId":"req-1","title":"Foto cacat + berita acara PDF",
                     "descriptionRaw":"Buyer sering komplain kain cacat itu bawaan dari mereka, tapi kami tidak punya bukti.",
                     "catalogEntryId":"${qcEntryId.value}"}
                    """.trimIndent()
                )
            }
            assertEquals(HttpStatusCode.Created, submitted.status)

            // 2. We break it down and estimate it from comparable past work.
            val estimated = client.post("/api/admin/module-dev/builds") {
                asSuperadmin()
                setBody(
                    """
                    {"buildId":"B-024","catalogEntryId":"${qcEntryId.value}",
                     "buildType":"CUSTOMIZATION","customizationRequestId":"req-1",
                     "requirementText":"rekam foto cacat kain per karton lalu cetak laporan inspeksi berita acara buyer",
                     "clarityScore":4, $qcFeaturesJson}
                    """.trimIndent()
                )
            }
            assertEquals(HttpStatusCode.Created, estimated.status)
            val estimateBody = estimated.bodyAsText()
            assertTrue(estimateBody.contains("\"estimated\":true"), estimateBody)
            assertTrue(estimateBody.contains("\"sizePoints\":60"), estimateBody)

            // 3. Hours go in as work happens — always hours, never days.
            listOf(
                """{"entryId":"e-1","role":"BACKEND","phase":"IMPLEMENTATION","hours":34.0,"hourlyRateIdr":138000}""",
                """{"entryId":"e-2","role":"FRONTEND","phase":"IMPLEMENTATION","hours":28.0,"hourlyRateIdr":138000}""",
                """{"entryId":"e-3","role":"QA","phase":"QA","hours":12.0,"hourlyRateIdr":138000}""",
                """{"entryId":"e-4","role":"BACKEND","phase":"REVIEW","hours":30.0,"hourlyRateIdr":138000}"""
            ).forEach { body ->
                val logged = client.post("/api/admin/module-dev/builds/B-024/effort") {
                    asSuperadmin()
                    setBody(body)
                }
                assertEquals(HttpStatusCode.Created, logged.status, logged.bodyAsText())
            }

            // 4. Close it with the actuals.
            val completed = client.post("/api/admin/module-dev/builds/B-024/complete") {
                asSuperadmin()
                setBody(
                    """{"revisionRoundCount":2,"leadTimeDays":18,
                        "discoveredScopeDelta":"kompresi & orientasi EXIF foto HP tidak terhitung"}"""
                )
            }
            assertEquals(HttpStatusCode.OK, completed.status, completed.bodyAsText())
            val completedBody = completed.bodyAsText()
            assertTrue(completedBody.contains("\"actualHours\":104.0"), completedBody)
            // Lead time is recorded but is not the effort measure; 18 days is not 104 hours.
            assertTrue(completedBody.contains("\"leadTimeDays\":18"), completedBody)

            // 5. Price it. Exclusive to this tenant, so it funds the build alone.
            val quoted = client.post("/api/admin/module-dev/quotes") {
                asSuperadmin()
                setBody(
                    """
                    {"quoteId":"q-1","buildId":"B-024","expectedTenantCount":1,
                     "amortizationMonths":24,"marginPercent":35.0,
                     "monthlyMaintenancePercent":1.5,"monthlyInfraCostIdr":120000,
                     "tenantId":"${tenantId.value}"}
                    """.trimIndent()
                )
            }
            assertEquals(HttpStatusCode.Created, quoted.status, quoted.bodyAsText())
            val quoteBody = quoted.bodyAsText()
            assertTrue(quoteBody.contains("\"pricingModelVersion\":\"v1\""), quoteBody)

            // 6. Accept it, then check the tenant's bill reflects module + customisation.
            runBlocking {
                val quote = fixture.quoteRepo.findById(
                    com.eventverse.app.domain.moduledev.QuoteId("q-1")
                )!!
                fixture.quoteRepo.save(quote.accept())
            }

            val bill = client.get("/api/tenant/billing-preview") { asTenant(tenantSlug) }
            assertEquals(HttpStatusCode.OK, bill.status)
            val billBody = bill.bodyAsText()
            assertTrue(billBody.contains("\"subscriptionTotalIdr\":400000"), billBody)
            assertTrue(billBody.contains("\"customizationTotalIdr\":"), billBody)
            assertFalse(billBody.contains("customizationTotalIdr\":0,"), billBody)
        }

    // ---- The refusal gate ----------------------------------------------------

    @Test
    fun unrelatedRequest_shouldBeRefusedRatherThanPricedCheaply() = testApplication {
        installModule(fixture(history = qcHistory()))

        val response = client.post("/api/admin/module-dev/builds") {
            asSuperadmin()
            setBody(
                """
                {"buildId":"B-999","catalogEntryId":"${qcEntryId.value}","buildType":"NEW_MODULE",
                 "requirementText":"integrasi payroll pajak karyawan dengan virtual account perbankan",
                 "clarityScore":3,
                 "features":{"entityCount":3,"integrationCount":2}}
                """.trimIndent()
            )
        }

        assertEquals(HttpStatusCode.Created, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("\"estimated\":false"), body)
        assertTrue(body.contains("\"requiresHumanEstimate\":true"), body)
        // No hours may be invented on a refusal.
        assertTrue(body.contains("\"estimatedHours\":null"), body)
    }

    @Test
    fun quotingABuildThatWasNeverEstimated_shouldBeRefused() = testApplication {
        installModule(fixture(history = qcHistory()))

        client.post("/api/admin/module-dev/builds") {
            asSuperadmin()
            setBody(
                """
                {"buildId":"B-998","catalogEntryId":"${qcEntryId.value}","buildType":"NEW_MODULE",
                 "requirementText":"integrasi payroll pajak karyawan dengan virtual account perbankan",
                 "features":{"entityCount":3}}
                """.trimIndent()
            )
        }

        val quoted = client.post("/api/admin/module-dev/quotes") {
            asSuperadmin()
            setBody("""{"quoteId":"q-none","buildId":"B-998","marginPercent":35.0}""")
        }
        assertEquals(HttpStatusCode.UnprocessableEntity, quoted.status)
    }

    @Test
    fun effortInDays_hasNoApiPath_andZeroHoursIsRejected() = testApplication {
        installModule(fixture(history = qcHistory()))
        client.post("/api/admin/module-dev/builds") {
            asSuperadmin()
            setBody(
                """{"buildId":"B-024","catalogEntryId":"${qcEntryId.value}",
                    "requirementText":"rekam foto cacat kain per karton lalu cetak laporan inspeksi",
                    "clarityScore":4, $qcFeaturesJson}"""
            )
        }

        // "days" is not a field the API knows; without hours the body is simply invalid.
        val inDays = client.post("/api/admin/module-dev/builds/B-024/effort") {
            asSuperadmin()
            setBody("""{"entryId":"e-x","role":"BACKEND","phase":"IMPLEMENTATION","days":3,"hourlyRateIdr":138000}""")
        }
        assertEquals(HttpStatusCode.BadRequest, inDays.status)

        val zeroHours = client.post("/api/admin/module-dev/builds/B-024/effort") {
            asSuperadmin()
            setBody("""{"entryId":"e-y","role":"BACKEND","phase":"IMPLEMENTATION","hours":0,"hourlyRateIdr":138000}""")
        }
        assertEquals(HttpStatusCode.BadRequest, zeroHours.status)
    }

    // ---- Confidentiality -----------------------------------------------------

    @Test
    fun tenantFacingResponses_shouldNeverExposeOurCost() = testApplication {
        val fixture = fixture(activeModules = listOf("quality_control" to false))
        installModule(fixture)

        client.post("/api/tenant/customization-requests") {
            asTenant(tenantSlug)
            setBody(
                """{"requestId":"req-2","title":"Foto cacat","descriptionRaw":"butuh bukti cacat kain"}"""
            )
        }

        listOf(
            client.get("/api/tenant/customization-requests") { asTenant(tenantSlug) },
            client.get("/api/tenant/billing-preview") { asTenant(tenantSlug) }
        ).forEach { response ->
            val body = response.bodyAsText()
            listOf(
                "actualHours", "estimatedHours", "hourlyRate", "blendedHourlyRate",
                "totalBuildCost", "buildCostIdr", "marginPercent"
            ).forEach { forbidden ->
                assertFalse(body.contains(forbidden), "leaked '$forbidden' to tenant: $body")
            }
        }
    }

    @Test
    fun billingPreview_shouldDropABypassedModule() = testApplication {
        installModule(
            fixture(activeModules = listOf("quality_control" to false, "inventory" to true))
        )

        val body = client.get("/api/tenant/billing-preview") { asTenant(tenantSlug) }.bodyAsText()
        assertTrue(body.contains("\"monthlyTotalIdr\":400000"), body)
        assertFalse(body.contains("\"moduleId\":\"inventory\""), body)
    }

    @Test
    fun similarBuilds_shouldRankGenuinelyRelatedWorkFirst() = testApplication {
        installModule(fixture(history = qcHistory()))

        val response = client.get(
            "/api/admin/module-dev/builds/similar" +
                "?archetype=quality_control&text=rekam%20foto%20cacat%20kain%20laporan%20inspeksi"
        ) { asSuperadmin() }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("\"neighbors\":["), body)
        // Productivity is what a neighbour actually lends; it has to be visible for review.
        assertTrue(body.contains("\"productivity\":"), body)
    }
}
