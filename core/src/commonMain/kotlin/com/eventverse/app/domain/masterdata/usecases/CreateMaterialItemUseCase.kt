package com.eventverse.app.domain.masterdata.usecases

import com.eventverse.app.domain.common.UnitOfMeasure
import com.eventverse.app.domain.customfield.CustomAttributes
import com.eventverse.app.domain.customfield.CustomFieldDefinitionRepository
import com.eventverse.app.domain.customfield.CustomFieldId
import com.eventverse.app.domain.customfield.CustomFieldValidation
import com.eventverse.app.domain.customfield.OwnerResource
import com.eventverse.app.domain.masterdata.MaterialCategory
import com.eventverse.app.domain.masterdata.MaterialCode
import com.eventverse.app.domain.masterdata.MaterialId
import com.eventverse.app.domain.masterdata.MaterialItem
import com.eventverse.app.domain.masterdata.MaterialItemRepository
import com.eventverse.app.domain.masterdata.UomConversion
import com.eventverse.app.domain.pipeline.StockOwnershipSemantics
import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.shared.json.JsonValue
import kotlinx.datetime.Clock

data class CreateMaterialCommand(
    val tenantId: TenantId,
    val code: String? = null,
    val name: String,
    val category: MaterialCategory,
    val baseUom: UnitOfMeasure? = null,
    val alternateUoms: List<UomConversion> = emptyList(),
    val defaultOwnership: StockOwnershipSemantics = StockOwnershipSemantics.OWNED_RAW_MATERIAL,
    val description: String = "",
    val customValues: Map<CustomFieldId, JsonValue.Obj?> = emptyMap(),
    val idGenerator: () -> String = { "mat-${Clock.System.now().toEpochMilliseconds()}" }
)

data class MaterialValidationException(
    val customFieldErrors: List<com.eventverse.app.domain.customfield.CustomFieldValidationError>
) : Exception("Validasi atribut kustom material gagal: ${customFieldErrors.size} kolom tidak valid")

class CreateMaterialItemUseCase(
    private val materialRepository: MaterialItemRepository,
    private val customFieldRepository: CustomFieldDefinitionRepository
) {
    suspend operator fun invoke(command: CreateMaterialCommand): Result<MaterialItem> = runCatching {
        require(command.name.isNotBlank()) { "Nama material tidak boleh kosong" }

        // 1. Resolve or reserve code
        val resolvedCode = if (command.code.isNullOrBlank()) {
            materialRepository.reserveNextCode(command.tenantId, command.category)
        } else {
            val normalized = MaterialCode.normalize(command.code)
            val existing = materialRepository.findByCode(command.tenantId, normalized)
            require(existing == null) {
                "Kode material '${normalized.value}' sudah digunakan oleh item '${existing?.name}'"
            }
            normalized
        }

        // 2. Base UoM default to category standard if unspecified
        val baseUom = command.baseUom ?: command.category.defaultUom
        require(!baseUom.isPackagingUnit) {
            "Satuan dasar tidak boleh satuan kemasan (${baseUom.displayName}); gunakan konversi alternatif."
        }

        // 3. Custom field validation
        val definitions = customFieldRepository.findActiveByResource(command.tenantId, OwnerResource.MASTER_DATA_MATERIAL)
        val errors = CustomFieldValidation.validateForCreate(definitions, command.customValues)
        if (errors.isNotEmpty()) throw MaterialValidationException(errors)

        var attributes = CustomAttributes.EMPTY
        command.customValues.forEach { (fieldId, cell) -> attributes = attributes.with(fieldId, cell) }

        val now = Clock.System.now()
        val item = MaterialItem(
            id = MaterialId(command.idGenerator()),
            tenantId = command.tenantId,
            code = resolvedCode,
            name = command.name.trim(),
            category = command.category,
            baseUom = baseUom,
            alternateUoms = command.alternateUoms,
            defaultOwnership = command.defaultOwnership,
            description = command.description.trim(),
            customAttributes = attributes,
            createdAt = now,
            updatedAt = now
        )

        materialRepository.save(item)
    }
}
