package com.eventverse.app.domain.moduledev.usecases

import com.eventverse.app.domain.moduledev.CustomizationRequestId
import com.eventverse.app.domain.moduledev.ModuleCatalogEntryId
import com.eventverse.app.domain.moduledev.ModuleCatalogRepository
import com.eventverse.app.domain.moduledev.ModuleCustomizationRequest
import com.eventverse.app.domain.moduledev.ModuleCustomizationRequestRepository
import com.eventverse.app.domain.tenant.TenantId
import kotlinx.datetime.Instant

/**
 * Records a factory's request for something the product does not do yet.
 *
 * The description is stored exactly as written. It is tempting to normalise it into something
 * tidier on the way in, but this text is the richest context any later estimate gets — and unlike
 * every other column, a rewritten version of it cannot be recovered.
 */
class SubmitCustomizationRequestUseCase(
    private val requestRepository: ModuleCustomizationRequestRepository,
    private val catalogRepository: ModuleCatalogRepository
) {
    suspend operator fun invoke(
        id: CustomizationRequestId,
        tenantId: TenantId,
        title: String,
        descriptionRaw: String,
        catalogEntryId: ModuleCatalogEntryId? = null,
        requestedByUserId: String? = null,
        requestedAt: Instant? = null
    ): Result<ModuleCustomizationRequest> = runCatching {
        if (catalogEntryId != null) {
            requireNotNull(catalogRepository.findById(catalogEntryId)) {
                "Modul ${catalogEntryId.value} tidak ada di katalog."
            }
        }

        val request = ModuleCustomizationRequest(
            id = id,
            tenantId = tenantId,
            title = title.trim(),
            descriptionRaw = descriptionRaw,
            catalogEntryId = catalogEntryId,
            requestedByUserId = requestedByUserId,
            requestedAt = requestedAt
        )

        requestRepository.save(request)
        request
    }
}
