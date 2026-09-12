package com.eventverse.app.domain.prospect

/**
 * Repository contracts and the translator port for the prospect flow.
 *
 * Grouped in one file because they are read together and share a vocabulary; each is narrow and
 * domain-shaped rather than a generic DAO.
 */

interface ProspectLeadRepository {
    suspend fun findById(id: ProspectLeadId): ProspectLead?

    /** Newest first. The review queue. */
    suspend fun findByStatus(status: LeadStatus, limit: Int = 50): List<ProspectLead>

    suspend fun findRecent(limit: Int = 50): List<ProspectLead>

    suspend fun save(lead: ProspectLead)
}

interface FlowTranslationRepository {
    suspend fun findById(id: FlowTranslationId): FlowTranslation?

    /**
     * All translations of one lead, newest first.
     *
     * Plural because re-translating writes a new row rather than overwriting: what we told a
     * prospect, and which model said it, stays answerable after the fact.
     */
    suspend fun findByLead(leadId: ProspectLeadId): List<FlowTranslation>

    suspend fun save(translation: FlowTranslation)
}

interface ProspectPriceEstimateRepository {
    suspend fun findById(id: ProspectPriceEstimateId): StoredPriceEstimate?

    suspend fun findByLead(leadId: ProspectLeadId): List<StoredPriceEstimate>

    suspend fun save(estimate: StoredPriceEstimate)
}

/**
 * A [ProspectPriceRange] with its identity and provenance, as persisted.
 *
 * Separate from the range itself so the domain calculation stays free of storage concerns and can
 * be unit-tested without inventing ids.
 */
data class StoredPriceEstimate(
    val id: ProspectPriceEstimateId,
    val leadId: ProspectLeadId,
    val translationId: FlowTranslationId,
    val range: ProspectPriceRange,
    val marginPercent: Double,
    val computedAt: kotlinx.datetime.Instant? = null
)

/**
 * Turns a prospect's narrative into capability requirements.
 *
 * An interface in the domain with its implementation in infrastructure, mirroring
 * [com.eventverse.app.domain.moduledev.EmbeddingProvider]: the domain never learns about HTTP or a
 * vendor, and the whole path stays testable on a deterministic stub instead of a paid, non-repeatable
 * network call.
 *
 * Implementations return a [FlowTranslationDraft] of plain strings and are assumed to be wrong
 * sometimes; validating the output is the caller's job, not theirs.
 */
interface FlowTranslator {
    /** `'<model>/<prompt-version>'`, recorded on every translation it produces. */
    val translatorRef: String

    suspend fun translate(narrative: String): Result<FlowTranslationDraft>
}
