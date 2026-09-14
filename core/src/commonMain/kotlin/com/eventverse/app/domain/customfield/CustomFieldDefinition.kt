package com.eventverse.app.domain.customfield

import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.shared.json.JsonValue
import kotlinx.datetime.Instant

/**
 * Schema/metadata for one tenant-defined custom field. Holds no values — see the
 * `custom_attributes` column of a consuming entity (e.g. `crm_leads`) for that.
 */
data class CustomFieldDefinition(
    val id: CustomFieldId,
    val tenantId: TenantId,
    val ownerResource: OwnerResource,
    val key: FieldKey,
    val label: String,
    val type: FieldType,
    val position: Double,
    val isRequired: Boolean = false,
    val requiredSince: Instant? = null,
    val defaultValue: JsonValue? = null,
    val isSystem: Boolean = false,
    val archivedAt: Instant? = null
) {
    init {
        require(label.isNotBlank()) { "CustomFieldDefinition.label cannot be blank" }
        require(label.length <= 120) { "CustomFieldDefinition.label must be at most 120 characters" }
    }

    val isArchived: Boolean get() = archivedAt != null

    /** Renaming is display-only: it touches [label], never [key] or [id]. */
    fun rename(newLabel: String): CustomFieldDefinition {
        require(newLabel.isNotBlank()) { "New label cannot be blank" }
        return copy(label = newLabel.trim())
    }

    /**
     * Turns the required flag on, stamping [requiredSince] so existing records are never
     * retroactively invalid — only records created (or edited on this field) from this
     * instant on must supply a value. See [com.eventverse.app.domain.customfield.CustomFieldValidation].
     */
    fun markRequired(at: Instant): CustomFieldDefinition {
        if (isRequired) return this
        return copy(isRequired = true, requiredSince = at)
    }

    fun markOptional(): CustomFieldDefinition = copy(isRequired = false, requiredSince = null)

    /**
     * Soft-archives this field. System fields provisioned by seed are refused here —
     * callers must use [com.eventverse.app.domain.customfield.usecases.ArchiveCustomFieldDefinitionUseCase],
     * which enforces that check before calling this.
     */
    fun archive(at: Instant): CustomFieldDefinition {
        require(!isSystem) { "System field cannot be archived: ${key.value}" }
        return copy(archivedAt = at)
    }

    fun restore(): CustomFieldDefinition = copy(archivedAt = null)
}
