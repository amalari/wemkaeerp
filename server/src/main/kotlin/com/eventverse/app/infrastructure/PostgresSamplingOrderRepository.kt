package com.eventverse.app.infrastructure

import com.eventverse.app.domain.sampling.*
import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.shared.sampling.SamplingProgramCodec
import com.eventverse.app.infrastructure.tables.*
import com.eventverse.app.shared.json.*
import com.eventverse.app.shared.sampling.StageWorkInputCodec
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.isNull

class PostgresSamplingOrderRepository : SamplingOrderRepository {

    override suspend fun findById(id: SamplingOrderId): SamplingOrder? =
        DatabaseFactory.dbQuery {
            val orderRow = SamplingOrdersTable.selectAll()
                .where { (SamplingOrdersTable.id eq id.value) and (SamplingOrdersTable.archivedAt.isNull()) }
                .singleOrNull() ?: return@dbQuery null

            loadOrderDetails(orderRow)
        }

    override suspend fun findBySpkNumber(tenantId: TenantId, spkNumber: SpkNumber): SamplingOrder? =
        DatabaseFactory.dbQuery(tenantId) {
            val orderRow = SamplingOrdersTable.selectAll()
                .where {
                    (SamplingOrdersTable.tenantId eq tenantId.value) and
                    (SamplingOrdersTable.spkNumber eq spkNumber.value) and
                    (SamplingOrdersTable.archivedAt.isNull())
                }
                .singleOrNull() ?: return@dbQuery null

            loadOrderDetails(orderRow)
        }

    override suspend fun findAll(tenantId: TenantId, status: SamplingStatus?): List<SamplingOrder> =
        DatabaseFactory.dbQuery(tenantId) {
            val query = SamplingOrdersTable.selectAll()
                .where {
                    val base = (SamplingOrdersTable.tenantId eq tenantId.value) and (SamplingOrdersTable.archivedAt.isNull())
                    if (status != null) base and (SamplingOrdersTable.status eq status.name) else base
                }
                .orderBy(SamplingOrdersTable.updatedAt, SortOrder.DESC)

            query.map { loadOrderDetails(it) }
        }

    override suspend fun findByDealId(tenantId: TenantId, dealId: String): List<SamplingOrder> =
        DatabaseFactory.dbQuery(tenantId) {
            SamplingOrdersTable.selectAll()
                .where {
                    (SamplingOrdersTable.tenantId eq tenantId.value) and
                        (SamplingOrdersTable.dealId eq dealId) and
                        (SamplingOrdersTable.archivedAt.isNull())
                }
                .orderBy(SamplingOrdersTable.updatedAt, SortOrder.ASC)
                .map { loadOrderDetails(it) }
        }

