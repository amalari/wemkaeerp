package com.eventverse.app.infrastructure

import com.eventverse.app.domain.moduledev.BuildPhase
import com.eventverse.app.domain.moduledev.BuildStatus
import com.eventverse.app.domain.moduledev.BuildType
import com.eventverse.app.domain.moduledev.BuilderExperienceLevel
import com.eventverse.app.domain.moduledev.ComplexityTier
import com.eventverse.app.domain.moduledev.CustomizationRequestId
import com.eventverse.app.domain.moduledev.EffortEntryId
import com.eventverse.app.domain.moduledev.EffortRole
import com.eventverse.app.domain.moduledev.EffortSource
import com.eventverse.app.domain.moduledev.EstimateConfidence
import com.eventverse.app.domain.moduledev.EstimatorKind
import com.eventverse.app.domain.moduledev.ModuleBuildEffortEntry
import com.eventverse.app.domain.moduledev.ModuleBuildId
import com.eventverse.app.domain.moduledev.ModuleBuildRecord
import com.eventverse.app.domain.moduledev.ModuleBuildRepository
import com.eventverse.app.domain.moduledev.ModuleCatalogEntry
import com.eventverse.app.domain.moduledev.ModuleCatalogEntryId
import com.eventverse.app.domain.moduledev.ModuleCatalogRepository
import com.eventverse.app.domain.moduledev.ModuleCustomizationRequest
import com.eventverse.app.domain.moduledev.ModuleCustomizationRequestRepository
import com.eventverse.app.domain.moduledev.ModuleLifecycleStatus
import com.eventverse.app.domain.moduledev.ModulePricingQuote
import com.eventverse.app.domain.moduledev.ModulePricingQuoteRepository
import com.eventverse.app.domain.moduledev.MoneyIdr
import com.eventverse.app.domain.moduledev.Percentage
import com.eventverse.app.domain.moduledev.PricingInputs
import com.eventverse.app.domain.moduledev.PricingResult
import com.eventverse.app.domain.moduledev.CustomizationRequestStatus
import com.eventverse.app.domain.moduledev.QuoteId
import com.eventverse.app.domain.moduledev.QuoteStatus
import com.eventverse.app.domain.moduledev.RequirementSource
import com.eventverse.app.domain.moduledev.SizePoints
import com.eventverse.app.domain.moduledev.SizingWeights
import com.eventverse.app.domain.moduledev.SizingWeightsRepository
import com.eventverse.app.domain.moduledev.WorkHours
import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.infrastructure.tables.ModuleBuildEffortEntriesTable
import com.eventverse.app.infrastructure.tables.ModuleBuildRecordsTable
import com.eventverse.app.infrastructure.tables.ModuleCatalogEntriesTable
import com.eventverse.app.infrastructure.tables.ModuleCustomizationRequestsTable
import com.eventverse.app.infrastructure.tables.ModulePricingQuotesTable
import com.eventverse.app.infrastructure.tables.ModuleSizingWeightsTable
import com.eventverse.app.shared.moduledev.ModuleDevCodec
import kotlinx.datetime.Clock
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import java.math.BigDecimal

// ==============================================================================
// Catalog
// ==============================================================================

/**
 * The module catalogue is platform data, so every query runs without a tenant context — these
 * tables have no `tenant_id` and no RLS policy to satisfy.
 */
class PostgresModuleCatalogRepository : ModuleCatalogRepository {

    override suspend fun findById(id: ModuleCatalogEntryId): ModuleCatalogEntry? =
        DatabaseFactory.dbQuery {
            ModuleCatalogEntriesTable.selectAll()
                .where { ModuleCatalogEntriesTable.id eq id.value }
                .map(::toCatalogEntry)
                .singleOrNull()
        }

    override suspend fun findByModuleId(moduleId: String): ModuleCatalogEntry? =
        DatabaseFactory.dbQuery {
            ModuleCatalogEntriesTable.selectAll()
                .where { ModuleCatalogEntriesTable.moduleId eq moduleId }
                .map(::toCatalogEntry)
                .singleOrNull()
        }

    override suspend fun findAll(): List<ModuleCatalogEntry> = DatabaseFactory.dbQuery {
        ModuleCatalogEntriesTable.selectAll().map(::toCatalogEntry)
    }

    override suspend fun findBillable(): List<ModuleCatalogEntry> = DatabaseFactory.dbQuery {
        ModuleCatalogEntriesTable.selectAll().map(::toCatalogEntry).filter { it.isBillable }
    }

