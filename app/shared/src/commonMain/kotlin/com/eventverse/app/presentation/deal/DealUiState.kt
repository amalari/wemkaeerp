package com.eventverse.app.presentation.deal

import com.eventverse.app.domain.deal.Deal
import com.eventverse.app.domain.deal.DealStage
import com.eventverse.app.domain.deal.PurchaseOrder
import com.eventverse.app.domain.sampling.SamplingOrder
import kotlinx.datetime.LocalDate

/** Tab di dalam dialog detail deal: siklus sampling vs produksi massal & PO. */
enum class DealDetailTab(val label: String) {
    SAMPLING("Siklus Sampling"),
    MASS_PRODUCTION("Produksi Massal & PO")
}

data class DealUiState(
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val error: String? = null,
    val statusMessage: String? = null,
    val deal: Deal? = null,
    val contactName: String? = null,
    val contactPhone: String? = null,
    val contactEmail: String? = null,
    val purchaseOrders: List<PurchaseOrder> = emptyList(),
    val samplingOrders: List<SamplingOrder> = emptyList(),
    val activeTab: DealDetailTab = DealDetailTab.SAMPLING,
    val canWrite: Boolean = true
) {
    val hasDeal: Boolean get() = deal != null

    /** Desain aktif = belum ACC dan tidak dibatalkan — inilah yang mengunci Tab Produksi. */
    val activeDesigns: List<SamplingOrder>
        get() = samplingOrders.filter { it.isActiveDesign }

    /** Desain yang sudah di-ACC buyer — acuan Golden Sample Tab Produksi. */
    val approvedDesigns: List<SamplingOrder>
        get() = samplingOrders.filter { it.isAccApproved }

    /** Gerbang Tab Produksi Massal: terbuka hanya jika tidak ada desain aktif tersisa. */
    val productionUnlocked: Boolean
        get() = samplingOrders.isNotEmpty() && activeDesigns.isEmpty()
}

sealed interface DealUiEvent {
    data object Load : DealUiEvent
    data object DismissError : DealUiEvent
    data object DismissStatusMessage : DealUiEvent
    data class ChangeStage(val stage: DealStage) : DealUiEvent

    /**
     * Menerbitkan SPK Produksi Massal ke modul `PRODUCTION_MRP` dan memindahkan deal ke
     * `IN_PRODUCTION`. Sebelumnya tombol ini hanya menggeser stage, sehingga lantai produksi
     * tidak pernah menerima dokumen kerja apa pun.
     */
    data object LaunchBulkProduction : DealUiEvent
    data class AttachManualPo(
        val poNumber: String,
        val description: String,
        val quantity: Double,
        val unitPriceIdr: Long
    ) : DealUiEvent
    data object UploadPoFile : DealUiEvent
    data class OpenPoDownload(val poId: String) : DealUiEvent

    // ── Siklus sampling (Tab 1) ──────────────────────────────────────────────────────────────
    data class SelectDealTab(val tab: DealDetailTab) : DealUiEvent

    /** [samplingOrderId] null = tambah desain baru; terisi = perbarui quantity/kurir/biaya. */
    data class SaveSamplingOrder(
        val samplingOrderId: String?,
        val styleName: String,
        val sampleQuantity: Int,
        val courierTracking: String?,
        val samplingFeeIdr: Long,
        val notes: String,
        val sizeMatrix: List<com.eventverse.app.domain.sampling.SizeChartRow>? = null,
        val deadlineDelivery: LocalDate? = null
    ) : DealUiEvent

    data class ToggleSampleAcc(
        val samplingId: String,
        val isApproved: Boolean,
        val notes: String
    ) : DealUiEvent

    /** Unggah foto mockup hasil cropper kotak (bytes sudah 1:1, slot: front / back). */
    data class UploadSamplingMockup(
        val samplingId: String,
        val fileName: String,
        val mimeType: String,
        val bytes: ByteArray,
        val slot: String = "front"
    ) : DealUiEvent

    /** Menerbitkan lembar sampling di deal menjadi SPK resmi ke Divisi Sampling. */
    data class CreateSamplingSpk(val samplingId: String) : DealUiEvent

    /** Memindahkan stage lembar sampling (misal: ke IN_DELIVERY / status pengiriman). */
    data class AdvanceSamplingStage(
        val samplingId: String,
        val targetStage: com.eventverse.app.domain.sampling.SamplingPipelineStage
    ) : DealUiEvent
}
