package com.eventverse.app.infrastructure

import com.eventverse.app.domain.common.UnitOfMeasure
import com.eventverse.app.domain.customfield.CustomAttributes
import com.eventverse.app.domain.masterdata.*
import com.eventverse.app.domain.pipeline.StockOwnershipSemantics
import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.infrastructure.tables.MaterialItemsTable
import com.eventverse.app.shared.json.JsonParser
import com.eventverse.app.shared.json.JsonValue
import com.eventverse.app.shared.json.jsonArrayOf
import com.eventverse.app.shared.masterdata.MaterialItemCodec
import kotlinx.datetime.Clock
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.SqlExpressionBuilder.isNull
import org.jetbrains.exposed.sql.SqlExpressionBuilder.like
import org.jetbrains.exposed.sql.transactions.TransactionManager

class PostgresMaterialItemRepository : MaterialItemRepository {

    override suspend fun findById(tenantId: TenantId, id: MaterialId): MaterialItem? =
        DatabaseFactory.dbQuery(tenantId) {
            MaterialItemsTable.selectAll()
                .where { (MaterialItemsTable.tenantId eq tenantId.value) and (MaterialItemsTable.id eq id.value) }
                .map(::toItem)
                .singleOrNull()
        }

    override suspend fun findByCode(tenantId: TenantId, code: MaterialCode): MaterialItem? =
        DatabaseFactory.dbQuery(tenantId) {
            MaterialItemsTable.selectAll()
                .where {
                    (MaterialItemsTable.tenantId eq tenantId.value) and
                        (MaterialItemsTable.code eq code.value) and
                        (MaterialItemsTable.archivedAt.isNull())
                }
                .map(::toItem)
                .singleOrNull()
        }

    override suspend fun findAllByIds(tenantId: TenantId, ids: Collection<MaterialId>): List<MaterialItem> =
        DatabaseFactory.dbQuery(tenantId) {
            if (ids.isEmpty()) return@dbQuery emptyList()
            MaterialItemsTable.selectAll()
                .where {
                    (MaterialItemsTable.tenantId eq tenantId.value) and
                        (MaterialItemsTable.id inList ids.map { it.value })
                }
                .map(::toItem)
        }

    override suspend fun searchCatalog(tenantId: TenantId, query: MaterialCatalogQuery): MaterialCatalogPage =
        DatabaseFactory.dbQuery(tenantId) {
            var condition: Op<Boolean> = MaterialItemsTable.tenantId eq tenantId.value

            if (!query.includeArchived) {
                condition = condition and MaterialItemsTable.archivedAt.isNull()
            }
            val cat = query.category
            if (cat != null) {
                condition = condition and (MaterialItemsTable.category eq cat.code)
            }
            val own = query.ownership
            if (own != null) {
                condition = condition and (MaterialItemsTable.defaultOwnership eq own.code)
            }
            val queryText = query.queryText
            if (!queryText.isNullOrBlank()) {
                val q = "%${queryText.trim().lowercase()}%"
                val textMatch = (MaterialItemsTable.name.lowerCase().like(q)) or
                    (MaterialItemsTable.code.lowerCase().like(q))
                condition = condition and textMatch
            }

            val totalCount = MaterialItemsTable.selectAll().where { condition }.count()
            val offset = ((query.page - 1) * query.pageSize).toLong()

            val items = MaterialItemsTable.selectAll()
                .where { condition }
                .orderBy(MaterialItemsTable.updatedAt, SortOrder.DESC)
                .limit(query.pageSize)
                .offset(offset)
                .map(::toItem)

            MaterialCatalogPage(
                items = items,
                totalCount = totalCount,
                page = query.page,
                pageSize = query.pageSize
            )
        }

    override suspend fun matchByFreeText(
        tenantId: TenantId,
        freeText: String,
        category: MaterialCategory?
    ): List<MaterialItem> = DatabaseFactory.dbQuery(tenantId) {
        val trimmed = freeText.trim()
        if (trimmed.isBlank()) return@dbQuery emptyList()

        var condition: Op<Boolean> = (MaterialItemsTable.tenantId eq tenantId.value) and
            MaterialItemsTable.archivedAt.isNull()

        if (category != null) {
            condition = condition and (MaterialItemsTable.category eq category.code)
        }

        val q = "%${trimmed.lowercase()}%"
        val textMatch = (MaterialItemsTable.name.lowerCase().like(q)) or
            (MaterialItemsTable.code.lowerCase().like(q))
        condition = condition and textMatch

        MaterialItemsTable.selectAll()
            .where { condition }
            .limit(10)
            .map(::toItem)
            .sortedWith(
                compareByDescending<MaterialItem> { it.code.value.equals(trimmed, ignoreCase = true) }
                    .thenByDescending { it.name.equals(trimmed, ignoreCase = true) }
            )
    }

