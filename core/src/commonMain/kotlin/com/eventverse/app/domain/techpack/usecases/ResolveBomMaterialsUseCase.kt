package com.eventverse.app.domain.techpack.usecases

import com.eventverse.app.domain.contracts.MaterialRef
import com.eventverse.app.domain.masterdata.MaterialItemRepository
import com.eventverse.app.domain.techpack.TechPack
import com.eventverse.app.domain.techpack.TechPackId
import com.eventverse.app.domain.techpack.TechPackRepository
import com.eventverse.app.domain.tenant.TenantId
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

data class ResolveBomMaterialsCommand(
    val tenantId: TenantId,
    val techPackId: TechPackId,
    val now: Instant = Clock.System.now()
)

data class ResolveBomMaterialsResult(
    val techPack: TechPack,
    val resolvedCount: Int,
    val remainingUnresolvedCount: Int
)

class ResolveBomMaterialsUseCase(
    private val techPackRepository: TechPackRepository,
    private val materialRepository: MaterialItemRepository
) {
    suspend operator fun invoke(command: ResolveBomMaterialsCommand): Result<ResolveBomMaterialsResult> = runCatching {
        val techPack = techPackRepository.findById(command.tenantId, command.techPackId)
            ?: error("Tech Pack dengan ID '${command.techPackId.value}' tidak ditemukan")

        if (!techPack.isEditable) {
            error("Tech Pack berstatus ${techPack.status.displayName} tidak dapat diubah")
        }

        var resolvedCount = 0
        val updatedLines = techPack.bomLines.map { line ->
            if (line.material.isResolved) {
                line
            } else {
                val matches = materialRepository.matchByFreeText(command.tenantId, line.material.freeText, line.category)
                if (matches.size == 1) {
                    val match = matches.first()
                    resolvedCount++
                    line.copy(
                        material = MaterialRef.resolved(match.id, match.code, match.name)
                    )
                } else {
                    line
                }
            }
        }

        val updatedTechPack = techPack.copy(bomLines = updatedLines, updatedAt = command.now)
        val saved = techPackRepository.save(updatedTechPack)

        ResolveBomMaterialsResult(
            techPack = saved,
            resolvedCount = resolvedCount,
            remainingUnresolvedCount = saved.unresolvedLines.size
        )
    }
}
