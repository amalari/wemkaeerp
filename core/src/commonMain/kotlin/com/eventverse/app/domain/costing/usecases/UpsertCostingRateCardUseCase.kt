package com.eventverse.app.domain.costing.usecases

import com.eventverse.app.domain.costing.CostingRateCard
import com.eventverse.app.domain.costing.CostingRateCardId
import com.eventverse.app.domain.costing.CostingRateCardRepository
import com.eventverse.app.domain.pipeline.CostingBehavior
import com.eventverse.app.domain.tenant.TenantId
import kotlinx.datetime.Clock

/**
 * Membuat atau memperbarui rate card aktif untuk behavior + tenant.
 * Jika ada rate card aktif, ia ditutup terlebih dahulu (versi baru dibuat).
 */
class UpsertCostingRateCardUseCase(
    private val rateCardRepository: CostingRateCardRepository,
    private val clock: Clock = Clock.System,
    private val idGenerator: () -> String = { "ratecard-${clock.now().toEpochMilliseconds()}" }
) {
    suspend operator fun invoke(
        tenantId: TenantId,
        behavior: CostingBehavior,
        updater: CostingRateCard.() -> CostingRateCard,
        updatedByUserId: String? = null
    ): Result<CostingRateCard> = runCatching {
        val now = clock.now()
        val existing = rateCardRepository.findActive(tenantId, behavior)

        val newCard = if (existing != null) {
            // Tutup yang lama, buat versi baru
            rateCardRepository.save(existing.closeAt(now))
            existing.nextVersion(
                newId = CostingRateCardId(idGenerator()),
                newFrom = now,
                updater = updater
            ).copy(
                tenantId = tenantId,
                createdByUserId = updatedByUserId
            )
        } else {
            // Buat kartu pertama untuk tenant ini
            CostingRateCard(
                id = CostingRateCardId(idGenerator()),
                tenantId = tenantId,
                behavior = behavior,
                effectiveFrom = now,
                createdByUserId = updatedByUserId,
                createdAt = now,
                updatedAt = now
            ).updater()
        }

        rateCardRepository.save(newCard)
        newCard
    }
}
