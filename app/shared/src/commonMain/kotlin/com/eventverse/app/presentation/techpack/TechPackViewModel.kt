package com.eventverse.app.presentation.techpack

import com.eventverse.app.domain.contracts.BomLine
import com.eventverse.app.domain.contracts.LaborOperation
import com.eventverse.app.domain.contracts.SizeYieldFactor
import com.eventverse.app.domain.masterdata.MaterialCatalogQuery
import com.eventverse.app.domain.techpack.TechPackId
import com.eventverse.app.domain.techpack.TechPackQuery
import com.eventverse.app.infrastructure.api.MasterDataApiClient
import com.eventverse.app.infrastructure.api.MasterDataRemoteDataSource
import com.eventverse.app.infrastructure.api.TechPackApiClient
import com.eventverse.app.infrastructure.api.TechPackRemoteDataSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class TechPackViewModel(
    private val tenantSlug: String,
    private val remoteDataSource: TechPackRemoteDataSource = TechPackApiClient(),
    private val masterDataSource: MasterDataRemoteDataSource = MasterDataApiClient(),
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main)
) {
    private val _uiState = MutableStateFlow(TechPackUiState())
    val uiState: StateFlow<TechPackUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun onEvent(event: TechPackUiEvent) {
        when (event) {
            is TechPackUiEvent.Load -> load()
            is TechPackUiEvent.SelectTechPack -> selectTechPack(event.id)
            is TechPackUiEvent.SelectWorkbenchTab -> {
                _uiState.update { it.copy(activeWorkbenchTab = event.tab) }
                if (event.tab == TechPackWorkbenchTab.COST_PREVIEW) {
                    loadCostPreview()
                }
            }
            is TechPackUiEvent.SelectMobileTab -> {
                _uiState.update { it.copy(activeMobileTab = event.tab) }
                if (event.tab == TechPackMobileTab.COST_PREVIEW) {
                    loadCostPreview()
                }
            }
            is TechPackUiEvent.SetStatusFilter -> _uiState.update { it.copy(selectedStatusFilter = event.status) }
            is TechPackUiEvent.UpdateSearchQuery -> _uiState.update { it.copy(searchQuery = event.query) }
            is TechPackUiEvent.UpdateOrderQuantity -> {
                _uiState.update { it.copy(orderQuantity = event.quantity) }
                loadCostPreview()
            }
            is TechPackUiEvent.LoadCostPreview -> loadCostPreview()
            is TechPackUiEvent.OpenCreateBlankDialog -> _uiState.update { it.copy(isCreateBlankDialogOpen = true) }
            is TechPackUiEvent.CloseCreateBlankDialog -> _uiState.update { it.copy(isCreateBlankDialogOpen = false) }
            is TechPackUiEvent.CreateBlankTechPack -> createBlankTechPack(event)
            is TechPackUiEvent.OpenCreateFromSamplingDialog -> _uiState.update { it.copy(isCreateFromSamplingDialogOpen = true) }
            is TechPackUiEvent.CloseCreateFromSamplingDialog -> _uiState.update { it.copy(isCreateFromSamplingDialogOpen = false) }
            is TechPackUiEvent.CreateFromSampling -> createFromSampling(event)
            is TechPackUiEvent.OpenAddBomLineDialog -> _uiState.update { it.copy(isBomLineDialogOpen = true, editingBomLine = null) }
            is TechPackUiEvent.OpenEditBomLineDialog -> _uiState.update { it.copy(isBomLineDialogOpen = true, editingBomLine = event.line) }
            is TechPackUiEvent.CloseBomLineDialog -> _uiState.update { it.copy(isBomLineDialogOpen = false, editingBomLine = null) }
            is TechPackUiEvent.SaveBomLine -> saveBomLine(event.line)
            is TechPackUiEvent.DeleteBomLine -> deleteBomLine(event.lineId)
            is TechPackUiEvent.OpenAddLaborOpDialog -> _uiState.update { it.copy(isLaborOpDialogOpen = true, editingLaborOp = null) }
            is TechPackUiEvent.OpenEditLaborOpDialog -> _uiState.update { it.copy(isLaborOpDialogOpen = true, editingLaborOp = event.op) }
            is TechPackUiEvent.CloseLaborOpDialog -> _uiState.update { it.copy(isLaborOpDialogOpen = false, editingLaborOp = null) }
            is TechPackUiEvent.SaveLaborOp -> saveLaborOp(event.op)
            is TechPackUiEvent.DeleteLaborOp -> deleteLaborOp(event.operationId)
            is TechPackUiEvent.OpenSizeYieldDialog -> _uiState.update { it.copy(isSizeYieldDialogOpen = true) }
            is TechPackUiEvent.CloseSizeYieldDialog -> _uiState.update { it.copy(isSizeYieldDialogOpen = false) }
            is TechPackUiEvent.UpdateSizeYieldFactors -> updateSizeYieldFactors(event.factors)
            is TechPackUiEvent.ResolveMaterials -> resolveMaterials()
            is TechPackUiEvent.ReleaseTechPack -> releaseTechPack()
            is TechPackUiEvent.ReviseTechPack -> reviseTechPack()
            is TechPackUiEvent.ArchiveTechPack -> archiveTechPack(event.id)
            is TechPackUiEvent.DismissStatusMessage -> _uiState.update { it.copy(statusMessage = null) }
        }
    }

    private fun load() {
        scope.launch {
            _uiState.update { it.copy(isLoading = true) }
            // 1. Fetch Tech Packs
            val tpResult = remoteDataSource.searchTechPacks(tenantSlug, TechPackQuery(pageSize = 100))
            // 2. Fetch Master Data materials catalog for autocomplete
            val mdResult = masterDataSource.searchMaterials(tenantSlug, MaterialCatalogQuery(pageSize = 200))

            val materials = mdResult.getOrNull()?.items ?: emptyList()

            tpResult
                .onSuccess { page ->
                    val selected = _uiState.value.selectedTechPackId ?: page.items.firstOrNull()?.id
                    _uiState.update { current ->
                        current.copy(
                            techPacks = page.items,
                            selectedTechPackId = selected,
                            availableMaterials = materials,
                            isLoading = false,
                            statusMessage = null
                        )
                    }
                    if (selected != null) {
                        val tp = page.items.firstOrNull { it.id == selected }
                        if (tp != null) loadVersions(tp.styleCode.value)
                    }
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            availableMaterials = materials,
                            statusMessage = "Gagal memuat Tech Pack: ${err.message}",
                            isErrorMessage = true
                        )
                    }
                }
        }
    }

    private fun selectTechPack(id: TechPackId) {
        _uiState.update { it.copy(selectedTechPackId = id, costPreview = null) }
        val tp = _uiState.value.selectedTechPack
        if (tp != null) {
            loadVersions(tp.styleCode.value)
            if (_uiState.value.activeWorkbenchTab == TechPackWorkbenchTab.COST_PREVIEW ||
                _uiState.value.activeMobileTab == TechPackMobileTab.COST_PREVIEW
            ) {
                loadCostPreview()
            }
        }
    }

    private fun loadVersions(styleCode: String) {
        scope.launch {
            remoteDataSource.getVersions(tenantSlug, styleCode)
                .onSuccess { versions ->
                    _uiState.update { it.copy(versions = versions) }
                }
        }
    }

    private fun loadCostPreview() {
        val selectedId = _uiState.value.selectedTechPackId ?: return
        scope.launch {
            _uiState.update { it.copy(isCostLoading = true) }
            remoteDataSource.previewCost(tenantSlug, selectedId.value, _uiState.value.orderQuantity)
                .onSuccess { preview ->
                    _uiState.update { it.copy(costPreview = preview, isCostLoading = false) }
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(
                            isCostLoading = false,
                            statusMessage = "Gagal memuat estimasi biaya bahan: ${err.message}",
                            isErrorMessage = true
                        )
                    }
                }
        }
    }

    private fun createBlankTechPack(event: TechPackUiEvent.CreateBlankTechPack) {
        scope.launch {
            _uiState.update { it.copy(isSubmitting = true) }
            remoteDataSource.createBlank(tenantSlug, event.styleName, event.styleCode, event.clientName)
                .onSuccess { created ->
                    _uiState.update { current ->
                        current.copy(
                            techPacks = listOf(created) + current.techPacks,
                            selectedTechPackId = created.id,
                            isSubmitting = false,
                            isCreateBlankDialogOpen = false,
                            statusMessage = "Tech Pack '${created.styleName}' berhasil dibuat (${created.styleCode.value})",
                            isErrorMessage = false
                        )
                    }
                    loadVersions(created.styleCode.value)
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(
                            isSubmitting = false,
                            statusMessage = "Gagal membuat Tech Pack: ${err.message}",
                            isErrorMessage = true
                        )
                    }
                }
        }
    }

    private fun createFromSampling(event: TechPackUiEvent.CreateFromSampling) {
        scope.launch {
            _uiState.update { it.copy(isSubmitting = true) }
            remoteDataSource.createFromSampling(
                tenantSlug = tenantSlug,
                samplingOrderId = event.samplingOrderId,
                styleCode = event.styleCode,
                defaultOwnership = event.defaultOwnership,
                defaultWastePercent = event.defaultWastePercent
            ).onSuccess { created ->
                _uiState.update { current ->
                    current.copy(
                        techPacks = listOf(created) + current.techPacks,
                        selectedTechPackId = created.id,
                        isSubmitting = false,
                        isCreateFromSamplingDialogOpen = false,
                        statusMessage = "Tech Pack berhasil dibuat dari Sampling ${created.sourceSpkNumber}",
                        isErrorMessage = false
                    )
                }
                loadVersions(created.styleCode.value)
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isSubmitting = false,
                        statusMessage = "Gagal membuat Tech Pack dari sampling: ${err.message}",
                        isErrorMessage = true
                    )
                }
            }
        }
    }

    private fun saveBomLine(line: BomLine) {
        val selected = _uiState.value.selectedTechPack ?: return
        val currentLines = selected.bomLines
        val existingIndex = currentLines.indexOfFirst { it.lineId == line.lineId }
        val updatedLines = if (existingIndex >= 0) {
            currentLines.toMutableList().apply { set(existingIndex, line) }
        } else {
            currentLines + line
        }

        scope.launch {
            _uiState.update { it.copy(isSubmitting = true) }
            remoteDataSource.updateBom(tenantSlug, selected.id.value, updatedLines)
                .onSuccess { updated ->
                    updateSelectedTechPackInState(updated, "BOM berhasil disimpan", false)
                    _uiState.update { it.copy(isBomLineDialogOpen = false, editingBomLine = null) }
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(
                            isSubmitting = false,
                            statusMessage = "Gagal menyimpan BOM: ${err.message}",
                            isErrorMessage = true
                        )
                    }
                }
        }
    }

    private fun deleteBomLine(lineId: String) {
        val selected = _uiState.value.selectedTechPack ?: return
        val updatedLines = selected.bomLines.filterNot { it.lineId == lineId }

        scope.launch {
            _uiState.update { it.copy(isSubmitting = true) }
            remoteDataSource.updateBom(tenantSlug, selected.id.value, updatedLines)
                .onSuccess { updated ->
                    updateSelectedTechPackInState(updated, "Baris BOM dihapus", false)
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(
                            isSubmitting = false,
                            statusMessage = "Gagal menghapus baris BOM: ${err.message}",
                            isErrorMessage = true
                        )
                    }
                }
        }
    }

    private fun saveLaborOp(op: LaborOperation) {
        val selected = _uiState.value.selectedTechPack ?: return
        val currentOps = selected.laborOperations
        val existingIndex = currentOps.indexOfFirst { it.operationId == op.operationId }
        val updatedOps = if (existingIndex >= 0) {
            currentOps.toMutableList().apply { set(existingIndex, op) }
        } else {
            currentOps + op
        }

        scope.launch {
            _uiState.update { it.copy(isSubmitting = true) }
            remoteDataSource.updateLabor(tenantSlug, selected.id.value, updatedOps)
                .onSuccess { updated ->
                    updateSelectedTechPackInState(updated, "Operasi kerja (SAM) berhasil disimpan", false)
                    _uiState.update { it.copy(isLaborOpDialogOpen = false, editingLaborOp = null) }
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(
                            isSubmitting = false,
                            statusMessage = "Gagal menyimpan operasi kerja: ${err.message}",
                            isErrorMessage = true
                        )
                    }
                }
        }
    }

    private fun deleteLaborOp(operationId: String) {
        val selected = _uiState.value.selectedTechPack ?: return
        val updatedOps = selected.laborOperations.filterNot { it.operationId == operationId }

        scope.launch {
            _uiState.update { it.copy(isSubmitting = true) }
            remoteDataSource.updateLabor(tenantSlug, selected.id.value, updatedOps)
                .onSuccess { updated ->
                    updateSelectedTechPackInState(updated, "Operasi kerja dihapus", false)
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(
                            isSubmitting = false,
                            statusMessage = "Gagal menghapus operasi kerja: ${err.message}",
                            isErrorMessage = true
                        )
                    }
                }
        }
    }

    private fun updateSizeYieldFactors(factors: List<SizeYieldFactor>) {
        val selected = _uiState.value.selectedTechPack ?: return
        scope.launch {
            _uiState.update { it.copy(isSubmitting = true) }
            remoteDataSource.updateSizeYield(tenantSlug, selected.id.value, factors)
                .onSuccess { updated ->
                    updateSelectedTechPackInState(updated, "Faktor yield ukuran berhasil diperbarui", false)
                    _uiState.update { it.copy(isSizeYieldDialogOpen = false) }
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(
                            isSubmitting = false,
                            statusMessage = "Gagal memperbarui yield ukuran: ${err.message}",
                            isErrorMessage = true
                        )
                    }
                }
        }
    }

    private fun resolveMaterials() {
        val selected = _uiState.value.selectedTechPack ?: return
        scope.launch {
            _uiState.update { it.copy(isSubmitting = true) }
            remoteDataSource.resolveMaterials(tenantSlug, selected.id.value)
                .onSuccess { updated ->
                    val resolvedCount = updated.bomLines.count { it.material.isResolved } - selected.bomLines.count { it.material.isResolved }
                    val msg = if (resolvedCount > 0) {
                        "Berhasil mencocokkan $resolvedCount bahan baku ke Master Data"
                    } else {
                        "Tidak ada bahan baru yang cocok secara otomatis"
                    }
                    updateSelectedTechPackInState(updated, msg, false)
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(
                            isSubmitting = false,
                            statusMessage = "Gagal mencocokkan bahan: ${err.message}",
                            isErrorMessage = true
                        )
                    }
                }
        }
    }

    private fun releaseTechPack() {
        val selected = _uiState.value.selectedTechPack ?: return
        scope.launch {
            _uiState.update { it.copy(isSubmitting = true) }
            remoteDataSource.release(tenantSlug, selected.id.value)
                .onSuccess { released ->
                    updateSelectedTechPackInState(
                        released,
                        "Tech Pack versi ${released.version} (${released.styleCode.value}) berhasil DIRILIS",
                        false
                    )
                    loadVersions(released.styleCode.value)
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(
                            isSubmitting = false,
                            statusMessage = "Gagal merilis Tech Pack: ${err.message}",
                            isErrorMessage = true
                        )
                    }
                }
        }
    }

    private fun reviseTechPack() {
        val selected = _uiState.value.selectedTechPack ?: return
        scope.launch {
            _uiState.update { it.copy(isSubmitting = true) }
            remoteDataSource.revise(tenantSlug, selected.id.value)
                .onSuccess { revised ->
                    _uiState.update { current ->
                        val updatedList = listOf(revised) + current.techPacks.map {
                            if (it.id == selected.id) it.copy(status = com.eventverse.app.domain.techpack.TechPackStatus.SUPERSEDED) else it
                        }
                        current.copy(
                            techPacks = updatedList,
                            selectedTechPackId = revised.id,
                            isSubmitting = false,
                            statusMessage = "Revisi dibuat: Versi ${revised.version} (DRAFT)",
                            isErrorMessage = false
                        )
                    }
                    loadVersions(revised.styleCode.value)
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(
                            isSubmitting = false,
                            statusMessage = "Gagal merevisi Tech Pack: ${err.message}",
                            isErrorMessage = true
                        )
                    }
                }
        }
    }

    private fun archiveTechPack(id: TechPackId) {
        scope.launch {
            _uiState.update { it.copy(isSubmitting = true) }
            remoteDataSource.archive(tenantSlug, id.value)
                .onSuccess {
                    _uiState.update { current ->
                        val remaining = current.techPacks.filterNot { it.id == id }
                        current.copy(
                            techPacks = remaining,
                            selectedTechPackId = remaining.firstOrNull()?.id,
                            isSubmitting = false,
                            statusMessage = "Tech Pack berhasil diarsipkan",
                            isErrorMessage = false
                        )
                    }
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(
                            isSubmitting = false,
                            statusMessage = "Gagal mengarsipkan: ${err.message}",
                            isErrorMessage = true
                        )
                    }
                }
        }
    }

    private fun updateSelectedTechPackInState(updated: com.eventverse.app.domain.techpack.TechPack, message: String, isError: Boolean) {
        _uiState.update { current ->
            val updatedList = current.techPacks.map { if (it.id == updated.id) updated else it }
            current.copy(
                techPacks = updatedList,
                isSubmitting = false,
                statusMessage = message,
                isErrorMessage = isError
            )
        }
        if (_uiState.value.activeWorkbenchTab == TechPackWorkbenchTab.COST_PREVIEW ||
            _uiState.value.activeMobileTab == TechPackMobileTab.COST_PREVIEW
        ) {
            loadCostPreview()
        }
    }
}
