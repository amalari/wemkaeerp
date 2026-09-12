package com.eventverse.app.domain.moduledev

import com.eventverse.app.domain.tenant.TenantId

/**
 * Repository contracts for the module development ledger.
 *
 * Grouped in one file because they are read together and share a vocabulary; each is narrow and
 * domain-shaped rather than a generic DAO, per the project's repository rules.
 */

interface ModuleCatalogRepository {
    suspend fun findById(id: ModuleCatalogEntryId): ModuleCatalogEntry?

    suspend fun findByModuleId(moduleId: String): ModuleCatalogEntry?

    suspend fun findAll(): List<ModuleCatalogEntry>

    /** The entries a billing run may charge for: released, and priced. */
    suspend fun findBillable(): List<ModuleCatalogEntry>

    suspend fun save(entry: ModuleCatalogEntry)
}

interface ModuleBuildRepository {
    suspend fun findById(id: ModuleBuildId): ModuleBuildRecord?

    /**
     * Candidate neighbours for an estimate.
     *
     * Filtering by archetype before similarity is deliberate: without it, a sewing build surfaces
     * as a neighbour of a costing build for sharing vocabulary, and the borrowed productivity is
     * meaningless. Only rows that can lend a productivity ratio are returned.
     */
    suspend fun findEstimationCandidates(archetypeCode: String, limit: Int = 50): List<ModuleBuildRecord>

    suspend fun findByCatalogEntry(catalogEntryId: ModuleCatalogEntryId): List<ModuleBuildRecord>

    suspend fun findEffortEntries(buildId: ModuleBuildId): List<ModuleBuildEffortEntry>

    suspend fun save(record: ModuleBuildRecord)

    suspend fun addEffortEntry(entry: ModuleBuildEffortEntry)
}

interface ModulePricingQuoteRepository {
    suspend fun findById(id: QuoteId): ModulePricingQuote?

    suspend fun findByCatalogEntry(catalogEntryId: ModuleCatalogEntryId): List<ModulePricingQuote>

    /** Accepted quotes for one tenant — the custom half of that tenant's monthly bill. */
    suspend fun findAcceptedForTenant(tenantId: TenantId): List<ModulePricingQuote>

    suspend fun save(quote: ModulePricingQuote)
}

interface ModuleCustomizationRequestRepository {
    suspend fun findById(id: CustomizationRequestId): ModuleCustomizationRequest?

    suspend fun findByTenant(tenantId: TenantId): List<ModuleCustomizationRequest>

    suspend fun save(request: ModuleCustomizationRequest)
}

interface SizingWeightsRepository {
    /** The version currently used to score new work. */
    suspend fun findActive(): SizingWeights

    suspend fun findByVersion(version: String): SizingWeights?

    suspend fun save(weights: SizingWeights)
}

/**
 * Turns requirement prose into an embedding.
 *
 * An interface in the domain with its implementation in infrastructure, so the domain never
 * learns about HTTP or a vendor. Also lets tests run the whole estimation path on a
 * deterministic stub instead of a paid network call.
 */
interface EmbeddingProvider {
    val model: String

    suspend fun embed(text: String): EmbeddingVector
}
