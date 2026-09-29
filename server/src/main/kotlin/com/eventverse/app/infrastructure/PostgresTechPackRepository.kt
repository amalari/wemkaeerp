package com.eventverse.app.infrastructure

import com.eventverse.app.infrastructure.tables.TechPacksTable
import com.eventverse.app.infrastructure.tables.TechPackStyleSequencesTable
import com.eventverse.app.domain.common.Quantity
import com.eventverse.app.domain.common.Ratio
import com.eventverse.app.domain.common.UnitOfMeasure
import com.eventverse.app.domain.contracts.BomLine
import com.eventverse.app.domain.contracts.LaborOperation
import com.eventverse.app.domain.contracts.MaterialRef
import com.eventverse.app.domain.contracts.SizeYieldFactor
import com.eventverse.app.domain.customfield.CustomAttributes
import com.eventverse.app.domain.masterdata.MaterialCategory
import com.eventverse.app.domain.masterdata.MaterialCode
import com.eventverse.app.domain.masterdata.MaterialId
import com.eventverse.app.domain.pipeline.StockOwnershipSemantics
import com.eventverse.app.domain.techpack.*
import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.infrastructure.tables.*
import com.eventverse.app.shared.json.JsonParser
import com.eventverse.app.shared.json.JsonValue
import kotlinx.datetime.Clock
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.isNull
import org.jetbrains.exposed.sql.SqlExpressionBuilder.like
import org.jetbrains.exposed.sql.transactions.TransactionManager

class PostgresTechPackRepository : TechPackRepository {

    override suspend fun findById(tenantId: TenantId, id: TechPackId): TechPack? =
        DatabaseFactory.dbQuery(tenantId) {
            val row = TechPacksTable.selectAll()
                .where { (TechPacksTable.tenantId eq tenantId.value) and (TechPacksTable.id eq id.value) }
                .singleOrNull() ?: return@dbQuery null

            loadSingleTechPack(row)
        }

    override suspend fun findVersions(tenantId: TenantId, styleCode: StyleCode): List<TechPack> =
        DatabaseFactory.dbQuery(tenantId) {
            val rows = TechPacksTable.selectAll()
                .where { (TechPacksTable.tenantId eq tenantId.value) and (TechPacksTable.styleCode eq styleCode.value) }
                .orderBy(TechPacksTable.version, SortOrder.DESC)
                .toList()

            loadTechPacksBatch(rows)
        }

    override suspend fun findLatestReleased(tenantId: TenantId, styleCode: StyleCode): TechPack? =
        DatabaseFactory.dbQuery(tenantId) {
            val row = TechPacksTable.selectAll()
                .where {
                    (TechPacksTable.tenantId eq tenantId.value) and
                    (TechPacksTable.styleCode eq styleCode.value) and
                    (TechPacksTable.status eq TechPackStatus.RELEASED.name) and
                    TechPacksTable.archivedAt.isNull()
                }
                .orderBy(TechPacksTable.version, SortOrder.DESC)
                .limit(1)
                .singleOrNull() ?: return@dbQuery null

            loadSingleTechPack(row)
        }

    override suspend fun findBySourceSample(tenantId: TenantId, sourceSampleSpecId: String): TechPack? =
        DatabaseFactory.dbQuery(tenantId) {
            val row = TechPacksTable.selectAll()
                .where {
                    (TechPacksTable.tenantId eq tenantId.value) and
                    (TechPacksTable.sourceSampleSpecId eq sourceSampleSpecId) and
                    TechPacksTable.archivedAt.isNull()
                }
                .orderBy(TechPacksTable.version, SortOrder.DESC)
                .limit(1)
                .singleOrNull() ?: return@dbQuery null

            loadSingleTechPack(row)
        }

