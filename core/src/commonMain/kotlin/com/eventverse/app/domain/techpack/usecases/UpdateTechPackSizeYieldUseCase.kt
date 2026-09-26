package com.eventverse.app.domain.techpack.usecases

import com.eventverse.app.domain.contracts.SizeYieldFactor
import com.eventverse.app.domain.techpack.TechPack
import com.eventverse.app.domain.techpack.TechPackId
import com.eventverse.app.domain.techpack.TechPackRepository
import com.eventverse.app.domain.tenant.TenantId
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

data class SetSizeYieldFactorsCommand(
    val tenantId: TenantId,
    val techPackId: TechPackId,
    val factors: List<SizeYieldFactor>,
    val now: Instant = Clock.System.now()
)

class UpdateTechPackSizeYieldUseCase(
    private val techPackRepository: TechPackRepository
) {
    suspend fun setFactors(command: SetSizeYieldFactorsCommand): Result<TechPack> = runCatching {
        val techPack = techPackRepository.findById(command.tenantId, command.techPackId)
            ?: error("Tech Pack dengan ID '${command.techPackId.value}' tidak ditemukan")

        val updated = techPack.setSizeYieldFactors(command.factors, command.now).getOrThrow()
        techPackRepository.save(updated)
    }
}