    override suspend fun save(entry: ModuleCatalogEntry) {
        DatabaseFactory.dbQuery {
            val updated = ModuleCatalogEntriesTable.update(
                { ModuleCatalogEntriesTable.id eq entry.id.value }
            ) { it.applyCatalogEntry(entry) }

            // Insert only when the update matched nothing, so two concurrent writers cannot both
            // see "absent" and both insert.
            if (updated == 0) {
                ModuleCatalogEntriesTable.insert {
                    it[id] = entry.id.value
                    it.applyCatalogEntry(entry)
                }
            }
        }
    }

    private fun org.jetbrains.exposed.sql.statements.UpdateBuilder<*>.applyCatalogEntry(
        entry: ModuleCatalogEntry
    ) {
        this[ModuleCatalogEntriesTable.moduleId] = entry.moduleId
        this[ModuleCatalogEntriesTable.archetypeCode] = entry.archetypeCode
        this[ModuleCatalogEntriesTable.displayName] = entry.displayName
        this[ModuleCatalogEntriesTable.description] = entry.description
        this[ModuleCatalogEntriesTable.categoryCode] = entry.categoryCode
        this[ModuleCatalogEntriesTable.scopeCapability] = entry.scopeCapabilityCode
        this[ModuleCatalogEntriesTable.stockOwnership] = entry.stockOwnershipCode
        this[ModuleCatalogEntriesTable.costingBehavior] = entry.costingBehaviorCode
        this[ModuleCatalogEntriesTable.acceptedInputTypes] =
            ModuleDevCodec.encodeStrings(entry.acceptedInputTypes)
        this[ModuleCatalogEntriesTable.producedOutputType] = entry.producedOutputType
        this[ModuleCatalogEntriesTable.isCustomPlugin] = entry.isCustomPlugin
        this[ModuleCatalogEntriesTable.originTenantId] = entry.originTenantId?.value
        this[ModuleCatalogEntriesTable.lifecycleStatus] = entry.lifecycleStatus.code
        this[ModuleCatalogEntriesTable.complexityTier] = entry.complexityTier?.code
        this[ModuleCatalogEntriesTable.baseMonthlyPriceIdr] = entry.baseMonthlyPriceIdr?.amount
        this[ModuleCatalogEntriesTable.releasedAt] = entry.releasedAt
    }

    private fun toCatalogEntry(row: ResultRow) = ModuleCatalogEntry(
        id = ModuleCatalogEntryId(row[ModuleCatalogEntriesTable.id]),
        moduleId = row[ModuleCatalogEntriesTable.moduleId],
        archetypeCode = row[ModuleCatalogEntriesTable.archetypeCode],
        displayName = row[ModuleCatalogEntriesTable.displayName],
        description = row[ModuleCatalogEntriesTable.description],
        categoryCode = row[ModuleCatalogEntriesTable.categoryCode],
        scopeCapabilityCode = row[ModuleCatalogEntriesTable.scopeCapability],
        stockOwnershipCode = row[ModuleCatalogEntriesTable.stockOwnership],
        costingBehaviorCode = row[ModuleCatalogEntriesTable.costingBehavior],
        acceptedInputTypes =
            ModuleDevCodec.decodeStrings(row[ModuleCatalogEntriesTable.acceptedInputTypes]),
        producedOutputType = row[ModuleCatalogEntriesTable.producedOutputType],
        isCustomPlugin = row[ModuleCatalogEntriesTable.isCustomPlugin],
        originTenantId = row[ModuleCatalogEntriesTable.originTenantId]?.let { TenantId(it) },
        lifecycleStatus = ModuleLifecycleStatus.fromCode(
            row[ModuleCatalogEntriesTable.lifecycleStatus]
        ) ?: ModuleLifecycleStatus.PLANNED,
        complexityTier = row[ModuleCatalogEntriesTable.complexityTier]
            ?.let { ComplexityTier.fromCode(it) },
        baseMonthlyPriceIdr = row[ModuleCatalogEntriesTable.baseMonthlyPriceIdr]
            ?.let { MoneyIdr(it) },
        releasedAt = row[ModuleCatalogEntriesTable.releasedAt]
    )
}

// ==============================================================================
// Builds and effort
// ==============================================================================

class PostgresModuleBuildRepository : ModuleBuildRepository {

