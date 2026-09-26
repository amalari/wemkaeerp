package com.eventverse.app.domain.costing

import com.eventverse.app.domain.common.Money
import com.eventverse.app.domain.common.Ratio
import com.eventverse.app.domain.pipeline.CostingBehavior
import com.eventverse.app.domain.tenant.TenantId
import kotlinx.datetime.Instant

/**
 * Rate card per tenant per [CostingBehavior], ber-tanggal-berlaku half-open [effectiveFrom, effectiveTo).
 *
 * Konvensi interval setengah-terbuka sama dengan [MaterialPriceRepository.effectivePriceAt]:
 * - Kartu berlaku mulai `effectiveFrom` hingga tepat sebelum `effectiveTo`.
 * - `effectiveTo == null` berarti berlaku sampai sekarang (kartu terkini).
 *
 * ## Mengapa semua field nullable?
 * `null` berarti "tenant belum mengatur" — berbeda makna dari nol eksplisit.
 * `0` pada `laborRatePerSamMinute` berarti tidak ada biaya tenaga kerja (disubsidi/sudah termasuk).
 * `null` berarti "gunakan fallback dari pipeline node atau hardcoded default".
 *
 * ## Riwayat immutable
 * Menyunting rate card **tidak pernah** mengubah riwayat:
 * 1. `closeAt(newFrom)` pada kartu lama — menutup `effectiveTo`
 * 2. `nextVersion(...)` membuat kartu baru dengan `effectiveFrom = newFrom`
 *
 * Lembar HPP yang disetujui menyimpan `rateCardId + version`, sehingga angkanya
 * bisa dijelaskan bertahun-tahun kemudian.
 */
data class CostingRateCard(
    val id: CostingRateCardId,
    val tenantId: TenantId,
    val behavior: CostingBehavior,
    val version: Int = 1,
    val effectiveFrom: Instant,
    val effectiveTo: Instant? = null,
    val description: String = "",

    /** Rp per SAM-menit untuk operator in-house. null = belum dikonfigurasi. */
    val laborRatePerSamMinute: Money? = null,

    /** Rp per SAM-menit untuk operasi yang disubkonkan ke vendor luar. null = pakai laborRate. */
    val subcontractRatePerSamMinute: Money? = null,

    /** Biaya jasa tetap per pcs (khusus SERVICE_FEE_ONLY / CMT). null = belum dikonfigurasi. */
    val serviceFeePerUnit: Money? = null,

    /** Overhead pabrik per pcs (listrik, mesin, manajemen). null = belum dikonfigurasi. */
    val overheadPerUnit: Money? = null,

    /** Biaya packing per pcs tambahan (mis. polybag, label). null = tidak ada. */
    val packingCostPerUnit: Money? = null,

    /**
     * Biaya packing tetap per order (mis. karton ekspor Rp 4.200/order).
     * Dibagi ke per-pcs saat kalkulasi; sisa didistribusikan ke [CostingCalculationResult.roundingResidual].
     */
    val packingCostPerOrder: Money? = null,

    /** Rasio margin laba atas `billablePerUnit`. null = belum dikonfigurasi, pakai default 0. */
    val marginRatio: Ratio? = null,

    /** Markup retail: `billablePerUnit × (1 + retailMarkupRatio) = hargaRetail`. null = tidak ada markup. */
    val retailMarkupRatio: Ratio? = null,

    /** Fee marketplace dihitung di atas **harga retail**, bukan HPP. null = tidak ada fee. */
    val marketplaceFeeRatio: Ratio? = null,

    /** Toleransi susut kain/waste. null = tidak ada toleransi (nol waste). */
    val fabricWastageToleranceRatio: Ratio? = null,

    /**
     * Jika true, biaya kain dimasukkan ke HPP (relevan untuk FOB dan D2C).
     * Jika false, kain tidak ditagihkan (relevan untuk CMT makloon murni).
     * null = gunakan default dari [CostingBehavior.includesFabricMaterialCost].
     */
    val includeFabricCost: Boolean? = null,

    /** Jejak asal: nodeId pipeline yang menjadi seed rate card ini (dari SeedCostingRateCardFromPipelineUseCase). */
    val seededFromNodeId: String? = null,

    val createdByUserId: String? = null,
    val createdAt: Instant,
    val updatedAt: Instant
) {
    init {
        require(version >= 1) { "Versi rate card harus minimal 1" }
        effectiveTo?.let { to ->
            require(to > effectiveFrom) {
                "effectiveTo ($to) harus lebih besar dari effectiveFrom ($effectiveFrom)"
            }
        }
    }

    val isActive: Boolean get() = effectiveTo == null

    /**
     * Menutup kartu ini pada [closedAt] — langkah pertama saat membuat versi baru.
     * Kalau [closedAt] sama atau lebih awal dari [effectiveFrom], operasi ditolak.
     */
    fun closeAt(closedAt: Instant): CostingRateCard {
        require(closedAt > effectiveFrom) {
            "Waktu penutupan harus lebih besar dari waktu mulai berlaku"
        }
        return copy(effectiveTo = closedAt, updatedAt = closedAt)
    }

    /**
     * Membuat kartu versi baru dengan [effectiveFrom] = `newFrom`.
     * Kartu lama harus sudah di-[closeAt] terlebih dahulu.
     */
    fun nextVersion(
        newId: CostingRateCardId,
        newFrom: Instant,
        updater: CostingRateCard.() -> CostingRateCard = { this }
    ): CostingRateCard = copy(
        id = newId,
        version = version + 1,
        effectiveFrom = newFrom,
        effectiveTo = null,
        createdAt = newFrom,
        updatedAt = newFrom
    ).updater()
}
