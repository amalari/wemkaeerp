package com.eventverse.app.presentation.masterdata

import com.eventverse.app.domain.masterdata.*
import com.eventverse.app.domain.masterdata.usecases.CreateMaterialCommand
import com.eventverse.app.domain.masterdata.usecases.SetMaterialStandardPriceCommand
import com.eventverse.app.domain.masterdata.usecases.UpdateMaterialCommand
import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.infrastructure.api.MasterDataApiClient
import com.eventverse.app.infrastructure.api.MasterDataRemoteDataSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MasterDataViewModel(
    private val tenantSlug: String,
    private val remoteDataSource: MasterDataRemoteDataSource = MasterDataApiClient(),
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main)
) {
    private val _uiState = MutableStateFlow(MasterDataUiState())
    val uiState: StateFlow<MasterDataUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun onEvent(event: MasterDataUiEvent) {
        when (event) {
            is MasterDataUiEvent.Load -> load()
            is MasterDataUiEvent.SelectMaterial -> selectMaterial(event.materialId)
            is MasterDataUiEvent.SelectMobileTab -> _uiState.update { it.copy(activeMobileTab = event.tab) }
            is MasterDataUiEvent.SetCategoryFilter -> _uiState.update { it.copy(selectedCategoryFilter = event.category) }
            is MasterDataUiEvent.SetOwnershipFilter -> _uiState.update { it.copy(selectedOwnershipFilter = event.ownership) }
            is MasterDataUiEvent.UpdateSearchQuery -> _uiState.update { it.copy(searchQuery = event.query) }
            is MasterDataUiEvent.OpenCreateDialog -> _uiState.update { it.copy(isCreateDialogOpen = true) }
            is MasterDataUiEvent.CloseCreateDialog -> _uiState.update { it.copy(isCreateDialogOpen = false) }
            is MasterDataUiEvent.OpenEditDialog -> _uiState.update { it.copy(isEditDialogOpen = true) }
            is MasterDataUiEvent.CloseEditDialog -> _uiState.update { it.copy(isEditDialogOpen = false) }
            is MasterDataUiEvent.OpenSetPriceDialog -> _uiState.update { it.copy(isSetPriceDialogOpen = true) }
            is MasterDataUiEvent.CloseSetPriceDialog -> _uiState.update { it.copy(isSetPriceDialogOpen = false) }
            is MasterDataUiEvent.CreateMaterial -> createMaterial(event)
            is MasterDataUiEvent.UpdateMaterial -> updateMaterial(event)
            is MasterDataUiEvent.ArchiveMaterial -> archiveMaterial(event.materialId)
            is MasterDataUiEvent.SetStandardPrice -> setStandardPrice(event)
            is MasterDataUiEvent.DismissStatusMessage -> _uiState.update { it.copy(statusMessage = null) }
        }
    }

    private fun load() {
        scope.launch {
            _uiState.update { it.copy(isLoading = true) }
            remoteDataSource.searchMaterials(tenantSlug, MaterialCatalogQuery(pageSize = 100))
                .onSuccess { page ->
                    val selected = _uiState.value.selectedMaterialId ?: page.items.firstOrNull()?.id
                    _uiState.update { current ->
                        current.copy(
                            materials = page.items,
                            selectedMaterialId = selected,
                            isLoading = false
                        )
                    }
                    if (selected != null) {
                        loadPriceHistory(selected)
                    }
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            statusMessage = "Gagal memuat katalog material: ${err.message}",
                            isErrorMessage = true
                        )
                    }
                }
        }
    }

    private fun selectMaterial(materialId: MaterialId) {
        _uiState.update { it.copy(selectedMaterialId = materialId) }
        loadPriceHistory(materialId)
    }

    private fun loadPriceHistory(materialId: MaterialId) {
        scope.launch {
            _uiState.update { it.copy(isPriceLoading = true) }
            remoteDataSource.getPriceHistory(tenantSlug, materialId.value)
                .onSuccess { history ->
                    _uiState.update {
                        it.copy(
                            priceHistory = history.entries,
                            isPriceLoading = false
                        )
                    }
                }
                .onFailure {
                    _uiState.update { current ->
                        current.copy(
                            priceHistory = emptyList(),
                            isPriceLoading = false
                        )
                    }
                }
        }
    }

    private fun createMaterial(event: MasterDataUiEvent.CreateMaterial) {
        scope.launch {
            _uiState.update { it.copy(isSubmitting = true) }
            val cmd = CreateMaterialCommand(
                tenantId = TenantId(""),
                code = event.code,
                name = event.name,
                category = event.category,
                baseUom = event.baseUom,
                alternateUoms = event.alternateUoms,
                defaultOwnership = event.defaultOwnership,
                description = event.description
            )
            remoteDataSource.createMaterial(tenantSlug, cmd)
                .onSuccess { created ->
                    _uiState.update {
                        it.copy(
                            isSubmitting = false,
                            isCreateDialogOpen = false,
                            statusMessage = "Material '${created.name}' (${created.code.value}) berhasil ditambahkan",
                            isErrorMessage = false
                        )
                    }
                    load()
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(
                            isSubmitting = false,
                            statusMessage = "Gagal menambah material: ${err.message}",
                            isErrorMessage = true
                        )
                    }
                }
        }
    }

    private fun updateMaterial(event: MasterDataUiEvent.UpdateMaterial) {
        val selected = _uiState.value.selectedMaterial ?: return
        scope.launch {
            _uiState.update { it.copy(isSubmitting = true) }
            val cmd = UpdateMaterialCommand(
                tenantId = TenantId(""),
                materialId = selected.id,
                name = event.name,
                category = event.category,
                defaultOwnership = event.defaultOwnership,
                alternateUoms = event.alternateUoms,
                description = event.description
            )
            remoteDataSource.updateMaterial(tenantSlug, cmd)
                .onSuccess { updated ->
                    _uiState.update {
                        it.copy(
                            isSubmitting = false,
                            isEditDialogOpen = false,
                            statusMessage = "Material '${updated.name}' berhasil diperbarui",
                            isErrorMessage = false
                        )
                    }
                    load()
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(
                            isSubmitting = false,
                            statusMessage = "Gagal memperbarui material: ${err.message}",
                            isErrorMessage = true
                        )
                    }
                }
        }
    }

    private fun archiveMaterial(materialId: MaterialId) {
        scope.launch {
            _uiState.update { it.copy(isSubmitting = true) }
            remoteDataSource.archiveMaterial(tenantSlug, materialId.value)
                .onSuccess {
                    _uiState.update {
                        it.copy(
                            isSubmitting = false,
                            selectedMaterialId = null,
                            statusMessage = "Material berhasil diarsipkan",
                            isErrorMessage = false
                        )
                    }
                    load()
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(
                            isSubmitting = false,
                            statusMessage = "Gagal mengarsipkan material: ${err.message}",
                            isErrorMessage = true
                        )
                    }
                }
        }
    }

    private fun setStandardPrice(event: MasterDataUiEvent.SetStandardPrice) {
        scope.launch {
            _uiState.update { it.copy(isSubmitting = true) }
            val cmd = SetMaterialStandardPriceCommand(
                tenantId = TenantId(""),
                materialId = event.materialId,
                unitPrice = event.unitPrice,
                effectiveFrom = event.effectiveFrom,
                note = event.note
            )
            remoteDataSource.setStandardPrice(tenantSlug, cmd)
                .onSuccess { price ->
                    _uiState.update {
                        it.copy(
                            isSubmitting = false,
                            isSetPriceDialogOpen = false,
                            statusMessage = "Tarif acuan berhasil ditetapkan (${price.unitPrice.amount.formatted()} / ${price.unitPrice.per.formatted()})",
                            isErrorMessage = false
                        )
                    }
                    loadPriceHistory(event.materialId)
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(
                            isSubmitting = false,
                            statusMessage = "Gagal menetapkan tarif: ${err.message}",
                            isErrorMessage = true
                        )
                    }
                }
        }
    }
}
