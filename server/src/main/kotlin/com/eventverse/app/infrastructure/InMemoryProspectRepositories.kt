package com.eventverse.app.infrastructure

import com.eventverse.app.domain.prospect.FlowTranslation
import com.eventverse.app.domain.prospect.FlowTranslationId
import com.eventverse.app.domain.prospect.FlowTranslationRepository
import com.eventverse.app.domain.prospect.LeadStatus
import com.eventverse.app.domain.prospect.ProspectLead
import com.eventverse.app.domain.prospect.ProspectLeadId
import com.eventverse.app.domain.prospect.ProspectLeadRepository
import com.eventverse.app.domain.prospect.ProspectPriceEstimateId
import com.eventverse.app.domain.prospect.ProspectPriceEstimateRepository
import com.eventverse.app.domain.prospect.StoredPriceEstimate
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory twins of the prospect repositories, kept beside the Postgres ones like every other
 * `InMemory*` repository here so API tests can run without a database.
 */

class InMemoryProspectLeadRepository(
    leads: List<ProspectLead> = emptyList()
) : ProspectLeadRepository {
    private val store = ConcurrentHashMap<String, ProspectLead>().apply {
        leads.forEach { put(it.id.value, it) }
    }

    override suspend fun findById(id: ProspectLeadId): ProspectLead? = store[id.value]

    override suspend fun findByStatus(status: LeadStatus, limit: Int): List<ProspectLead> =
        store.values.filter { it.status == status }.take(limit)

    override suspend fun findRecent(limit: Int): List<ProspectLead> = store.values.take(limit)

    override suspend fun save(lead: ProspectLead) {
        store[lead.id.value] = lead
    }
}

class InMemoryFlowTranslationRepository : FlowTranslationRepository {
    private val store = ConcurrentHashMap<String, FlowTranslation>()

    override suspend fun findById(id: FlowTranslationId): FlowTranslation? = store[id.value]

    override suspend fun findByLead(leadId: ProspectLeadId): List<FlowTranslation> =
        store.values.filter { it.leadId == leadId }

    override suspend fun save(translation: FlowTranslation) {
        store[translation.id.value] = translation
    }
}

class InMemoryProspectPriceEstimateRepository : ProspectPriceEstimateRepository {
    private val store = ConcurrentHashMap<String, StoredPriceEstimate>()

    override suspend fun findById(id: ProspectPriceEstimateId): StoredPriceEstimate? =
        store[id.value]

    override suspend fun findByLead(leadId: ProspectLeadId): List<StoredPriceEstimate> =
        store.values.filter { it.leadId == leadId }

    override suspend fun save(estimate: StoredPriceEstimate) {
        store[estimate.id.value] = estimate
    }
}
