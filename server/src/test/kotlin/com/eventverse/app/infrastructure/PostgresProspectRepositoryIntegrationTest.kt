package com.eventverse.app.infrastructure

import com.eventverse.app.domain.pack.GarmentSlots

import com.eventverse.app.domain.moduledev.BuildFeatureVector
import com.eventverse.app.domain.moduledev.MoneyIdr
import com.eventverse.app.domain.pipeline.CustomPipelineEdge
import com.eventverse.app.domain.pipeline.CustomPipelineNode
import com.eventverse.app.domain.pipeline.CustomTenantPipeline
import com.eventverse.app.domain.pipeline.GarmentBusinessPreset
import com.eventverse.app.domain.pipeline.ModuleArchetype
import com.eventverse.app.domain.prospect.CapabilityRequirement
import com.eventverse.app.domain.prospect.FlowTranslation
import com.eventverse.app.domain.prospect.FlowTranslationId
import com.eventverse.app.domain.prospect.LeadStatus
import com.eventverse.app.domain.prospect.ProspectLead
import com.eventverse.app.domain.prospect.ProspectLeadId
import com.eventverse.app.domain.prospect.ProspectPriceEstimateId
import com.eventverse.app.domain.prospect.ProspectPriceRange
import com.eventverse.app.domain.prospect.StoredPriceEstimate
import com.eventverse.app.infrastructure.tables.ProspectLeadsTable
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import org.jetbrains.exposed.sql.selectAll
import kotlin.math.abs
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Integration coverage for the prospect flow against the local docker-compose database.
 *
 * Three things can only be checked against a real server:
 *
 *  - the JSONB columns (`proposed_graph`, `capability_requirements`) are rejected outright if the
 *    driver sends them as `varchar`;
 *  - the `chk_prospect_range_ordered` and `chk_prospect_publishable_has_range` constraints must
 *    actually fire, so an outward-rounding bug surfaces at write time rather than in front of a
 *    customer;
 *  - a prospect must leave **no row in `tenants`**, despite its pipeline carrying a `TenantId`.
 */
class PostgresProspectRepositoryIntegrationTest {

    private lateinit var leadRepo: PostgresProspectLeadRepository
    private lateinit var translationRepo: PostgresFlowTranslationRepository
    private lateinit var estimateRepo: PostgresProspectPriceEstimateRepository

    private val suffix get() = abs(System.nanoTime() % 1_000_000).toString()

    @BeforeTest
    fun setup() {
        DatabaseFactory.init()
        leadRepo = PostgresProspectLeadRepository()
        translationRepo = PostgresFlowTranslationRepository()
        estimateRepo = PostgresProspectPriceEstimateRepository()
    }

    private fun lead(id: String = "lead-$suffix") = ProspectLead(
        id = ProspectLeadId(id),
        companyName = "CV Berkah Makloon $suffix",
        narrativeRaw = "Kami makloon jaket. Kain dari buyer, kami cuma jahit. QC pakai AQL 2.5.",
        contactEmail = "owner@berkah.test",
        submittedAt = Clock.System.now()
    )

    private fun translationFor(l: ProspectLead) = FlowTranslation(
        id = FlowTranslationId("tr-$suffix"),
        leadId = l.id,
        translatorRef = "keyword/v1",
        requirements = listOf(
            CapabilityRequirement(
                GarmentSlots.ORDER_INGESTION, "Terima SPK buyer",
                sourceQuote = "Kami makloon jaket",
                features = BuildFeatureVector(entityCount = 2, screenCount = 2)
            ),
            CapabilityRequirement(
                GarmentSlots.SEWING, "Jahit jaket",
                sourceQuote = "kami cuma jahit",
                features = BuildFeatureVector(entityCount = 3, screenCount = 3)
            )
        ),
        proposedPipeline = CustomTenantPipeline(
            tenantId = l.placeholderTenantId,
            pipelineName = "Usulan alur",
            baseStarterPreset = GarmentBusinessPreset.CMT_MAKLOON,
            nodes = listOf(
                CustomPipelineNode("n1", "proposed_order_ingestion_1", "Terima SPK", GarmentSlots.ORDER_INGESTION, stepOrderIndex = 0),
                CustomPipelineNode("n2", "proposed_sewing_2", "Jahit", GarmentSlots.SEWING, stepOrderIndex = 1)
            ),
            edges = listOf(CustomPipelineEdge("e1", "n1", "n2", "CutPiecesBundle"))
        ),
        detectedPreset = GarmentBusinessPreset.CMT_MAKLOON,
        openQuestions = listOf("Sablon dikerjakan sendiri?"),
        validationWarnings = listOf("Kebutuhan tanpa kutipan."),
        translatedAt = Clock.System.now()
    )

    @Test
    fun lead_shouldRoundTripWithItsNarrativeVerbatim() = runBlocking<Unit> {
        val l = lead()
        leadRepo.save(l)

        val loaded = assertNotNull(leadRepo.findById(l.id))
        assertEquals(l.narrativeRaw, loaded.narrativeRaw)
        assertEquals(LeadStatus.SUBMITTED, loaded.status)
        assertNull(loaded.convertedTenantId)
    }