    override suspend fun reserveNextCode(tenantId: TenantId, category: MaterialCategory): MaterialCode =
        DatabaseFactory.dbQuery(tenantId) {
            val prefix = category.codePrefix
            val sql = """
                INSERT INTO material_code_sequences (tenant_id, category_code, current_seq)
                VALUES ('${tenantId.value}', '$prefix', 1)
                ON CONFLICT (tenant_id, category_code)
                DO UPDATE SET current_seq = material_code_sequences.current_seq + 1
                RETURNING current_seq
            """.trimIndent()

            val seq = TransactionManager.current().exec(sql) { rs ->
                if (rs.next()) rs.getLong("current_seq") else 1L
            } ?: 1L

            MaterialCode("$prefix-${seq.toString().padStart(4, '0')}")
        }

    override suspend fun save(material: MaterialItem): MaterialItem =
        DatabaseFactory.dbQuery(material.tenantId) {
            val alternateUomsJson = jsonArrayOf(
                material.alternateUoms.map(MaterialItemCodec::encodeConversion)
            ).encode()

            val customAttrsJson = material.customAttributes.toJsonValue().encode()

            val existing = MaterialItemsTable.selectAll()
                .where { (MaterialItemsTable.tenantId eq material.tenantId.value) and (MaterialItemsTable.id eq material.id.value) }
                .singleOrNull()

            if (existing != null) {
                MaterialItemsTable.update({
                    (MaterialItemsTable.tenantId eq material.tenantId.value) and (MaterialItemsTable.id eq material.id.value)
                }) {
                    it[name] = material.name
                    it[category] = material.category.code
                    it[baseUom] = material.baseUom.code
                    it[alternateUoms] = alternateUomsJson
                    it[defaultOwnership] = material.defaultOwnership.code
                    it[description] = material.description
                    it[customAttributes] = customAttrsJson
                    it[updatedAt] = material.updatedAt
                    it[archivedAt] = material.archivedAt
                }
            } else {
                MaterialItemsTable.insert {
                    it[id] = material.id.value
                    it[tenantId] = material.tenantId.value
                    it[code] = material.code.value
                    it[name] = material.name
                    it[category] = material.category.code
                    it[baseUom] = material.baseUom.code
                    it[alternateUoms] = alternateUomsJson
                    it[defaultOwnership] = material.defaultOwnership.code
                    it[description] = material.description
                    it[customAttributes] = customAttrsJson
                    it[createdAt] = material.createdAt
                    it[updatedAt] = material.updatedAt
                    it[archivedAt] = material.archivedAt
                }
            }
            material
        }

    override suspend fun archive(tenantId: TenantId, id: MaterialId): Boolean =
        DatabaseFactory.dbQuery(tenantId) {
            val now = Clock.System.now()
            val rows = MaterialItemsTable.update({
                (MaterialItemsTable.tenantId eq tenantId.value) and (MaterialItemsTable.id eq id.value)
            }) {
                it[archivedAt] = now
                it[updatedAt] = now
            }
            rows > 0
        }

    private fun toItem(row: ResultRow): MaterialItem {
        val category = MaterialCategory.fromCode(row[MaterialItemsTable.category]) ?: MaterialCategory.YARN
        val baseUom = UnitOfMeasure.fromCode(row[MaterialItemsTable.baseUom]) ?: category.defaultUom

        val alternateUomsRaw = row[MaterialItemsTable.alternateUoms]
        val alternateUoms = runCatching {
            JsonParser.parseArray(alternateUomsRaw).filterIsInstance<JsonValue.Obj>().map(MaterialItemCodec::decodeConversion)
        }.getOrDefault(emptyList())

        val ownershipCode = row[MaterialItemsTable.defaultOwnership]
        val ownership = StockOwnershipSemantics.entries.firstOrNull {
            it.code == ownershipCode || it.name.equals(ownershipCode, ignoreCase = true)
        } ?: StockOwnershipSemantics.OWNED_RAW_MATERIAL

        val customAttributesRaw = row[MaterialItemsTable.customAttributes]
        val customAttributes = runCatching {
            val parsed = JsonParser.parse(customAttributesRaw) as? JsonValue.Obj
            if (parsed != null) CustomAttributes.fromJsonValue(parsed) else CustomAttributes.EMPTY
        }.getOrDefault(CustomAttributes.EMPTY)

        return MaterialItem(
            id = MaterialId(row[MaterialItemsTable.id]),
            tenantId = TenantId(row[MaterialItemsTable.tenantId]),
            code = MaterialCode(row[MaterialItemsTable.code]),
            name = row[MaterialItemsTable.name],
            category = category,
            baseUom = baseUom,
            alternateUoms = alternateUoms,
            defaultOwnership = ownership,
            description = row[MaterialItemsTable.description],
            customAttributes = customAttributes,
            createdAt = row[MaterialItemsTable.createdAt],
            updatedAt = row[MaterialItemsTable.updatedAt],
            archivedAt = row[MaterialItemsTable.archivedAt]
        )
    }
}
