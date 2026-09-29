package com.eventverse.app.domain.stageflow.usecases

import com.eventverse.app.domain.stageflow.IndustryTemplateCode
import com.eventverse.app.domain.stageflow.TenantStageFlow
import com.eventverse.app.domain.stageflow.TenantStageFlowRepository
import com.eventverse.app.domain.tenant.TenantId

/**
 * Operasi sunting kerangka tahap tenant (TRD-FLOW-001 Tahap 3b) — satu use case per operasi.
 *
 * Semua operasi: muat (provision bila belum ada) → terapkan aturan agregat → simpan. Menyunting
 * kerangka pabrik tidak menyentuh SPK yang sudah beku (FR-5b); SPK yang belum beku hanya ada di
 * tahap masuk, yang tidak bisa disunting. Karena itu yang dijaga di sini bukan SPK, melainkan
 * proses opsional katalog yang berjangkar pada tahap.
 */
internal suspend fun TenantStageFlowRepository.edit(
    tenantId: TenantId,
    fallbackTemplate: IndustryTemplateCode,
    change: (TenantStageFlow) -> TenantStageFlow
): TenantStageFlow {
    val current = GetTenantStageFlowUseCase(this)(tenantId, fallbackTemplate).getOrThrow()
    return save(change(current)).getOrThrow()
}
