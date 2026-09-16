package com.eventverse.app.infrastructure.api

import com.eventverse.app.domain.deal.Deal
import com.eventverse.app.domain.deal.DealStage
import com.eventverse.app.domain.deal.PurchaseOrder
import com.eventverse.app.domain.sampling.SamplingOrder
import com.eventverse.app.shared.deal.DealCodec

/**
 * Remote operations the Deal screen needs — the same interface-for-testability pattern as
 * [CrmRemoteDataSource].
 */
interface DealRemoteDataSource {

    suspend fun getDeals(tenantSlug: String): Result<List<Deal>>

    suspend fun getContacts(tenantSlug: String): Result<List<com.eventverse.app.domain.crm.Contact>>

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

    // ── Sampling siklus (Golden Sample Lock) ─────────────────────────────────────────────────

    /** Seluruh order sampling (per desain) yang menempel pada deal. */
    suspend fun getDealSamplingOrders(tenantSlug: String, dealId: String): Result<List<SamplingOrder>>

    /** Membuat lembar sampling baru ATAU memperbarui quantity/kurir/biaya milik yang sudah ada. */
    suspend fun saveSamplingOrderFromDeal(
        tenantSlug: String,
        dealId: String,
        request: SaveSamplingOrderFromDealRequest
    ): Result<SamplingOrder>

    /** ACC / revisi satu desain; mengembalikan seluruh daftar sampling deal (gerbang Tab 2). */
    suspend fun approveSamplingOrder(
        tenantSlug: String,
        dealId: String,
        samplingOrderId: String,
        isApproved: Boolean,
        notes: String
    ): Result<List<SamplingOrder>>

    /**
     * Mengunggah foto mockup desain. Kontrak kawatnya sama dengan upload PO: metadata lewat
     * query, bytes sebagai raw body (satu PUT, tanpa multipart).
     */
    suspend fun uploadSamplingMockup(
        tenantSlug: String,
        dealId: String,
        samplingOrderId: String,
        fileName: String,
        mimeType: String,
        bytes: ByteArray
    ): Result<SamplingOrder>
}

/** Payload tombol simpan lembar sampling di Tab 1 dialog detail deal. */
data class SaveSamplingOrderFromDealRequest(
    val samplingOrderId: String?,
    val styleName: String,
    val sampleQuantity: Int,
    val courierTracking: String?,
    val samplingFeeIdr: Long,
    val notes: String
)

data class DealDetailResponse(
    val deal: Deal,
    val contact: com.eventverse.app.domain.crm.Contact?,
    val purchaseOrders: List<PurchaseOrder>
)
