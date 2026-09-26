package com.eventverse.app.domain.customfield.usecases

import com.eventverse.app.domain.customfield.CustomFieldDefinition
import com.eventverse.app.domain.customfield.CustomFieldDefinitionRepository
import com.eventverse.app.domain.customfield.CustomFieldId
import com.eventverse.app.domain.tenant.TenantId
import kotlinx.datetime.Instant

/**
 * Soft-archives a custom field definition.
 *
 * System fields provisioned by seed are protected against archival per [CustomFieldDefinition.archive].
 */
class ArchiveCustomFieldDefinitionUseCase(
    private val repository: CustomFieldDefinitionRepository
) {
    suspend operator fun invoke(
        tenantId: TenantId,
        fieldId: CustomFieldId,
        at: Instant
    ): Result<CustomFieldDefinition> = runCatching {
        val existing = repository.findById(tenantId, fieldId)
            ?: error("Definisi kolom kustom tidak ditemukan: ${fieldId.value}")
        val archived = existing.archive(at)
        repository.save(archived).getOrThrow()
    }
}
