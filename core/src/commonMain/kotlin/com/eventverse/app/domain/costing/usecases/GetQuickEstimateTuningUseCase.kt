package com.eventverse.app.domain.costing.usecases

import com.eventverse.app.domain.costing.QuickEstimateTuning
import com.eventverse.app.domain.costing.QuickEstimateTuningCodec
import com.eventverse.app.domain.costing.ResolvedQuickEstimateTuning
import com.eventverse.app.domain.pipeline.TenantPipelineRepository
import com.eventverse.app.domain.tenant.TenantId

/**
 * Mengambil koefisien estimator milik satu tenant dari node `COSTING_HPP` di pipeline-nya.
 *
 * ## Kenapa lewat pipeline node, bukan tabel setelan baru
 * Kontrak 4 (`module-integration-rules`) sudah menunjuk `customFormulaParameters` sebagai tempat
 * parameter rumus per tenant, dan [com.eventverse.app.domain.costing.CostingParameterCodec] sudah
 * memakainya untuk kalkulasi HPP resmi. Menambah tabel kedua berarti dua tempat menyimpan hal
 * yang sama, dan cepat atau lambat keduanya berbeda isi.
 *
 * ## Kenapa tidak pernah gagal
 * Tenant tanpa pipeline, tanpa node HPP, atau dengan setelan salah ketik tetap mendapat
 * [QuickEstimateTuning.SYSTEM_DEFAULT]. Estimator adalah alat yang dipakai CS sambil menelepon
 * klien; ia tidak boleh mati karena konfigurasi pipeline belum rapi.
 */
class GetQuickEstimateTuningUseCase(
    private val pipelineRepository: TenantPipelineRepository?
) {
    suspend operator fun invoke(tenantId: TenantId): ResolvedQuickEstimateTuning {
        val nodeParams = runCatching {
            pipelineRepository
                ?.findByTenantId(tenantId)
                ?.nodes
                ?.firstOrNull { it.moduleId == QuickEstimateTuningCodec.COSTING_MODULE_CODE }
                ?.customFormulaParameters
        }.getOrNull().orEmpty()

        return QuickEstimateTuningCodec.resolveSafely(nodeParams)
    }
}
