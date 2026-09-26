package com.eventverse.app.domain.masterdata.usecases

import com.eventverse.app.domain.masterdata.MaterialId
import com.eventverse.app.domain.masterdata.MaterialItemRepository
import com.eventverse.app.domain.tenant.TenantId

class ArchiveMaterialItemUseCase(
    private val materialRepository: MaterialItemRepository
) {
    suspend operator fun invoke(tenantId: TenantId, materialId: MaterialId): Result<Boolean> = runCatching {
        val existing = materialRepository.findById(tenantId, materialId)
            ?: error("Material '${materialId.value}' tidak ditemukan")
        materialRepository.archive(tenantId, materialId)
    }
}
