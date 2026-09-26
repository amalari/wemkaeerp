package com.eventverse.app.domain.masterdata

import com.eventverse.app.domain.common.UnitPrice
import com.eventverse.app.domain.tenant.TenantId
import kotlinx.datetime.Instant

enum class PriceSource(val displayName: String) {
    STANDARD("Tarif Standar Acuan Pabrik"),
    PURCHASE_ACTUAL("Harga Beli Aktual Terakhir"),
    CLIENT_SUPPLIED_ZERO("Bahan Titipan Klien (Rp 0)");
}

/**
 * Immutable point-in-time material price entry.
 */
data class MaterialPrice(
    val id: MaterialPriceId,
    val tenantId: TenantId,
    val materialId: MaterialId,
    val unitPrice: UnitPrice,
    val source: PriceSource = PriceSource.STANDARD,
    val effectiveFrom: Instant,
    val note: String = "",
    val recordedByUserId: String = "",
    val recordedAt: Instant
) {
    init {
        require(source != PriceSource.CLIENT_SUPPLIED_ZERO || unitPrice.amount.isZero) {
            "Bahan titipan konsinyasi klien harus bernilai Rp 0 di neraca pabrik (Kontrak 3 & 4)"
        }
    }
}

/**
 * Append-only price history of a material.
 */
data class MaterialPriceHistory(
    val materialId: MaterialId,
    val entries: List<MaterialPrice> = emptyList()
) {
    /**
     * Resolves the effective price at a specific [instant] point in time.
     * Selects the latest entry with effectiveFrom <= [instant].
     */
    fun priceAt(instant: Instant, source: PriceSource = PriceSource.STANDARD): MaterialPrice? {
        return entries
            .filter { it.source == source && it.effectiveFrom <= instant }
            .maxByOrNull { it.effectiveFrom.toEpochMilliseconds() }
    }

    fun currentPrice(now: Instant, source: PriceSource = PriceSource.STANDARD): MaterialPrice? =
        priceAt(now, source)

    fun futurePrices(now: Instant, source: PriceSource = PriceSource.STANDARD): List<MaterialPrice> {
        return entries
            .filter { it.source == source && it.effectiveFrom > now }
            .sortedBy { it.effectiveFrom.toEpochMilliseconds() }
    }

    fun timelineFor(source: PriceSource? = null): List<MaterialPrice> {
        val filtered = if (source == null) entries else entries.filter { it.source == source }
        return filtered.sortedByDescending { it.effectiveFrom.toEpochMilliseconds() }
    }

    fun append(newPrice: MaterialPrice): MaterialPriceHistory {
        require(newPrice.materialId == materialId) {
            "MaterialId tidak cocok: ${newPrice.materialId} vs $materialId"
        }
        return copy(entries = (entries + newPrice).sortedBy { it.effectiveFrom.toEpochMilliseconds() })
    }
}
