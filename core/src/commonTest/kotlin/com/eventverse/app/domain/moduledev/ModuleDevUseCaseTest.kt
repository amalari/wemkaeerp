package com.eventverse.app.domain.moduledev

import com.eventverse.app.domain.moduledev.usecases.CompleteModuleBuildUseCase
import com.eventverse.app.domain.moduledev.usecases.EstimateModuleBuildUseCase
import com.eventverse.app.domain.moduledev.usecases.GetTenantBillingPreviewUseCase
import com.eventverse.app.domain.moduledev.usecases.LogBuildEffortUseCase
import com.eventverse.app.domain.moduledev.usecases.QuoteModulePriceUseCase
import com.eventverse.app.domain.moduledev.usecases.SubmitCustomizationRequestUseCase
import com.eventverse.app.domain.pipeline.CustomPipelineNode
import com.eventverse.app.domain.pipeline.CustomTenantPipeline
import com.eventverse.app.domain.pipeline.ModuleArchetype
import com.eventverse.app.domain.tenant.TenantId
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ModuleDevUseCaseTest {

    private val tenantId = TenantId("ten-demo-cmt")
    private val qcEntryId = ModuleCatalogEntryId("mce-quality-control")
    private val now = Instant.fromEpochSeconds(1_800_000_000)

    private val qcCatalogEntry = ModuleCatalogEntry(
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

    private fun pastBuild(
        id: String,
        text: String,
        sizePoints: Int,
        actualHours: Double
    ) = ModuleBuildRecord(
        id = ModuleBuildId(id),
        catalogEntryId = qcEntryId,
        buildType = BuildType.CUSTOMIZATION,
        archetypeCode = "quality_control",
        requirementText = text,
        sizePoints = SizePoints(sizePoints),
        actualHours = WorkHours(actualHours),
        effortSource = EffortSource.LOGGED,
        embedding = null
    )

    private suspend fun withEmbedding(
        record: ModuleBuildRecord,
        provider: FakeEmbeddingProvider
    ) = record.copy(embedding = provider.embed(record.requirementText))

    // ---- Estimation ----------------------------------------------------------

    @Test
    fun similar_past_work_should_produce_an_estimate_and_record_its_neighbours() = runTest {
        val embeddings = FakeEmbeddingProvider()
        val history = listOf(
            pastBuild("B-019", "rekam inspeksi cacat kain per karton lalu ekspor laporan", 46, 58.0),
            pastBuild("B-007", "upload foto sample cacat untuk approval buyer inspeksi", 31, 44.0),
            pastBuild("B-012", "cetak laporan surat jalan berlogo tenant per karton", 24, 26.0)
        ).map { withEmbedding(it, embeddings) }

        val buildRepository = FakeModuleBuildRepository(history)
        val useCase = EstimateModuleBuildUseCase(
            buildRepository = buildRepository,
            sizingWeightsRepository = FakeSizingWeightsRepository(),
            embeddingProvider = embeddings
        )

        val draft = ModuleBuildRecord(
            id = ModuleBuildId("B-024"),
            catalogEntryId = qcEntryId,
            buildType = BuildType.CUSTOMIZATION,
            archetypeCode = "quality_control",
            requirementText = "rekam foto cacat kain per karton lalu cetak laporan inspeksi buyer"
        )

        val estimation = useCase(
            draft = draft,
            features = BuildFeatureVector(
                entityCount = 2, useCaseCount = 4, screenCount = 2, apiEndpointCount = 5,
                dbTableCount = 2, reportCount = 1, targetPlatformCount = 3,
                affectedExistingModuleCount = 1, requiresFileUpload = true
            ),
            clarityScore = 4,
            estimatedAt = now
        ).getOrThrow()

        val outcome = assertIs<EstimationOutcome.Estimated>(estimation.outcome)
        assertEquals(SizePoints(60), outcome.sizePoints)
        assertTrue(estimation.record.isEstimated)
        assertTrue(estimation.record.retrievedNeighborIds.isNotEmpty())
        // The p90 is what a price is built on, so it must be persisted, not just computed.
        assertTrue(estimation.record.estimatedHoursP90!!.hours >= estimation.record.estimatedHours!!.hours)
        assertEquals(EstimatorKind.AI, estimation.record.estimatedBy)
    }

    @Test
    fun unrelated_request_should_refuse_to_estimate_but_still_keep_the_row() = runTest {
        val embeddings = FakeEmbeddingProvider()
        val history = listOf(
            pastBuild("B-019", "rekam inspeksi cacat kain per karton lalu ekspor laporan", 46, 58.0)
        ).map { withEmbedding(it, embeddings) }

        val buildRepository = FakeModuleBuildRepository(history)
        val useCase = EstimateModuleBuildUseCase(
            buildRepository = buildRepository,
            sizingWeightsRepository = FakeSizingWeightsRepository(),
            embeddingProvider = embeddings
        )

        val estimation = useCase(
            draft = ModuleBuildRecord(
                id = ModuleBuildId("B-999"),
                catalogEntryId = qcEntryId,
                buildType = BuildType.NEW_MODULE,
                archetypeCode = "quality_control",
                requirementText = "integrasi payroll pajak karyawan dengan bank mandiri virtual account"
            ),
            features = BuildFeatureVector(entityCount = 3, integrationCount = 2),
            clarityScore = 3,
            estimatedAt = now
        ).getOrThrow()

        assertIs<EstimationOutcome.InsufficientEvidence>(estimation.outcome)
        assertTrue(!estimation.isPriceable)
        // No hours may be invented, but the requirement, features and embedding are worth keeping:
        // a human estimate completes this same row, and the next request can retrieve it.
        assertEquals(null, estimation.record.estimatedHours)
        assertTrue(buildRepository.findById(ModuleBuildId("B-999")) != null)
    }

    @Test
    fun estimating_a_build_twice_should_fail() = runTest {
        val embeddings = FakeEmbeddingProvider()
        val useCase = EstimateModuleBuildUseCase(
            buildRepository = FakeModuleBuildRepository(),
            sizingWeightsRepository = FakeSizingWeightsRepository(),
            embeddingProvider = embeddings
        )
        val alreadyEstimated = ModuleBuildRecord(
            id = ModuleBuildId("B-Z"),
            catalogEntryId = qcEntryId,
            buildType = BuildType.CUSTOMIZATION,
            archetypeCode = "quality_control",
            requirementText = "sudah diestimasi",
            estimatedHours = WorkHours(20.0),
            estimatedAt = now
        )

        val result = useCase(alreadyEstimated, BuildFeatureVector(entityCount = 1), 4, now)
        assertTrue(result.isFailure)
    }

    // ---- Effort and completion ----------------------------------------------

    @Test
    fun logging_effort_then_completing_should_roll_up_hours_cost_and_variance() = runTest {
        val build = ModuleBuildRecord(
            id = ModuleBuildId("B-024"),
            catalogEntryId = qcEntryId,
            buildType = BuildType.CUSTOMIZATION,
            archetypeCode = "quality_control",
            requirementText = "foto cacat + berita acara PDF",
            sizePoints = SizePoints(60),
            estimatedHours = WorkHours(87.0),
            estimatedHoursP90 = WorkHours(98.0),
            estimateConfidence = EstimateConfidence.MEDIUM,
            estimatedAt = now,
            status = BuildStatus.IN_PROGRESS
        )
        val repository = FakeModuleBuildRepository(listOf(build))
        val logEffort = LogBuildEffortUseCase(repository)
        val complete = CompleteModuleBuildUseCase(repository)

        listOf(
            Triple(EffortRole.BACKEND, BuildPhase.IMPLEMENTATION, 34.0),
            Triple(EffortRole.FRONTEND, BuildPhase.IMPLEMENTATION, 28.0),
            Triple(EffortRole.DESIGN, BuildPhase.DESIGN, 6.0),
            Triple(EffortRole.QA, BuildPhase.QA, 12.0),
            Triple(EffortRole.BACKEND, BuildPhase.REVIEW, 14.0),
            Triple(EffortRole.FRONTEND, BuildPhase.REVIEW, 10.0)
        ).forEachIndexed { index, (role, phase, hours) ->
            logEffort(
                ModuleBuildEffortEntry(
                    id = EffortEntryId("e-$index"),
                    buildRecordId = build.id,
                    role = role,
                    phase = phase,
                    hours = WorkHours(hours),
                    hourlyRateIdr = MoneyIdr(138_000)
                )
            ).getOrThrow()
        }

        val completed = complete(
            buildId = build.id,
            completedAt = now,
            revisionRoundCount = 2,
            leadTimeDays = 18,
            discoveredScopeDelta = "kompresi & orientasi EXIF foto HP tidak terhitung"
        ).getOrThrow()

        assertEquals(104.0, completed.actualHours!!.hours, 1e-9)
        assertEquals(MoneyIdr(104 * 138_000L), completed.totalBuildCostIdr)
        assertEquals(24.0, completed.reworkHours.hours, 1e-9)
        assertEquals(BuildStatus.DELIVERED, completed.status)
        // Overran the p50 by ~20%, which is exactly the signal the ledger exists to capture.
        assertEquals(19.54, completed.estimateVariancePercent!!, 0.01)
        // The estimate itself is untouched.
        assertEquals(87.0, completed.estimatedHours!!.hours, 1e-9)
        // Lead time is recorded but is not an effort measure and must not equal the hours.
        assertEquals(18, completed.leadTimeDays)
    }

    @Test
    fun completing_without_any_logged_hours_should_fail() = runTest {
        val build = ModuleBuildRecord(
            id = ModuleBuildId("B-empty"),
            catalogEntryId = qcEntryId,
            buildType = BuildType.CUSTOMIZATION,
            archetypeCode = "quality_control",
            requirementText = "tidak ada jam tercatat"
        )
        val repository = FakeModuleBuildRepository(listOf(build))
        val result = CompleteModuleBuildUseCase(repository)(build.id, now)
        assertTrue(result.isFailure)
    }

    @Test
    fun logging_effort_on_a_closed_build_should_fail() = runTest {
        val closed = ModuleBuildRecord(
            id = ModuleBuildId("B-closed"),
            catalogEntryId = qcEntryId,
            buildType = BuildType.CUSTOMIZATION,
            archetypeCode = "quality_control",
            requirementText = "sudah dikirim",
            status = BuildStatus.DELIVERED
        )
        val repository = FakeModuleBuildRepository(listOf(closed))
        val result = LogBuildEffortUseCase(repository)(
            ModuleBuildEffortEntry(
                id = EffortEntryId("e-late"),
                buildRecordId = closed.id,
                role = EffortRole.BACKEND,
                phase = BuildPhase.IMPLEMENTATION,
                hours = WorkHours(4.0),
                hourlyRateIdr = MoneyIdr(138_000)
            )
        )
        assertTrue(result.isFailure)
    }

    // ---- Pricing -------------------------------------------------------------

    @Test
    fun quoting_should_price_from_p90_and_snapshot_every_input() = runTest {
        val build = deliveredBuild()
        val quoteRepository = FakeModulePricingQuoteRepository()
        val quote = QuoteModulePriceUseCase(
            FakeModuleBuildRepository(listOf(build)), quoteRepository
        )(
            quoteId = QuoteId("q-1"),
            buildId = build.id,
            expectedTenantCount = 1,
            amortizationMonths = 24,
            marginPercent = Percentage(35.0),
            monthlyMaintenancePercent = Percentage(1.5),
            monthlyInfraCost = MoneyIdr(120_000),
            tenantId = tenantId
        ).getOrThrow()

        // Built on the p90 of 98 hours, not the 87-hour midpoint and not the 104 actual.
        assertEquals(98.0, quote.inputs.basisHours.hours, 1e-9)
        assertEquals(MoneyIdr(1_083_585), quote.result.monthlyPrice)
        assertEquals("v1", quote.inputs.pricingModelVersion)
        assertTrue(quoteRepository.findById(QuoteId("q-1")) != null)
    }

    @Test
    fun low_confidence_estimate_should_refuse_to_produce_a_price() = runTest {
        val shaky = deliveredBuild().copy(estimateConfidence = EstimateConfidence.LOW)
        val result = QuoteModulePriceUseCase(
            FakeModuleBuildRepository(listOf(shaky)), FakeModulePricingQuoteRepository()
        )(
            quoteId = QuoteId("q-low"),
            buildId = shaky.id,
            expectedTenantCount = 1,
            amortizationMonths = 24,
            marginPercent = Percentage(35.0)
        )

        assertTrue(result.isFailure)
        assertTrue(
            result.exceptionOrNull()?.message?.contains("konfidensi rendah") == true,
            "unexpected message: ${result.exceptionOrNull()?.message}"
        )
    }

    private fun deliveredBuild() = ModuleBuildRecord(
        id = ModuleBuildId("B-024"),
        catalogEntryId = qcEntryId,
        buildType = BuildType.CUSTOMIZATION,
        archetypeCode = "quality_control",
        requirementText = "foto cacat + berita acara PDF",
        sizePoints = SizePoints(60),
        estimatedHours = WorkHours(87.0),
        estimatedHoursP90 = WorkHours(98.0),
        estimateConfidence = EstimateConfidence.MEDIUM,
        estimatedAt = now,
        actualHours = WorkHours(104.0),
        blendedHourlyRateIdr = MoneyIdr(138_000),
        totalBuildCostIdr = MoneyIdr(104 * 138_000L),
        status = BuildStatus.DELIVERED
    )

    // ---- Billing preview -----------------------------------------------------

    private fun pipelineWith(vararg modules: Pair<String, Boolean>) = CustomTenantPipeline(
        tenantId = tenantId,
        pipelineName = "Alur CMT",
        baseStarterPreset = null,
        nodes = modules.mapIndexed { index, (moduleId, isBypassed) ->
            CustomPipelineNode(
                nodeId = "node-$moduleId",
                moduleId = moduleId,
                customDisplayName = moduleId,
                archetype = ModuleArchetype.forModuleCode(moduleId),
                isBypassed = isBypassed,
                stepOrderIndex = index
            )
        },
        edges = emptyList()
    )

    @Test
    fun billing_preview_should_sum_active_modules_and_accepted_customizations() = runTest {
        val quoteRepository = FakeModulePricingQuoteRepository()
        quoteRepository.save(
            ModulePricingQuote(
                id = QuoteId("q-1"),
                catalogEntryId = qcEntryId,
                inputs = PricingInputs(WorkHours(98.0), MoneyIdr(138_000)),
                result = PricingResult(monthlyPrice = MoneyIdr(361_000)),
                tenantId = tenantId,
                status = QuoteStatus.ACCEPTED
            )
        )

        val preview = GetTenantBillingPreviewUseCase(
            pipelineRepository = FakeTenantPipelineRepository(
                mutableMapOf(
                    tenantId.value to pipelineWith("quality_control" to false, "inventory" to false)
                )
            ),
            catalogRepository = FakeModuleCatalogRepository(listOf(qcCatalogEntry, inventoryEntry)),
            quoteRepository = quoteRepository
        )(tenantId).getOrThrow()

        assertEquals(MoneyIdr(800_000), preview.subscriptionTotal)
        assertEquals(MoneyIdr(361_000), preview.customizationTotal)
        assertEquals(MoneyIdr(1_161_000), preview.monthlyTotal)
    }

    @Test
    fun bypassing_a_module_should_lower_the_monthly_total() = runTest {
        val useCase = GetTenantBillingPreviewUseCase(
            pipelineRepository = FakeTenantPipelineRepository(
                mutableMapOf(
                    tenantId.value to pipelineWith("quality_control" to false, "inventory" to true)
                )
            ),
            catalogRepository = FakeModuleCatalogRepository(listOf(qcCatalogEntry, inventoryEntry)),
            quoteRepository = FakeModulePricingQuoteRepository()
        )

        val preview = useCase(tenantId).getOrThrow()
        assertEquals(MoneyIdr(400_000), preview.monthlyTotal)
        assertEquals(1, preview.lines.size)
    }

    @Test
    fun an_unreleased_module_should_be_skipped_rather_than_billed_at_zero() = runTest {
        val unreleased = inventoryEntry.copy(lifecycleStatus = ModuleLifecycleStatus.IN_DEVELOPMENT)
        val preview = GetTenantBillingPreviewUseCase(
            pipelineRepository = FakeTenantPipelineRepository(
                mutableMapOf(
                    tenantId.value to pipelineWith("quality_control" to false, "inventory" to false)
                )
            ),
            catalogRepository = FakeModuleCatalogRepository(listOf(qcCatalogEntry, unreleased)),
            quoteRepository = FakeModulePricingQuoteRepository()
        )(tenantId).getOrThrow()

        assertEquals(MoneyIdr(400_000), preview.monthlyTotal)
        assertTrue(preview.lines.none { it.moduleId == "inventory" })
    }

    @Test
    fun a_rejected_quote_should_not_reach_the_bill() = runTest {
        val quoteRepository = FakeModulePricingQuoteRepository()
        quoteRepository.save(
            ModulePricingQuote(
                id = QuoteId("q-rejected"),
                catalogEntryId = qcEntryId,
                inputs = PricingInputs(WorkHours(98.0), MoneyIdr(138_000)),
                result = PricingResult(monthlyPrice = MoneyIdr(999_000)),
                tenantId = tenantId,
                status = QuoteStatus.REJECTED
            )
        )

        val preview = GetTenantBillingPreviewUseCase(
            pipelineRepository = FakeTenantPipelineRepository(
                mutableMapOf(tenantId.value to pipelineWith("quality_control" to false))
            ),
            catalogRepository = FakeModuleCatalogRepository(listOf(qcCatalogEntry)),
            quoteRepository = quoteRepository
        )(tenantId).getOrThrow()

        assertEquals(MoneyIdr(400_000), preview.monthlyTotal)
    }

    // ---- Customization request ----------------------------------------------

    @Test
    fun submitting_a_request_should_store_the_description_verbatim() = runTest {
        val repository = FakeModuleCustomizationRequestRepository()
        val raw = "Buyer kami sering komplain kain cacat itu bawaan dari mereka,\n" +
            "tapi kami tidak punya bukti."

        val request = SubmitCustomizationRequestUseCase(
            repository, FakeModuleCatalogRepository(listOf(qcCatalogEntry))
        )(
            id = CustomizationRequestId("req-1"),
            tenantId = tenantId,
            title = "  Foto cacat + berita acara PDF  ",
            descriptionRaw = raw,
            catalogEntryId = qcEntryId
        ).getOrThrow()

        assertEquals(raw, request.descriptionRaw)
        assertEquals("Foto cacat + berita acara PDF", request.title)
        assertEquals(CustomizationRequestStatus.SUBMITTED, request.status)
    }

    @Test
    fun requesting_against_a_module_that_does_not_exist_should_fail() = runTest {
        val result = SubmitCustomizationRequestUseCase(
            FakeModuleCustomizationRequestRepository(), FakeModuleCatalogRepository()
        )(
            id = CustomizationRequestId("req-2"),
            tenantId = tenantId,
            title = "Modul hantu",
            descriptionRaw = "mengacu ke modul yang tidak ada",
            catalogEntryId = ModuleCatalogEntryId("mce-tidak-ada")
        )
        assertTrue(result.isFailure)
    }

    @Test
    fun approving_a_request_without_a_quote_should_fail() {
        val request = ModuleCustomizationRequest(
            id = CustomizationRequestId("req-3"),
            tenantId = tenantId,
            title = "Belum ada harga",
            descriptionRaw = "disetujui sebelum diberi harga"
        )
        assertTrue(runCatching { request.approve(now) }.isFailure)
    }
}
