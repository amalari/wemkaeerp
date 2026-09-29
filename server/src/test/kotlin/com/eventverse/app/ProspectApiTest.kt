package com.eventverse.app

import com.eventverse.app.domain.pack.GarmentBlueprints

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
import com.eventverse.app.infrastructure.InMemoryFlowTranslationRepository
import com.eventverse.app.infrastructure.InMemoryModuleBuildRepository
import com.eventverse.app.infrastructure.InMemoryModuleCatalogRepository
import com.eventverse.app.infrastructure.InMemoryProspectLeadRepository
import com.eventverse.app.infrastructure.InMemoryProspectPriceEstimateRepository
import com.eventverse.app.infrastructure.InMemorySizingWeightsRepository
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
 * End-to-end coverage of the prospect flow over HTTP.
 *
 * The happy path matters less here than three properties:
 *
 *  - a CMT workshop must not be filed as a full-package exporter;
 *  - an empty ledger must produce **no number at all**, not Rp 0;
 *  - the public response must never carry a cost figure.
 */
class ProspectApiTest {

    private val cmtNarrative =
        "Kami makloon jaket. Kain dari buyer, kami cuma jahit sesuai pesanan mereka."

    private val sewingEntry = ModuleCatalogEntry(
        id = ModuleCatalogEntryId("mce-operator-exec"),
        moduleId = "operator_exec",
        archetypeCode = "sewing",
        displayName = "Catatan Kerja Operator",
        lifecycleStatus = ModuleLifecycleStatus.RELEASED,
        baseMonthlyPriceIdr = MoneyIdr(300_000)
    )

    private class Fixture(
        val leadRepo: InMemoryProspectLeadRepository = InMemoryProspectLeadRepository(),
        val translationRepo: InMemoryFlowTranslationRepository = InMemoryFlowTranslationRepository(),
        val estimateRepo: InMemoryProspectPriceEstimateRepository = InMemoryProspectPriceEstimateRepository(),
        val catalogRepo: InMemoryModuleCatalogRepository = InMemoryModuleCatalogRepository(),
        val buildRepo: InMemoryModuleBuildRepository = InMemoryModuleBuildRepository(),
        val tenantRepo: InMemoryTenantRepository = InMemoryTenantRepository()
    )

    private fun ApplicationTestBuilder.installModule(fixture: Fixture) {
        application {
            module(
                tenantRepository = fixture.tenantRepo,
                moduleCatalogRepository = fixture.catalogRepo,
                moduleBuildRepository = fixture.buildRepo,
                sizingWeightsRepository = InMemorySizingWeightsRepository(),
                embeddingProvider = LexicalEmbeddingProvider(),
                prospectLeadRepository = fixture.leadRepo,
                flowTranslationRepository = fixture.translationRepo,
                prospectPriceEstimateRepository = fixture.estimateRepo
            )
        }
    }

    /** Past order-intake work with logged hours, embedded the way the server will embed a gap. */
    private fun orderIntakeHistory(): List<ModuleBuildRecord> = runBlocking {
        val embeddings = LexicalEmbeddingProvider()
        listOf(
            "penerimaan pesanan spk buyer pencatatan order masuk pelanggan klien" to (40 to 52.0),
            "pencatatan order masuk konfirmasi spk buyer pelanggan ekspor" to (34 to 46.0),
            "input order pelanggan spk buyer beserta spesifikasi pesanan" to (28 to 34.0)
        ).mapIndexed { index, (text, sizeAndHours) ->
            val (points, hours) = sizeAndHours
            ModuleBuildRecord(
                id = ModuleBuildId("hist-$index"),
                catalogEntryId = ModuleCatalogEntryId("mce-crm"),
                buildType = BuildType.NEW_MODULE,
                archetypeCode = "order_ingestion",
                requirementText = text,
                sizePoints = SizePoints(points),
                actualHours = WorkHours(hours),
                effortSource = EffortSource.LOGGED,
                embedding = embeddings.embed(text)
            )
        }
    }

    private suspend fun submit(client: io.ktor.client.HttpClient, narrative: String = cmtNarrative) =
        client.post("/api/public/prospect-assessment") {
            setBody(
                """{"companyName":"CV Berkah Makloon","narrative":"$narrative",
                    "contactEmail":"owner@berkah.test"}"""
            )
        }

    // ---- Public endpoint -----------------------------------------------------

    @Test
    fun theAssessmentEndpointShouldBeReachableWithoutCredentials() = testApplication {
        installModule(Fixture())
        assertEquals(HttpStatusCode.Created, submit(client).status)
    }

    @Test
    fun aCmtNarrativeMustNotBeFiledAsFullPackage() = testApplication {
        installModule(Fixture())
        val body = submit(client).bodyAsText()
        // the legacy `GarmentBusinessPreset.fromCode()` (removed in B4d) falls back to DEFAULT; if that helper were used here,
        // this workshop would be quoted for buying fabric it never buys.
        assertTrue(body.contains("CMT"), body)
        assertFalse(body.contains("FOB"), body)
    }

    @Test
    fun anEmptyLedgerShouldWithholdTheNumberRatherThanQuoteZero() = testApplication {
        installModule(Fixture(catalogRepo = InMemoryModuleCatalogRepository(listOf(sewingEntry))))
        val body = submit(client).bodyAsText()

        assertTrue(body.contains("\"available\":false"), body)
        assertTrue(body.contains("1×24 jam"), body)
        assertFalse(body.contains("monthlyLowIdr"), body)
    }

