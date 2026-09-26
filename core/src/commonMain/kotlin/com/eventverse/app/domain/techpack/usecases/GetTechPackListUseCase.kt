package com.eventverse.app.domain.techpack.usecases

import com.eventverse.app.domain.techpack.*
import com.eventverse.app.domain.tenant.TenantId

class GetTechPackListUseCase(
    private val techPackRepository: TechPackRepository
) {
    suspend operator fun invoke(tenantId: TenantId, query: TechPackQuery): Result<TechPackPage> = runCatching {
        techPackRepository.search(tenantId, query)
    }
}

class GetTechPackDetailUseCase(
    private val techPackRepository: TechPackRepository
) {
    suspend fun getDetail(tenantId: TenantId, id: TechPackId): Result<TechPack?> = runCatching {
        techPackRepository.findById(tenantId, id)
    }

    suspend fun getVersions(tenantId: TenantId, styleCode: StyleCode): Result<List<TechPack>> = runCatching {
        techPackRepository.findVersions(tenantId, styleCode)
    }

    suspend fun getLatestReleased(tenantId: TenantId, styleCode: StyleCode): Result<TechPack?> = runCatching {
        techPackRepository.findLatestReleased(tenantId, styleCode)
    }
}
