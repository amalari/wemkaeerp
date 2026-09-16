package com.eventverse.app.infrastructure.api

import com.eventverse.app.domain.deal.Deal
import com.eventverse.app.domain.deal.DealStage
import com.eventverse.app.domain.deal.PurchaseOrder
import com.eventverse.app.shared.deal.DealCodec

/**
 * Remote operations the Deal screen needs — the same interface-for-testability pattern as
 * [CrmRemoteDataSource].
 */
interface DealRemoteDataSource {

    suspend fun getDeals(tenantSlug: String): Result<List<Deal>>

    /** Detail payload: deal + contact + purchase orders. */
    suspend fun getDealDetail(tenantSlug: String, dealId: String): Result<DealDetailResponse>

    suspend fun updateStage(tenantSlug: String, dealId: String, stage: DealStage): Result<Deal>

    suspend fun attachManualPurchaseOrder(
        tenantSlug: String,
        dealId: String,
        request: DealCodec.AttachManualPoRequest
    ): Result<PurchaseOrder>

    /**
     * Uploads a PO file. Metadata travels as query parameters, bytes as the raw body —
     * the same single-PUT contract the server route implements.
     */
    suspend fun uploadPurchaseOrder(
        tenantSlug: String,
        dealId: String,
        poNumber: String,
        fileName: String,
        mimeType: String,
        bytes: ByteArray
    ): Result<PurchaseOrder>

    /** Absolute presigned download URL for an uploaded PO. */
    suspend fun purchaseOrderDownloadUrl(tenantSlug: String, dealId: String, poId: String): Result<String>
}

data class DealDetailResponse(
    val deal: Deal,
    val contact: com.eventverse.app.domain.crm.Contact?,
    val purchaseOrders: List<PurchaseOrder>
)