    override suspend fun findById(id: ModuleBuildId): ModuleBuildRecord? =
        DatabaseFactory.dbQuery {
            ModuleBuildRecordsTable.selectAll()
                .where { ModuleBuildRecordsTable.id eq id.value }
                .map(::toBuildRecord)
                .singleOrNull()
        }

    /**
     * Filters by archetype in SQL before any similarity is computed.
     *
     * Retrieval across archetypes produces neighbours that merely share vocabulary — a sewing
     * build surfacing for a costing request — and the productivity borrowed from them means
     * nothing. Rows without hours are excluded here rather than downstream so the limit is spent
     * on candidates that can actually contribute.
     */
    override suspend fun findEstimationCandidates(
        archetypeCode: String,
        limit: Int
    ): List<ModuleBuildRecord> = DatabaseFactory.dbQuery {
        ModuleBuildRecordsTable.selectAll()
            .where {
                (ModuleBuildRecordsTable.archetypeCode eq archetypeCode) and
                    (ModuleBuildRecordsTable.actualHours.isNotNull()) and
                    (ModuleBuildRecordsTable.effortSource eq EffortSource.LOGGED.code)
            }
            .limit(limit)
            .map(::toBuildRecord)
    }

    override suspend fun findByCatalogEntry(
        catalogEntryId: ModuleCatalogEntryId
    ): List<ModuleBuildRecord> = DatabaseFactory.dbQuery {
        ModuleBuildRecordsTable.selectAll()
            .where { ModuleBuildRecordsTable.catalogEntryId eq catalogEntryId.value }
            .map(::toBuildRecord)
    }

    override suspend fun findEffortEntries(buildId: ModuleBuildId): List<ModuleBuildEffortEntry> =
        DatabaseFactory.dbQuery {
            ModuleBuildEffortEntriesTable.selectAll()
                .where { ModuleBuildEffortEntriesTable.buildRecordId eq buildId.value }
                .map(::toEffortEntry)
        }

    override suspend fun save(record: ModuleBuildRecord) {
        DatabaseFactory.dbQuery {
            val updated = ModuleBuildRecordsTable.update(
                { ModuleBuildRecordsTable.id eq record.id.value }
            ) { it.applyBuildRecord(record) }

            if (updated == 0) {
                ModuleBuildRecordsTable.insert {
                    it[id] = record.id.value
                    it.applyBuildRecord(record)
                }
            }
        }
    }

    override suspend fun addEffortEntry(entry: ModuleBuildEffortEntry) {
        DatabaseFactory.dbQuery {
            ModuleBuildEffortEntriesTable.insert {
                it[id] = entry.id.value
                it[buildRecordId] = entry.buildRecordId.value
                it[roleCode] = entry.role.code
                it[phaseCode] = entry.phase.code
                it[hours] = entry.hours.hours.toBigDecimal()
                it[hourlyRateIdr] = entry.hourlyRateIdr.amount
                it[performedBy] = entry.performedBy
                it[note] = entry.note
                it[loggedAt] = entry.loggedAt ?: Clock.System.now()
            }
        }
    }

