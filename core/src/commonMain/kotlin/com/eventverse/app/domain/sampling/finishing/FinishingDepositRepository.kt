package com.eventverse.app.domain.sampling.finishing

import com.eventverse.app.domain.sampling.SamplingOrderId
import com.eventverse.app.domain.tenant.TenantId

/**
 * Operasi domain setoran finishing — bukan DAO generik.
 *
 * Tidak ada `deleteById`: setoran adalah catatan kerja yang sudah terjadi di lantai produksi.
 * Koreksi dilakukan dengan setoran penyeimbang yang tercatat, bukan dengan menghapus jejak.
 */
interface FinishingDepositRepository {
    suspend fun findBySamplingOrder(tenantId: TenantId, samplingOrderId: SamplingOrderId): List<FinishingDeposit>

    /** Seluruh setoran tenant — dasar rekap antrean kerja lintas SPK di layar operator. */
    suspend fun findAll(tenantId: TenantId): List<FinishingDeposit>

    suspend fun save(deposit: FinishingDeposit): FinishingDeposit

    suspend fun nextId(tenantId: TenantId): FinishingDepositId
}
