package com.eventverse.app.infrastructure.tables

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

/**
 * Custom field definitions shared across every operational module — see V20.
 * `config` carries options/format/maxCount depending on `fieldType`, decoded by
 * [com.eventverse.app.domain.customfield.CustomAttributesCodec].
 */
object CustomFieldDefinitionsTable : Table("custom_field_definitions") {
    val id = varchar("id", 64)
    val tenantId = varchar("tenant_id", 64).references(TenantsTable.id)
    val ownerResource = varchar("owner_resource", 64)
    val fieldKey = varchar("field_key", 64)
    val label = varchar("label", 120)
    val fieldType = varchar("field_type", 32)
    val config = jsonbText("config").default("{}")
    val position = double("position")
    val isRequired = bool("is_required").default(false)
    val requiredSince = timestamp("required_since").nullable()
    val defaultValue = jsonbText("default_value").nullable()
    val isSystem = bool("is_system").default(false)
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")
    val archivedAt = timestamp("archived_at").nullable()

    override val primaryKey = PrimaryKey(id)
}

/**
 * Referential-integrity side table for USER_REF (people) custom fields — see V20. Written
 * in the same transaction as the owning entity's `custom_attributes` blob so the two never
 * diverge.
 */
object CustomFieldLinksTable : Table("custom_field_links") {
    val tenantId = varchar("tenant_id", 64).references(TenantsTable.id)
    val ownerResource = varchar("owner_resource", 64)
    val ownerRecordId = varchar("owner_record_id", 64)
    val fieldId = varchar("field_id", 64).references(CustomFieldDefinitionsTable.id)
    val ordinal = short("ordinal").default(0)
    val targetEmployeeId = varchar("target_employee_id", 64).references(EmployeesTable.id)

    override val primaryKey = PrimaryKey(ownerResource, ownerRecordId, fieldId, ordinal)
}