    /**
     * Note the absence of `estimateVariancePercent`: PostgreSQL computes it and rejects any
     * attempt to write it.
     */
    private fun org.jetbrains.exposed.sql.statements.UpdateBuilder<*>.applyBuildRecord(
        record: ModuleBuildRecord
    ) {
        this[ModuleBuildRecordsTable.catalogEntryId] = record.catalogEntryId.value
        this[ModuleBuildRecordsTable.customizationRequestId] = record.customizationRequestId?.value
        this[ModuleBuildRecordsTable.versionLabel] = record.versionLabel
        this[ModuleBuildRecordsTable.buildType] = record.buildType.code
        this[ModuleBuildRecordsTable.status] = record.status.code

        this[ModuleBuildRecordsTable.requirementText] = record.requirementText
        this[ModuleBuildRecordsTable.requirementSource] = record.requirementSource.code
        this[ModuleBuildRecordsTable.archetypeCode] = record.archetypeCode
        this[ModuleBuildRecordsTable.embedding] =
            record.embedding?.let { ModuleDevCodec.encodeEmbedding(it) }
        this[ModuleBuildRecordsTable.embeddingModel] = record.embedding?.model
        this[ModuleBuildRecordsTable.embeddingVersion] = record.embedding?.version

        val features = record.features
        this[ModuleBuildRecordsTable.featureVector] = ModuleDevCodec.encodeFeatures(features)
        this[ModuleBuildRecordsTable.entityCount] = features.entityCount.toShort()
        this[ModuleBuildRecordsTable.useCaseCount] = features.useCaseCount.toShort()
        this[ModuleBuildRecordsTable.screenCount] = features.screenCount.toShort()
        this[ModuleBuildRecordsTable.apiEndpointCount] = features.apiEndpointCount.toShort()
        this[ModuleBuildRecordsTable.dbTableCount] = features.dbTableCount.toShort()
        this[ModuleBuildRecordsTable.reportCount] = features.reportCount.toShort()
        this[ModuleBuildRecordsTable.integrationCount] = features.integrationCount.toShort()
        this[ModuleBuildRecordsTable.targetPlatformCount] = features.targetPlatformCount.toShort()
        this[ModuleBuildRecordsTable.affectedExistingModuleCount] =
            features.affectedExistingModuleCount.toShort()
        this[ModuleBuildRecordsTable.requiresCustomFormula] = features.requiresCustomFormula
        this[ModuleBuildRecordsTable.requiresExternalIntegration] =
            features.requiresExternalIntegration
        this[ModuleBuildRecordsTable.requiresRealtime] = features.requiresRealtime
        this[ModuleBuildRecordsTable.requiresOfflineSync] = features.requiresOfflineSync
        this[ModuleBuildRecordsTable.requiresFileUpload] = features.requiresFileUpload
        this[ModuleBuildRecordsTable.requiresNewDesignComponent] =
            features.requiresNewDesignComponent
        this[ModuleBuildRecordsTable.sizePoints] = record.sizePoints.value
        this[ModuleBuildRecordsTable.sizePointsWeightsVersion] = record.sizePointsWeightsVersion

        this[ModuleBuildRecordsTable.requirementClarityScore] = record.clarityScore?.toShort()
        this[ModuleBuildRecordsTable.builderExperienceLevel] = record.builderExperience?.code
        this[ModuleBuildRecordsTable.wasRushed] = record.wasRushed
        this[ModuleBuildRecordsTable.hadParallelWork] = record.hadParallelWork

        this[ModuleBuildRecordsTable.estimatedHours] = record.estimatedHours?.hours?.toBigDecimal()
        this[ModuleBuildRecordsTable.estimatedHoursP90] =
            record.estimatedHoursP90?.hours?.toBigDecimal()
        this[ModuleBuildRecordsTable.estimatedBy] = record.estimatedBy?.code
        this[ModuleBuildRecordsTable.estimatorRef] = record.estimatorRef
        this[ModuleBuildRecordsTable.estimateConfidence] = record.estimateConfidence?.code
        this[ModuleBuildRecordsTable.retrievedNeighborIds] =
            ModuleDevCodec.encodeStrings(record.retrievedNeighborIds)
        this[ModuleBuildRecordsTable.nearestNeighborSimilarity] =
            record.nearestNeighborSimilarity?.toBigDecimal()
        this[ModuleBuildRecordsTable.openQuestions] =
            ModuleDevCodec.encodeStrings(record.openQuestions)
        this[ModuleBuildRecordsTable.estimatedAt] = record.estimatedAt

        this[ModuleBuildRecordsTable.actualHours] = record.actualHours?.hours?.toBigDecimal()
        this[ModuleBuildRecordsTable.reworkHours] = record.reworkHours.hours.toBigDecimal()
        this[ModuleBuildRecordsTable.revisionRoundCount] = record.revisionRoundCount.toShort()
        this[ModuleBuildRecordsTable.postReleaseDefectCount] =
            record.postReleaseDefectCount.toShort()
        this[ModuleBuildRecordsTable.startedAt] = record.startedAt
        this[ModuleBuildRecordsTable.completedAt] = record.completedAt
        this[ModuleBuildRecordsTable.leadTimeDays] = record.leadTimeDays?.toShort()
        this[ModuleBuildRecordsTable.discoveredScopeDelta] = record.discoveredScopeDelta
        this[ModuleBuildRecordsTable.effortSource] = record.effortSource.code
        this[ModuleBuildRecordsTable.blendedHourlyRateIdr] = record.blendedHourlyRateIdr?.amount
        this[ModuleBuildRecordsTable.totalBuildCostIdr] = record.totalBuildCostIdr?.amount
        this[ModuleBuildRecordsTable.gitRef] = record.gitRef
        this[ModuleBuildRecordsTable.retrospectiveNotes] = record.retrospectiveNotes
    }

