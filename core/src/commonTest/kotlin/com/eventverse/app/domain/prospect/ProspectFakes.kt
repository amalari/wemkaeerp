package com.eventverse.app.domain.prospect

/**
 * Test doubles for the prospect flow. Plain maps, so the use-case tests exercise real domain
 * behaviour and only storage is swapped out.
 */

class FakeProspectLeadRepository(
    leads: List<ProspectLead> = emptyList()
) : ProspectLeadRepository {
    private val store = leads.associateBy { it.id.value }.toMutableMap()

    override suspend fun findById(id: ProspectLeadId): ProspectLead? = store[id.value]

    override suspend fun findByStatus(status: LeadStatus, limit: Int): List<ProspectLead> =
        store.values.filter { it.status == status }.take(limit)

    override suspend fun findRecent(limit: Int): List<ProspectLead> = store.values.take(limit)

    override suspend fun save(lead: ProspectLead) {
        store[lead.id.value] = lead
    }
}

class FakeFlowTranslationRepository : FlowTranslationRepository {
    private val store = mutableMapOf<String, FlowTranslation>()

    override suspend fun findById(id: FlowTranslationId): FlowTranslation? = store[id.value]

    override suspend fun findByLead(leadId: ProspectLeadId): List<FlowTranslation> =
        store.values.filter { it.leadId == leadId }

    override suspend fun save(translation: FlowTranslation) {
        store[translation.id.value] = translation
    }
}

class FakeProspectPriceEstimateRepository : ProspectPriceEstimateRepository {
    private val store = mutableMapOf<String, StoredPriceEstimate>()

    override suspend fun findById(id: ProspectPriceEstimateId): StoredPriceEstimate? = store[id.value]

    override suspend fun findByLead(leadId: ProspectLeadId): List<StoredPriceEstimate> =
        store.values.filter { it.leadId == leadId }

    override suspend fun save(estimate: StoredPriceEstimate) {
        store[estimate.id.value] = estimate
    }
}

/**
 * Returns whatever the test hands it, unchanged.
 *
 * The point of these tests is what happens to a translator's output — including output that is
 * wrong — so the translator itself must be fully controllable rather than merely plausible.
 */
class StubFlowTranslator(
    private val draft: FlowTranslationDraft,
    override val translatorRef: String = draft.translatorRef
) : FlowTranslator {
    override suspend fun translate(narrative: String): Result<FlowTranslationDraft> =
        Result.success(draft)
}

/** A translator that fails, to check the failure does not leave a half-written lead behind. */
class FailingFlowTranslator(
    override val translatorRef: String = "failing/v1"
) : FlowTranslator {
    override suspend fun translate(narrative: String): Result<FlowTranslationDraft> =
        Result.failure(IllegalStateException("model tidak merespons"))
}
