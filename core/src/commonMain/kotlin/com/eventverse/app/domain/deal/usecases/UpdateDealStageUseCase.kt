package com.eventverse.app.domain.deal.usecases

import com.eventverse.app.domain.deal.Deal
import com.eventverse.app.domain.deal.DealId
import com.eventverse.app.domain.deal.DealRepository
import com.eventverse.app.domain.deal.DealStage
import com.eventverse.app.domain.tenant.TenantId
import kotlinx.datetime.Clock

/** Moves a deal through its pipeline, refusing an illegal transition (see [DealStage.canTransitionTo]). */
class UpdateDealStageUseCase(
    private val dealRepository: DealRepository
) {
    suspend operator fun invoke(
        tenantId: TenantId,
        dealId: DealId,
        newStage: DealStage
    ): Result<Deal> = runCatching {
        val existing = requireNotNull(dealRepository.findById(tenantId, dealId)) {
            "Deal tidak ditemukan: ${dealId.value}"
        }
        // Idempoten: permintaan ke tahap yang SAMA dianggap sukses tanpa menulis ulang.
        // Konteks nyata: penerbitan SPK sampling otomatis menggeser deal ke PO_RECEIVED, dan
        // state deal di klien bisa kedaluwarsa (di server sudah PO_RECEIVED). Tanpa no-op ini
        // request ulang yang sama gagal HTTP 400 "dari PO Diterima ke PO Diterima".
        if (existing.stage == newStage) return@runCatching existing
        val transitioned = existing.transitionTo(newStage, Clock.System.now()).getOrThrow()
        dealRepository.save(transitioned).getOrThrow()
    }
}
