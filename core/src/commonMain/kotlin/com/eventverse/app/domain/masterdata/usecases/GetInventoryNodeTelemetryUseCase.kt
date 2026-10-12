package com.eventverse.app.domain.masterdata.usecases

import com.eventverse.app.domain.masterdata.MaterialCatalogQuery
import com.eventverse.app.domain.masterdata.MaterialItemRepository
import com.eventverse.app.domain.masterdata.MaterialPriceRepository
import com.eventverse.app.domain.pack.GarmentModules
import com.eventverse.app.domain.pipeline.FlowHealthStatus
import com.eventverse.app.domain.pipeline.ModuleTelemetry
import com.eventverse.app.domain.tenant.TenantId
import kotlinx.datetime.Clock

/**
 * Telemetri modul `inventory` dari **data katalog material** yang benar-benar ada — bukan angka
 * stok yang tidak dimiliki sistem.
 *
 * `PLAN-factory-flow-a8-debt` §1 menulis metrik "stock level / low stock alerts", tetapi modul ini
 * hanya dipunggungi katalog master data (`MaterialItem` + riwayat harga); **tidak ada ledger stok**
 * (ledger stok adalah fitur Gudang yang belum di-discovery). Menghasilkan angka stok di sini berarti
 * memalsukan data, jadi metrik diturunkan ke yang jujur:
 *
 * - `wipPieces` = jumlah material aktif (non-arsip) yang dilacak tenant.
 * - `healthStatus` dari rasio material aktif **tanpa harga acuan** — material tak berharga tidak bisa
 *   ikut perhitungan HPP di hilir, jadi antrean ini sinyal bottleneck nyata (pola sama dengan CRM
 *   dan TechPack: `wipPieces` melaporkan seluruh populasi aktif, health dari bagian yang tertahan).
 *
 * Tidak ada `stageWip`: modul Inventory tidak punya kerangka tahap.
 */
class GetInventoryNodeTelemetryUseCase(
    private val materialRepository: MaterialItemRepository,
    private val priceRepository: MaterialPriceRepository,
    private val clock: Clock = Clock.System
) {
    suspend operator fun invoke(tenantId: TenantId): Result<ModuleTelemetry> = runCatching {
        val active = materialRepository.searchCatalog(
            tenantId,
            MaterialCatalogQuery(includeArchived = false, pageSize = MAX_ACTIVE_MATERIALS)
        ).items

        val stillUnpriced = if (active.isEmpty()) {
            0
        } else {
            val priced = priceRepository.effectivePricesAt(tenantId, active.map { it.id }, clock.now())
            active.count { it.id !in priced }
        }

        ModuleTelemetry(
            module = GarmentModules.INVENTORY,
            wipPieces = active.size,
            cycleTimeHours = 0.0,
            healthStatus = when {
                active.isEmpty() -> FlowHealthStatus.HEALTHY
                stillUnpriced > active.size * 0.5 -> FlowHealthStatus.CRITICAL
                stillUnpriced > active.size * 0.25 -> FlowHealthStatus.BOTTLENECK
                else -> FlowHealthStatus.HEALTHY
            }
        )
    }

    private companion object {
        const val MAX_ACTIVE_MATERIALS = 1000
    }
}