    private fun toBuildRecord(row: ResultRow) = ModuleBuildRecord(
        id = ModuleBuildId(row[ModuleBuildRecordsTable.id]),
        catalogEntryId = ModuleCatalogEntryId(row[ModuleBuildRecordsTable.catalogEntryId]),
        buildType = BuildType.fromCode(row[ModuleBuildRecordsTable.buildType])
            ?: BuildType.CUSTOMIZATION,
        archetypeCode = row[ModuleBuildRecordsTable.archetypeCode],
        requirementText = row[ModuleBuildRecordsTable.requirementText],
        status = BuildStatus.fromCode(row[ModuleBuildRecordsTable.status])
            ?: BuildStatus.ESTIMATING,
        customizationRequestId = row[ModuleBuildRecordsTable.customizationRequestId]
            ?.let { CustomizationRequestId(it) },
        versionLabel = row[ModuleBuildRecordsTable.versionLabel],
        requirementSource = RequirementSource.fromCode(
            row[ModuleBuildRecordsTable.requirementSource]
        ) ?: RequirementSource.TENANT_REQUEST,
        embedding = ModuleDevCodec.decodeEmbedding(
            json = row[ModuleBuildRecordsTable.embedding],
            model = row[ModuleBuildRecordsTable.embeddingModel],
            version = row[ModuleBuildRecordsTable.embeddingVersion]
        ),
        features = ModuleDevCodec.decodeFeatures(row[ModuleBuildRecordsTable.featureVector]),
        sizePoints = SizePoints(row[ModuleBuildRecordsTable.sizePoints]),
        sizePointsWeightsVersion = row[ModuleBuildRecordsTable.sizePointsWeightsVersion],
        clarityScore = row[ModuleBuildRecordsTable.requirementClarityScore]?.toInt(),
        builderExperience = row[ModuleBuildRecordsTable.builderExperienceLevel]
            ?.let { BuilderExperienceLevel.fromCode(it) },
        wasRushed = row[ModuleBuildRecordsTable.wasRushed],
        hadParallelWork = row[ModuleBuildRecordsTable.hadParallelWork],
        estimatedHours = row[ModuleBuildRecordsTable.estimatedHours]?.toWorkHours(),
        estimatedHoursP90 = row[ModuleBuildRecordsTable.estimatedHoursP90]?.toWorkHours(),
        estimatedBy = row[ModuleBuildRecordsTable.estimatedBy]?.let { EstimatorKind.fromCode(it) },
        estimatorRef = row[ModuleBuildRecordsTable.estimatorRef],
        estimateConfidence = row[ModuleBuildRecordsTable.estimateConfidence]
            ?.let { EstimateConfidence.fromCode(it) },
        retrievedNeighborIds =
            ModuleDevCodec.decodeStrings(row[ModuleBuildRecordsTable.retrievedNeighborIds]),
        nearestNeighborSimilarity =
            row[ModuleBuildRecordsTable.nearestNeighborSimilarity]?.toDouble(),
        openQuestions = ModuleDevCodec.decodeStrings(row[ModuleBuildRecordsTable.openQuestions]),
        estimatedAt = row[ModuleBuildRecordsTable.estimatedAt],
        actualHours = row[ModuleBuildRecordsTable.actualHours]?.toWorkHours(),
        reworkHours = row[ModuleBuildRecordsTable.reworkHours].toWorkHours(),
        revisionRoundCount = row[ModuleBuildRecordsTable.revisionRoundCount].toInt(),
        postReleaseDefectCount = row[ModuleBuildRecordsTable.postReleaseDefectCount].toInt(),
        startedAt = row[ModuleBuildRecordsTable.startedAt],
        completedAt = row[ModuleBuildRecordsTable.completedAt],
        leadTimeDays = row[ModuleBuildRecordsTable.leadTimeDays]?.toInt(),
        discoveredScopeDelta = row[ModuleBuildRecordsTable.discoveredScopeDelta],
        effortSource = EffortSource.fromCode(row[ModuleBuildRecordsTable.effortSource])
            ?: EffortSource.LOGGED,
        blendedHourlyRateIdr = row[ModuleBuildRecordsTable.blendedHourlyRateIdr]
            ?.let { MoneyIdr(it) },
        totalBuildCostIdr = row[ModuleBuildRecordsTable.totalBuildCostIdr]?.let { MoneyIdr(it) },
        gitRef = row[ModuleBuildRecordsTable.gitRef],
        retrospectiveNotes = row[ModuleBuildRecordsTable.retrospectiveNotes]
    )

