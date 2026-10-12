package com.eventverse.app.domain.fulfillment.usecases

import com.eventverse.app.domain.fulfillment.carton.CartonRepository
import com.eventverse.app.domain.fulfillment.carton.PackingQueueItem
import com.eventverse.app.domain.tenant.TenantId

/**
 * U1: Mengambil daftar item yang menunggu untuk di-packing.
 *
 * Menggunakan data dari stage sebelumnya (QC Inspection) untuk menampilkan
 * unit yang sudah lulus QC dan siap masuk ke proses packing.
 */
class GetPackingQueueUseCase(
    private val cartonRepository: CartonRepository
) {
    suspend operator fun invoke(
        tenantId: TenantId
    ): Result<List<PackingQueueItem>> = runCatching {
        cartonRepository.getPackingQueue(tenantId)
    }
}