    override suspend fun save(order: SamplingOrder): SamplingOrder =
        DatabaseFactory.dbQuery(order.tenantId) {
            val existing = SamplingOrdersTable.selectAll()
                .where { (SamplingOrdersTable.id eq order.id.value) }
                .singleOrNull()

            if (existing == null) {
                SamplingOrdersTable.insert {
                    it[id] = order.id.value
                    it[tenantId] = order.tenantId.value
                    it[spkNumber] = order.spkNumber.value
                    it[clientName] = order.clientName
                    it[styleName] = order.styleName
                    it[status] = order.status.name
                    it[pipelineStage] = order.pipelineStage.name
                    it[finishingPath] = order.finishingPath.name
                    it[vendorName] = order.vendorInfo.vendorName.takeIf { it.isNotBlank() }
                    it[vendorPhone] = order.vendorInfo.vendorPhone.takeIf { it.isNotBlank() }
                    it[vendorSentAt] = order.vendorInfo.sentAt
                    it[vendorTargetAt] = order.vendorInfo.expectedReturnAt
                    it[vendorReturnedAt] = order.vendorInfo.returnedAt
                    it[vendorCostPerPcs] = order.vendorInfo.costPerPcsIdr
                    it[vendorStatus] = order.vendorInfo.status.name
                    it[vendorNotes] = order.vendorInfo.notes
                    it[sizeMode] = order.sizeMode.name
                    it[deadlineProgram] = order.deadlineProgram
                    it[deadlineFinishing] = order.deadlineFinishing
                    it[deadlineDelivery] = order.deadlineDelivery
                    it[leadId] = order.leadId
                    it[dealId] = order.dealId
                    it[sampleQuantity] = order.sampleQuantity
                    it[courierTracking] = order.courierTracking
                    it[samplingFeeIdr] = order.samplingFeeIdr
                    it[revisionCount] = order.revisionCount
                    it[revisionHistory] = encodeRevisionHistory(order.revisionHistory)
                    it[sizeMatrix] = sizeMatrixJson(order.sizeMatrix).encode()
                    it[stageInputs] = StageWorkInputCodec.encodeInputs(order.stageInputs)
                    it[stageHistory] = StageWorkInputCodec.encodeHistory(order.stageHistory)
                    it[accNotes] = order.accNotes
                    it[notes] = order.notes
                    it[createdAt] = order.createdAt
                    it[updatedAt] = order.updatedAt
                }
            } else {
                SamplingOrdersTable.update({ SamplingOrdersTable.id eq order.id.value }) {
                    it[clientName] = order.clientName
                    it[styleName] = order.styleName
                    it[status] = order.status.name
                    it[pipelineStage] = order.pipelineStage.name
                    it[finishingPath] = order.finishingPath.name
                    it[vendorName] = order.vendorInfo.vendorName.takeIf { it.isNotBlank() }
                    it[vendorPhone] = order.vendorInfo.vendorPhone.takeIf { it.isNotBlank() }
                    it[vendorSentAt] = order.vendorInfo.sentAt
                    it[vendorTargetAt] = order.vendorInfo.expectedReturnAt
                    it[vendorReturnedAt] = order.vendorInfo.returnedAt
                    it[vendorCostPerPcs] = order.vendorInfo.costPerPcsIdr
                    it[vendorStatus] = order.vendorInfo.status.name
                    it[vendorNotes] = order.vendorInfo.notes
                    it[sizeMode] = order.sizeMode.name
                    it[deadlineProgram] = order.deadlineProgram
                    it[deadlineFinishing] = order.deadlineFinishing
                    it[deadlineDelivery] = order.deadlineDelivery
                    it[leadId] = order.leadId
                    it[dealId] = order.dealId
                    it[sampleQuantity] = order.sampleQuantity
                    it[courierTracking] = order.courierTracking
                    it[samplingFeeIdr] = order.samplingFeeIdr
                    it[revisionCount] = order.revisionCount
                    it[revisionHistory] = encodeRevisionHistory(order.revisionHistory)
                    it[sizeMatrix] = sizeMatrixJson(order.sizeMatrix).encode()
                    it[stageInputs] = StageWorkInputCodec.encodeInputs(order.stageInputs)
                    it[stageHistory] = StageWorkInputCodec.encodeHistory(order.stageHistory)
                    it[accNotes] = order.accNotes
                    it[notes] = order.notes
                    it[updatedAt] = order.updatedAt
                    it[archivedAt] = order.archivedAt
                }
            }

            // Save Knit Specs
            SamplingKnitSpecsTable.deleteWhere { samplingOrderId eq order.id.value }
            SamplingKnitSpecsTable.insert {
                it[id] = "ks_${order.id.value}"
                it[tenantId] = order.tenantId.value
                it[samplingOrderId] = order.id.value
                it[yarnType] = order.knitSpec.yarnType
                it[knitType] = order.knitSpec.knitType
                it[ribSpec] = order.knitSpec.ribSpec
                it[collarSpec] = order.knitSpec.collarSpec
                it[placketSpec] = order.knitSpec.placketSpec
                it[colorwayNotes] = order.knitSpec.colorwayNotes
                it[mockupImageUrls] = jsonArrayOf(order.knitSpec.mockupImageUrls.map { url -> jsonOf(url) }).encode()
                it[createdAt] = order.createdAt
                it[updatedAt] = order.updatedAt
            }

            // Save Size Charts (Finished & Raw)
            SamplingSizeChartsTable.deleteWhere { samplingOrderId eq order.id.value }
            order.finishedSizeCharts.forEachIndexed { idx, sc ->
                insertSizeChartRow(order, SizeCategory.FINISHED_SIZE, idx, sc)
            }
            order.rawKnitSizeCharts.forEachIndexed { idx, sc ->
                insertSizeChartRow(order, SizeCategory.KNIT_RAW_SIZE, idx, sc)
            }

            // Save Machine Program
            SamplingMachineProgramsTable.deleteWhere { samplingOrderId eq order.id.value }
            SamplingMachineProgramsTable.insert {
                it[id] = "mp_${order.id.value}"
                it[tenantId] = order.tenantId.value
                it[samplingOrderId] = order.id.value
                it[programFront] = order.machineProgram.programFront
                it[programBack] = order.machineProgram.programBack
                it[programSleeve] = order.machineProgram.programSleeve
                it[programCollar] = order.machineProgram.programCollar
                it[programPlacket] = order.machineProgram.programPlacket
                it[feederInstructions] = jsonArrayOf(order.machineProgram.feederInstructions.map { f ->
                    jsonObjectOf(
                        "feederNumber" to jsonOf(f.feederNumber),
                        "name" to jsonOf(f.name),
                        "ply" to jsonOf(f.ply),
                        "color" to jsonOf(f.color)
                    )
                }).encode()
                it[patternFormulas] = jsonObjectOf(
                    "bodyLengthK" to jsonOf(order.machineProgram.patternFormulas.bodyLengthK),
                    "bodyWidthN" to jsonOf(order.machineProgram.patternFormulas.bodyWidthN),
                    "ribK" to jsonOf(order.machineProgram.patternFormulas.ribK)
                ).encode()
                it[tensionSettings] = JsonValue.Obj(order.machineProgram.tensionSettings.mapValues { (_, value) -> jsonOf(value) }).encode()
                it[tenselityEntries] = jsonArrayOf(order.machineProgram.tenselityEntries.map { t ->
                    jsonObjectOf(
                        "parameter" to jsonOf(t.parameter),
                        "body" to jsonOf(t.body),
                        "sleeve" to jsonOf(t.sleeve),
                        "collar" to jsonOf(t.collar)
                    )
                }).encode()
                it[createdAt] = order.createdAt
                it[updatedAt] = order.updatedAt
            }

            // Save Yield & Timings
            SamplingYieldTimingsTable.deleteWhere { samplingOrderId eq order.id.value }
            SamplingYieldTimingsTable.insert {
                it[id] = "yt_${order.id.value}"
                it[tenantId] = order.tenantId.value
                it[samplingOrderId] = order.id.value
                it[panelWeightsGrams] = jsonObjectOf(
                    "front" to jsonOf(order.yieldAndTiming.panelWeights.front),
                    "back" to jsonOf(order.yieldAndTiming.panelWeights.back),
                    "sleeve" to jsonOf(order.yieldAndTiming.panelWeights.sleeve),
                    "collar" to jsonOf(order.yieldAndTiming.panelWeights.collar),
                    "placket" to jsonOf(order.yieldAndTiming.panelWeights.placket)
                ).encode()
                it[panelKnittingMinutes] = jsonObjectOf(
                    "front" to jsonOf(order.yieldAndTiming.panelMinutes.front),
                    "back" to jsonOf(order.yieldAndTiming.panelMinutes.back),
                    "sleeve" to jsonOf(order.yieldAndTiming.panelMinutes.sleeve),
                    "collar" to jsonOf(order.yieldAndTiming.panelMinutes.collar),
                    "placket" to jsonOf(order.yieldAndTiming.panelMinutes.placket)
                ).encode()
                it[panelSizeSpecs] = SamplingProgramCodec
                    .encodePanelSizeSpecs(order.yieldAndTiming.perSize).encode()
                it[linkingNotes] = order.yieldAndTiming.linkingNotes
                it[additionalProcess] = order.yieldAndTiming.additionalProcess
                it[isWashed] = order.yieldAndTiming.isWashed
                it[estimatedHppIdr] = order.yieldAndTiming.estimatedHppIdr
                it[createdAt] = order.createdAt
                it[updatedAt] = order.updatedAt
            }

            // Save Milestones
            SamplingMilestonesTable.deleteWhere { samplingOrderId eq order.id.value }
            order.milestones.forEachIndexed { idx, m ->
                SamplingMilestonesTable.insert {
                    it[id] = "sm_${order.id.value}_${m.step.name}"
                    it[tenantId] = order.tenantId.value
                    it[samplingOrderId] = order.id.value
                    it[stepName] = m.step.name
                    it[isCompleted] = m.isCompleted
                    it[completedAt] = m.completedAt
                    it[stepOrder] = idx + 1
                    it[notes] = m.notes
                }
            }

            // Save Finishing Deposits
            SamplingFinishingDepositsTable.deleteWhere { samplingOrderId eq order.id.value }
            order.finishingDeposits.forEachIndexed { idx, dep ->
                val depId = dep.id.ifBlank { "dep_${order.id.value}_$idx" }
                SamplingFinishingDepositsTable.insert {
                    it[id] = depId
                    it[tenantId] = order.tenantId.value
                    it[samplingOrderId] = order.id.value
                    it[depositDate] = dep.depositDate
                    it[qtyPcs] = dep.qtyPcs
                    it[weightKg] = dep.weightKg
                    it[scalePhotoKey] = dep.scalePhotoKey
                    it[garmentPhotoKey] = dep.garmentPhotoKey
                    it[operatorName] = dep.operatorName
                    it[notes] = dep.notes
                    it[createdAt] = dep.createdAt ?: order.updatedAt
                }
            }

            // Save QC Inspections
            SamplingQcInspectionsTable.deleteWhere { samplingOrderId eq order.id.value }
            order.qcInspections.forEachIndexed { idx, qc ->
                val qcId = qc.id.ifBlank { "qc_${order.id.value}_$idx" }
                SamplingQcInspectionsTable.insert {
                    it[id] = qcId
                    it[tenantId] = order.tenantId.value
                    it[samplingOrderId] = order.id.value
                    it[kind] = qc.kind.name
                    it[inspectorName] = qc.inspectorName
                    it[inspectedAt] = qc.inspectedAt
                    it[inspectedQty] = qc.inspectedQty
                    it[pieceNo] = qc.pieceNo
                    it[measuredPomValues] = jsonArrayOf(qc.pomMeasurements.map { m ->
                        jsonObjectOf(
                            "pomName" to jsonOf(m.pomName),
                            "targetCm" to jsonOf(m.targetCm),
                            "actualCm" to jsonOf(m.actualCm),
                            "toleranceCm" to jsonOf(m.toleranceCm),
                            "notes" to jsonOf(m.notes),
                            "carriedOver" to jsonOf(m.carriedOver)
                        )
                    }).encode()
                    it[defectsFound] = jsonArrayOf(qc.defectsFound.map { d -> jsonOf(d) }).encode()
                    it[qcResult] = qc.qcResult.name
                    it[qcNotes] = qc.qcNotes
                    it[verifiedPhotoFrontKey] = qc.verifiedPhotoFrontKey
                    it[verifiedPhotoBackKey] = qc.verifiedPhotoBackKey
                }
            }

            order
        }

