package com.eventverse.app.domain.discovery.usecases

import com.eventverse.app.domain.discovery.DeterministicDiscoveryAgent
import com.eventverse.app.domain.discovery.DiscoveryRequest
import com.eventverse.app.domain.moduledev.EmbeddingVector
import com.eventverse.app.domain.moduledev.EmbeddingProvider
import com.eventverse.app.domain.moduledev.MoneyIdr
import com.eventverse.app.domain.moduledev.ModuleBuildEffortEntry
import com.eventverse.app.domain.moduledev.ModuleBuildId
import com.eventverse.app.domain.moduledev.ModuleBuildRecord
import com.eventverse.app.domain.moduledev.ModuleBuildRepository
import com.eventverse.app.domain.moduledev.ModuleCatalogEntry
import com.eventverse.app.domain.moduledev.ModuleCatalogEntryId
import com.eventverse.app.domain.moduledev.ModuleCatalogRepository
import com.eventverse.app.domain.moduledev.ModuleLifecycleStatus
import com.eventverse.app.domain.moduledev.Percentage
import com.eventverse.app.domain.moduledev.SizingWeights
import com.eventverse.app.domain.moduledev.SizingWeightsRepository
import com.eventverse.app.domain.prospect.usecases.PriceProspectFlowUseCase
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Plan §3 B1: estimasi dari draf. Draf **disintesis** menjadi CoverageAnalysis lalu didelegasikan ke
 * [PriceProspectFlowUseCase] — satu sumber rumus harga; mesin estimator itu sendiri dites di modul
 * prospect. Yang dites di sini: pemisahan covered/new, langganan tepat, penahanan jujur ketika belum ada
 * data historis, dan bobot fitur yang merespons jumlah layar.
 */
class PriceDiscoveryDraftUseCaseTest {

    private class FakeCatalog(private val billable: List<ModuleCatalogEntry>) : ModuleCatalogRepository {
        override suspend fun findById(id: ModuleCatalogEntryId) = null
        override suspend fun findByModuleId(moduleId: String) = billable.firstOrNull { it.moduleId == moduleId }
        override suspend fun findAll() = billable
        override suspend fun findBillable() = billable
        override suspend fun save(entry: ModuleCatalogEntry) {}
    }
    // PART2

    private object NoHistory : ModuleBuildRepository {
        override suspend fun findById(id: ModuleBuildId): ModuleBuildRecord? = null
        override suspend fun findEstimationCandidates(archetypeCode: String, limit: Int) = emptyList<ModuleBuildRecord>()
        override suspend fun findByCatalogEntry(catalogEntryId: ModuleCatalogEntryId) = emptyList<ModuleBuildRecord>()
        override suspend fun findEffortEntries(buildId: ModuleBuildId) = emptyList<ModuleBuildEffortEntry>()
        override suspend fun save(record: ModuleBuildRecord) {}
        override suspend fun addEffortEntry(entry: ModuleBuildEffortEntry) {}
    }

    private object StaticWeights : SizingWeightsRepository {
        override suspend fun findActive() = SizingWeights.V1
        override suspend fun findByVersion(version: String) = null
        override suspend fun save(weights: SizingWeights) {}
    }

    private object ConstantEmbedding : EmbeddingProvider {
        override val model = "test-constant"
        override suspend fun embed(text: String) = EmbeddingVector(listOf(1.0), model)
    }

    private val priceProspect = PriceProspectFlowUseCase(
        buildRepository = NoHistory, sizingWeightsRepository = StaticWeights,
        embeddingProvider = ConstantEmbedding, defaultBlendedHourlyRate = MoneyIdr(250_000)
    )

    private fun entry(moduleId: String, archetypeCode: String, price: Long) = ModuleCatalogEntry(
        id = ModuleCatalogEntryId("cat-$moduleId"), moduleId = moduleId, archetypeCode = archetypeCode,
        displayName = moduleId, lifecycleStatus = ModuleLifecycleStatus.RELEASED, baseMonthlyPriceIdr = MoneyIdr(price)
    )

    @Test
    fun `modul yang sudah dibangun jadi langganan, sisanya gap yang ditahan jujur`() = runTest {
        val draft = DeterministicDiscoveryAgent().draft(
            DiscoveryRequest("Kami klinik dengan antrean pasien dan tagihan kasir.", industryHint = "klinik")
        ).getOrThrow()
        val built = draft.pack.modules.first()
        // Plugin kustom ini pernah dibangun untuk klien lain → masuk katalog billable → langganan.
        val pricing = PriceDiscoveryDraftUseCase(
            billableCatalog = { listOf(entry(built.id.value, built.slot?.value ?: "governance", 750_000)) },
            priceProspectFlow = priceProspect
        )(draft, Percentage(35.0)).getOrThrow()

        assertEquals(listOf(built.id.value), pricing.coveredModuleIds)
        assertTrue(pricing.newModuleIds.size == draft.pack.modules.size - 1)
        assertEquals(MoneyIdr(750_000), pricing.pricing.range.subscriptionMonthly)
        // Tanpa build historis sejenis, rentang ditahan — bukan dikarang (filosofi PriceProspectFlow).
        assertTrue(!pricing.pricing.range.isPublishable)
        assertTrue(pricing.pricing.range.unpriceableGapCount >= 1)
    }

    @Test
    fun `draf garment yang sepenuhnya tercakup dihargai tepat tanpa gap`() = runTest {
        val draft = DeterministicDiscoveryAgent().draft(
            DiscoveryRequest("Konveksi brand sendiri untuk distro retail.")
        ).getOrThrow()
        val entries = draft.pack.modules.map { entry(it.id.value, it.slot?.value ?: "governance", 100_000) }
        val pricing = PriceDiscoveryDraftUseCase({ entries }, priceProspect)(draft, Percentage(35.0)).getOrThrow()

        assertTrue(pricing.isFullyCovered)
        assertTrue(pricing.pricing.range.isPublishable)
        assertEquals(MoneyIdr(100_000L * draft.pack.modules.size), pricing.pricing.range.subscriptionMonthly)
    }

    @Test
    fun `layar tambahan menaikkan skor sizing modul baru`() {
        val one = PriceDiscoveryDraftUseCase.featuresFor(1)
        val three = PriceDiscoveryDraftUseCase.featuresFor(3)
        assertEquals(1, one.screenCount)
        assertEquals(3, three.screenCount)
        assertTrue(SizingWeights.V1.scoreOf(three).value > SizingWeights.V1.scoreOf(one).value)
    }

    private fun runTest(block: suspend () -> Unit) = kotlinx.coroutines.test.runTest { block() }
}