    private fun toEffortEntry(row: ResultRow) = ModuleBuildEffortEntry(
        id = EffortEntryId(row[ModuleBuildEffortEntriesTable.id]),
        buildRecordId = ModuleBuildId(row[ModuleBuildEffortEntriesTable.buildRecordId]),
        role = EffortRole.fromCode(row[ModuleBuildEffortEntriesTable.roleCode])
            ?: EffortRole.BACKEND,
        phase = BuildPhase.fromCode(row[ModuleBuildEffortEntriesTable.phaseCode])
            ?: BuildPhase.IMPLEMENTATION,
        hours = row[ModuleBuildEffortEntriesTable.hours].toWorkHours(),
        hourlyRateIdr = MoneyIdr(row[ModuleBuildEffortEntriesTable.hourlyRateIdr]),
        performedBy = row[ModuleBuildEffortEntriesTable.performedBy],
        note = row[ModuleBuildEffortEntriesTable.note],
        loggedAt = row[ModuleBuildEffortEntriesTable.loggedAt]
    )
}

// ==============================================================================
// Quotes
// ==============================================================================

class PostgresModulePricingQuoteRepository : ModulePricingQuoteRepository {

    override suspend fun findById(id: QuoteId): ModulePricingQuote? = DatabaseFactory.dbQuery {
        ModulePricingQuotesTable.selectAll()
            .where { ModulePricingQuotesTable.id eq id.value }
            .map(::toQuote)
            .singleOrNull()
    }

    override suspend fun findByCatalogEntry(
        catalogEntryId: ModuleCatalogEntryId
    ): List<ModulePricingQuote> = DatabaseFactory.dbQuery {
        ModulePricingQuotesTable.selectAll()
            .where { ModulePricingQuotesTable.catalogEntryId eq catalogEntryId.value }
            .map(::toQuote)
    }

    override suspend fun findAcceptedForTenant(tenantId: TenantId): List<ModulePricingQuote> =
        DatabaseFactory.dbQuery {
            ModulePricingQuotesTable.selectAll()
                .where {
                    (ModulePricingQuotesTable.tenantId eq tenantId.value) and
                        (ModulePricingQuotesTable.quoteStatus eq QuoteStatus.ACCEPTED.code)
                }
                .map(::toQuote)
        }

    override suspend fun save(quote: ModulePricingQuote) {
        DatabaseFactory.dbQuery {
            val updated = ModulePricingQuotesTable.update(
                { ModulePricingQuotesTable.id eq quote.id.value }
            ) { it.applyQuote(quote) }

            if (updated == 0) {
                ModulePricingQuotesTable.insert {
                    it[id] = quote.id.value
                    it.applyQuote(quote)
                }
            }
        }
    }

    private fun org.jetbrains.exposed.sql.statements.UpdateBuilder<*>.applyQuote(
        quote: ModulePricingQuote
    ) {
        val inputs = quote.inputs
        this[ModulePricingQuotesTable.catalogEntryId] = quote.catalogEntryId.value
        this[ModulePricingQuotesTable.buildRecordId] = quote.buildRecordId?.value
        this[ModulePricingQuotesTable.tenantId] = quote.tenantId?.value
        this[ModulePricingQuotesTable.quoteStatus] = quote.status.code
        this[ModulePricingQuotesTable.basisHours] = inputs.basisHours.hours.toBigDecimal()
        this[ModulePricingQuotesTable.buildCostIdr] = inputs.buildCost.amount
        this[ModulePricingQuotesTable.expectedTenantCount] = inputs.expectedTenantCount.toShort()
        this[ModulePricingQuotesTable.amortizationMonths] = inputs.amortizationMonths.toShort()
        this[ModulePricingQuotesTable.marginPercent] = inputs.marginPercent.value.toBigDecimal()
        this[ModulePricingQuotesTable.monthlyMaintenancePercent] =
            inputs.monthlyMaintenancePercent.value.toBigDecimal()
        this[ModulePricingQuotesTable.monthlyInfraCostIdr] = inputs.monthlyInfraCost.amount
        this[ModulePricingQuotesTable.discountPercent] = inputs.discountPercent.value.toBigDecimal()
        this[ModulePricingQuotesTable.pricingModelVersion] = inputs.pricingModelVersion
        this[ModulePricingQuotesTable.oneTimeFeeIdr] = quote.result.oneTimeFee.amount
        this[ModulePricingQuotesTable.monthlyPriceIdr] = quote.result.monthlyPrice.amount
        this[ModulePricingQuotesTable.calculationBreakdown] =
            ModuleDevCodec.encodeBreakdown(quote.result.breakdown)
        this[ModulePricingQuotesTable.quotedAt] = quote.quotedAt ?: Clock.System.now()
    }