    override suspend fun nextSpkNumber(tenantId: TenantId): SpkNumber =
        DatabaseFactory.dbQuery(tenantId) {
            val count = SamplingOrdersTable.selectAll()
                .where { SamplingOrdersTable.tenantId eq tenantId.value }
                .count()

            val index = count + 1
            val padded = index.toString().padStart(4, '0')
            SpkNumber("SPK-SMP-$padded")
        }

    override suspend fun archive(id: SamplingOrderId): Boolean =
        DatabaseFactory.dbQuery {
            val updated = SamplingOrdersTable.update({ SamplingOrdersTable.id eq id.value }) {
                it[archivedAt] = Clock.System.now()
            }
            updated > 0
        }

    private fun insertSizeChartRow(
        order: SamplingOrder,
        category: SizeCategory,
        index: Int,
        sc: SizeMeasurement
    ) {
        SamplingSizeChartsTable.insert {
            it[id] = "sc_${order.id.value}_${category.name}_$index"
            it[tenantId] = order.tenantId.value
            it[samplingOrderId] = order.id.value
            it[this.category] = category.name
            it[sizeLabel] = sc.sizeLabel
            it[bodyLength] = sc.bodyLength
            it[bodyWidth] = sc.bodyWidth
            it[sleeveLength] = sc.sleeveLength
            it[armHole] = sc.armHole
            it[neckDrop] = sc.neckDrop
            it[neckWidth] = sc.neckWidth
            it[shoulderWidth] = sc.shoulderWidth
            it[ribHeight] = sc.ribHeight
            it[collarHeight] = sc.collarHeight
            it[placketWidth] = sc.placketWidth
            it[sleeveOpening] = sc.sleeveOpening
            it[createdAt] = order.createdAt
        }
    }