    @Test
    fun aProspectShouldNeverCreateATenantRow() = runBlocking<Unit> {
        val before = DatabaseFactory.dbQuery {
            com.eventverse.app.infrastructure.tables.TenantsTable.selectAll().count()
        }

        val l = lead()
        leadRepo.save(l)
        translationRepo.save(translationFor(l))

        val after = DatabaseFactory.dbQuery {
            com.eventverse.app.infrastructure.tables.TenantsTable.selectAll().count()
        }
        assertEquals(before, after, "a prospect leaked into the tenants table")

        // The pipeline still carries a TenantId — it is a placeholder, and it names no tenant row.
        val loaded = assertNotNull(translationRepo.findById(translationFor(l).id.let { _ ->
            translationRepo.findByLead(l.id).single().id
        }))
        assertTrue(loaded.proposedPipeline.tenantId.value.startsWith("prospect-"))
    }

    @Test
    fun translation_shouldRoundTripGraphRequirementsAndWarnings() = runBlocking<Unit> {
        val l = lead()
        leadRepo.save(l)
        val translation = translationFor(l)
        translationRepo.save(translation)

        val loaded = assertNotNull(translationRepo.findByLead(l.id).firstOrNull())
        assertEquals(GarmentBusinessPreset.CMT_MAKLOON, loaded.detectedPreset)
        assertEquals(2, loaded.requirements.size)
        assertEquals("kami cuma jahit", loaded.requirements.single { it.title == "Jahit jaket" }.sourceQuote)
        // Features must survive the JSONB round trip, or every restored gap would score zero points.
        assertEquals(3, loaded.requirements.single { it.title == "Jahit jaket" }.features.entityCount)
        assertEquals(2, loaded.proposedPipeline.nodes.size)
        assertEquals(1, loaded.proposedPipeline.edges.size)
        assertEquals(listOf("Sablon dikerjakan sendiri?"), loaded.openQuestions)
        assertTrue(loaded.needsHumanReview)
    }

    @Test
    fun anUnmatchedBusinessModelShouldPersistAsNullNotAsFob() = runBlocking<Unit> {
        val l = lead()
        leadRepo.save(l)
        translationRepo.save(translationFor(l).copy(detectedPreset = null))

        val loaded = assertNotNull(translationRepo.findByLead(l.id).firstOrNull())
        assertNull(loaded.detectedPreset, "an unknown factory was silently filed as full-package")
    }

    @Test
    fun publishableEstimate_shouldStoreTheRoundedRangeItShowed() = runBlocking<Unit> {
        val l = lead()
        leadRepo.save(l)
        val translation = translationFor(l)
        translationRepo.save(translation)

        val range = ProspectPriceRange(
            subscriptionMonthly = MoneyIdr(800_000),
            gapLowMonthly = MoneyIdr(1_100_000),
            gapHighMonthly = MoneyIdr(1_600_000),
            unpriceableGapCount = 0,
            expectedTenantCount = 1,
            amortizationMonths = 24,
            pricingModelVersion = "v1"
        )
        val id = ProspectPriceEstimateId("pe-$suffix")
        estimateRepo.save(StoredPriceEstimate(id, l.id, translation.id, range, 35.0, Clock.System.now()))

        val loaded = assertNotNull(estimateRepo.findById(id))
        assertTrue(loaded.range.isPublishable)
        assertEquals(MoneyIdr(1_900_000), loaded.range.displayLow)
        assertEquals(MoneyIdr(2_500_000), loaded.range.displayHigh)
        assertEquals(35.0, loaded.marginPercent, 0.001)
    }

    @Test
    fun withheldEstimate_shouldStoreNoRangeAtAll() = runBlocking<Unit> {
        val l = lead()
        leadRepo.save(l)
        val translation = translationFor(l)
        translationRepo.save(translation)

        val id = ProspectPriceEstimateId("pe-withheld-$suffix")
        estimateRepo.save(
            StoredPriceEstimate(
                id, l.id, translation.id,
                ProspectPriceRange.withheld(MoneyIdr(800_000), unpriceableGapCount = 2),
                35.0, Clock.System.now()
            )
        )

        val loaded = assertNotNull(estimateRepo.findById(id))
        assertTrue(!loaded.range.isPublishable)
        assertNull(loaded.range.displayLow)
        assertEquals(2, loaded.range.unpriceableGapCount)
        // The catalogue half is still known and still useful to whoever picks up the call.
        assertEquals(MoneyIdr(800_000), loaded.range.subscriptionMonthly)
    }

    @Test
    fun leadStatus_shouldBeQueryableAsAReviewQueue() = runBlocking<Unit> {
        val l = lead().markNeedsReview()
        leadRepo.save(l)

        assertTrue(leadRepo.findByStatus(LeadStatus.NEEDS_REVIEW).any { it.id == l.id })
    }

    @Test
    fun narrativeColumn_shouldAcceptALongMultilineNarrative() = runBlocking<Unit> {
        val long = buildString {
            repeat(40) { appendLine("Baris $it: kami jahit jaket, kain dari buyer, QC pakai AQL.") }
        }
        val l = lead().copy(narrativeRaw = long)
        leadRepo.save(l)

        val stored = DatabaseFactory.dbQuery {
            ProspectLeadsTable.selectAll()
                .where { ProspectLeadsTable.id eq l.id.value }
                .single()[ProspectLeadsTable.narrativeRaw]
        }
        assertEquals(long, stored)
    }
}
