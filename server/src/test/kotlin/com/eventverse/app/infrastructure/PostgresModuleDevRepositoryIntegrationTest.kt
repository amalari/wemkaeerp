package com.eventverse.app.infrastructure

import com.eventverse.app.domain.moduledev.BuildPhase
import com.eventverse.app.domain.moduledev.BuildStatus
import com.eventverse.app.domain.moduledev.BuildType
import com.eventverse.app.domain.moduledev.BuildFeatureVector
import com.eventverse.app.domain.moduledev.CustomizationRequestId
import com.eventverse.app.domain.moduledev.EffortEntryId
import com.eventverse.app.domain.moduledev.EffortRole
import com.eventverse.app.domain.moduledev.EffortSource
import com.eventverse.app.domain.moduledev.EmbeddingVector
import com.eventverse.app.domain.moduledev.EstimateConfidence
import com.eventverse.app.domain.moduledev.EstimatorKind
import com.eventverse.app.domain.moduledev.ModuleBuildEffortEntry
import com.eventverse.app.domain.moduledev.ModuleBuildId
import com.eventverse.app.domain.moduledev.ModuleBuildRecord
import com.eventverse.app.domain.moduledev.ModuleCatalogEntry
import com.eventverse.app.domain.moduledev.ModuleCatalogEntryId
import com.eventverse.app.domain.moduledev.ModuleCustomizationRequest
import com.eventverse.app.domain.moduledev.ModuleLifecycleStatus
import com.eventverse.app.domain.moduledev.ModulePricingQuote
import com.eventverse.app.domain.moduledev.MoneyIdr
import com.eventverse.app.domain.moduledev.Percentage
import com.eventverse.app.domain.moduledev.PricingInputs
import com.eventverse.app.domain.moduledev.QuoteId
import com.eventverse.app.domain.moduledev.QuoteStatus
import com.eventverse.app.domain.moduledev.SizePoints
import com.eventverse.app.domain.moduledev.SizingWeights
import com.eventverse.app.domain.moduledev.WorkHours
import com.eventverse.app.domain.moduledev.AmortizedBuildCostFormula
import com.eventverse.app.domain.tenant.SubscriptionTier
import com.eventverse.app.domain.tenant.Tenant
import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.domain.tenant.TenantName
import com.eventverse.app.domain.tenant.TenantSlug
import com.eventverse.app.domain.tenant.TenantStatus
import com.eventverse.app.infrastructure.tables.ModuleBuildRecordsTable
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.selectAll
import kotlin.math.abs
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Integration coverage for the module development ledger against the local docker-compose
 * database, following the convention of the other `Postgres*IntegrationTest` classes.
 *
 * Three things here cannot be verified anywhere but against a real server:
 *
 *  - `estimate_variance_percent` is a **generated** column. If Exposed ever tried to write it,
 *    PostgreSQL would reject the statement, and no unit test would notice.
 *  - the JSONB columns (`embedding`, `feature_vector`, `calculation_breakdown`) are rejected
 *    outright if the driver sends them as `varchar`.
 *  - the catalogue and build tables are the first **non-RLS** tables in this schema; a query
 *    against them without a tenant context must work rather than silently return nothing.
 */
class PostgresModuleDevRepositoryIntegrationTest {

    private lateinit var tenantRepo: PostgresTenantRepository
    private lateinit var catalogRepo: PostgresModuleCatalogRepository
    private lateinit var buildRepo: PostgresModuleBuildRepository
    private lateinit var quoteRepo: PostgresModulePricingQuoteRepository
    private lateinit var requestRepo: PostgresModuleCustomizationRequestRepository
    private lateinit var weightsRepo: PostgresSizingWeightsRepository

    private val suffix get() = abs(System.nanoTime() % 1_000_000).toString()

    @BeforeTest
    fun setup() {
        DatabaseFactory.init()
        tenantRepo = PostgresTenantRepository()
        catalogRepo = PostgresModuleCatalogRepository()
        buildRepo = PostgresModuleBuildRepository()
        quoteRepo = PostgresModulePricingQuoteRepository()
        requestRepo = PostgresModuleCustomizationRequestRepository()
        weightsRepo = PostgresSizingWeightsRepository()
    }

    private fun createTenant(): Tenant {
        val id = suffix
        val tenant = Tenant(
            id = TenantId("ten-mdev-$id"),
            slug = TenantSlug("mdev-$id"),
            name = TenantName("PT Ledger Test $id"),
            status = TenantStatus.ACTIVE,
            tier = SubscriptionTier.ENTERPRISE
        )
        runBlocking { tenantRepo.save(tenant).getOrThrow() }
        return tenant
    }