    private fun loadOrderDetails(orderRow: ResultRow): SamplingOrder {
        val orderId = orderRow[SamplingOrdersTable.id]

        // 1. Knit Spec
        val knitSpecRow = SamplingKnitSpecsTable.selectAll()
            .where { SamplingKnitSpecsTable.samplingOrderId eq orderId }
            .singleOrNull()

        val knitSpec = if (knitSpecRow != null) {
            val urls = (JsonParser.parse(knitSpecRow[SamplingKnitSpecsTable.mockupImageUrls]) as? JsonValue.Arr)
                ?.items?.mapNotNull { item -> (item as? JsonValue.Str)?.value }
                ?: emptyList()

            KnitSpec(
                yarnType = knitSpecRow[SamplingKnitSpecsTable.yarnType],
                knitType = knitSpecRow[SamplingKnitSpecsTable.knitType],
                ribSpec = knitSpecRow[SamplingKnitSpecsTable.ribSpec],
                collarSpec = knitSpecRow[SamplingKnitSpecsTable.collarSpec],
                placketSpec = knitSpecRow[SamplingKnitSpecsTable.placketSpec],
                colorwayNotes = knitSpecRow[SamplingKnitSpecsTable.colorwayNotes],
                mockupImageUrls = urls
            )
        } else KnitSpec()

        // 2. Size Charts
        val sizeChartRows = SamplingSizeChartsTable.selectAll()
            .where { SamplingSizeChartsTable.samplingOrderId eq orderId }
            .toList()

        val finishedSizes = sizeChartRows
            .filter { it[SamplingSizeChartsTable.category] == SizeCategory.FINISHED_SIZE.name }
            .map(::toSizeMeasurement)
            .ifEmpty { listOf(FactorySizePresets.ALL_SIZE_CARDIGAN_FINISHED) }

        val rawKnitSizes = sizeChartRows
            .filter { it[SamplingSizeChartsTable.category] == SizeCategory.KNIT_RAW_SIZE.name }
            .map(::toSizeMeasurement)
            .ifEmpty { listOf(FactorySizePresets.ALL_SIZE_CARDIGAN_RAW_KNIT) }

        // 3. Machine Program
        val mpRow = SamplingMachineProgramsTable.selectAll()
            .where { SamplingMachineProgramsTable.samplingOrderId eq orderId }
            .singleOrNull()

        val machineProgram = if (mpRow != null) {
            val feeders = (JsonParser.parse(mpRow[SamplingMachineProgramsTable.feederInstructions]) as? JsonValue.Arr)
                ?.items?.mapNotNull { item ->
                    val obj = item as? JsonValue.Obj ?: return@mapNotNull null
                    FeederEntry(
                        feederNumber = obj.int("feederNumber") ?: 1,
                        name = obj.string("name") ?: "",
                        ply = obj.string("ply") ?: "",
                        color = obj.string("color") ?: ""
                    )
                } ?: emptyList()

            val formulasObj = JsonParser.parse(mpRow[SamplingMachineProgramsTable.patternFormulas]) as? JsonValue.Obj
            val formulas = PatternFormulas(
                bodyLengthK = formulasObj?.double("bodyLengthK") ?: 2.94,
                bodyWidthN = formulasObj?.double("bodyWidthN") ?: 6.6,
                ribK = formulasObj?.double("ribK") ?: 4.7
            )

            val tensionObj = JsonParser.parse(mpRow[SamplingMachineProgramsTable.tensionSettings]) as? JsonValue.Obj
            val tensions = tensionObj?.stringMap("") ?: emptyMap()
            val tenselities = (JsonParser.parse(mpRow[SamplingMachineProgramsTable.tenselityEntries]) as? JsonValue.Arr)
                ?.items?.mapNotNull { item ->
                    val obj = item as? JsonValue.Obj ?: return@mapNotNull null
                    TenselityEntry(
                        parameter = obj.string("parameter") ?: "",
                        body = obj.string("body") ?: "",
                        sleeve = obj.string("sleeve") ?: "",
                        collar = obj.string("collar") ?: ""
                    )
                } ?: emptyList()

            MachineProgram(
                programFront = mpRow[SamplingMachineProgramsTable.programFront],
                programBack = mpRow[SamplingMachineProgramsTable.programBack],
                programSleeve = mpRow[SamplingMachineProgramsTable.programSleeve],
                programCollar = mpRow[SamplingMachineProgramsTable.programCollar],
                programPlacket = mpRow[SamplingMachineProgramsTable.programPlacket],
                feederInstructions = feeders,
                patternFormulas = formulas,
                tensionSettings = tensions,
                tenselityEntries = tenselities
            )
        } else MachineProgram()

        // 4. Yield & Timings
        val ytRow = SamplingYieldTimingsTable.selectAll()
            .where { SamplingYieldTimingsTable.samplingOrderId eq orderId }
            .singleOrNull()

        val yieldAndTiming = if (ytRow != null) {
            val weightsObj = JsonParser.parse(ytRow[SamplingYieldTimingsTable.panelWeightsGrams]) as? JsonValue.Obj
            val minutesObj = JsonParser.parse(ytRow[SamplingYieldTimingsTable.panelKnittingMinutes]) as? JsonValue.Obj

            YieldAndTiming(
                panelWeights = PanelWeightGrams(
                    front = weightsObj?.double("front") ?: 0.0,
                    back = weightsObj?.double("back") ?: 0.0,
                    sleeve = weightsObj?.double("sleeve") ?: 0.0,
                    collar = weightsObj?.double("collar") ?: 0.0,
                    placket = weightsObj?.double("placket") ?: 0.0
                ),
                panelMinutes = PanelKnittingMinutes(
                    front = minutesObj?.int("front") ?: 0,
                    back = minutesObj?.int("back") ?: 0,
                    sleeve = minutesObj?.int("sleeve") ?: 0,
                    collar = minutesObj?.int("collar") ?: 0,
                    placket = minutesObj?.int("placket") ?: 0
                ),
                perSize = SamplingProgramCodec
                    .decodePanelSizeSpecs(ytRow[SamplingYieldTimingsTable.panelSizeSpecs]),
                linkingNotes = ytRow[SamplingYieldTimingsTable.linkingNotes],
                additionalProcess = ytRow[SamplingYieldTimingsTable.additionalProcess],
                isWashed = ytRow[SamplingYieldTimingsTable.isWashed],
                estimatedHppIdr = ytRow[SamplingYieldTimingsTable.estimatedHppIdr]
            )
        } else YieldAndTiming()

        // 5. Milestones
        val milestoneRows = SamplingMilestonesTable.selectAll()
            .where { SamplingMilestonesTable.samplingOrderId eq orderId }
            .orderBy(SamplingMilestonesTable.stepOrder, SortOrder.ASC)
            .toList()

        val milestones = if (milestoneRows.isNotEmpty()) {
            milestoneRows.map {
                val step = runCatching { MilestoneStep.valueOf(it[SamplingMilestonesTable.stepName]) }.getOrNull() ?: MilestoneStep.PROGRAM
                MilestoneProgress(
                    step = step,
                    isCompleted = it[SamplingMilestonesTable.isCompleted],
                    completedAt = it[SamplingMilestonesTable.completedAt],
                    notes = it[SamplingMilestonesTable.notes]
                )
            }
        } else SamplingOrder.defaultMilestones()

        // 6. Finishing Deposits
        val finishingDeposits = SamplingFinishingDepositsTable.selectAll()
            .where { SamplingFinishingDepositsTable.samplingOrderId eq orderId }
            .orderBy(SamplingFinishingDepositsTable.depositDate, SortOrder.ASC)
            .map { row ->
                FinishingDeposit(
                    id = row[SamplingFinishingDepositsTable.id],
                    samplingOrderId = row[SamplingFinishingDepositsTable.samplingOrderId],
                    depositDate = row[SamplingFinishingDepositsTable.depositDate],
                    qtyPcs = row[SamplingFinishingDepositsTable.qtyPcs],
                    weightKg = row[SamplingFinishingDepositsTable.weightKg],
                    scalePhotoKey = row[SamplingFinishingDepositsTable.scalePhotoKey],
                    garmentPhotoKey = row[SamplingFinishingDepositsTable.garmentPhotoKey],
                    operatorName = row[SamplingFinishingDepositsTable.operatorName],
                    notes = row[SamplingFinishingDepositsTable.notes],
                    createdAt = row[SamplingFinishingDepositsTable.createdAt]
                )
            }

        // 7. QC Inspections
        val qcInspections = SamplingQcInspectionsTable.selectAll()
            .where { SamplingQcInspectionsTable.samplingOrderId eq orderId }
            .orderBy(SamplingQcInspectionsTable.inspectedAt, SortOrder.ASC)
            .map { row ->
                val pomList = (JsonParser.parse(row[SamplingQcInspectionsTable.measuredPomValues]) as? JsonValue.Arr)
                    ?.items?.mapNotNull { item ->
                        val obj = item as? JsonValue.Obj ?: return@mapNotNull null
                        QcPomMeasurement(
                            pomName = obj.string("pomName") ?: "",
                            targetCm = obj.double("targetCm") ?: 0.0,
                            actualCm = obj.double("actualCm") ?: 0.0,
                            toleranceCm = obj.double("toleranceCm") ?: 1.0,
                            notes = obj.string("notes") ?: "",
                            carriedOver = obj.boolean("carriedOver") ?: false
                        )
                    } ?: emptyList()
                val defects = (JsonParser.parse(row[SamplingQcInspectionsTable.defectsFound]) as? JsonValue.Arr)
                    ?.items?.mapNotNull { (it as? JsonValue.Str)?.value } ?: emptyList()
                val qcResult = runCatching { QcInspectionResult.valueOf(row[SamplingQcInspectionsTable.qcResult]) }.getOrNull() ?: QcInspectionResult.PASSED

                QcInspectionReport(
                    id = row[SamplingQcInspectionsTable.id],
                    samplingOrderId = row[SamplingQcInspectionsTable.samplingOrderId],
                    kind = runCatching { QcInspectionKind.valueOf(row[SamplingQcInspectionsTable.kind]) }
                        .getOrDefault(QcInspectionKind.FINISHING),
                    inspectorName = row[SamplingQcInspectionsTable.inspectorName],
                    inspectedAt = row[SamplingQcInspectionsTable.inspectedAt],
                    inspectedQty = row[SamplingQcInspectionsTable.inspectedQty],
                    pieceNo = row[SamplingQcInspectionsTable.pieceNo],
                    pomMeasurements = pomList,
                    defectsFound = defects,
                    qcResult = qcResult,
                    qcNotes = row[SamplingQcInspectionsTable.qcNotes],
                    verifiedPhotoFrontKey = row[SamplingQcInspectionsTable.verifiedPhotoFrontKey],
                    verifiedPhotoBackKey = row[SamplingQcInspectionsTable.verifiedPhotoBackKey]
                )
            }

        val vendorInfo = MakloonVendorInfo(
            vendorName = orderRow[SamplingOrdersTable.vendorName] ?: "",
            vendorPhone = orderRow[SamplingOrdersTable.vendorPhone] ?: "",
            sentAt = orderRow[SamplingOrdersTable.vendorSentAt],
            expectedReturnAt = orderRow[SamplingOrdersTable.vendorTargetAt],
            returnedAt = orderRow[SamplingOrdersTable.vendorReturnedAt],
            costPerPcsIdr = orderRow[SamplingOrdersTable.vendorCostPerPcs],
            status = runCatching { VendorFollowUpStatus.valueOf(orderRow[SamplingOrdersTable.vendorStatus]) }.getOrNull() ?: VendorFollowUpStatus.NONE,
            notes = orderRow[SamplingOrdersTable.vendorNotes]
        )

        return SamplingOrder(
            id = SamplingOrderId(orderId),
            tenantId = TenantId(orderRow[SamplingOrdersTable.tenantId]),
            spkNumber = SpkNumber(orderRow[SamplingOrdersTable.spkNumber]),
            clientName = orderRow[SamplingOrdersTable.clientName],
            styleName = orderRow[SamplingOrdersTable.styleName],
            status = runCatching { SamplingStatus.valueOf(orderRow[SamplingOrdersTable.status]) }.getOrNull() ?: SamplingStatus.DRAFT,
            pipelineStage = runCatching { SamplingPipelineStage.valueOf(orderRow[SamplingOrdersTable.pipelineStage]) }.getOrNull() ?: SamplingPipelineStage.NEW_INTAKE,
            finishingPath = runCatching { FinishingPath.valueOf(orderRow[SamplingOrdersTable.finishingPath]) }.getOrNull() ?: FinishingPath.INTERNAL,
            vendorInfo = vendorInfo,
            sizeMode = runCatching { SizeMode.valueOf(orderRow[SamplingOrdersTable.sizeMode]) }.getOrNull() ?: SizeMode.ALL_SIZE,
            deadlineProgram = orderRow[SamplingOrdersTable.deadlineProgram],
            deadlineFinishing = orderRow[SamplingOrdersTable.deadlineFinishing],
            deadlineDelivery = orderRow[SamplingOrdersTable.deadlineDelivery],
            leadId = orderRow[SamplingOrdersTable.leadId],
            dealId = orderRow[SamplingOrdersTable.dealId],
            sampleQuantity = orderRow[SamplingOrdersTable.sampleQuantity],
            courierTracking = orderRow[SamplingOrdersTable.courierTracking],
            samplingFeeIdr = orderRow[SamplingOrdersTable.samplingFeeIdr],
            revisionCount = orderRow[SamplingOrdersTable.revisionCount],
            revisionHistory = parseRevisionHistory(
                raw = orderRow[SamplingOrdersTable.revisionHistory],
                fallbackAt = orderRow[SamplingOrdersTable.updatedAt]
            ),
            accNotes = orderRow[SamplingOrdersTable.accNotes],
            notes = orderRow[SamplingOrdersTable.notes],
            knitSpec = knitSpec,
            finishedSizeCharts = finishedSizes,
            rawKnitSizeCharts = rawKnitSizes,
            sizeMatrix = parseSizeMatrix(orderRow[SamplingOrdersTable.sizeMatrix]),
            stageInputs = StageWorkInputCodec.decodeInputs(orderRow[SamplingOrdersTable.stageInputs]),
            stageHistory = StageWorkInputCodec.decodeHistory(
                raw = orderRow[SamplingOrdersTable.stageHistory],
                fallbackAt = orderRow[SamplingOrdersTable.updatedAt]
            ),
            machineProgram = machineProgram,
            yieldAndTiming = yieldAndTiming,
            finishingDeposits = finishingDeposits,
            qcInspections = qcInspections,
            milestones = milestones,
            createdAt = orderRow[SamplingOrdersTable.createdAt],
            updatedAt = orderRow[SamplingOrdersTable.updatedAt],
            archivedAt = orderRow[SamplingOrdersTable.archivedAt]
        )
    }

