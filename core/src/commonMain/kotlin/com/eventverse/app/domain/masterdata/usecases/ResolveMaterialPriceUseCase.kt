package com.eventverse.app.domain.masterdata.usecases

import com.eventverse.app.domain.masterdata.MaterialPriceRepository
import com.eventverse.app.domain.masterdata.PriceQuery
import com.eventverse.app.domain.masterdata.PriceSourceResolver
import com.eventverse.app.domain.masterdata.ResolvedPrice

class ResolveMaterialPriceUseCase(
    private val priceResolver: PriceSourceResolver,
    private val priceRepository: MaterialPriceRepository
) {
    suspend operator fun invoke(query: PriceQuery): Result<ResolvedPrice> = runCatching {
        val policy = priceRepository.policyFor(query.tenantId)
        priceResolver.resolve(query, policy).getOrThrow()
    }
}