    private fun toQuote(row: ResultRow) = ModulePricingQuote(
        id = QuoteId(row[ModulePricingQuotesTable.id]),
        catalogEntryId = ModuleCatalogEntryId(row[ModulePricingQuotesTable.catalogEntryId]),
        inputs = PricingInputs(
            basisHours = row[ModulePricingQuotesTable.basisHours].toWorkHours(),
            // Recovered from the stored cost and hours rather than kept as its own column: the
            // two are what the price was actually built from, so deriving keeps them consistent.
            blendedHourlyRate = deriveHourlyRate(
                row[ModulePricingQuotesTable.buildCostIdr],
                row[ModulePricingQuotesTable.basisHours]
            ),
            expectedTenantCount = row[ModulePricingQuotesTable.expectedTenantCount].toInt(),
            amortizationMonths = row[ModulePricingQuotesTable.amortizationMonths].toInt(),
            marginPercent = Percentage(row[ModulePricingQuotesTable.marginPercent].toDouble()),
            monthlyMaintenancePercent =
                Percentage(row[ModulePricingQuotesTable.monthlyMaintenancePercent].toDouble()),
            monthlyInfraCost = MoneyIdr(row[ModulePricingQuotesTable.monthlyInfraCostIdr]),
            discountPercent = Percentage(row[ModulePricingQuotesTable.discountPercent].toDouble()),
            pricingModelVersion = row[ModulePricingQuotesTable.pricingModelVersion]
        ),
        result = PricingResult(
            monthlyPrice = MoneyIdr(row[ModulePricingQuotesTable.monthlyPriceIdr]),
            oneTimeFee = MoneyIdr(row[ModulePricingQuotesTable.oneTimeFeeIdr]),
            breakdown = ModuleDevCodec.decodeBreakdown(
                row[ModulePricingQuotesTable.calculationBreakdown]
            )
        ),
        buildRecordId = row[ModulePricingQuotesTable.buildRecordId]?.let { ModuleBuildId(it) },
        tenantId = row[ModulePricingQuotesTable.tenantId]?.let { TenantId(it) },
        status = QuoteStatus.fromCode(row[ModulePricingQuotesTable.quoteStatus])
            ?: QuoteStatus.DRAFT,
        quotedAt = row[ModulePricingQuotesTable.quotedAt]
    )

    private fun deriveHourlyRate(buildCost: Long, basisHours: BigDecimal): MoneyIdr {
        val hours = basisHours.toDouble()
        return if (hours > 0.0) MoneyIdr((buildCost / hours).toLong()) else MoneyIdr.ZERO
    }
}

// ==============================================================================
// Customization requests (tenant-scoped: RLS applies)
// ==============================================================================

class PostgresModuleCustomizationRequestRepository : ModuleCustomizationRequestRepository {

    /**
     * Reads by id still need a tenant context to satisfy RLS, so the caller's tenant is taken from
     * the row itself only after the fact — a request belonging to another tenant simply is not
     * visible. Lookups therefore go through the tenant-scoped listing where possible.
     */
    override suspend fun findById(id: CustomizationRequestId): ModuleCustomizationRequest? =
        DatabaseFactory.dbQuery {
            ModuleCustomizationRequestsTable.selectAll()
                .where { ModuleCustomizationRequestsTable.id eq id.value }
                .map(::toRequest)
                .singleOrNull()
        }

    override suspend fun findByTenant(tenantId: TenantId): List<ModuleCustomizationRequest> =
        DatabaseFactory.dbQuery(tenantId) {
            ModuleCustomizationRequestsTable.selectAll()
                .where { ModuleCustomizationRequestsTable.tenantId eq tenantId.value }
                .map(::toRequest)
        }

    override suspend fun save(request: ModuleCustomizationRequest) {
        // Scoped to the owning tenant so the RLS WITH CHECK clause passes on insert.
        DatabaseFactory.dbQuery(request.tenantId) {
            val updated = ModuleCustomizationRequestsTable.update(
                { ModuleCustomizationRequestsTable.id eq request.id.value }
            ) { it.applyRequest(request) }

            if (updated == 0) {
                ModuleCustomizationRequestsTable.insert {
                    it[id] = request.id.value
                    it[tenantId] = request.tenantId.value
                    it.applyRequest(request)
                }
            }
        }
    }

