package com.eventverse.app.domain.techpack.usecases

import com.eventverse.app.domain.contracts.BomLine
import com.eventverse.app.domain.masterdata.MaterialItemRepository
import com.eventverse.app.domain.techpack.TechPack
import com.eventverse.app.domain.techpack.TechPackId
import com.eventverse.app.domain.techpack.TechPackRepository
import com.eventverse.app.domain.tenant.TenantId
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

data class UpsertBomLineCommand(
    val tenantId: TenantId,
    val techPackId: TechPackId,
    val line: BomLine,
    val now: Instant = Clock.System.now()
)

data class RemoveBomLineCommand(
    val tenantId: TenantId,
    val techPackId: TechPackId,
    val lineId: String,
    val now: Instant = Clock.System.now()
)

class UpdateTechPackBomUseCase(
    private val techPackRepository: TechPackRepository,
    private val materialRepository: MaterialItemRepository
) {
    suspend fun upsertLine(command: UpsertBomLineCommand): Result<TechPack> = runCatching {
        val techPack = techPackRepository.findById(command.tenantId, command.techPackId)
            ?: error("Tech Pack dengan ID '${command.techPackId.value}' tidak ditemukan")

        val materialId = command.line.material.materialId
        if (materialId != null) {
            val item = materialRepository.findById(command.tenantId, materialId)
            if (item != null) {
                val canConvert = command.line.netQuantityPerGarment.uom.canConvertTo(item.baseUom) ||
                    item.alternateUoms.any { it.from == command.line.netQuantityPerGarment.uom }
                require(canConvert) {
                    "Satuan '${command.line.netQuantityPerGarment.uom.displayName}' pada baris BOM tidak kompatibel dengan satuan dasar material '${item.name}' (${item.baseUom.displayName})"
                }
            }
        }

        val updated = techPack.upsertBomLine(command.line, command.now).getOrThrow()
        techPackRepository.save(updated)
    }

    suspend fun removeLine(command: RemoveBomLineCommand): Result<TechPack> = runCatching {
        val techPack = techPackRepository.findById(command.tenantId, command.techPackId)
            ?: error("Tech Pack dengan ID '${command.techPackId.value}' tidak ditemukan")

        val updated = techPack.removeBomLine(command.lineId, command.now).getOrThrow()
        techPackRepository.save(updated)
    }

    suspend fun replaceLines(command: ReplaceBomLinesCommand): Result<TechPack> = runCatching {
        val techPack = techPackRepository.findById(command.tenantId, command.techPackId)
            ?: error("Tech Pack dengan ID '${command.techPackId.value}' tidak ditemukan")

        if (!techPack.isEditable) {
            error("Tech Pack berstatus ${techPack.status.displayName} tidak dapat diubah")
        }

        for (line in command.lines) {
            val materialId = line.material.materialId
            if (materialId != null) {
                val item = materialRepository.findById(command.tenantId, materialId)
                if (item != null) {
                    val canConvert = line.netQuantityPerGarment.uom.canConvertTo(item.baseUom) ||
                        item.alternateUoms.any { it.from == line.netQuantityPerGarment.uom }
                    require(canConvert) {
                        "Satuan '${line.netQuantityPerGarment.uom.displayName}' pada baris BOM tidak kompatibel dengan satuan dasar material '${item.name}' (${item.baseUom.displayName})"
                    }
                }
            }
        }

        val updated = techPack.copy(bomLines = command.lines, updatedAt = command.now)
        techPackRepository.save(updated)
    }
}

data class ReplaceBomLinesCommand(
    val tenantId: TenantId,
    val techPackId: TechPackId,
    val lines: List<BomLine>,
    val now: Instant = Clock.System.now()
)