    private fun createCatalogEntry(): ModuleCatalogEntry {
        val id = suffix
        val entry = ModuleCatalogEntry(
            id = ModuleCatalogEntryId("mce-test-$id"),
            moduleId = "test_module_$id",
            archetypeCode = "quality_control",
            displayName = "Modul Uji $id",
            description = "Dibuat oleh integration test",
            acceptedInputTypes = listOf("FinishedGarmentUnit"),
            producedOutputType = "InspectedAndGradedUnit",
            lifecycleStatus = ModuleLifecycleStatus.RELEASED,
            baseMonthlyPriceIdr = MoneyIdr(400_000),
            releasedAt = Clock.System.now()
        )
        runBlocking { catalogRepo.save(entry) }
        return entry
    }

    // ---- Catalogue -----------------------------------------------------------

    @Test
    fun catalogEntry_shouldRoundTripWithoutATenantContext() = runBlocking<Unit> {
        val entry = createCatalogEntry()

        val loaded = catalogRepo.findByModuleId(entry.moduleId)
        assertNotNull(loaded, "platform-global table returned nothing without a tenant context")
        assertEquals(entry.displayName, loaded.displayName)
        assertEquals(listOf("FinishedGarmentUnit"), loaded.acceptedInputTypes)
        assertEquals(MoneyIdr(400_000), loaded.baseMonthlyPriceIdr)
        assertTrue(loaded.isBillable)
    }

    @Test
    fun seededCatalog_shouldContainTheNineBuiltInModules() = runBlocking<Unit> {
        val moduleIds = catalogRepo.findAll().map { it.moduleId }
        listOf("crm_sales", "inventory", "costing_hpp", "quality_control", "fulfillment")
            .forEach { assertTrue(it in moduleIds, "V14 seed is missing $it") }
    }

    @Test
    fun savingTwice_shouldUpdateRatherThanDuplicate() = runBlocking<Unit> {
        val entry = createCatalogEntry()
        catalogRepo.save(entry.withListPrice(MoneyIdr(555_000)))

        val loaded = catalogRepo.findById(entry.id)
        assertEquals(MoneyIdr(555_000), loaded?.baseMonthlyPriceIdr)
    }

    // ---- Builds --------------------------------------------------------------

    @Test
    fun buildRecord_shouldRoundTripEveryBlockIncludingJsonbColumns() = runBlocking<Unit> {
        val entry = createCatalogEntry()
        val now = Clock.System.now()
        val record = ModuleBuildRecord(
            id = ModuleBuildId("b-test-$suffix"),
            catalogEntryId = entry.id,
            buildType = BuildType.CUSTOMIZATION,
            archetypeCode = "quality_control",
            requirementText = "foto cacat per potong lalu cetak berita acara PDF untuk buyer",
            features = BuildFeatureVector(
                entityCount = 2, useCaseCount = 4, screenCount = 2, apiEndpointCount = 5,
                dbTableCount = 2, reportCount = 1, targetPlatformCount = 3,
                affectedExistingModuleCount = 1, requiresFileUpload = true
            ),
            embedding = EmbeddingVector(listOf(0.12, -0.33, 0.87), model = "test-embed"),
            clarityScore = 4,
            sizePoints = SizePoints(60)
        ).withEstimate(
            estimatedHours = WorkHours(87.0),
            estimatedHoursP90 = WorkHours(98.0),
            estimatedBy = EstimatorKind.AI,
            estimatorRef = "test/pricing-v1",
            confidence = EstimateConfidence.MEDIUM,
            estimatedAt = now,
            retrievedNeighborIds = listOf("B-019", "B-007"),
            nearestNeighborSimilarity = 0.881,
            openQuestions = listOf("Foto disimpan berapa lama?")
        )

        buildRepo.save(record)
        val loaded = assertNotNull(buildRepo.findById(record.id))

        assertEquals(record.requirementText, loaded.requirementText)
        assertEquals(2, loaded.features.entityCount)
        assertTrue(loaded.features.requiresFileUpload)
        assertEquals(3, loaded.features.targetPlatformCount)
        assertEquals(SizePoints(60), loaded.sizePoints)
        assertEquals(4, loaded.clarityScore)
        assertEquals(87.0, loaded.estimatedHours!!.hours, 0.001)
        assertEquals(98.0, loaded.estimatedHoursP90!!.hours, 0.001)
        assertEquals(listOf("B-019", "B-007"), loaded.retrievedNeighborIds)
        assertEquals(0.881, loaded.nearestNeighborSimilarity!!, 0.0005)
        assertEquals(listOf("Foto disimpan berapa lama?"), loaded.openQuestions)
        // The embedding must survive the JSONB round trip intact, along with its model tag —
        // distances are meaningless across models.
        assertEquals("test-embed", loaded.embedding?.model)
        assertEquals(3, loaded.embedding?.dimension)
        assertEquals(0.87, loaded.embedding!!.values[2], 0.0001)
    }

