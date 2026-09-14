package com.eventverse.app.domain.masterdata

import com.eventverse.app.domain.common.Money
import com.eventverse.app.domain.common.Quantity
import com.eventverse.app.domain.common.UnitOfMeasure
import com.eventverse.app.domain.common.UnitPrice
import com.eventverse.app.domain.pipeline.StockOwnershipSemantics
import com.eventverse.app.domain.tenant.TenantId
import kotlinx.datetime.Instant

data class PriceQuery(
    val tenantId: TenantId,
    val materialId: MaterialId,
    val at: Instant,
    val ownership: StockOwnershipSemantics = StockOwnershipSemantics.OWNED_RAW_MATERIAL,
    val targetUom: UnitOfMeasure? = null
)

data class ResolvedPrice(
    val materialId: MaterialId,
    val unitPrice: UnitPrice,
    val source: PriceSource,
    val effectiveFrom: Instant,
    val explanation: String
)

interface PriceSourceStrategy {
    val source: PriceSource
    suspend fun resolve(query: PriceQuery): ResolvedPrice?
}

data class TenantPricePolicy(
    val tenantId: TenantId,
    val preferenceOrder: List<PriceSource> = listOf(PriceSource.STANDARD),
    val fallbackToStandard: Boolean = true
)

class StandardPriceSource(
    private val priceRepository: MaterialPriceRepository
) : PriceSourceStrategy {
    override val source: PriceSource = PriceSource.STANDARD

    override suspend fun resolve(query: PriceQuery): ResolvedPrice? {
        val effective = priceRepository.effectivePriceAt(query.tenantId, query.materialId, query.at, source)
            ?: return null
        return ResolvedPrice(
            materialId = query.materialId,
            unitPrice = effective.unitPrice,
            source = source,
            effectiveFrom = effective.effectiveFrom,
            explanation = "Tarif standar acuan per ${effective.effectiveFrom} (${effective.unitPrice.amount.formatted()} / ${effective.unitPrice.per.formatted()})"
        )
    }
}

class PriceSourceResolver(
    private val strategies: Map<PriceSource, PriceSourceStrategy>,
    private val materialRepository: MaterialItemRepository
) {
    suspend fun resolve(query: PriceQuery, policy: TenantPricePolicy): Result<ResolvedPrice> {
        // Enforce Contract 3 & 4: Consigned client material is ALWAYS Rp 0 without calling strategies
        if (query.ownership == StockOwnershipSemantics.CONSIGNED_CLIENT_MATERIAL) {
            val item = materialRepository.findById(query.tenantId, query.materialId)
            val uom = query.targetUom ?: item?.baseUom ?: UnitOfMeasure.PIECE
            return Result.success(
                ResolvedPrice(
                    materialId = query.materialId,
                    unitPrice = UnitPrice(Money.zero(), Quantity.of(1.0, uom)),
                    source = PriceSource.CLIENT_SUPPLIED_ZERO,
                    effectiveFrom = query.at,
                    explanation = "Bahan konsinyasi titipan klien (Rp 0 di neraca pabrik)"
                )
            )
        }

        for (source in policy.preferenceOrder) {
            val strat = strategies[source] ?: continue
            val resolved = strat.resolve(query)
            if (resolved != null) {
                return Result.success(adaptTargetUom(resolved, query))
            }
        }

        if (policy.fallbackToStandard && !policy.preferenceOrder.contains(PriceSource.STANDARD)) {
            val standardStrat = strategies[PriceSource.STANDARD]
            val fallback = standardStrat?.resolve(query)
            if (fallback != null) {
                return Result.success(adaptTargetUom(fallback, query))
            }
        }

        return Result.failure(
            NoSuchElementException(
                "Tidak ditemukan tarif harga aktif untuk material '${query.materialId.value}' pada waktu ${query.at}"
            )
        )
    }

    private suspend fun adaptTargetUom(resolved: ResolvedPrice, query: PriceQuery): ResolvedPrice {
        val targetUom = query.targetUom ?: return resolved
        if (resolved.unitPrice.per.uom == targetUom) return resolved

        val item = materialRepository.findById(query.tenantId, query.materialId)
        if (item != null) {
            val targetQty = Quantity.of(1.0, targetUom)
            val inPerUom = item.convert(targetQty, resolved.unitPrice.per.uom)
            val cost = resolved.unitPrice.costOf(inPerUom)
            return resolved.copy(
                unitPrice = UnitPrice(cost, targetQty),
                explanation = "${resolved.explanation} -> Dikonversi ke ${targetUom.displayName}"
            )
        }
        return resolved.copy(
            unitPrice = resolved.unitPrice.convertedTo(targetUom)
        )
    }
}
