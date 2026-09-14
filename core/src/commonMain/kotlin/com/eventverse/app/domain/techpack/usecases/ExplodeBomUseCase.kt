package com.eventverse.app.domain.techpack.usecases

import com.eventverse.app.domain.common.Quantity
import com.eventverse.app.domain.contracts.BomLine
import com.eventverse.app.domain.techpack.TechPackId
import com.eventverse.app.domain.techpack.TechPackRepository
import com.eventverse.app.domain.tenant.TenantId

data class BomLineRequirement(
    val line: BomLine,
    val grossQuantityTotal: Quantity
)

data class BomExplosionResult(
    val techPackId: TechPackId,
    val orderQuantity: Long,
    val requirements: List<BomLineRequirement>
)

class ExplodeBomUseCase(
    private val techPackRepository: TechPackRepository
) {
    suspend operator fun invoke(
        tenantId: TenantId,
        techPackId: TechPackId,
        orderQuantity: Long
    ): Result<BomExplosionResult> = runCatching {
        require(orderQuantity > 0L) { "Kuantitas order harus lebih besar dari 0" }
        val techPack = techPackRepository.findById(tenantId, techPackId)
            ?: error("Tech Pack dengan ID '${techPackId.value}' tidak ditemukan")

        val requirements = techPack.bomLines.map { line ->
            val grossTotal = if (techPack.sizeYieldFactors.isNotEmpty() && techPack.totalOrderedQuantity > 0L) {
                techPack.grossRequirementFor(line)
            } else {
                line.grossFor(orderQuantity)
            }
            BomLineRequirement(
                line = line,
                grossQuantityTotal = grossTotal
            )
        }

        BomExplosionResult(
            techPackId = techPackId,
            orderQuantity = orderQuantity,
            requirements = requirements
        )
    }
}