    override suspend fun search(tenantId: TenantId, query: TechPackQuery): TechPackPage =
        DatabaseFactory.dbQuery(tenantId) {
            var condition: Op<Boolean> = TechPacksTable.tenantId eq tenantId.value

            if (!query.includeArchived) {
                condition = condition and TechPacksTable.archivedAt.isNull()
            }

            val qStatus = query.status
            if (qStatus != null) {
                condition = condition and (TechPacksTable.status eq qStatus.name)
            }

            val qStyle = query.styleCode
            if (qStyle != null) {
                condition = condition and (TechPacksTable.styleCode eq qStyle.value)
            }

            val queryText = query.queryText
            if (!queryText.isNullOrBlank()) {
                val q = "%${queryText.trim().lowercase()}%"
                val textMatch = (TechPacksTable.styleCode.lowerCase().like(q)) or
                    (TechPacksTable.styleName.lowerCase().like(q)) or
                    (TechPacksTable.clientName.lowerCase().like(q))
                condition = condition and textMatch
            }

            if (query.latestVersionOnly) {
                val latestVersionOp = object : Op<Boolean>() {
                    override fun toQueryBuilder(queryBuilder: QueryBuilder) {
                        queryBuilder.append("tech_packs.version = (SELECT MAX(tp_inner.version) FROM ${TechPacksTable.tableName} tp_inner WHERE tp_inner.tenant_id = tech_packs.tenant_id AND tp_inner.style_code = tech_packs.style_code")
                        if (!query.includeArchived) {
                            queryBuilder.append(" AND tp_inner.archived_at IS NULL")
                        }
                        queryBuilder.append(")")
                    }
                }
                condition = condition and latestVersionOp
            }

            val totalCount = TechPacksTable.selectAll().where { condition }.count()
            val offset = ((query.page - 1).coerceAtLeast(0) * query.pageSize).toLong()

            val rows = TechPacksTable.selectAll()
                .where { condition }
                .orderBy(TechPacksTable.updatedAt, SortOrder.DESC)
                .limit(query.pageSize)
                .offset(offset)
                .toList()

            val items = loadTechPacksBatch(rows)

            TechPackPage(
                items = items,
                totalCount = totalCount,
                page = query.page,
                pageSize = query.pageSize
            )
        }

    override suspend fun save(techPack: TechPack): TechPack =
        DatabaseFactory.dbQuery(techPack.tenantId) {
            saveInternal(techPack)
            techPack
        }

    override suspend fun saveRevision(oldVersion: TechPack, newVersion: TechPack): TechPack =
        DatabaseFactory.dbQuery(newVersion.tenantId) {
            saveInternal(oldVersion)
            saveInternal(newVersion)
            newVersion
        }

    private fun saveInternal(techPack: TechPack) {
        val existing = TechPacksTable.selectAll()
            .where { (TechPacksTable.tenantId eq techPack.tenantId.value) and (TechPacksTable.id eq techPack.id.value) }
            .singleOrNull()

        val customAttrsJson = techPack.customAttributes.toJsonValue().encode()

        if (existing == null) {
            TechPacksTable.insert {
                it[id] = techPack.id.value
                it[tenantId] = techPack.tenantId.value
                it[styleCode] = techPack.styleCode.value
                it[styleName] = techPack.styleName
                it[clientName] = techPack.clientName
                it[status] = techPack.status.name
                it[version] = techPack.version
                it[sourceSampleSpecId] = techPack.sourceSampleSpecId
                it[sourceSpkNumber] = techPack.sourceSpkNumber
                it[customAttributes] = customAttrsJson
                it[notes] = techPack.notes
                it[createdByUserId] = techPack.createdByUserId
                it[createdAt] = techPack.createdAt
                it[updatedAt] = techPack.updatedAt
                it[releasedAt] = techPack.releasedAt
                it[archivedAt] = techPack.archivedAt
            }
        } else {
            TechPacksTable.update({ (TechPacksTable.tenantId eq techPack.tenantId.value) and (TechPacksTable.id eq techPack.id.value) }) {
                it[styleName] = techPack.styleName
                it[clientName] = techPack.clientName
                it[status] = techPack.status.name
                it[version] = techPack.version
                it[sourceSampleSpecId] = techPack.sourceSampleSpecId
                it[sourceSpkNumber] = techPack.sourceSpkNumber
                it[customAttributes] = customAttrsJson
                it[notes] = techPack.notes
                it[updatedAt] = techPack.updatedAt
                it[releasedAt] = techPack.releasedAt
                it[archivedAt] = techPack.archivedAt
            }
        }

        // Child table 1: BOM Lines
        TechPackBomLinesTable.deleteWhere { (tenantId eq techPack.tenantId.value) and (techPackId eq techPack.id.value) }
        techPack.bomLines.forEachIndexed { idx, line ->
            TechPackBomLinesTable.insert {
                it[id] = "${techPack.id.value}_${line.lineId}"
                it[tenantId] = techPack.tenantId.value
                it[techPackId] = techPack.id.value
                it[lineId] = line.lineId
                it[materialFreeText] = line.material.freeText
                it[materialId] = line.material.materialId?.value
                it[materialCode] = line.material.resolvedCode?.value
                it[materialName] = line.material.resolvedName
                it[category] = line.category.name
                it[netQuantityMicros] = line.netQuantityPerGarment.micros
                it[netUom] = line.netQuantityPerGarment.uom.name
                it[wasteNumerator] = line.wasteAllowance.numerator
                it[wasteDenominator] = line.wasteAllowance.denominator
                it[ownership] = line.ownership.code
                it[notes] = line.notes
                it[sortOrder] = idx
            }
        }

        // Child table 2: Labor Operations
        TechPackLaborOperationsTable.deleteWhere { (tenantId eq techPack.tenantId.value) and (techPackId eq techPack.id.value) }
        techPack.laborOperations.forEachIndexed { idx, op ->
            TechPackLaborOperationsTable.insert {
                it[id] = "${techPack.id.value}_${op.operationId}"
                it[tenantId] = techPack.tenantId.value
                it[techPackId] = techPack.id.value
                it[operationId] = op.operationId
                it[name] = op.name
                it[samNumerator] = op.samMinutes.numerator
                it[samDenominator] = op.samMinutes.denominator
                it[workstation] = op.workstation
                it[isSubcontracted] = op.isSubcontracted
                it[sortOrder] = idx
            }
        }

        // Child table 3: Size Yields
        TechPackSizeYieldsTable.deleteWhere { (tenantId eq techPack.tenantId.value) and (techPackId eq techPack.id.value) }
        techPack.sizeYieldFactors.forEach { factor ->
            TechPackSizeYieldsTable.insert {
                it[id] = "${techPack.id.value}_${factor.sizeLabel}"
                it[tenantId] = techPack.tenantId.value
                it[techPackId] = techPack.id.value
                it[sizeLabel] = factor.sizeLabel
                it[scaleNumerator] = factor.scale.numerator
                it[scaleDenominator] = factor.scale.denominator
                it[orderedQuantity] = factor.orderedQuantity
            }
        }
    }