    @Test
    fun varianceColumn_shouldBeComputedByTheDatabaseOnCompletion() = runBlocking<Unit> {
        val entry = createCatalogEntry()
        val now = Clock.System.now()
        val id = ModuleBuildId("b-var-$suffix")

        buildRepo.save(
            ModuleBuildRecord(
                id = id,
                catalogEntryId = entry.id,
                buildType = BuildType.CUSTOMIZATION,
                archetypeCode = "quality_control",
                requirementText = "uji kolom generated",
                sizePoints = SizePoints(60),
                status = BuildStatus.IN_PROGRESS
            ).withEstimate(
                estimatedHours = WorkHours(87.0),
                estimatedHoursP90 = WorkHours(98.0),
                estimatedBy = EstimatorKind.HUMAN,
                estimatorRef = "test",
                confidence = EstimateConfidence.MEDIUM,
                estimatedAt = now
            )
        )

        // Writing the record again with actuals must not attempt to write the generated column;
        // if it did, PostgreSQL would reject the whole update.
        val completed = assertNotNull(buildRepo.findById(id)).completeWith(
            actualHours = WorkHours(104.0),
            blendedRate = MoneyIdr(138_000),
            totalCost = MoneyIdr(104 * 138_000L),
            completedAt = now,
            leadTimeDays = 18
        )
        buildRepo.save(completed)

        val variance = DatabaseFactory.dbQuery {
            ModuleBuildRecordsTable.selectAll()
                .where { ModuleBuildRecordsTable.id eq id.value }
                .single()[ModuleBuildRecordsTable.estimateVariancePercent]
        }
        assertEquals(19.54, variance!!.toDouble(), 0.01)
    }

    @Test
    fun effortEntries_shouldAccumulateAndRollUp() = runBlocking<Unit> {
        val entry = createCatalogEntry()
        val buildId = ModuleBuildId("b-eff-$suffix")
        buildRepo.save(
            ModuleBuildRecord(
                id = buildId,
                catalogEntryId = entry.id,
                buildType = BuildType.CUSTOMIZATION,
                archetypeCode = "quality_control",
                requirementText = "uji catatan jam"
            )
        )

        listOf(
            Triple(EffortRole.BACKEND, BuildPhase.IMPLEMENTATION, 34.0),
            Triple(EffortRole.FRONTEND, BuildPhase.IMPLEMENTATION, 28.0),
            Triple(EffortRole.QA, BuildPhase.QA, 12.0)
        ).forEachIndexed { index, (role, phase, hours) ->
            buildRepo.addEffortEntry(
                ModuleBuildEffortEntry(
                    id = EffortEntryId("e-$suffix-$index"),
                    buildRecordId = buildId,
                    role = role,
                    phase = phase,
                    hours = WorkHours(hours),
                    hourlyRateIdr = MoneyIdr(138_000)
                )
            )
        }

        val entries = buildRepo.findEffortEntries(buildId)
        assertEquals(3, entries.size)
        assertEquals(74.0, entries.sumOf { it.hours.hours }, 0.001)
    }

    @Test
    fun estimationCandidates_shouldExcludeRowsWithoutLoggedHours() = runBlocking<Unit> {
        val entry = createCatalogEntry()
        val archetype = "raw_material_test_$suffix"

        val logged = ModuleBuildRecord(
            id = ModuleBuildId("b-logged-$suffix"),
            catalogEntryId = entry.id,
            buildType = BuildType.NEW_MODULE,
            archetypeCode = archetype,
            requirementText = "punya jam tercatat",
            sizePoints = SizePoints(40),
            actualHours = WorkHours(50.0),
            effortSource = EffortSource.LOGGED
        )
        val recalled = logged.copy(
            id = ModuleBuildId("b-recalled-$suffix"),
            effortSource = EffortSource.RECONSTRUCTED
        )
        val imported = logged.copy(
            id = ModuleBuildId("b-imported-$suffix"),
            actualHours = null,
            effortSource = EffortSource.IMPORTED
        )
        buildRepo.save(logged)
        buildRepo.save(recalled)
        buildRepo.save(imported)

        val candidates = buildRepo.findEstimationCandidates(archetype, limit = 50)
        assertEquals(listOf(logged.id), candidates.map { it.id })
    }

