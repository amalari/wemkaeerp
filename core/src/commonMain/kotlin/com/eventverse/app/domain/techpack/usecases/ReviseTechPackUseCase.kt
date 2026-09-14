package com.eventverse.app.domain.techpack.usecases

import com.eventverse.app.domain.techpack.TechPack
import com.eventverse.app.domain.techpack.TechPackId
import com.eventverse.app.domain.techpack.TechPackRepository
import com.eventverse.app.domain.techpack.TechPackStatus
import com.eventverse.app.domain.tenant.TenantId
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

data class ReviseTechPackCommand(
    val tenantId: TenantId,
    val techPackId: TechPackId,
    val createdByUserId: String? = null,
    val now: Instant = Clock.System.now()
)

class ReviseTechPackUseCase(
    private val techPackRepository: TechPackRepository
) {
    suspend operator fun invoke(command: ReviseTechPackCommand): Result<TechPack> = runCatching {
        val existing = techPackRepository.findById(command.tenantId, command.techPackId)
            ?: error("Tech Pack dengan ID '${command.techPackId.value}' tidak ditemukan")

        require(existing.status == TechPackStatus.RELEASED) {
            "Hanya Tech Pack berstatus RELEASED yang dapat direvisi (status saat ini: ${existing.status.displayName})"
        }

        val newVersionNumber = existing.version + 1
        val newId = TechPackId("tp-${command.tenantId.value}-${existing.styleCode.value}-v$newVersionNumber")

        val revisedNew = existing.reviseAs(newId, command.now).copy(
            createdByUserId = command.createdByUserId ?: existing.createdByUserId
        )
        val supersededOld = existing.copy(
            status = TechPackStatus.SUPERSEDED,
            updatedAt = command.now
        )

        techPackRepository.saveRevision(oldVersion = supersededOld, newVersion = revisedNew)
    }
}
