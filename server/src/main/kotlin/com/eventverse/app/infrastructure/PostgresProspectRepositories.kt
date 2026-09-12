package com.eventverse.app.infrastructure

import com.eventverse.app.domain.moduledev.MoneyIdr
import com.eventverse.app.domain.pipeline.GarmentBusinessPreset
import com.eventverse.app.domain.prospect.FlowTranslation
import com.eventverse.app.domain.prospect.FlowTranslationId
import com.eventverse.app.domain.prospect.FlowTranslationRepository
import com.eventverse.app.domain.prospect.LeadSource
import com.eventverse.app.domain.prospect.LeadStatus
import com.eventverse.app.domain.prospect.ProspectLead
import com.eventverse.app.domain.prospect.ProspectLeadId
import com.eventverse.app.domain.prospect.ProspectLeadRepository
import com.eventverse.app.domain.prospect.ProspectPriceEstimateId
import com.eventverse.app.domain.prospect.ProspectPriceEstimateRepository
import com.eventverse.app.domain.prospect.ProspectPriceRange
import com.eventverse.app.domain.prospect.StoredPriceEstimate
import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.infrastructure.tables.ProspectFlowTranslationsTable
import com.eventverse.app.infrastructure.tables.ProspectLeadsTable
import com.eventverse.app.infrastructure.tables.ProspectPriceEstimatesTable
import com.eventverse.app.shared.prospect.ProspectCodec
import kotlinx.datetime.Clock
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update

/**
 * Prospect persistence. Every query runs without a tenant context — these tables carry no
 * `tenant_id` and have no RLS policy to satisfy.
 */
class PostgresProspectLeadRepository : ProspectLeadRepository {

    override suspend fun findById(id: ProspectLeadId): ProspectLead? = DatabaseFactory.dbQuery {
        ProspectLeadsTable.selectAll()
            .where { ProspectLeadsTable.id eq id.value }
            .map(::toLead)
            .singleOrNull()
    }

    override suspend fun findByStatus(status: LeadStatus, limit: Int): List<ProspectLead> =
        DatabaseFactory.dbQuery {
            ProspectLeadsTable.selectAll()
                .where { ProspectLeadsTable.status eq status.code }
                .orderBy(ProspectLeadsTable.submittedAt, SortOrder.DESC)
                .limit(limit)
                .map(::toLead)
        }

    override suspend fun findRecent(limit: Int): List<ProspectLead> = DatabaseFactory.dbQuery {
        ProspectLeadsTable.selectAll()
            .orderBy(ProspectLeadsTable.submittedAt, SortOrder.DESC)
            .limit(limit)
            .map(::toLead)
    }

    override suspend fun save(lead: ProspectLead) {
        DatabaseFactory.dbQuery {
            val updated = ProspectLeadsTable.update({ ProspectLeadsTable.id eq lead.id.value }) {
                it.applyLead(lead)
            }
            if (updated == 0) {
                ProspectLeadsTable.insert {
                    it[id] = lead.id.value
                    it.applyLead(lead)
                }
            }
        }
    }

    private fun org.jetbrains.exposed.sql.statements.UpdateBuilder<*>.applyLead(lead: ProspectLead) {
        this[ProspectLeadsTable.companyName] = lead.companyName
        this[ProspectLeadsTable.contactName] = lead.contactName
        this[ProspectLeadsTable.contactEmail] = lead.contactEmail
        this[ProspectLeadsTable.contactPhone] = lead.contactPhone
        this[ProspectLeadsTable.narrativeRaw] = lead.narrativeRaw
        this[ProspectLeadsTable.leadSource] = lead.source.code
        this[ProspectLeadsTable.status] = lead.status.code
        this[ProspectLeadsTable.convertedTenantId] = lead.convertedTenantId?.value
        this[ProspectLeadsTable.submittedAt] = lead.submittedAt ?: Clock.System.now()
    }

    private fun toLead(row: ResultRow) = ProspectLead(
        id = ProspectLeadId(row[ProspectLeadsTable.id]),
        companyName = row[ProspectLeadsTable.companyName],
        narrativeRaw = row[ProspectLeadsTable.narrativeRaw],
        contactName = row[ProspectLeadsTable.contactName],
        contactEmail = row[ProspectLeadsTable.contactEmail],
        contactPhone = row[ProspectLeadsTable.contactPhone],
        source = LeadSource.fromCode(row[ProspectLeadsTable.leadSource]) ?: LeadSource.LANDING_PAGE,
        status = LeadStatus.fromCode(row[ProspectLeadsTable.status]) ?: LeadStatus.SUBMITTED,
        convertedTenantId = row[ProspectLeadsTable.convertedTenantId]?.let { TenantId(it) },
        submittedAt = row[ProspectLeadsTable.submittedAt]
    )
}

