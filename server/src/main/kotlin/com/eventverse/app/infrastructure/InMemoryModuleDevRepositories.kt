package com.eventverse.app.infrastructure

import com.eventverse.app.domain.moduledev.CustomizationRequestId
import com.eventverse.app.domain.moduledev.EmbeddingProvider
import com.eventverse.app.domain.moduledev.EmbeddingVector
import com.eventverse.app.domain.moduledev.ModuleBuildEffortEntry
import com.eventverse.app.domain.moduledev.ModuleBuildId
import com.eventverse.app.domain.moduledev.ModuleBuildRecord
import com.eventverse.app.domain.moduledev.ModuleBuildRepository
import com.eventverse.app.domain.moduledev.ModuleCatalogEntry
import com.eventverse.app.domain.moduledev.ModuleCatalogEntryId
import com.eventverse.app.domain.moduledev.ModuleCatalogRepository
import com.eventverse.app.domain.moduledev.ModuleCustomizationRequest
import com.eventverse.app.domain.moduledev.ModuleCustomizationRequestRepository
import com.eventverse.app.domain.moduledev.ModulePricingQuote
import com.eventverse.app.domain.moduledev.ModulePricingQuoteRepository
import com.eventverse.app.domain.moduledev.QuoteId
import com.eventverse.app.domain.moduledev.SizingWeights
import com.eventverse.app.domain.moduledev.SizingWeightsRepository
import com.eventverse.app.domain.moduledev.EffortSource
import com.eventverse.app.domain.tenant.TenantId
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory twins of the module development ledger repositories.
 *
 * Test doubles only, kept beside the Postgres implementations like the other `InMemory*`
 * repositories here so API tests can exercise the routes without a database.
 *
 * **They do not enforce RLS.** [InMemoryModuleCustomizationRequestRepository] filters by tenant in
 * code, which mimics the effect but not the guarantee — isolation itself is proved against a real
 * server in `PostgresModuleDevRepositoryIntegrationTest`.
 */

class InMemoryModuleCatalogRepository(
    entries: List<ModuleCatalogEntry> = emptyList()
) : ModuleCatalogRepository {
    private val store = ConcurrentHashMap<String, ModuleCatalogEntry>().apply {
        entries.forEach { put(it.id.value, it) }
    }

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

class InMemoryModuleBuildRepository(
    records: List<ModuleBuildRecord> = emptyList()
) : ModuleBuildRepository {
    private val store = ConcurrentHashMap<String, ModuleBuildRecord>().apply {
        records.forEach { put(it.id.value, it) }
    }
    private val effort = mutableListOf<ModuleBuildEffortEntry>()

    override suspend fun findById(id: ModuleBuildId): ModuleBuildRecord? = store[id.value]

    /** Mirrors the SQL filter: same archetype, real logged hours, nothing else. */
    override suspend fun findEstimationCandidates(
        archetypeCode: String,
        limit: Int
    ): List<ModuleBuildRecord> = store.values
        .filter {
            it.archetypeCode == archetypeCode &&
                it.actualHours != null &&
                it.effortSource == EffortSource.LOGGED
        }
        .take(limit)

    override suspend fun findByCatalogEntry(
        catalogEntryId: ModuleCatalogEntryId
    ): List<ModuleBuildRecord> = store.values.filter { it.catalogEntryId == catalogEntryId }

    override suspend fun findEffortEntries(buildId: ModuleBuildId): List<ModuleBuildEffortEntry> =
        synchronized(effort) { effort.filter { it.buildRecordId == buildId } }

    override suspend fun save(record: ModuleBuildRecord) {
        store[record.id.value] = record
    }

    override suspend fun addEffortEntry(entry: ModuleBuildEffortEntry) {
        synchronized(effort) { effort += entry }
    }
}

class InMemoryModulePricingQuoteRepository : ModulePricingQuoteRepository {
    private val store = ConcurrentHashMap<String, ModulePricingQuote>()

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

class InMemoryModuleCustomizationRequestRepository : ModuleCustomizationRequestRepository {
    private val store = ConcurrentHashMap<String, ModuleCustomizationRequest>()

    override suspend fun findById(id: CustomizationRequestId): ModuleCustomizationRequest? =
        store[id.value]

    override suspend fun findByTenant(tenantId: TenantId): List<ModuleCustomizationRequest> =
        store.values.filter { it.tenantId == tenantId }

    override suspend fun save(request: ModuleCustomizationRequest) {
        store[request.id.value] = request
    }
}

class InMemorySizingWeightsRepository(
    private val active: SizingWeights = SizingWeights.V1
) : SizingWeightsRepository {
    private val store = ConcurrentHashMap<String, SizingWeights>().apply {
        put(active.version, active)
    }

    override suspend fun findActive(): SizingWeights = active

    override suspend fun findByVersion(version: String): SizingWeights? = store[version]

    override suspend fun save(weights: SizingWeights) {
        store[weights.version] = weights
    }
}

/**
 * A fixed embedding for every text, so retrieval always matches.
 *
 * Useful for tests about what happens *after* retrieval — logging effort, closing a build, pricing
 * it — where varying similarity would only add noise. Tests about the similarity gate itself must
 * use [LexicalEmbeddingProvider], which actually distinguishes texts.
 */
class ConstantEmbeddingProvider(
    override val model: String = "constant-test-embed"
) : EmbeddingProvider {
    override suspend fun embed(text: String): EmbeddingVector =
        EmbeddingVector(listOf(1.0, 0.0, 0.0), model = model)
}