    private fun org.jetbrains.exposed.sql.statements.UpdateBuilder<*>.applyRequest(
        request: ModuleCustomizationRequest
    ) {
        this[ModuleCustomizationRequestsTable.catalogEntryId] = request.catalogEntryId?.value
        this[ModuleCustomizationRequestsTable.title] = request.title
        this[ModuleCustomizationRequestsTable.descriptionRaw] = request.descriptionRaw
        this[ModuleCustomizationRequestsTable.requestedByUserId] = request.requestedByUserId
        this[ModuleCustomizationRequestsTable.requestedAt] =
            request.requestedAt ?: Clock.System.now()
        this[ModuleCustomizationRequestsTable.status] = request.status.code
        this[ModuleCustomizationRequestsTable.activeQuoteId] = request.activeQuoteId?.value
        this[ModuleCustomizationRequestsTable.decidedAt] = request.decidedAt
        this[ModuleCustomizationRequestsTable.rejectionReason] = request.rejectionReason
    }

    private fun toRequest(row: ResultRow) = ModuleCustomizationRequest(
        id = CustomizationRequestId(row[ModuleCustomizationRequestsTable.id]),
        tenantId = TenantId(row[ModuleCustomizationRequestsTable.tenantId]),
        title = row[ModuleCustomizationRequestsTable.title],
        descriptionRaw = row[ModuleCustomizationRequestsTable.descriptionRaw],
        catalogEntryId = row[ModuleCustomizationRequestsTable.catalogEntryId]
            ?.let { ModuleCatalogEntryId(it) },
        requestedByUserId = row[ModuleCustomizationRequestsTable.requestedByUserId],
        requestedAt = row[ModuleCustomizationRequestsTable.requestedAt],
        status = CustomizationRequestStatus.fromCode(
            row[ModuleCustomizationRequestsTable.status]
        ) ?: CustomizationRequestStatus.SUBMITTED,
        activeQuoteId = row[ModuleCustomizationRequestsTable.activeQuoteId]?.let { QuoteId(it) },
        decidedAt = row[ModuleCustomizationRequestsTable.decidedAt],
        rejectionReason = row[ModuleCustomizationRequestsTable.rejectionReason]
    )
}

// ==============================================================================
// Sizing weights
// ==============================================================================

class PostgresSizingWeightsRepository(
    private val activeVersion: String = DEFAULT_ACTIVE_VERSION
) : SizingWeightsRepository {

    /**
     * Falls back to the in-code v1 weights when the table has no rows for the active version.
     *
     * Scoring a little differently is recoverable; refusing to estimate at all because a seed did
     * not run is not.
     */
    override suspend fun findActive(): SizingWeights =
        findByVersion(activeVersion) ?: SizingWeights.V1

    override suspend fun findByVersion(version: String): SizingWeights? =
        DatabaseFactory.dbQuery {
            val weights = ModuleSizingWeightsTable.selectAll()
                .where { ModuleSizingWeightsTable.weightsVersion eq version }
                .associate {
                    it[ModuleSizingWeightsTable.featureKey] to
                        it[ModuleSizingWeightsTable.weight].toDouble()
                }
            if (weights.isEmpty()) null else SizingWeights(version, weights)
        }

    override suspend fun save(weights: SizingWeights) {
        DatabaseFactory.dbQuery {
            weights.weights.forEach { (key, weight) ->
                val updated = ModuleSizingWeightsTable.update(
                    {
                        (ModuleSizingWeightsTable.weightsVersion eq weights.version) and
                            (ModuleSizingWeightsTable.featureKey eq key)
                    }
                ) { it[ModuleSizingWeightsTable.weight] = weight.toBigDecimal() }

                if (updated == 0) {
                    ModuleSizingWeightsTable.insert {
                        it[weightsVersion] = weights.version
                        it[featureKey] = key
                        it[ModuleSizingWeightsTable.weight] = weight.toBigDecimal()
                    }
                }
            }
        }
    }

    companion object {
        const val DEFAULT_ACTIVE_VERSION = "v1"
    }
}

private fun BigDecimal.toWorkHours(): WorkHours = WorkHours(toDouble())