class PostgresFlowTranslationRepository : FlowTranslationRepository {

    override suspend fun findById(id: FlowTranslationId): FlowTranslation? {
        val row = DatabaseFactory.dbQuery {
            ProspectFlowTranslationsTable.selectAll()
                .where { ProspectFlowTranslationsTable.id eq id.value }
                .singleOrNull()
        } ?: return null
        return toTranslation(row)
    }

    override suspend fun findByLead(leadId: ProspectLeadId): List<FlowTranslation> {
        val rows = DatabaseFactory.dbQuery {
            ProspectFlowTranslationsTable.selectAll()
                .where { ProspectFlowTranslationsTable.leadId eq leadId.value }
                .orderBy(ProspectFlowTranslationsTable.translatedAt, SortOrder.DESC)
                .toList()
        }
        return rows.map { toTranslation(it) }
    }

    override suspend fun save(translation: FlowTranslation) {
        DatabaseFactory.dbQuery {
            val updated = ProspectFlowTranslationsTable.update(
                { ProspectFlowTranslationsTable.id eq translation.id.value }
            ) { it.applyTranslation(translation) }

            if (updated == 0) {
                ProspectFlowTranslationsTable.insert {
                    it[id] = translation.id.value
                    it[leadId] = translation.leadId.value
                    it.applyTranslation(translation)
                }
            }
        }
    }

    private fun org.jetbrains.exposed.sql.statements.UpdateBuilder<*>.applyTranslation(
        translation: FlowTranslation
    ) {
        this[ProspectFlowTranslationsTable.translatorRef] = translation.translatorRef
        this[ProspectFlowTranslationsTable.detectedPreset] = translation.detectedPreset?.code
        this[ProspectFlowTranslationsTable.proposedGraph] =
            ProspectCodec.encodePipeline(translation.proposedPipeline)
        this[ProspectFlowTranslationsTable.capabilityRequirements] =
            ProspectCodec.encodeRequirements(translation.requirements)
        // Coverage is written by the pricing step, which runs after translation; an empty array
        // here is the honest initial state rather than a missing key.
        this[ProspectFlowTranslationsTable.coverage] = "[]"
        this[ProspectFlowTranslationsTable.openQuestions] =
            ProspectCodec.encodeStrings(translation.openQuestions)
        this[ProspectFlowTranslationsTable.validationWarnings] =
            ProspectCodec.encodeStrings(translation.validationWarnings)
        this[ProspectFlowTranslationsTable.needsHumanReview] = translation.needsHumanReview
        this[ProspectFlowTranslationsTable.translatedAt] =
            translation.translatedAt ?: Clock.System.now()
    }

    /**
     * The pipeline is rebuilt against the lead's placeholder tenant id, never a real one.
     *
     * The id is derived rather than stored so a row can never carry a tenant reference that
     * outlives the lead it belongs to.
     */
    private fun toTranslation(row: ResultRow): FlowTranslation {
        val leadId = ProspectLeadId(row[ProspectFlowTranslationsTable.leadId])
        val placeholder = TenantId("prospect-${leadId.value}".take(64))

        return FlowTranslation(
            id = FlowTranslationId(row[ProspectFlowTranslationsTable.id]),
            leadId = leadId,
            translatorRef = row[ProspectFlowTranslationsTable.translatorRef],
            requirements = ProspectCodec.decodeRequirements(
                row[ProspectFlowTranslationsTable.capabilityRequirements]
            ),
            proposedPipeline = ProspectCodec.decodePipeline(
                tenantId = placeholder,
                name = "Usulan alur ${leadId.value}",
                rawJson = row[ProspectFlowTranslationsTable.proposedGraph]
            ),
            detectedPreset = row[ProspectFlowTranslationsTable.detectedPreset]
                ?.let { code -> GarmentBusinessPreset.entries.firstOrNull { it.code == code } },
            openQuestions = ProspectCodec.decodeStrings(
                row[ProspectFlowTranslationsTable.openQuestions]
            ),
            validationWarnings = ProspectCodec.decodeStrings(
                row[ProspectFlowTranslationsTable.validationWarnings]
            ),
            translatedAt = row[ProspectFlowTranslationsTable.translatedAt]
        )
    }
}