    /** Jsonb `size_matrix` -> daftar baris ukuran; fallback ke default jika kosong. */
    private fun parseSizeMatrix(raw: String?): List<SizeChartRow> =
        runCatching {
            if (raw.isNullOrBlank()) return@runCatching defaultSamplingSizeMatrix()
            JsonParser.parseArray(raw)
                .filterIsInstance<JsonValue.Obj>()
                .map(::parseSizeChartRow)
                .ifEmpty { defaultSamplingSizeMatrix() }.let(::ensureSamplingQtyRow)
        }.getOrDefault(defaultSamplingSizeMatrix()).let(::ensureSamplingQtyRow)

    /** Satu baris jsonb size_matrix -> SizeChartRow (dipakai parseSizeMatrix & snapshot revisi). */
    private fun parseSizeChartRow(rowObj: JsonValue.Obj): SizeChartRow {
        val valuesMap = mutableMapOf<String, String>()
        rowObj.obj("values")?.entries?.forEach { (k, v) ->
            when (v) {
                is JsonValue.Str -> valuesMap[k] = v.value
                is JsonValue.Num -> valuesMap[k] = v.raw
                else -> Unit
            }
        }
        return SizeChartRow(
            id = rowObj.string("id") ?: "",
            pomName = rowObj.string("pomName") ?: "",
            values = valuesMap
        )
    }

