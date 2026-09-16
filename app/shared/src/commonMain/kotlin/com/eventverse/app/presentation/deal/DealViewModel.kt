package com.eventverse.app.presentation.deal

import com.eventverse.app.domain.deal.DealStage
import com.eventverse.app.domain.deal.PurchaseOrderLine
import com.eventverse.app.infrastructure.api.DealApiClient
import com.eventverse.app.infrastructure.api.DealRemoteDataSource
import com.eventverse.app.shared.deal.DealCodec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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
            is DealUiEvent.AttachManualPo -> attachManualPo(event)
            is DealUiEvent.UploadPoFile -> uploadPoFile()
            is DealUiEvent.OpenPoDownload -> openDownload(event.poId)
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
