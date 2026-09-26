package com.eventverse.app.domain.techpack.usecases

import com.eventverse.app.domain.contracts.TechPackAndYieldData
import com.eventverse.app.domain.techpack.TechPackId
import com.eventverse.app.domain.techpack.TechPackRepository
import com.eventverse.app.domain.techpack.toTechPackAndYieldData
import com.eventverse.app.domain.tenant.TenantId
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

class PublishTechPackAndYieldDataUseCase(
    private val techPackRepository: TechPackRepository
) {
    suspend operator fun invoke(
        tenantId: TenantId,
        techPackId: TechPackId,
        preparedAt: Instant = Clock.System.now()
    ): Result<TechPackAndYieldData> = runCatching {
        val techPack = techPackRepository.findById(tenantId, techPackId)
            ?: error("Tech Pack dengan ID '${techPackId.value}' tidak ditemukan")

        techPack.toTechPackAndYieldData(preparedAt).getOrThrow()
    }
}