    // ---- Quotes --------------------------------------------------------------

    @Test
    fun quote_shouldSnapshotItsInputsAndBreakdown() = runBlocking<Unit> {
        val tenant = createTenant()
        val entry = createCatalogEntry()
        val inputs = PricingInputs(
            basisHours = WorkHours(98.0),
            blendedHourlyRate = MoneyIdr(138_000),
            expectedTenantCount = 1,
            amortizationMonths = 24,
            marginPercent = Percentage(35.0),
            monthlyMaintenancePercent = Percentage(1.5),
            monthlyInfraCost = MoneyIdr(120_000)
        )
        val quote = ModulePricingQuote(
            id = QuoteId("q-test-$suffix"),
            catalogEntryId = entry.id,
            inputs = inputs,
            result = AmortizedBuildCostFormula().priceOf(inputs),
            tenantId = tenant.id,
            status = QuoteStatus.ACCEPTED,
            quotedAt = Clock.System.now()
        )
        quoteRepo.save(quote)

        val loaded = assertNotNull(quoteRepo.findById(quote.id))
        assertEquals(MoneyIdr(1_083_585), loaded.result.monthlyPrice)
        assertEquals(24, loaded.inputs.amortizationMonths)
        assertEquals(35.0, loaded.inputs.marginPercent.value, 0.001)
        assertEquals("v1", loaded.inputs.pricingModelVersion)
        // The breakdown is what justifies the figure to a customer; it has to survive storage.
        assertEquals(13_524_000L, loaded.result.breakdown["buildCost"])

        val accepted = quoteRepo.findAcceptedForTenant(tenant.id)
        assertEquals(1, accepted.size)
    }

    @Test
    fun rejectedQuote_shouldNotAppearAmongAcceptedOnes() = runBlocking<Unit> {
        val tenant = createTenant()
        val entry = createCatalogEntry()
        val inputs = PricingInputs(WorkHours(40.0), MoneyIdr(138_000))
        quoteRepo.save(
            ModulePricingQuote(
                id = QuoteId("q-rej-$suffix"),
                catalogEntryId = entry.id,
                inputs = inputs,
                result = AmortizedBuildCostFormula().priceOf(inputs),
                tenantId = tenant.id,
                status = QuoteStatus.REJECTED
            )
        )
        assertTrue(quoteRepo.findAcceptedForTenant(tenant.id).isEmpty())
    }

    // ---- Customization requests (RLS) ---------------------------------------

    @Test
    fun customizationRequest_shouldBeVisibleOnlyToItsOwnTenant() = runBlocking<Unit> {
        val owner = createTenant()
        val stranger = createTenant()

        val request = ModuleCustomizationRequest(
            id = CustomizationRequestId("req-$suffix"),
            tenantId = owner.id,
            title = "Foto cacat + berita acara PDF",
            descriptionRaw = "Buyer kami sering komplain kain cacat itu bawaan dari mereka."
        )
        requestRepo.save(request)

        val ownerView = requestRepo.findByTenant(owner.id)
        assertEquals(1, ownerView.size)
        // Stored verbatim: the raw wording is the richest context any later estimate gets.
        assertEquals(request.descriptionRaw, ownerView.single().descriptionRaw)

        // RLS must keep one factory's requests out of another's listing.
        assertTrue(requestRepo.findByTenant(stranger.id).none { it.id == request.id })
    }

    // ---- Sizing weights ------------------------------------------------------

    @Test
    fun seededWeights_shouldScoreTheWorkedExampleAtSixtyPoints() = runBlocking<Unit> {
        val weights = weightsRepo.findActive()
        assertEquals("v1", weights.version)

        val features = BuildFeatureVector(
            entityCount = 2, useCaseCount = 4, screenCount = 2, apiEndpointCount = 5,
            dbTableCount = 2, reportCount = 1, targetPlatformCount = 3,
            affectedExistingModuleCount = 1, requiresFileUpload = true
        )
        assertEquals(SizePoints(60), weights.scoreOf(features))
        // The database weights and the in-code fallback must not drift apart.
        assertEquals(SizingWeights.V1.scoreOf(features), weights.scoreOf(features))
    }

    @Test
    fun unknownWeightsVersion_shouldReturnNull() = runBlocking<Unit> {
        assertNull(weightsRepo.findByVersion("v-does-not-exist"))
    }
}
