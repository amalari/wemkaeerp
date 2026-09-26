package com.eventverse.app.domain.techpack.usecases

import com.eventverse.app.domain.common.CurrencyCode
import com.eventverse.app.domain.common.Money
import com.eventverse.app.domain.masterdata.*
import com.eventverse.app.domain.pipeline.StockOwnershipSemantics
import com.eventverse.app.domain.techpack.BomCostPreview
import com.eventverse.app.domain.techpack.BomLineCost
import com.eventverse.app.domain.techpack.TechPackId
import com.eventverse.app.domain.techpack.TechPackRepository
import com.eventverse.app.domain.tenant.TenantId
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

class PreviewBomMaterialCostUseCase(
    private val techPackRepository: TechPackRepository,
    private val materialRepository: MaterialItemRepository,
    private val priceRepository: MaterialPriceRepository
) {
    suspend operator fun invoke(
        tenantId: TenantId,
        techPackId: TechPackId,
        orderQuantity: Long = 1L,
        at: Instant = Clock.System.now(),
        currency: CurrencyCode = CurrencyCode.IDR
    ): Result<BomCostPreview> = runCatching {
        require(orderQuantity > 0L) { "Kuantitas order harus lebih besar dari 0" }
        val techPack = techPackRepository.findById(tenantId, techPackId)
            ?: error("Tech Pack dengan ID '${techPackId.value}' tidak ditemukan")

        // Batch fetch all referenced material items
        val resolvedMaterialIds = techPack.bomLines.mapNotNull { it.material.materialId }.distinct()
        val materialItemsMap = if (resolvedMaterialIds.isNotEmpty()) {
            materialRepository.findAllByIds(tenantId, resolvedMaterialIds).associateBy { it.id }
        } else {
            emptyMap()
        }

        // Batch fetch standard effective prices
        val pricesMap = if (resolvedMaterialIds.isNotEmpty()) {
            priceRepository.effectivePricesAt(tenantId, resolvedMaterialIds, at, PriceSource.STANDARD)
        } else {
            emptyMap()
        }

        val lineCosts = techPack.bomLines.map { line ->
            val materialId = line.material.materialId
            val materialItem = materialId?.let { materialItemsMap[it] }
            val priceRecord = materialId?.let { pricesMap[it] }

            val grossPerGarment = line.grossQuantityPerGarment
            val grossTotal = if (techPack.sizeYieldFactors.isNotEmpty() && techPack.totalOrderedQuantity > 0L) {
                techPack.grossRequirementFor(line)
            } else {
                line.grossFor(orderQuantity)
            }

            if (line.ownership == StockOwnershipSemantics.CONSIGNED_CLIENT_MATERIAL) {
                // Bahan konsinyasi klien: biaya di neraca pabrik adalah Rp 0 (Kontrak 3 & 4),
                // namun bila terdapat harga acuan standar, nilainya dicatat untuk rekonsiliasi waste.
                val notionalPrice = priceRecord?.unitPrice
                val notionalCostTotal = if (notionalPrice != null && materialItem != null) {
                    val converted = materialItem.convert(grossTotal, notionalPrice.per.uom)
                    notionalPrice.costOf(converted)
                } else if (notionalPrice != null && grossTotal.uom.canConvertTo(notionalPrice.per.uom)) {
                    val converted = grossTotal.convertTo(notionalPrice.per.uom)
                    notionalPrice.costOf(converted)
                } else {
                    Money.zero(currency)
                }

                val resolved = if (materialId != null) {
                    ResolvedPrice(
                        materialId = materialId,
                        unitPrice = com.eventverse.app.domain.common.UnitPrice(
                            amount = Money.zero(currency),
                            per = com.eventverse.app.domain.common.Quantity(1_000_000L, line.netQuantityPerGarment.uom)
                        ),
                        source = PriceSource.CLIENT_SUPPLIED_ZERO,
                        effectiveFrom = at,
                        explanation = "Bahan konsinyasi titipan klien (Rp 0 di neraca pabrik; nilai notional: ${notionalCostTotal.formatted()})"
                    )
                } else null

                BomLineCost(
                    lineId = line.lineId,
                    material = line.material,
                    category = line.category,
                    grossQuantityPerGarment = grossPerGarment,
                    grossQuantityTotal = grossTotal,
                    resolvedPrice = resolved,
                    costPerGarment = Money.zero(currency),
                    costTotal = notionalCostTotal, // Digunakan untuk consignedNotionalValue pada BomCostPreview
                    ownership = line.ownership
                )
            } else {
                // Bahan milik pabrik (OWNED_RAW_MATERIAL / dll)
                if (priceRecord != null && materialItem != null) {
                    val unitPrice = priceRecord.unitPrice
                    val convertedGrossGarment = materialItem.convert(grossPerGarment, unitPrice.per.uom)
                    val convertedGrossTotal = materialItem.convert(grossTotal, unitPrice.per.uom)

                    val costPerGarment = unitPrice.costOf(convertedGrossGarment)
                    val costTotal = unitPrice.costOf(convertedGrossTotal)

                    val resolved = ResolvedPrice(
                        materialId = materialItem.id,
                        unitPrice = unitPrice,
                        source = priceRecord.source,
                        effectiveFrom = priceRecord.effectiveFrom,
                        explanation = "Tarif ${priceRecord.source.displayName} berlaku sejak ${priceRecord.effectiveFrom}"
                    )

                    BomLineCost(
                        lineId = line.lineId,
                        material = line.material,
                        category = line.category,
                        grossQuantityPerGarment = grossPerGarment,
                        grossQuantityTotal = grossTotal,
                        resolvedPrice = resolved,
                        costPerGarment = costPerGarment,
                        costTotal = costTotal,
                        ownership = line.ownership
                    )
                } else if (priceRecord != null && grossPerGarment.uom.canConvertTo(priceRecord.unitPrice.per.uom)) {
                    val unitPrice = priceRecord.unitPrice
                    val convertedGrossGarment = grossPerGarment.convertTo(unitPrice.per.uom)
                    val convertedGrossTotal = grossTotal.convertTo(unitPrice.per.uom)

                    val costPerGarment = unitPrice.costOf(convertedGrossGarment)
                    val costTotal = unitPrice.costOf(convertedGrossTotal)

                    val resolved = ResolvedPrice(
                        materialId = priceRecord.materialId,
                        unitPrice = unitPrice,
                        source = priceRecord.source,
                        effectiveFrom = priceRecord.effectiveFrom,
                        explanation = "Tarif ${priceRecord.source.displayName} berlaku sejak ${priceRecord.effectiveFrom}"
                    )

                    BomLineCost(
                        lineId = line.lineId,
                        material = line.material,
                        category = line.category,
                        grossQuantityPerGarment = grossPerGarment,
                        grossQuantityTotal = grossTotal,
                        resolvedPrice = resolved,
                        costPerGarment = costPerGarment,
                        costTotal = costTotal,
                        ownership = line.ownership
                    )
                } else {
                    // Belum ada harga acuan aktif atau belum terpetakan ke katalog
                    BomLineCost(
                        lineId = line.lineId,
                        material = line.material,
                        category = line.category,
                        grossQuantityPerGarment = grossPerGarment,
                        grossQuantityTotal = grossTotal,
                        resolvedPrice = null,
                        costPerGarment = Money.zero(currency),
                        costTotal = Money.zero(currency),
                        ownership = line.ownership
                    )
                }
            }
        }

        BomCostPreview(
            techPackId = techPackId,
            orderQuantity = orderQuantity,
            at = at,
            currency = currency,
            lines = lineCosts
        )
    }
}
