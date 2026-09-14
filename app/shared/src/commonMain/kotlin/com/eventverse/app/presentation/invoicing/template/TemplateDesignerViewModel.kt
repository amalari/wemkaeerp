package com.eventverse.app.presentation.invoicing.template

import com.eventverse.app.domain.invoicing.InvoiceTemplateId
import com.eventverse.app.domain.invoicing.template.*
import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.infrastructure.api.InvoicingApiClient
import com.eventverse.app.infrastructure.api.InvoicingRemoteDataSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

class TemplateDesignerViewModel(
    private val tenantSlug: String,
    private val initialTemplateId: String? = null,
    private val remoteDataSource: InvoicingRemoteDataSource = InvoicingApiClient(),
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main)
) {
    private val initialTemplate = InvoiceTemplateFactory.standardIndonesianInvoice(
        tenantId = TenantId(tenantSlug),
        now = Clock.System.now()
    ).let {
        if (!initialTemplateId.isNullOrBlank()) it.copy(id = InvoiceTemplateId(initialTemplateId)) else it
    }

    private val _uiState = MutableStateFlow(TemplateDesignerUiState(template = initialTemplate))
    val uiState: StateFlow<TemplateDesignerUiState> = _uiState.asStateFlow()

    init {
        if (!initialTemplateId.isNullOrBlank()) {
            loadTemplate(InvoiceTemplateId(initialTemplateId))
        }
    }

    private fun loadTemplate(id: InvoiceTemplateId) {
        scope.launch {
            remoteDataSource.getTemplate(tenantSlug, id).onSuccess { tpl ->
                _uiState.update { it.copy(template = tpl) }
            }.onFailure { err ->
                _uiState.update { it.copy(error = "Gagal memuat template: ${err.message}") }
            }
        }
    }

    fun onEvent(event: TemplateDesignerUiEvent) {
        when (event) {
            is TemplateDesignerUiEvent.SelectElement -> _uiState.update {
                it.copy(selectedElementId = event.elementId)
            }
            is TemplateDesignerUiEvent.UpdateElementRect -> updateElementRect(event.elementId, event.newBounds)
            is TemplateDesignerUiEvent.UpdateElement -> updateElement(event.updatedElement)
            is TemplateDesignerUiEvent.AddElement -> addElement(event.element)
            is TemplateDesignerUiEvent.DeleteElement -> deleteElement(event.elementId)
            is TemplateDesignerUiEvent.UpdateTemplateName -> _uiState.update {
                it.copy(template = it.template.copy(name = event.name, updatedAt = Clock.System.now()))
            }
            is TemplateDesignerUiEvent.SetZoom -> _uiState.update {
                it.copy(zoomPercent = event.percent.coerceIn(50, 200))
            }
            is TemplateDesignerUiEvent.ToggleGrid -> _uiState.update {
                it.copy(showGrid = event.show)
            }
            is TemplateDesignerUiEvent.SetSnapGrid -> _uiState.update {
                it.copy(snapGridMm = event.mm)
            }
            is TemplateDesignerUiEvent.SaveTemplate -> saveTemplate()
            is TemplateDesignerUiEvent.DismissMessage -> _uiState.update {
                it.copy(error = null, successMessage = null)
            }
        }
    }

    private fun updateElementRect(elementId: String, newBounds: TemplateRect) {
        _uiState.update { current ->
            val updated = current.template.elements.map { el ->
                if (el.elementId == elementId) {
                    el.withRect(newBounds)
                } else el
            }
            current.copy(
                template = current.template.copy(elements = updated, updatedAt = Clock.System.now())
            )
        }
    }

    private fun updateElement(updatedElement: TemplateElement) {
        _uiState.update { current ->
            val updated = current.template.elements.map { el ->
                if (el.elementId == updatedElement.elementId) updatedElement else el
            }
            current.copy(
                template = current.template.copy(elements = updated, updatedAt = Clock.System.now())
            )
        }
    }

    private fun addElement(element: TemplateElement) {
        _uiState.update { current ->
            val updated = current.template.elements + element
            current.copy(
                template = current.template.copy(elements = updated, updatedAt = Clock.System.now()),
                selectedElementId = element.elementId
            )
        }
    }

    private fun deleteElement(elementId: String) {
        _uiState.update { current ->
            val updated = current.template.elements.filterNot { it.elementId == elementId }
            current.copy(
                template = current.template.copy(elements = updated, updatedAt = Clock.System.now()),
                selectedElementId = null
            )
        }
    }

    private fun saveTemplate() {
        scope.launch {
            _uiState.update { it.copy(isSaving = true) }
            val tpl = _uiState.value.template
            remoteDataSource.saveTemplate(tenantSlug, tpl).onSuccess { saved ->
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        template = saved,
                        successMessage = "Template '${saved.name}' berhasil disimpan!"
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(isSaving = false, error = "Gagal menyimpan template: ${err.message}")
                }
            }
        }
    }
}
