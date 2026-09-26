package com.eventverse.app.infrastructure

import com.eventverse.app.domain.customfield.CustomAttributesCodec
import com.eventverse.app.domain.customfield.CustomFieldDefinition
import com.eventverse.app.domain.customfield.CustomFieldDefinitionRepository
import com.eventverse.app.domain.customfield.CustomFieldId
import com.eventverse.app.domain.customfield.FieldKey
import com.eventverse.app.domain.customfield.OwnerResource
import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.infrastructure.tables.CustomFieldDefinitionsTable
import com.eventverse.app.shared.json.JsonParser
import com.eventverse.app.shared.json.JsonValue
import kotlinx.datetime.Clock
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.isNull
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update

/**
 * PostgreSQL implementation of [CustomFieldDefinitionRepository], shared by every module —
 * see V20. Tenant isolation enforced by Row-Level Security (`DatabaseFactory.dbQuery`).
 */
class PostgresCustomFieldDefinitionRepository : CustomFieldDefinitionRepository {

    override suspend fun findActiveByResource(
        tenantId: TenantId,
        ownerResource: OwnerResource
    ): List<CustomFieldDefinition> = DatabaseFactory.dbQuery(tenantId) {
        CustomFieldDefinitionsTable.selectAll()
            .where {
                (CustomFieldDefinitionsTable.tenantId eq tenantId.value) and
                    (CustomFieldDefinitionsTable.ownerResource eq ownerResource.value) and
                    (CustomFieldDefinitionsTable.archivedAt.isNull())
            }
            .orderBy(CustomFieldDefinitionsTable.position)
            .mapNotNull(::toDefinitionOrNull)
    }

    override suspend fun findById(tenantId: TenantId, id: CustomFieldId): CustomFieldDefinition? =
        DatabaseFactory.dbQuery(tenantId) {
            CustomFieldDefinitionsTable.selectAll()
                .where {
                    (CustomFieldDefinitionsTable.tenantId eq tenantId.value) and
                        (CustomFieldDefinitionsTable.id eq id.value)
                }
                .mapNotNull(::toDefinitionOrNull)
                .singleOrNull()
        }

    override suspend fun save(definition: CustomFieldDefinition): Result<CustomFieldDefinition> = runCatching {
        DatabaseFactory.dbQuery(definition.tenantId) {
            val config = CustomAttributesCodec.encodeConfig(definition.type).encode()
            val defaultValueJson = definition.defaultValue?.encode()

            val updatedRows = CustomFieldDefinitionsTable.update(
                {
                    (CustomFieldDefinitionsTable.tenantId eq definition.tenantId.value) and
                        (CustomFieldDefinitionsTable.id eq definition.id.value)
                }
            ) {
                it[label] = definition.label
                it[fieldType] = definition.type.code
                it[this.config] = config
                it[position] = definition.position
                it[isRequired] = definition.isRequired
                it[requiredSince] = definition.requiredSince
                it[defaultValue] = defaultValueJson
                it[archivedAt] = definition.archivedAt
                it[updatedAt] = Clock.System.now()
            }

            if (updatedRows == 0) {
                CustomFieldDefinitionsTable.insert {
                    it[id] = definition.id.value
                    it[tenantId] = definition.tenantId.value
                    it[ownerResource] = definition.ownerResource.value
                    it[fieldKey] = definition.key.value
                    it[label] = definition.label
                    it[fieldType] = definition.type.code
                    it[this.config] = config
                    it[position] = definition.position
                    it[isRequired] = definition.isRequired
                    it[requiredSince] = definition.requiredSince
                    it[defaultValue] = defaultValueJson
                    it[isSystem] = definition.isSystem
                    it[createdAt] = Clock.System.now()
                    it[updatedAt] = Clock.System.now()
                }
            }
            definition
        }
    }

    override suspend fun existingKeys(tenantId: TenantId, ownerResource: OwnerResource): Set<String> =
        DatabaseFactory.dbQuery(tenantId) {
            CustomFieldDefinitionsTable.selectAll()
                .where {
                    (CustomFieldDefinitionsTable.tenantId eq tenantId.value) and
                        (CustomFieldDefinitionsTable.ownerResource eq ownerResource.value)
                }
                .map { it[CustomFieldDefinitionsTable.fieldKey] }
                .toSet()
        }

    private fun toDefinitionOrNull(row: ResultRow): CustomFieldDefinition? {
        val configObj = JsonParser.parseObjectOrNull(row[CustomFieldDefinitionsTable.config]) ?: JsonValue.Obj(emptyMap())
        val type = CustomAttributesCodec.decodeFieldType(row[CustomFieldDefinitionsTable.fieldType], configObj)
            ?: return null

        return CustomFieldDefinition(
            id = CustomFieldId(row[CustomFieldDefinitionsTable.id]),
            tenantId = TenantId(row[CustomFieldDefinitionsTable.tenantId]),
            ownerResource = OwnerResource(row[CustomFieldDefinitionsTable.ownerResource]),
            key = FieldKey(row[CustomFieldDefinitionsTable.fieldKey]),
            label = row[CustomFieldDefinitionsTable.label],
            type = type,
            position = row[CustomFieldDefinitionsTable.position],
            isRequired = row[CustomFieldDefinitionsTable.isRequired],
            requiredSince = row[CustomFieldDefinitionsTable.requiredSince],
            defaultValue = row[CustomFieldDefinitionsTable.defaultValue]?.let { JsonParser.parse(it) },
            isSystem = row[CustomFieldDefinitionsTable.isSystem],
            archivedAt = row[CustomFieldDefinitionsTable.archivedAt]
        )
    }
}