    override suspend fun archive(tenantId: TenantId, id: TechPackId): Boolean =
        DatabaseFactory.dbQuery(tenantId) {
            val now = Clock.System.now()
            val count = TechPacksTable.update({ (TechPacksTable.tenantId eq tenantId.value) and (TechPacksTable.id eq id.value) }) {
                it[archivedAt] = now
                it[updatedAt] = now
            }
            count > 0
        }

    override suspend fun reserveNextStyleCode(tenantId: TenantId, prefix: String): StyleCode =
        DatabaseFactory.dbQuery(tenantId) {
            val cleanPrefix = prefix.trim().ifBlank { "STY" }
            val sql = """
                INSERT INTO ${TechPackStyleSequencesTable.tableName} (tenant_id, prefix, current_seq)
                VALUES ('${tenantId.value}', '$cleanPrefix', 1)
                ON CONFLICT (tenant_id, prefix)
                DO UPDATE SET current_seq = tech_pack_style_sequences.current_seq + 1
                RETURNING current_seq
            """.trimIndent()

            val seq = TransactionManager.current().exec(sql) { rs ->
                if (rs.next()) rs.getLong("current_seq") else 1L
            } ?: 1L

            StyleCode("$cleanPrefix-${seq.toString().padStart(4, '0')}")
        }

    private fun loadSingleTechPack(row: ResultRow): TechPack {
        return loadTechPacksBatch(listOf(row)).first()
    }

