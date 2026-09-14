package com.eventverse.app.domain.techpack.usecases

import com.eventverse.app.domain.techpack.TechPack
import com.eventverse.app.domain.techpack.TechPackId
import com.eventverse.app.domain.techpack.TechPackRepository
import com.eventverse.app.domain.tenant.TenantId
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

data class ReleaseTechPackCommand(
    val tenantId: TenantId,
    val techPackId: TechPackId,
    val now: Instant = Clock.System.now()
)

class ReleaseTechPackUseCase(
    private val techPackRepository: TechPackRepository
) {
    suspend operator fun invoke(command: ReleaseTechPackCommand): Result<TechPack> = runCatching {
        val techPack = techPackRepository.findById(command.tenantId, command.techPackId)
            ?: error("Tech Pack dengan ID '${command.techPackId.value}' tidak ditemukan")

        val released = techPack.release(command.now).getOrThrow()
        techPackRepository.save(released)
    }
}
