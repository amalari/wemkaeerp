package com.eventverse.app.presentation.deal

import com.eventverse.app.domain.deal.DealStage
import com.eventverse.app.domain.deal.PurchaseOrderLine
import com.eventverse.app.domain.sampling.SamplingOrder
import com.eventverse.app.domain.sampling.SamplingPipelineStage
import com.eventverse.app.infrastructure.api.SamplingApiClient
import com.eventverse.app.infrastructure.api.SamplingRemoteDataSource
import com.eventverse.app.infrastructure.api.CreateSamplingOrderFromDealRequest
import com.eventverse.app.infrastructure.api.DealApiClient
import com.eventverse.app.infrastructure.api.DealRemoteDataSource
import com.eventverse.app.infrastructure.api.ProductionApiClient
import com.eventverse.app.infrastructure.api.ProductionRemoteDataSource
import com.eventverse.app.infrastructure.api.UpdateSamplingOrderFromDealRequest
import com.eventverse.app.shared.deal.DealCodec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * State holder untuk satu Deal (MVI). Dibuat per-dialog: Deal masih satu layar terbatas di
 * dalam CRM, jadi ViewModel ini menampung satu deal + contact + daftar PO-nya.
 */
class DealViewModel(
    private val tenantSlug: String,
    /** Muat deal by id ATAU by source lead (resolusi lewat daftar deal). */
    private val dealId: String? = null,
    private val sourceLeadId: String? = null,
    private val remoteDataSource: DealRemoteDataSource = DealApiClient(),
    private val productionDataSource: ProductionRemoteDataSource = ProductionApiClient(),
    private val samplingDataSource: SamplingRemoteDataSource = SamplingApiClient(),
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main)
) {
    private val _uiState = MutableStateFlow(DealUiState())
    val uiState: StateFlow<DealUiState> = _uiState.asStateFlow()

    fun onEvent(event: DealUiEvent) {
        when (event) {
            is DealUiEvent.Load -> load()
            is DealUiEvent.DismissError -> _uiState.update { it.copy(error = null) }
            is DealUiEvent.DismissStatusMessage -> _uiState.update { it.copy(statusMessage = null) }
            is DealUiEvent.ChangeStage -> changeStage(event.stage)
            is DealUiEvent.LaunchBulkProduction -> launchBulkProduction()
            is DealUiEvent.AttachManualPo -> attachManualPo(event)
            is DealUiEvent.UploadPoFile -> uploadPoFile()
            is DealUiEvent.OpenPoDownload -> openDownload(event.poId)
            is DealUiEvent.SelectDealTab -> selectDealTab(event.tab)
            is DealUiEvent.SaveSamplingOrder -> saveSamplingOrder(event)
            is DealUiEvent.ToggleSampleAcc -> toggleSampleAcc(event)
            is DealUiEvent.UploadSamplingMockup -> uploadSamplingMockup(event)
            is DealUiEvent.CreateSamplingSpk -> createSamplingSpk(event.samplingId)
            is DealUiEvent.AdvanceSamplingStage -> advanceSamplingStage(event.samplingId, event.targetStage)
        }
    }

    private fun selectDealTab(tab: DealDetailTab) {
        val state = _uiState.value
        // Gerbang: Tab Produksi Massal terkunci selama masih ada desain sampling aktif.
        if (tab == DealDetailTab.MASS_PRODUCTION && !state.productionUnlocked) {
            _uiState.update {
                it.copy(
                    statusMessage = "Tab Produksi terkunci: ${it.activeDesigns.size} desain belum di-ACC " +
                        "(${it.approvedDesigns.size} dari ${it.samplingOrders.size} desain sudah ACC)."
                )
            }
            return
        }
        _uiState.update { it.copy(activeTab = tab) }
    }

    /**
     * Autosave ada dua sumber (commit rename + debounce fee/catatan) dan bisa nyaris bersamaan.
     * Tanpa serialisasi, dua PUT read-modify-write bisa saling menimpa (lost update).
     */
    private val samplingSaveMutex = Mutex()

    private fun saveSamplingOrder(event: DealUiEvent.SaveSamplingOrder) {
        val deal = _uiState.value.deal ?: return
        if (!_uiState.value.canWrite) return
        _uiState.update { it.copy(isSaving = true) }
        val isCreate = event.samplingOrderId == null
        scope.launch {
            samplingSaveMutex.withLock {
                if (isCreate) {
                    remoteDataSource.createSamplingOrderFromDeal(
                        tenantSlug = tenantSlug,
                        dealId = deal.id.value,
                        request = CreateSamplingOrderFromDealRequest(
                            styleName = event.styleName.trim(),
                            sampleQuantity = event.sampleQuantity,
                            courierTracking = event.courierTracking?.trim()?.takeIf { it.isNotEmpty() },
                            samplingFeeIdr = event.samplingFeeIdr,
                            notes = event.notes,
                            sizeMatrix = event.sizeMatrix,
                            deadlineDelivery = event.deadlineDelivery
                        )
                    )
                } else {
                    remoteDataSource.updateSamplingOrderFromDeal(
                        tenantSlug = tenantSlug,
                        dealId = deal.id.value,
                        samplingOrderId = event.samplingOrderId,
                        request = UpdateSamplingOrderFromDealRequest(
                            styleName = event.styleName.trim(),
                            sampleQuantity = event.sampleQuantity,
                            courierTracking = event.courierTracking?.trim()?.takeIf { it.isNotEmpty() },
                            samplingFeeIdr = event.samplingFeeIdr,
                            notes = event.notes,
                            sizeMatrix = event.sizeMatrix,
                            deadlineDelivery = event.deadlineDelivery
                        )
                    )
                }
            }.onSuccess { saved ->
                _uiState.update { current ->
                    current.copy(
                        isSaving = false,
                        statusMessage = if (isCreate) {
                            "Desain \"${saved.styleName}\" ditambahkan ke siklus sampling."
                        } else {
                            "Lembar sampling \"${saved.styleName}\" diperbarui."
                        },
                        // Ganti di tempat, BUKAN filter+append: kode desain (DSG-01/02/…) dihitung
                        // dari posisi kartu, jadi memindahkan kartu yang disave ke akhir list akan
                        // membuat kode desain saling bertukar di layar.
                        samplingOrders = current.samplingOrders.replaceOrAppendById(saved)
                    )
                }
            }.onFailure { error ->
                _uiState.update { it.copy(isSaving = false, error = error.message) }
            }
        }
    }

    /** Ganti order dengan id sama di posisinya; pertahankan foto mockup URL jika respon autosave belum presign. */
    private fun List<SamplingOrder>.replaceOrAppendById(saved: SamplingOrder): List<SamplingOrder> =
        if (any { it.id == saved.id }) {
            map { existing ->
                if (existing.id == saved.id) {
                    val existingFront = existing.mockupFrontKey
                    val savedFront = saved.mockupFrontKey
                    val keepExistingFront = (savedFront.isNullOrBlank() || (!savedFront.startsWith("http") && !savedFront.startsWith("data:"))) &&
                        (existingFront?.startsWith("http") == true || existingFront?.startsWith("data:") == true)

                    val existingBack = existing.mockupBackKey
                    val savedBack = saved.mockupBackKey
                    val keepExistingBack = (savedBack.isNullOrBlank() || (!savedBack.startsWith("http") && !savedBack.startsWith("data:"))) &&
                        (existingBack?.startsWith("http") == true || existingBack?.startsWith("data:") == true)

                    if (keepExistingFront || keepExistingBack) {
                        val mergedUrls = mutableListOf<String>()
                        if (keepExistingFront && existingFront != null) {
                            mergedUrls.add("front:$existingFront")
                        } else if (!savedFront.isNullOrBlank()) {
                            mergedUrls.add("front:$savedFront")
                        }
                        if (keepExistingBack && existingBack != null) {
                            mergedUrls.add("back:$existingBack")
                        } else if (!savedBack.isNullOrBlank()) {
                            mergedUrls.add("back:$savedBack")
                        }
                        saved.copy(knitSpec = saved.knitSpec.copy(mockupImageUrls = mergedUrls))
                    } else {
                        saved
                    }
                } else existing
            }
        } else {
            this + saved
        }

    private fun toggleSampleAcc(event: DealUiEvent.ToggleSampleAcc) {
        val deal = _uiState.value.deal ?: return
        if (!_uiState.value.canWrite) return
        _uiState.update { it.copy(isSaving = true) }
        scope.launch {
            remoteDataSource.approveSamplingOrder(
                tenantSlug = tenantSlug,
                dealId = deal.id.value,
                samplingOrderId = event.samplingId,
                isApproved = event.isApproved,
                notes = event.notes
            ).onSuccess { orders ->
                _uiState.update { current ->
                    current.copy(
                        isSaving = false,
                        samplingOrders = orders,
                        statusMessage = if (event.isApproved) {
                            "Sampel di-ACC — spesifikasi terkunci sebagai acuan produksi."
                        } else {
                            "Revisi sampling dicatat."
                        },
                        // Seluruh desain beres? Otomatis buka Tab Produksi Massal.
                        activeTab = if (
                            orders.isNotEmpty() && orders.none { it.isActiveDesign }
                        ) {
                            DealDetailTab.MASS_PRODUCTION
                        } else {
                            current.activeTab
                        }
                    )
                }
            }.onFailure { error ->
                _uiState.update { it.copy(isSaving = false, error = error.message) }
            }
        }
    }

    /**
     * Mengunggah foto mockup yang SUDAH dipotong kotak (1:1) lewat cropper di dialog detail.
     * Pemilihan & pemotongan berkas terjadi di lapisan UI; ViewModel hanya mengirim hasilnya.
     */
    private fun uploadSamplingMockup(event: DealUiEvent.UploadSamplingMockup) {
        val deal = _uiState.value.deal ?: return
        if (!_uiState.value.canWrite) return
        _uiState.update { it.copy(isSaving = true) }
        scope.launch {
            remoteDataSource.uploadSamplingMockup(
                tenantSlug = tenantSlug,
                dealId = deal.id.value,
                samplingOrderId = event.samplingId,
                fileName = event.fileName,
                mimeType = event.mimeType,
                bytes = event.bytes,
                slot = event.slot
            ).onSuccess { updated ->
                _uiState.update { current ->
                    current.copy(
                        isSaving = false,
                        statusMessage = "Foto desain " + updated.styleName + " terunggah.",
                        samplingOrders = current.samplingOrders.map { order ->
                            if (order.id == updated.id) updated else order
                        }
                    )
                }
            }.onFailure { error ->
                _uiState.update { it.copy(isSaving = false, error = error.message) }
            }
        }
    }

    private fun createSamplingSpk(samplingId: String) {
        val deal = _uiState.value.deal ?: return
        val currentOrder = _uiState.value.samplingOrders.firstOrNull { it.id.value == samplingId } ?: return
        _uiState.update { it.copy(isSaving = true) }
        scope.launch {
            val targetStage = if (currentOrder.pipelineStage == SamplingPipelineStage.NEW_INTAKE) {
                SamplingPipelineStage.CAM_PROGRAMMING
            } else currentOrder.pipelineStage
            samplingDataSource.advanceStage(tenantSlug, samplingId, targetStage)
                .onSuccess { updatedOrder ->
                    _uiState.update { current ->
                        current.copy(
                            isSaving = false,
                            statusMessage = "SPK #${updatedOrder.spkNumber.value} (${updatedOrder.styleName}) berhasil diterbitkan ke Divisi Sampling.",
                            samplingOrders = current.samplingOrders.replaceOrAppendById(updatedOrder)
                        )
                    }
                    if (_uiState.value.deal?.stage == DealStage.OPEN) {
                        changeStage(DealStage.PO_RECEIVED)
                    }
                }
                .onFailure { err ->
                    _uiState.update { it.copy(isSaving = false, error = "Gagal menerbitkan SPK: ${err.message}") }
                }
        }
    }

    private fun advanceSamplingStage(samplingId: String, targetStage: SamplingPipelineStage) {
        val deal = _uiState.value.deal ?: return
        _uiState.update { it.copy(isSaving = true) }
        scope.launch {
            samplingDataSource.advanceStage(tenantSlug, samplingId, targetStage)
                .onSuccess { updatedOrder ->
                    _uiState.update { current ->
                        current.copy(
                            isSaving = false,
                            statusMessage = "Status lembar sampling diubah ke ${updatedOrder.pipelineStage.displayName}.",
                            samplingOrders = current.samplingOrders.replaceOrAppendById(updatedOrder)
                        )
                    }
                    if (_uiState.value.deal?.stage == DealStage.OPEN) {
                        changeStage(DealStage.PO_RECEIVED)
                    }
                }
                .onFailure { err ->
                    _uiState.update { it.copy(isSaving = false, error = "Gagal mengubah stage: ${err.message}") }
                }
        }
    }

    private fun load() {
        _uiState.update { it.copy(isLoading = true, error = null) }
        scope.launch {
            val resolved = when {
                dealId != null -> dealId
                sourceLeadId != null -> resolveDealIdByLead(sourceLeadId)
                else -> null
            }
            if (resolved == null) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        deal = null,
                        statusMessage = "Deal belum dibuat untuk lead ini. Qualify lead untuk membuatnya."
                    )
                }
                return@launch
            }
            remoteDataSource.getDealDetail(tenantSlug, resolved)
                .onSuccess { detail ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            deal = detail.deal,
                            contactName = detail.contact?.displayName,
                            contactPhone = detail.contact?.phone?.localDisplay,
                            contactEmail = detail.contact?.email?.takeIf { email -> email.isNotBlank() },
                            purchaseOrders = detail.purchaseOrders
                        )
                    }
                    // Lembar sampling dimuat terpisah — gagalnya tidak boleh menggagalkan detail deal.
                    remoteDataSource.getDealSamplingOrders(tenantSlug, resolved)
                        .onSuccess { orders ->
                            _uiState.update { it.copy(samplingOrders = orders) }
                        }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(isLoading = false, error = error.message) }
                }
        }
    }

    private suspend fun resolveDealIdByLead(leadId: String): String? {
        val deals = remoteDataSource.getDeals(tenantSlug).getOrNull() ?: return null
        return deals.firstOrNull { it.sourceLeadId?.value == leadId }?.id?.value
    }

    private fun changeStage(stage: DealStage) {
        val deal = _uiState.value.deal ?: return
        if (!_uiState.value.canWrite) return
        scope.launch {
            remoteDataSource.updateStage(tenantSlug, deal.id.value, stage)
                .onSuccess { updated -> _uiState.update { it.copy(deal = updated) } }
                .onFailure { error -> _uiState.update { it.copy(error = error.message) } }
        }
    }

    /**
     * Menerbitkan SPK massal, lalu baru memindahkan stage deal.
     *
     * Urutannya penting: kalau stage digeser lebih dulu dan penerbitan SPK gagal (belum ada
     * sampel ACC, belum ada PO), deal akan tampak "sedang diproduksi" padahal lantai produksi
     * tidak memegang dokumen apa pun.
     */
    private fun launchBulkProduction() {
        val deal = _uiState.value.deal ?: return
        if (!_uiState.value.canWrite) return
        _uiState.update { it.copy(isSaving = true) }
        scope.launch {
            productionDataSource.launchFromDeal(tenantSlug, deal.id.value)
                .onSuccess { workOrder ->
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            statusMessage = "SPK massal ${workOrder.spkNumber.value} diterbitkan " +
                                "(${workOrder.totalOrderedPcs} pcs)."
                        )
                    }
                    changeStage(DealStage.IN_PRODUCTION)
                }
                .onFailure { error ->
                    _uiState.update { it.copy(isSaving = false, error = error.message) }
                }
        }
    }

    private fun attachManualPo(event: DealUiEvent.AttachManualPo) {
        val deal = _uiState.value.deal ?: return
        if (!_uiState.value.canWrite) return
        _uiState.update { it.copy(isSaving = true) }
        scope.launch {
            val request = DealCodec.AttachManualPoRequest(
                poNumber = event.poNumber.trim(),
                poDate = kotlinx.datetime.Clock.System.now()
                    .toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault()).date,
                lines = listOf(
                    PurchaseOrderLine(
                        description = event.description,
                        quantity = event.quantity,
                        unitPriceIdr = event.unitPriceIdr
                    )
                ),
                notes = ""
            )
            remoteDataSource.attachManualPurchaseOrder(tenantSlug, deal.id.value, request)
                .onSuccess { po ->
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            statusMessage = "PO \"${po.poNumber.value}\" ditambahkan.",
                            purchaseOrders = listOf(po) + it.purchaseOrders
                        )
                    }
                    // Deal masih OPEN? Muat ulang untuk menangkap transisi otomatis ke PO_RECEIVED.
                    if (_uiState.value.deal?.stage == DealStage.OPEN) load()
                }
                .onFailure { error -> _uiState.update { it.copy(isSaving = false, error = error.message) } }
        }
    }

    private fun uploadPoFile() {
        val deal = _uiState.value.deal ?: return
        if (!_uiState.value.canWrite) return
        scope.launch {
            val picked = pickPoFile()
            if (picked == null) {
                _uiState.update {
                    it.copy(statusMessage = "Upload berkas belum didukung di platform ini — gunakan input manual PO.")
                }
                return@launch
            }
            _uiState.update { it.copy(isSaving = true) }
            remoteDataSource.uploadPurchaseOrder(
                tenantSlug = tenantSlug,
                dealId = deal.id.value,
                poNumber = picked.suggestedPoNumber,
                fileName = picked.fileName,
                mimeType = picked.mimeType,
                bytes = picked.bytes
            ).onSuccess { po ->
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        statusMessage = "PO \"${po.poNumber.value}\" terunggah.",
                        purchaseOrders = listOf(po) + it.purchaseOrders
                    )
                }
                if (_uiState.value.deal?.stage == DealStage.OPEN) load()
            }.onFailure { error ->
                _uiState.update { it.copy(isSaving = false, error = error.message) }
            }
        }
    }

    private fun openDownload(poId: String) {
        val deal = _uiState.value.deal ?: return
        scope.launch {
            remoteDataSource.purchaseOrderDownloadUrl(tenantSlug, deal.id.value, poId)
                .onSuccess { url -> openInBrowser(url) }
                .onFailure { error -> _uiState.update { it.copy(error = error.message) } }
        }
    }
}
