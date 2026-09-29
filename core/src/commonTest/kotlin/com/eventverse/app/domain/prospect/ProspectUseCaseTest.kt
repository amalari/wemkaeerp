package com.eventverse.app.domain.prospect

import com.eventverse.app.domain.pack.GarmentBlueprints

import com.eventverse.app.domain.pack.GarmentSlots

import com.eventverse.app.domain.moduledev.BuildFeatureVector
import com.eventverse.app.domain.moduledev.BuildType
import com.eventverse.app.domain.moduledev.EffortSource
import com.eventverse.app.domain.moduledev.FakeEmbeddingProvider
import com.eventverse.app.domain.moduledev.FakeModuleBuildRepository
import com.eventverse.app.domain.moduledev.FakeModuleCatalogRepository
import com.eventverse.app.domain.moduledev.FakeSizingWeightsRepository
import com.eventverse.app.domain.moduledev.ModuleBuildId
import com.eventverse.app.domain.moduledev.ModuleBuildRecord
import com.eventverse.app.domain.moduledev.ModuleCatalogEntry
import com.eventverse.app.domain.moduledev.ModuleCatalogEntryId
import com.eventverse.app.domain.moduledev.ModuleLifecycleStatus
import com.eventverse.app.domain.moduledev.MoneyIdr
import com.eventverse.app.domain.moduledev.Percentage
import com.eventverse.app.domain.moduledev.SizePoints
import com.eventverse.app.domain.moduledev.WorkHours
import com.eventverse.app.domain.pipeline.ModuleArchetype
import com.eventverse.app.domain.prospect.usecases.AnalyzeCoverageUseCase
import com.eventverse.app.domain.prospect.usecases.PriceProspectFlowUseCase
import com.eventverse.app.domain.prospect.usecases.SubmitProspectLeadUseCase
import com.eventverse.app.domain.prospect.usecases.TranslateProspectFlowUseCase
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProspectUseCaseTest {

    private val now = Instant.fromEpochSeconds(1_800_000_000)
    private val leadId = ProspectLeadId("lead-1")

    private val cmtNarrative =
        "Kami makloon jaket. Kain dari buyer, kami cuma jahit. Ada sablon di bagian dada. " +
            "QC pakai AQL 2.5 karena buyer ekspor."

    private fun lead() = ProspectLead(
        id = leadId,
        companyName = "CV Berkah Makloon",
        narrativeRaw = cmtNarrative
    )

    private fun raw(
        archetypeCode: String,
        title: String,
        quote: String = "kutipan",
        suggestedModuleId: String? = null,
        features: BuildFeatureVector = BuildFeatureVector.EMPTY
    ) = RawCapabilityRequirement(archetypeCode, title, "", quote, suggestedModuleId, features)

    private fun draft(
        presetCode: String? = "cmt_makloon",
        requirements: List<RawCapabilityRequirement>
    ) = FlowTranslationDraft("stub/v1", presetCode, requirements)

    private fun translateUseCase(
        d: FlowTranslationDraft,
        leadRepo: FakeProspectLeadRepository = FakeProspectLeadRepository(),
        translationRepo: FakeFlowTranslationRepository = FakeFlowTranslationRepository()
    ) = TranslateProspectFlowUseCase(StubFlowTranslator(d), translationRepo, leadRepo)

    // ---- Submission ----------------------------------------------------------

    @Test
    fun narrative_should_be_stored_exactly_as_written() = runTest {
        val repo = FakeProspectLeadRepository()
        val saved = SubmitProspectLeadUseCase(repo)(
            id = leadId,
            companyName = "  CV Berkah Makloon  ",
            narrativeRaw = cmtNarrative
        ).getOrThrow()

        assertEquals(cmtNarrative, saved.narrativeRaw)
        assertEquals("CV Berkah Makloon", saved.companyName)
    }

    @Test
    fun an_oversized_narrative_should_be_refused_in_the_domain_not_only_at_the_edge() = runTest {
        val result = SubmitProspectLeadUseCase(FakeProspectLeadRepository())(
            id = leadId,
            companyName = "PT Panjang",
            narrativeRaw = "a".repeat(SubmitProspectLeadUseCase.MAX_NARRATIVE_LENGTH + 1)
        )
        assertTrue(result.isFailure)
    }

    // ---- Translation ---------------------------------------------------------

    @Test
    fun requirements_should_be_reordered_into_production_sequence() = runTest {
        // The narrative mentions QC before sewing, as people actually talk.
        val translation = translateUseCase(
            draft(
                requirements = listOf(
                    raw("quality_control", "Inspeksi AQL"),
                    raw("sewing", "Jahit jaket"),
                    raw("order_ingestion", "Terima SPK buyer")
                )
            )
        )(FlowTranslationId("tr-1"), lead(), now).getOrThrow()

        assertEquals(
            listOf(GarmentSlots.ORDER_INGESTION, GarmentSlots.SEWING, GarmentSlots.QUALITY_CONTROL),
            translation.requirements.map { it.archetype }
        )
    }

    @Test
    fun an_unrecognised_business_model_must_not_silently_become_fob() = runTest {
        // GarmentBlueprints.fromCodeOrDefault() falls back to DEFAULT. If this use case ever routed
        // through it, every unknown factory would be filed as a full-package exporter and quoted
        // for buying fabric it never buys.
        val translation = translateUseCase(
            draft(presetCode = "konveksi_rumahan", requirements = listOf(raw("sewing", "Jahit")))
        )(FlowTranslationId("tr-2"), lead(), now).getOrThrow()

        assertNull(translation.detectedPreset)
        assertTrue(translation.validationWarnings.any { it.contains("konveksi_rumahan") })
        assertTrue(translation.needsHumanReview)
    }

    @Test
    fun a_recognised_business_model_should_be_detected() = runTest {
        val translation = translateUseCase(
            draft(presetCode = "cmt_makloon", requirements = listOf(
                raw("order_ingestion", "Terima SPK"), raw("sewing", "Jahit")
            ))
        )(FlowTranslationId("tr-3"), lead(), now).getOrThrow()

        assertEquals(GarmentBlueprints.CMT_MAKLOON, translation.detectedPreset)
    }

    @Test
    fun an_invented_archetype_should_become_a_custom_module_with_a_warning() = runTest {
        val translation = translateUseCase(
            draft(requirements = listOf(
                raw("order_ingestion", "Terima SPK"),
                raw("laundry_kimia", "Cuci kimia")
            ))
        )(FlowTranslationId("tr-4"), lead(), now).getOrThrow()

        val laundry = translation.requirements.single { it.title == "Cuci kimia" }
        assertEquals(GarmentSlots.CUSTOM_EXTENSION, laundry.archetype)
        assertTrue(translation.validationWarnings.any { it.contains("laundry_kimia") })
    }

    @Test
    fun a_requirement_without_a_supporting_quote_should_be_flagged() = runTest {
        val translation = translateUseCase(
            draft(requirements = listOf(
                raw("order_ingestion", "Terima SPK"),
                raw("sewing", "Jahit", quote = "")
            ))
        )(FlowTranslationId("tr-5"), lead(), now).getOrThrow()

        assertTrue(translation.validationWarnings.any { it.contains("kutipan") })
        assertTrue(translation.needsHumanReview)
    }

    @Test
    fun a_translation_with_warnings_should_park_the_lead_for_review() = runTest {
        val leadRepo = FakeProspectLeadRepository(listOf(lead()))
        translateUseCase(
            draft(presetCode = null, requirements = listOf(raw("sewing", "Jahit"))),
            leadRepo = leadRepo
        )(FlowTranslationId("tr-6"), lead(), now).getOrThrow()

        assertEquals(LeadStatus.NEEDS_REVIEW, leadRepo.findById(leadId)?.status)
    }

    @Test
    fun a_narrative_yielding_nothing_recognisable_should_fail_rather_than_invent_a_pipeline() =
        runTest {
            val result = translateUseCase(draft(requirements = emptyList()))(
                FlowTranslationId("tr-7"), lead(), now
            )
            assertTrue(result.isFailure)
        }

    @Test
    fun the_proposed_pipeline_should_use_a_placeholder_tenant_never_a_real_one() = runTest {
        val translation = translateUseCase(
            draft(requirements = listOf(raw("order_ingestion", "Terima SPK"), raw("sewing", "Jahit")))
        )(FlowTranslationId("tr-8"), lead(), now).getOrThrow()

        assertTrue(translation.proposedPipeline.tenantId.value.startsWith("prospect-"))
        assertEquals(2, translation.proposedPipeline.nodes.size)
        assertEquals(1, translation.proposedPipeline.edges.size)
    }

    @Test
    fun a_translator_failure_should_not_leave_the_lead_marked_translated() = runTest {
        val leadRepo = FakeProspectLeadRepository(listOf(lead()))
        val result = TranslateProspectFlowUseCase(
            FailingFlowTranslator(), FakeFlowTranslationRepository(), leadRepo
        )(FlowTranslationId("tr-9"), lead(), now)

        assertTrue(result.isFailure)
        assertEquals(LeadStatus.SUBMITTED, leadRepo.findById(leadId)?.status)
    }

    // ---- Coverage ------------------------------------------------------------

    private val sewingEntry = ModuleCatalogEntry(
        id = ModuleCatalogEntryId("mce-operator-exec"),
        moduleId = "operator_exec",
        archetypeCode = "sewing",
        displayName = "Catatan Kerja Operator",
        lifecycleStatus = ModuleLifecycleStatus.RELEASED,
        baseMonthlyPriceIdr = MoneyIdr(300_000)
    )

    private val sablonEntry = ModuleCatalogEntry(
        id = ModuleCatalogEntryId("mce-sablon"),
        moduleId = "sablon_bordir_custom",
        archetypeCode = "custom_extension",
        displayName = "Sablon Manual & Bordir",
        isCustomPlugin = true,
        lifecycleStatus = ModuleLifecycleStatus.RELEASED,
        baseMonthlyPriceIdr = MoneyIdr(275_000)
    )

    private suspend fun coverageFor(
        requirements: List<RawCapabilityRequirement>,
        catalog: List<ModuleCatalogEntry>
    ): CoverageAnalysis {
        val translation = translateUseCase(draft(requirements = requirements))(
            FlowTranslationId("tr-cov"), lead(), now
        ).getOrThrow()
        return AnalyzeCoverageUseCase(FakeModuleCatalogRepository(catalog))(translation).getOrThrow()
    }

    @Test
    fun a_slot_an_existing_module_fills_should_count_as_covered() = runTest {
        val analysis = coverageFor(
            listOf(raw("order_ingestion", "Terima SPK"), raw("sewing", "Jahit")),
            listOf(sewingEntry)
        )
        assertTrue(analysis.covered.any { it.entry.moduleId == "operator_exec" })
        assertTrue(analysis.gaps.any { it.requirement.archetype == GarmentSlots.ORDER_INGESTION })
    }

    @Test
    fun a_custom_need_must_not_be_covered_just_because_another_plugin_shares_the_slot() = runTest {
        // "Laundry kimia" and "sablon" both land in CUSTOM_EXTENSION but are entirely different
        // work. Matching the wildcard slot by archetype would quote the laundry as already built.
        val analysis = coverageFor(
            listOf(raw("order_ingestion", "SPK"), raw("laundry_kimia", "Cuci kimia")),
            listOf(sablonEntry)
        )
        assertTrue(analysis.gaps.any { it.requirement.title == "Cuci kimia" })
    }

    @Test
    fun an_unreleased_module_should_not_count_as_covering_anything() = runTest {
        val analysis = coverageFor(
            listOf(raw("order_ingestion", "SPK"), raw("sewing", "Jahit")),
            listOf(sewingEntry.copy(lifecycleStatus = ModuleLifecycleStatus.IN_DEVELOPMENT))
        )
        assertTrue(analysis.gaps.any { it.requirement.archetype == GarmentSlots.SEWING })
    }

    // ---- Pricing -------------------------------------------------------------

    private fun pricingUseCase(history: List<ModuleBuildRecord>) = PriceProspectFlowUseCase(
        buildRepository = FakeModuleBuildRepository(history),
        sizingWeightsRepository = FakeSizingWeightsRepository(),
        embeddingProvider = FakeEmbeddingProvider(),
        defaultBlendedHourlyRate = MoneyIdr(250_000)
    )

    private suspend fun loggedHistory(): List<ModuleBuildRecord> {
        val embeddings = FakeEmbeddingProvider()
        return listOf(
            "terima pesanan spk buyer lalu catat order masuk produksi" to (40 to 52.0),
            "pencatatan order masuk dan konfirmasi spk dari buyer ekspor" to (34 to 46.0),
            "input order pelanggan beserta spesifikasi produksi" to (28 to 34.0)
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

    @Test
    fun an_empty_ledger_should_withhold_the_range_rather_than_quote_low() = runTest {
        val analysis = coverageFor(
            listOf(raw("order_ingestion", "Terima SPK"), raw("sewing", "Jahit")),
            listOf(sewingEntry)
        )
        val result = pricingUseCase(emptyList())(analysis, Percentage(35.0)).getOrThrow()

        assertTrue(!result.range.isPublishable)
        assertNull(result.range.displayLow)
        // The catalogue half is still known and useful to a reviewer.
        assertEquals(MoneyIdr(300_000), result.range.subscriptionMonthly)
        assertTrue(result.gapPricings.all { it.refusalReason != null })
    }

    @Test
    fun comparable_history_should_produce_a_publishable_range() = runTest {
        val analysis = coverageFor(
            listOf(
                raw(
                    "order_ingestion", "terima pesanan spk buyer lalu catat order masuk produksi",
                    features = BuildFeatureVector(entityCount = 2, useCaseCount = 3, screenCount = 2)
                ),
                raw("sewing", "Jahit")
            ),
            listOf(sewingEntry)
        )
        val result = pricingUseCase(loggedHistory())(analysis, Percentage(35.0)).getOrThrow()

        assertTrue(result.range.isPublishable, result.gapPricings.map { it.refusalReason }.toString())
        val low = result.range.displayLow!!
        val high = result.range.displayHigh!!
        assertTrue(high.amount >= low.amount)
        // Rounding must never pull the ceiling below the real arithmetic.
        val realHigh = result.range.subscriptionMonthly + result.range.gapHighMonthly!!
        assertTrue(high.amount >= realHigh.amount)
    }

    @Test
    fun spreading_a_gap_across_tenants_should_lower_the_range() = runTest {
        val analysis = coverageFor(
            listOf(
                raw(
                    "order_ingestion", "terima pesanan spk buyer lalu catat order masuk produksi",
                    features = BuildFeatureVector(entityCount = 2, useCaseCount = 3, screenCount = 2)
                )
            ),
            emptyList()
        )
        val useCase = pricingUseCase(loggedHistory())
        val exclusive = useCase(analysis, Percentage(35.0), expectedTenantCount = 1).getOrThrow()
        val shared = useCase(analysis, Percentage(35.0), expectedTenantCount = 4).getOrThrow()

        assertTrue(
            shared.range.gapHighMonthly!!.amount < exclusive.range.gapHighMonthly!!.amount,
            "shared=${shared.range.gapHighMonthly} exclusive=${exclusive.range.gapHighMonthly}"
        )
    }

    @Test
    fun a_fully_covered_prospect_should_get_an_exact_figure_not_a_range() = runTest {
        val analysis = coverageFor(listOf(raw("sewing", "Jahit")), listOf(sewingEntry))
        val result = pricingUseCase(emptyList())(analysis, Percentage(35.0)).getOrThrow()

        assertTrue(analysis.isFullyCovered)
        assertTrue(result.range.isPublishable)
        assertEquals(result.range.displayLow, result.range.displayHigh)
    }
}
