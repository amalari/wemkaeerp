package com.eventverse.app.domain.techpack.usecases

import com.eventverse.app.domain.contracts.LaborOperation
import com.eventverse.app.domain.techpack.TechPack
import com.eventverse.app.domain.techpack.TechPackId
import com.eventverse.app.domain.techpack.TechPackRepository
import com.eventverse.app.domain.tenant.TenantId
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

data class UpsertLaborOperationCommand(
    val tenantId: TenantId,
    val techPackId: TechPackId,
    val operation: LaborOperation,
    val now: Instant = Clock.System.now()
)

data class RemoveLaborOperationCommand(
    val tenantId: TenantId,
    val techPackId: TechPackId,
    val operationId: String,
    val now: Instant = Clock.System.now()
)

class UpdateTechPackLaborUseCase(
    private val techPackRepository: TechPackRepository
) {
    suspend fun upsertOperation(command: UpsertLaborOperationCommand): Result<TechPack> = runCatching {
        require(command.operation.samMinutes.numerator >= 0L) { "Nilai SAM tidak boleh negatif" }
        require(command.operation.name.isNotBlank()) { "Nama operasi tidak boleh kosong" }

        val techPack = techPackRepository.findById(command.tenantId, command.techPackId)
            ?: error("Tech Pack dengan ID '${command.techPackId.value}' tidak ditemukan")

        val updated = techPack.upsertLaborOperation(command.operation, command.now).getOrThrow()
        techPackRepository.save(updated)
    }

    suspend fun removeOperation(command: RemoveLaborOperationCommand): Result<TechPack> = runCatching {
        val techPack = techPackRepository.findById(command.tenantId, command.techPackId)
            ?: error("Tech Pack dengan ID '${command.techPackId.value}' tidak ditemukan")

        val updated = techPack.removeLaborOperation(command.operationId, command.now).getOrThrow()
        techPackRepository.save(updated)
    }

    suspend fun replaceOperations(command: ReplaceLaborOperationsCommand): Result<TechPack> = runCatching {
        val techPack = techPackRepository.findById(command.tenantId, command.techPackId)
            ?: error("Tech Pack dengan ID '${command.techPackId.value}' tidak ditemukan")

        if (!techPack.isEditable) {
            error("Tech Pack berstatus ${techPack.status.displayName} tidak dapat diubah")
        }

        for (op in command.operations) {
            require(op.samMinutes.numerator >= 0L) { "Nilai SAM tidak boleh negatif" }
            require(op.name.isNotBlank()) { "Nama operasi tidak boleh kosong" }
        }

        val updated = techPack.copy(laborOperations = command.operations, updatedAt = command.now)
        techPackRepository.save(updated)
    }
}

data class ReplaceLaborOperationsCommand(
    val tenantId: TenantId,
    val techPackId: TechPackId,
    val operations: List<LaborOperation>,
    val now: Instant = Clock.System.now()
)