    @Test
    fun comparableHistoryShouldProduceAPublishedRange() = testApplication {
        installModule(
            Fixture(
                catalogRepo = InMemoryModuleCatalogRepository(listOf(sewingEntry)),
                buildRepo = InMemoryModuleBuildRepository(orderIntakeHistory())
            )
        )
        val body = submit(
            client,
            "Kami makloon jaket. Kain dari buyer, kami cuma jahit. " +
                "Pesanan spk dari buyer dicatat manual, pelanggan klien banyak."
        ).bodyAsText()

        assertTrue(body.contains("\"available\":true"), body)
        assertTrue(body.contains("monthlyLowIdr"), body)
        assertTrue(body.contains("monthlyHighIdr"), body)
    }

    @Test
    fun thePublicResponseShouldNeverExposeOurCost() = testApplication {
        installModule(
            Fixture(
                catalogRepo = InMemoryModuleCatalogRepository(listOf(sewingEntry)),
                buildRepo = InMemoryModuleBuildRepository(orderIntakeHistory())
            )
        )
        val body = submit(client).bodyAsText()

        listOf(
            "actualHours", "estimatedHours", "hourlyRate", "blendedHourlyRate",
            "buildCost", "marginPercent", "sizePoints", "expectedTenantCount",
            "gapLowMonthlyIdr", "refusalReason"
        ).forEach { forbidden ->
            assertFalse(body.contains(forbidden), "leaked '$forbidden' to a prospect: $body")
        }
    }

    @Test
    fun theModuleListShouldSayWhatExistsAndWhatMustBeBuilt() = testApplication {
        installModule(Fixture(catalogRepo = InMemoryModuleCatalogRepository(listOf(sewingEntry))))
        val body = submit(client).bodyAsText()

        assertTrue(body.contains("TERSEDIA"), body)
        assertTrue(body.contains("PERLU DIBANGUN"), body)
    }

    @Test
    fun anEmptyBodyShouldBeRejected() = testApplication {
        installModule(Fixture())
        val response = client.post("/api/public/prospect-assessment") { setBody("{}") }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun anOversizedNarrativeShouldBeRejectedNotSentOnward() = testApplication {
        installModule(Fixture())
        // Once a paid model sits behind this endpoint, an unbounded narrative is somebody else's
        // bill. The cap lives in the domain, so it holds regardless of caller.
        val response = client.post("/api/public/prospect-assessment") {
            setBody("""{"companyName":"PT Panjang","narrative":"${"a".repeat(9_000)}"}""")
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun aProspectShouldNotBecomeATenant() = testApplication {
        val fixture = Fixture()
        installModule(fixture)

        // The repository ships with three seeded demo factories, so the check is that submitting a
        // prospect adds none — and that no tenant ends up carrying a placeholder id.
        val before = runBlocking { fixture.tenantRepo.findAll() }.map { it.id.value }.toSet()
        submit(client)
        val after = runBlocking { fixture.tenantRepo.findAll() }.map { it.id.value }.toSet()

        assertEquals(before, after, "a prospect leaked into the tenant repository")
        assertTrue(after.none { it.startsWith("prospect-") })
    }

    // ---- Admin side ----------------------------------------------------------

    @Test
    fun theProspectQueueShouldRequireASuperadmin() = testApplication {
        installModule(Fixture())
        assertEquals(HttpStatusCode.Unauthorized, client.get("/api/admin/prospects").status)
    }

    @Test
    fun theReviewerShouldSeeWarningsQuotesAndRefusalReasons() = testApplication {
        val fixture = Fixture(catalogRepo = InMemoryModuleCatalogRepository(listOf(sewingEntry)))
        installModule(fixture)
        submit(client)

        val leadId = runBlocking { fixture.leadRepo.findRecent().single().id.value }
        val body = client.get("/api/admin/prospects/$leadId") { asSuperadmin() }.bodyAsText()

        assertTrue(body.contains("narrativeRaw"), body)
        assertTrue(body.contains("sourceQuote"), body)
        assertTrue(body.contains("validationWarnings"), body)
        // Why a gap could not be priced is the most actionable line on the review screen.
        assertTrue(body.contains("refusalReason"), body)
    }

    @Test
    fun repricingAcrossMoreTenantsShouldLowerTheRange() = testApplication {
        val fixture = Fixture(
            catalogRepo = InMemoryModuleCatalogRepository(listOf(sewingEntry)),
            buildRepo = InMemoryModuleBuildRepository(orderIntakeHistory())
        )
        installModule(fixture)
        submit(
            client,
            "Kami makloon jaket. Kain dari buyer, kami cuma jahit. " +
                "Pesanan spk dari buyer dicatat manual, pelanggan klien banyak."
        )

        val leadId = runBlocking { fixture.leadRepo.findRecent().single().id.value }

        fun priceWith(count: Int): Long {
            val body = runBlocking {
                client.post("/api/admin/prospects/$leadId/price") {
                    asSuperadmin()
                    setBody("""{"expectedTenantCount":$count,"amortizationMonths":24,"marginPercent":35.0}""")
                }.bodyAsText()
            }
            return Regex("\"gapHighMonthlyIdr\":(\\d+)").find(body)!!.groupValues[1].toLong()
        }

        val exclusive = priceWith(1)
        val shared = priceWith(4)
        assertTrue(shared < exclusive, "shared=$shared exclusive=$exclusive")
    }

    @Test
    fun anUnknownProspectShouldReturn404() = testApplication {
        installModule(Fixture())
        assertEquals(
            HttpStatusCode.NotFound,
            client.get("/api/admin/prospects/lead-tidak-ada") { asSuperadmin() }.status
        )
    }
}
