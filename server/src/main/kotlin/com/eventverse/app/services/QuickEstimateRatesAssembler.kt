package com.eventverse.app.services

import com.eventverse.app.domain.common.Money
import com.eventverse.app.domain.common.Quantity
import com.eventverse.app.domain.common.UnitOfMeasure
import com.eventverse.app.domain.costing.CostingRateCardRepository
import com.eventverse.app.domain.costing.MaterialCharacter
import com.eventverse.app.domain.costing.QuickEstimateRates
import com.eventverse.app.domain.masterdata.MaterialCategory
import com.eventverse.app.domain.masterdata.MaterialItem
import com.eventverse.app.domain.masterdata.MaterialItemRepository
import com.eventverse.app.domain.masterdata.MaterialPriceRepository
import com.eventverse.app.domain.pipeline.CostingBehavior
import com.eventverse.app.domain.tenant.TenantId
import kotlinx.datetime.Instant

/**
 * Merakit [QuickEstimateRates] dari data master tenant yang sudah ada.
 *
 * ## Mengapa perakitan ini di server, bukan di dalam use case?
 * Kontrak 4 (`module-integration-rules`) melarang rumus costing mengunci tenant pada satu
 * sumber tarif. [com.eventverse.app.domain.costing.usecases.EstimateCostingFromAiDesignUseCase]
 * karenanya hanya menerima angka jadi dan tidak tahu apa-apa soal rate card maupun master
 * material — tenant yang menyimpan tarifnya di tempat lain cukup mengganti perakit ini.
 *
 * ## Mengapa harga benang dicari lewat teks, bukan id material?
 * CS tidak memilih SKU benang; ia memilih "yang adem" atau "yang hangat".
 * [MaterialCharacter.yarnKeyword] menjadi jembatannya ke katalog material, dan bila tidak ada
 * yang cocok, harga dibiarkan null supaya estimator jatuh ke penskalaan benchmark alih-alih
 * memakai harga benang yang salah jenis.
 */
class QuickEstimateRatesAssembler(
    private val rateCardRepository: CostingRateCardRepository,
    private val materialRepository: MaterialItemRepository,
    private val materialPriceRepository: MaterialPriceRepository
) {

    suspend fun assemble(
        tenantId: TenantId,
        behavior: CostingBehavior,
        character: MaterialCharacter,
        at: Instant
    ): QuickEstimateRates {
        val card = rateCardRepository.findEffectiveAt(tenantId, behavior, at)

        return QuickEstimateRates(
            yarnPricePerKg = priceOf(
                tenantId, at,
                category = MaterialCategory.YARN,
                freeText = character.yarnKeyword,
                per = Quantity.kilograms(1.0)
            ),
            laborRatePerKnittingMinute = card?.laborRatePerSamMinute,
            buttonUnitPrice = priceOf(
                tenantId, at,
                category = MaterialCategory.TRIM,
                freeText = "kancing",
                per = Quantity.pieces(1)
            ),
            labelUnitPrice = priceOf(
                tenantId, at,
                category = MaterialCategory.ACCESSORY,
                freeText = "label",
                per = Quantity.pieces(1)
            ),
            hangtagUnitPrice = priceOf(
                tenantId, at,
                category = MaterialCategory.ACCESSORY,
                freeText = "hangtag",
                per = Quantity.pieces(1)
            ),
            overheadPerUnit = card?.overheadPerUnit,
            marginRatio = card?.marginRatio
        )
    }

    /**
     * Harga satu satuan acuan ([per]) dari material pertama yang cocok.
     *
     * `costOf` dipakai alih-alih membaca `unitPrice.amount` langsung karena katalog boleh
     * menyimpan harga per 5 kg atau per lusin; tanpa konversi, benang seharga Rp 500.000/5kg
     * akan terbaca sebagai Rp 500.000/kg.
     */
    private suspend fun priceOf(
        tenantId: TenantId,
        at: Instant,
        category: MaterialCategory,
        freeText: String,
        per: Quantity
    ): Money? {
        val material: MaterialItem = materialRepository
            .matchByFreeText(tenantId, freeText, category)
            .firstOrNull { !it.isArchived }
            ?: return null

        val price = materialPriceRepository.effectivePriceAt(tenantId, material.id, at) ?: return null
        return runCatching { price.unitPrice.costOf(per) }.getOrNull()
    }
}