    /** Satu bentuk encoding size_matrix untuk insert, update, dan snapshot revisi. */
    private fun sizeMatrixJson(rows: List<SizeChartRow>) =
        jsonArrayOf(rows.map { row ->
            jsonObjectOf(
                "id" to jsonOf(row.id),
                "pomName" to jsonOf(row.pomName),
                "values" to jsonStringMapOf(row.values)
            )
        })

    private fun encodeRevisionHistory(history: List<RevisionFeedback>): String =
        jsonArrayOf(history.map { entry ->
            val pairs = mutableListOf(
                "revision" to jsonOf(entry.revision),
                "notes" to jsonOf(entry.notes),
                "at" to jsonOf(entry.at.toString())
            )
            entry.snapshot?.let { snap ->
                pairs.add("snapshot" to jsonObjectOf(
                    "mockupFrontKey" to jsonOf(snap.mockupFrontKey),
                    "mockupBackKey" to jsonOf(snap.mockupBackKey),
                    "sampleQuantity" to jsonOf(snap.sampleQuantity),
                    "samplingFeeIdr" to jsonOf(snap.samplingFeeIdr),
                    "notes" to jsonOf(snap.notes),
                    "sizeMatrix" to sizeMatrixJson(snap.sizeMatrix)
                ))
            }
            JsonValue.Obj(pairs.toMap())
        }).encode()

