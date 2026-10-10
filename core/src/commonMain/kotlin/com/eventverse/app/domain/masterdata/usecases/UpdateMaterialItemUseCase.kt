package com.eventverse.app.domain.masterdata.usecases

import com.eventverse.app.domain.customfield.CustomFieldDefinitionRepository
import com.eventverse.app.domain.customfield.CustomFieldId
import com.eventverse.app.domain.customfield.CustomFieldValidation
import com.eventverse.app.domain.customfield.OwnerResource
import com.eventverse.app.domain.masterdata.MaterialCategory
import com.eventverse.app.domain.masterdata.MaterialId
import com.eventverse.app.domain.masterdata.MaterialItem
import com.eventverse.app.domain.masterdata.MaterialItemRepository
import com.eventverse.app.domain.masterdata.MaterialPriceRepository
import com.eventverse.app.domain.masterdata.UomConversion
import com.eventverse.app.domain.pipeline.StockOwnershipSemantics
import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.shared.json.JsonValue
import kotlinx.datetime.Clock

data class UpdateMaterialCommand(
    val tenantId: TenantId,
    val materialId: MaterialId,
    val name: String? = null,
    val description: String? = null,
    val category: MaterialCategory? = null,
    val alternateUoms: List<UomConversion>? = null,
    val defaultOwnership: StockOwnershipSemantics? = null,
    val customValues: Map<CustomFieldId, JsonValue.Obj?> = emptyMap()
)

class UpdateMaterialItemUseCase(
    private val materialRepository: MaterialItemRepository,
    private val priceRepository: MaterialPriceRepository,
    private val customFieldRepository: CustomFieldDefinitionRepository
) {
    suspend operator fun invoke(command: UpdateMaterialCommand): Result<MaterialItem> = runCatching {
        val existing = materialRepository.findById(command.tenantId, command.materialId)
            ?: error("Material '${command.materialId.value}' tidak ditemukan")

        val now = Clock.System.now()
        var updated = existing

        if (command.name != null && command.name.isNotBlank()) {
            updated = updated.rename(command.name, now)
        }

        if (command.description != null) {
            updated = updated.describe(command.description, now)
        }

        if (command.category != null && command.category != existing.category) {
            val history = priceRepository.historyFor(command.tenantId, command.materialId)
            val hasPrices = history.entries.isNotEmpty()
            updated = updated.reclassify(command.category, now, hasPrices).getOrThrow()
        }

        if (command.alternateUoms != null) {
            var withConvs = updated.copy(alternateUoms = emptyList())
            for (conv in command.alternateUoms) {
                withConvs = withConvs.defineConversion(conv, now)
            }
            updated = withConvs
        }

        if (command.defaultOwnership != null) {
            updated = updated.withOwnership(command.defaultOwnership, now)
        }

        if (command.customValues.isNotEmpty()) {
            val definitions = customFieldRepository.findActiveByResource(command.tenantId, OwnerResource.MASTER_DATA_MATERIAL)
            val errors = CustomFieldValidation.validateForPatch(command.tenantId, definitions, updated.createdAt, command.customValues)
            if (errors.isNotEmpty()) throw MaterialValidationException(errors)

            var attrs = updated.customAttributes
            command.customValues.forEach { (fieldId, cell) ->
                attrs = attrs.with(fieldId, cell)
            }
            updated = updated.withCustomAttributes(attrs, now)
        }

        materialRepository.save(updated)
    }
}
