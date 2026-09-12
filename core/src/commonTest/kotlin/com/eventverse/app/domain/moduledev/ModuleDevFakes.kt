package com.eventverse.app.domain.moduledev

import com.eventverse.app.domain.pipeline.CustomTenantPipeline
import com.eventverse.app.domain.pipeline.TenantPipelineRepository
import com.eventverse.app.domain.tenant.TenantId
import kotlin.math.abs

/**
 * Test doubles for the module development ledger.
 *
 * Plain maps rather than mocks, so the use-case tests exercise real domain behaviour and only the
 * storage is swapped out.
 */

class FakeModuleCatalogRepository(
    entries: List<ModuleCatalogEntry> = emptyList()
) : ModuleCatalogRepository {
    private val store = entries.associateBy { it.id.value }.toMutableMap()

    override suspend fun findById(id: ModuleCatalogEntryId): ModuleCatalogEntry? = store[id.value]

    override suspend fun findByModuleId(moduleId: String): ModuleCatalogEntry? =
        store.values.firstOrNull { it.moduleId == moduleId }

    override suspend fun findAll(): List<ModuleCatalogEntry> = store.values.toList()

    override suspend fun findBillable(): List<ModuleCatalogEntry> =
        store.values.filter { it.isBillable }

    override suspend fun save(entry: ModuleCatalogEntry) {
        store[entry.id.value] = entry
    }
}

class FakeModuleBuildRepository(
    records: List<ModuleBuildRecord> = emptyList()
) : ModuleBuildRepository {
    private val store = records.associateBy { it.id.value }.toMutableMap()
    private val effort = mutableListOf<ModuleBuildEffortEntry>()

    override suspend fun findById(id: ModuleBuildId): ModuleBuildRecord? = store[id.value]

    override suspend fun findEstimationCandidates(
        archetypeCode: String,
        limit: Int
    ): List<ModuleBuildRecord> = store.values
        .filter { it.archetypeCode == archetypeCode && it.embedding != null }
        .take(limit)

    override suspend fun findByCatalogEntry(
        catalogEntryId: ModuleCatalogEntryId
    ): List<ModuleBuildRecord> = store.values.filter { it.catalogEntryId == catalogEntryId }

    override suspend fun findEffortEntries(buildId: ModuleBuildId): List<ModuleBuildEffortEntry> =
        effort.filter { it.buildRecordId == buildId }

    override suspend fun save(record: ModuleBuildRecord) {
        store[record.id.value] = record
    }

    override suspend fun addEffortEntry(entry: ModuleBuildEffortEntry) {
        effort += entry
    }
}

class FakeModulePricingQuoteRepository : ModulePricingQuoteRepository {
    private val store = mutableMapOf<String, ModulePricingQuote>()

    override suspend fun findById(id: QuoteId): ModulePricingQuote? = store[id.value]

    override suspend fun findByCatalogEntry(
        catalogEntryId: ModuleCatalogEntryId
    ): List<ModulePricingQuote> = store.values.filter { it.catalogEntryId == catalogEntryId }

    override suspend fun findAcceptedForTenant(tenantId: TenantId): List<ModulePricingQuote> =
        store.values.filter { it.tenantId == tenantId && it.isBillable }

    override suspend fun save(quote: ModulePricingQuote) {
        store[quote.id.value] = quote
    }
}

class FakeModuleCustomizationRequestRepository : ModuleCustomizationRequestRepository {
    private val store = mutableMapOf<String, ModuleCustomizationRequest>()

    override suspend fun findById(id: CustomizationRequestId): ModuleCustomizationRequest? =
        store[id.value]

    override suspend fun findByTenant(tenantId: TenantId): List<ModuleCustomizationRequest> =
        store.values.filter { it.tenantId == tenantId }

    override suspend fun save(request: ModuleCustomizationRequest) {
        store[request.id.value] = request
    }
}

class FakeSizingWeightsRepository(
    private val active: SizingWeights = SizingWeights.V1
) : SizingWeightsRepository {
    private val store = mutableMapOf(active.version to active)

    override suspend fun findActive(): SizingWeights = active

    override suspend fun findByVersion(version: String): SizingWeights? = store[version]

    override suspend fun save(weights: SizingWeights) {
        store[weights.version] = weights
    }
}

class FakeTenantPipelineRepository(
    private val pipelines: MutableMap<String, CustomTenantPipeline> = mutableMapOf()
) : TenantPipelineRepository {
    override suspend fun findByTenantId(tenantId: TenantId): CustomTenantPipeline? =
        pipelines[tenantId.value]

    override suspend fun save(pipeline: CustomTenantPipeline): Result<CustomTenantPipeline> {
        pipelines[pipeline.tenantId.value] = pipeline
        return Result.success(pipeline)
    }

    override suspend fun deleteByTenantId(tenantId: TenantId): Result<Unit> {
        pipelines.remove(tenantId.value)
        return Result.success(Unit)
    }
}

/**
 * A deterministic stand-in for a real embedding model.
 *
 * Hashes words into a fixed number of buckets, so texts sharing vocabulary genuinely land near
 * each other and unrelated texts do not. That is enough to exercise retrieval end to end — the
 * filtering, the similarity gate, the neighbour ordering — without a network call, and it keeps
 * the tests deterministic in a way a real model would not.
 */
class FakeEmbeddingProvider(
    override val model: String = "fake-hash-embed",
    private val dimension: Int = 64
) : EmbeddingProvider {

    override suspend fun embed(text: String): EmbeddingVector {
        val buckets = DoubleArray(dimension)
        text.lowercase()
            .split(*DELIMITERS)
            .filter { it.length > 2 }
            .forEach { token ->
                buckets[abs(token.hashCode()) % dimension] += 1.0
            }
        // A text of only short words would otherwise produce a zero vector, which has no direction
        // and would make every similarity undefined.
        if (buckets.all { it == 0.0 }) buckets[0] = 1.0
        return EmbeddingVector(buckets.toList(), model = model)
    }

    private companion object {
        val DELIMITERS = arrayOf(" ", ",", ".", ";", ":", "\n", "\t", "/", "(", ")", "-")
    }
}
