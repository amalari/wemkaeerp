package com.eventverse.app.domain.sampling.usecases

import com.eventverse.app.domain.deal.DealId
import com.eventverse.app.domain.deal.DealRepository
import com.eventverse.app.domain.deal.DealStage
import com.eventverse.app.domain.sampling.*
import com.eventverse.app.domain.tenant.TenantId
import kotlinx.datetime.Clock

data class PublishSamplingSpkCommand(
    val tenantId: TenantId,
    val dealId: String,
    val samplingOrderId: SamplingOrderId,
    val actorEmail: String = "",
    val actorRole: String = ""
)

/**
 * Menerbitkan SPK Sampling dari lembar desain yang menempel pada satu Deal.
 *
 * Sesuai prinsip pabrik "1 SPK = 1 ukuran":
 * Jika lembar desain memiliki multi-size yang aktif (misal ALL SIZE dan S),
 * use case ini memecahnya menjadi N SPK sampling terpisah:
 * - Ukuran pertama dialokasikan ke order utama (root order).
 * - Ukuran berikutnya diterbitkan sebagai child order (menautkan [parentSamplingOrderId]).
 *
 * Operasi ini sepenuhnya idempotent: pemanggilan ulang tidak menduplikasi SPK yang sudah terbit.
 */
class PublishSamplingSpkFromDealUseCase(
    private val samplingRepository: SamplingOrderRepository,
    private val dealRepository: DealRepository? = null,
    private val idGenerator: () -> String = { "smp-" + Clock.System.now().toEpochMilliseconds() }
) {
    suspend operator fun invoke(command: PublishSamplingSpkCommand): Result<List<SamplingOrder>> = runCatching {
        require(command.dealId.isNotBlank()) { "DealId tidak boleh kosong" }

        val rootOrder = samplingRepository.findById(command.samplingOrderId)
            ?: error("Sampling order tidak ditemukan")
        require(rootOrder.tenantId == command.tenantId) { "Sampling order bukan milik tenant ini" }
        require(rootOrder.dealId == command.dealId) { "Sampling order tidak menempel pada deal ini" }

        val now = Clock.System.now()
        val activeSizes = activeSizesWithAllocatedQty(rootOrder.sizeMatrix).ifEmpty {
            // Fallback untuk data legacy tanpa matriks POM lengkap
            listOf(Pair(rootOrder.sizeLabel ?: "ALL SIZE", rootOrder.sampleQuantity.coerceAtLeast(1)))
        }

        val existingChildren = samplingRepository.findByDealId(command.tenantId, command.dealId)
            .filter { it.parentSamplingOrderId == rootOrder.id && !it.isArchived }

        val targetStage = SamplingPipelineStage.NEW_INTAKE

        // Ukuran pertama dialokasikan ke root order
        val firstSize = activeSizes.first()
        val updatedRoot = rootOrder.copy(
            sizeLabel = firstSize.first,
            sampleQuantity = firstSize.second,
            status = if (rootOrder.status == SamplingStatus.DRAFT) SamplingStatus.IN_PROGRESS else rootOrder.status,
            pipelineStage = targetStage,
            updatedAt = now
        )
        samplingRepository.save(updatedRoot)

        val childOrders = mutableListOf<SamplingOrder>()
        for (i in 1 until activeSizes.size) {
            val (sizeName, qty) = activeSizes[i]
            val existing = existingChildren.firstOrNull { it.sizeLabel.equals(sizeName, ignoreCase = true) }
            if (existing != null) {
                val updatedChild = existing.copy(
                    sampleQuantity = qty,
                    status = if (existing.status == SamplingStatus.DRAFT) SamplingStatus.IN_PROGRESS else existing.status,
                    pipelineStage = targetStage,
                    updatedAt = now
                )
                samplingRepository.save(updatedChild)
                childOrders.add(updatedChild)
            } else {
                val nextSpk = samplingRepository.nextSpkNumber(command.tenantId)
                val newChild = rootOrder.copy(
                    id = SamplingOrderId(idGenerator() + "-" + (i + 1)),
                    spkNumber = nextSpk,
                    parentSamplingOrderId = rootOrder.id,
                    sizeLabel = sizeName,
                    sampleQuantity = qty,
                    status = SamplingStatus.IN_PROGRESS,
                    pipelineStage = targetStage,
                    createdAt = now,
                    updatedAt = now
                )
                samplingRepository.save(newChild)
                childOrders.add(newChild)
            }
        }

        if (dealRepository != null) {
            val deal = dealRepository.findById(command.tenantId, DealId(command.dealId))
            if (deal != null && deal.stage == DealStage.OPEN) {
                dealRepository.save(
                    deal.transitionTo(DealStage.PO_RECEIVED, now)
                        .getOrDefault(deal.copy(stage = DealStage.PO_RECEIVED, updatedAt = now))
                )
            }
        }

        listOf(updatedRoot) + childOrders
    }
}