class PostgresProspectPriceEstimateRepository : ProspectPriceEstimateRepository {

    override suspend fun findById(id: ProspectPriceEstimateId): StoredPriceEstimate? =
        DatabaseFactory.dbQuery {
            ProspectPriceEstimatesTable.selectAll()
                .where { ProspectPriceEstimatesTable.id eq id.value }
                .map(::toEstimate)
                .singleOrNull()
        }

    override suspend fun findByLead(leadId: ProspectLeadId): List<StoredPriceEstimate> =
        DatabaseFactory.dbQuery {
            ProspectPriceEstimatesTable.selectAll()
                .where { ProspectPriceEstimatesTable.leadId eq leadId.value }
                .orderBy(ProspectPriceEstimatesTable.computedAt, SortOrder.DESC)
                .map(::toEstimate)
        }

    override suspend fun save(estimate: StoredPriceEstimate) {
        DatabaseFactory.dbQuery {
            val updated = ProspectPriceEstimatesTable.update(
                { ProspectPriceEstimatesTable.id eq estimate.id.value }
            ) { it.applyEstimate(estimate) }

            if (updated == 0) {
                ProspectPriceEstimatesTable.insert {
                    it[id] = estimate.id.value
                    it[leadId] = estimate.leadId.value
                    it[translationId] = estimate.translationId.value
                    it.applyEstimate(estimate)
                }
            }
        }
    }

    private fun org.jetbrains.exposed.sql.statements.UpdateBuilder<*>.applyEstimate(
        estimate: StoredPriceEstimate
    ) {
        val range = estimate.range
        this[ProspectPriceEstimatesTable.subscriptionMonthlyIdr] = range.subscriptionMonthly.amount
        this[ProspectPriceEstimatesTable.gapLowMonthlyIdr] = range.gapLowMonthly?.amount
        this[ProspectPriceEstimatesTable.gapHighMonthlyIdr] = range.gapHighMonthly?.amount
        // Stored as computed. Re-deriving on read would risk a different rounding than the one the
        // prospect was actually shown.
        this[ProspectPriceEstimatesTable.displayLowIdr] = range.displayLow?.amount
        this[ProspectPriceEstimatesTable.displayHighIdr] = range.displayHigh?.amount
        this[ProspectPriceEstimatesTable.unpriceableGapCount] = range.unpriceableGapCount.toShort()
        this[ProspectPriceEstimatesTable.expectedTenantCount] = range.expectedTenantCount.toShort()
        this[ProspectPriceEstimatesTable.amortizationMonths] = range.amortizationMonths.toShort()
        this[ProspectPriceEstimatesTable.marginPercent] = estimate.marginPercent.toBigDecimal()
        this[ProspectPriceEstimatesTable.pricingModelVersion] = range.pricingModelVersion
        this[ProspectPriceEstimatesTable.isPublishable] = range.isPublishable
        this[ProspectPriceEstimatesTable.computedAt] = estimate.computedAt ?: Clock.System.now()
    }

    private fun toEstimate(row: ResultRow) = StoredPriceEstimate(
        id = ProspectPriceEstimateId(row[ProspectPriceEstimatesTable.id]),
        leadId = ProspectLeadId(row[ProspectPriceEstimatesTable.leadId]),
        translationId = FlowTranslationId(row[ProspectPriceEstimatesTable.translationId]),
        range = ProspectPriceRange(
            subscriptionMonthly = MoneyIdr(row[ProspectPriceEstimatesTable.subscriptionMonthlyIdr]),
            gapLowMonthly = row[ProspectPriceEstimatesTable.gapLowMonthlyIdr]?.let { MoneyIdr(it) },
            gapHighMonthly = row[ProspectPriceEstimatesTable.gapHighMonthlyIdr]?.let { MoneyIdr(it) },
            unpriceableGapCount = row[ProspectPriceEstimatesTable.unpriceableGapCount].toInt(),
            expectedTenantCount = row[ProspectPriceEstimatesTable.expectedTenantCount].toInt(),
            amortizationMonths = row[ProspectPriceEstimatesTable.amortizationMonths].toInt(),
            pricingModelVersion = row[ProspectPriceEstimatesTable.pricingModelVersion]
        ),
        marginPercent = row[ProspectPriceEstimatesTable.marginPercent].toDouble(),
        computedAt = row[ProspectPriceEstimatesTable.computedAt]
    )
}