    /** Jsonb `revision_history` -> daftar feedback + snapshot; data rusak/lama = daftar kosong, bukan error. */
    private fun parseRevisionHistory(raw: String, fallbackAt: Instant): List<RevisionFeedback> =
        runCatching {
            JsonParser.parseArray(raw)
                .filterIsInstance<JsonValue.Obj>()
                .mapNotNull { entry ->
                    val at = runCatching { Instant.parse(entry.string("at") ?: "") }
                        .getOrDefault(fallbackAt)
                    val revision = entry.int("revision") ?: return@mapNotNull null
                    val snapObj = entry.obj("snapshot")
                    val snap = snapObj?.let { sObj ->
                        val snapMatrix = sObj.objectArray("sizeMatrix")
                            .map(::parseSizeChartRow)
                            .ifEmpty { defaultSamplingSizeMatrix() }.let(::ensureSamplingQtyRow)
                        SamplingSnapshot(
                            mockupFrontKey = sObj.string("mockupFrontKey"),
                            mockupBackKey = sObj.string("mockupBackKey"),
                            sizeMatrix = snapMatrix,
                            sampleQuantity = sObj.int("sampleQuantity") ?: 1,
                            samplingFeeIdr = sObj.long("samplingFeeIdr") ?: 0L,
                            notes = sObj.string("notes") ?: ""
                        )
                    }
                    RevisionFeedback(
                        revision = revision,
                        notes = entry.string("notes") ?: "",
                        at = at,
                        snapshot = snap
                    )
                }
        }.getOrDefault(emptyList())

    private fun toSizeMeasurement(row: ResultRow): SizeMeasurement = SizeMeasurement(
        sizeLabel = row[SamplingSizeChartsTable.sizeLabel],
        bodyLength = row[SamplingSizeChartsTable.bodyLength],
        bodyWidth = row[SamplingSizeChartsTable.bodyWidth],
        sleeveLength = row[SamplingSizeChartsTable.sleeveLength],
        armHole = row[SamplingSizeChartsTable.armHole],
        neckDrop = row[SamplingSizeChartsTable.neckDrop],
        neckWidth = row[SamplingSizeChartsTable.neckWidth],
        shoulderWidth = row[SamplingSizeChartsTable.shoulderWidth],
        ribHeight = row[SamplingSizeChartsTable.ribHeight],
        collarHeight = row[SamplingSizeChartsTable.collarHeight],
        placketWidth = row[SamplingSizeChartsTable.placketWidth],
        sleeveOpening = row[SamplingSizeChartsTable.sleeveOpening]
    )
}
