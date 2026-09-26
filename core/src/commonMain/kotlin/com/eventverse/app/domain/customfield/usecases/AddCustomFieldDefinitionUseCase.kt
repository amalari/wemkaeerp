package com.eventverse.app.domain.customfield.usecases

import com.eventverse.app.domain.customfield.CustomFieldDefinition
import com.eventverse.app.domain.customfield.CustomFieldDefinitionRepository
import com.eventverse.app.domain.customfield.CustomFieldId
import com.eventverse.app.domain.customfield.FieldKey
import com.eventverse.app.domain.customfield.FieldType
import com.eventverse.app.domain.customfield.OwnerResource
import com.eventverse.app.domain.tenant.TenantId
import kotlinx.datetime.Clock

/**
 * Adds one custom field to a module's schema.
 *
 * The authority check ("tenant admins can edit columns") happens at the route layer via
 * `AccessLevel.MANAGE` — this use case only enforces domain invariants: a unique key per
 * resource, and (per the plan) a hard cap so a board a tenant can actually navigate.
 */
class AddCustomFieldDefinitionUseCase(
    private val repository: CustomFieldDefinitionRepository
) {
    companion object {
        /** Soft product limit — JSONB row width and GIN index cost, not a physical wall. */
        const val MAX_ACTIVE_FIELDS_PER_RESOURCE = 60
    }

    suspend operator fun invoke(
        tenantId: TenantId,
        ownerResource: OwnerResource,
        label: String,
        type: FieldType,
        isRequired: Boolean = false,
        newId: () -> String
    ): Result<CustomFieldDefinition> = runCatching {
        val existing = repository.findActiveByResource(tenantId, ownerResource)
        require(existing.size < MAX_ACTIVE_FIELDS_PER_RESOURCE) {
            "Batas $MAX_ACTIVE_FIELDS_PER_RESOURCE kolom aktif untuk modul ini sudah tercapai"
        }

        val existingKeys = repository.existingKeys(tenantId, ownerResource)
        val key = FieldKey.fromLabel(label, existingKeys)

        val now = Clock.System.now()
        val definition = CustomFieldDefinition(
            id = CustomFieldId(newId()),
            tenantId = tenantId,
            ownerResource = ownerResource,
            key = key,
            label = label.trim(),
            type = type,
            position = ((existing.maxOfOrNull { it.position } ?: 0.0) + 1000.0),
            isRequired = isRequired,
            requiredSince = if (isRequired) now else null
        )

        repository.save(definition).getOrThrow()
    }
}