    private fun loadTechPacksBatch(rows: List<ResultRow>): List<TechPack> {
        if (rows.isEmpty()) return emptyList()

        val techPackIds = rows.map { it[TechPacksTable.id] }

        val bomLinesMap = TechPackBomLinesTable.selectAll()
            .where { TechPackBomLinesTable.techPackId inList techPackIds }
            .orderBy(TechPackBomLinesTable.sortOrder, SortOrder.ASC)
            .map { row ->
                val tpId = row[TechPackBomLinesTable.techPackId]
                val matId = row[TechPackBomLinesTable.materialId]?.let { MaterialId(it) }
                val matCode = row[TechPackBomLinesTable.materialCode]?.let { MaterialCode(it) }
                val matName = row[TechPackBomLinesTable.materialName]
                val freeText = row[TechPackBomLinesTable.materialFreeText]

                val materialRef = if (matId != null && matCode != null && matName != null) {
                    MaterialRef.resolved(matId, matCode, matName)
                } else {
                    MaterialRef(freeText = freeText, materialId = matId, resolvedCode = matCode, resolvedName = matName)
                }

                val category = MaterialCategory.fromCode(row[TechPackBomLinesTable.category])
                    ?: runCatching { MaterialCategory.valueOf(row[TechPackBomLinesTable.category]) }.getOrDefault(MaterialCategory.YARN)

                val uom = UnitOfMeasure.fromCode(row[TechPackBomLinesTable.netUom])
                    ?: runCatching { UnitOfMeasure.valueOf(row[TechPackBomLinesTable.netUom]) }.getOrDefault(UnitOfMeasure.PIECE)

                val ownershipCode = row[TechPackBomLinesTable.ownership]
                val ownership = StockOwnershipSemantics.entries.firstOrNull {
                    it.code == ownershipCode || it.name.equals(ownershipCode, ignoreCase = true)
                } ?: StockOwnershipSemantics.OWNED_RAW_MATERIAL

                val line = BomLine(
                    lineId = row[TechPackBomLinesTable.lineId],
                    material = materialRef,
                    category = category,
                    netQuantityPerGarment = Quantity(row[TechPackBomLinesTable.netQuantityMicros], uom),
                    wasteAllowance = Ratio(row[TechPackBomLinesTable.wasteNumerator], row[TechPackBomLinesTable.wasteDenominator]),
                    ownership = ownership,
                    notes = row[TechPackBomLinesTable.notes]
                )
                tpId to line
            }
            .groupBy({ it.first }, { it.second })

        val laborOpsMap = TechPackLaborOperationsTable.selectAll()
            .where { TechPackLaborOperationsTable.techPackId inList techPackIds }
            .orderBy(TechPackLaborOperationsTable.sortOrder, SortOrder.ASC)
            .map { row ->
                val tpId = row[TechPackLaborOperationsTable.techPackId]
                val op = LaborOperation(
                    operationId = row[TechPackLaborOperationsTable.operationId],
                    name = row[TechPackLaborOperationsTable.name],
                    samMinutes = Ratio(row[TechPackLaborOperationsTable.samNumerator], row[TechPackLaborOperationsTable.samDenominator]),
                    workstation = row[TechPackLaborOperationsTable.workstation],
                    isSubcontracted = row[TechPackLaborOperationsTable.isSubcontracted]
                )
                tpId to op
            }
            .groupBy({ it.first }, { it.second })

        val sizeYieldsMap = TechPackSizeYieldsTable.selectAll()
            .where { TechPackSizeYieldsTable.techPackId inList techPackIds }
            .map { row ->
                val tpId = row[TechPackSizeYieldsTable.techPackId]
                val yield = SizeYieldFactor(
                    sizeLabel = row[TechPackSizeYieldsTable.sizeLabel],
                    scale = Ratio(row[TechPackSizeYieldsTable.scaleNumerator], row[TechPackSizeYieldsTable.scaleDenominator]),
                    orderedQuantity = row[TechPackSizeYieldsTable.orderedQuantity]
                )
                tpId to yield
            }
            .groupBy({ it.first }, { it.second })

        return rows.map { row ->
            val id = row[TechPacksTable.id]
            val customAttrsRaw = row[TechPacksTable.customAttributes]
            val customAttrs = runCatching {
                val json = (JsonParser.parse(customAttrsRaw) as? JsonValue.Obj) ?: JsonValue.Obj(emptyMap())
                CustomAttributes.fromJsonValue(json)
            }.getOrDefault(CustomAttributes.EMPTY)

            val statusStr = row[TechPacksTable.status]
            val status = TechPackStatus.fromCode(statusStr) ?: TechPackStatus.DRAFT

            TechPack(
                id = TechPackId(id),
                tenantId = TenantId(row[TechPacksTable.tenantId]),
                styleCode = StyleCode(row[TechPacksTable.styleCode]),
                styleName = row[TechPacksTable.styleName],
                clientName = row[TechPacksTable.clientName],
                status = status,
                version = row[TechPacksTable.version],
                sourceSampleSpecId = row[TechPacksTable.sourceSampleSpecId],
                sourceSpkNumber = row[TechPacksTable.sourceSpkNumber],
                bomLines = bomLinesMap[id] ?: emptyList(),
                laborOperations = laborOpsMap[id] ?: emptyList(),
                sizeYieldFactors = sizeYieldsMap[id] ?: emptyList(),
                customAttributes = customAttrs,
                notes = row[TechPacksTable.notes],
                createdByUserId = row[TechPacksTable.createdByUserId],
                createdAt = row[TechPacksTable.createdAt],
                updatedAt = row[TechPacksTable.updatedAt],
                releasedAt = row[TechPacksTable.releasedAt],
                archivedAt = row[TechPacksTable.archivedAt]
            )
        }
    }
}
