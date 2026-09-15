package com.eventverse.app.presentation.invoicing.template

import com.eventverse.app.domain.common.Money
import com.eventverse.app.domain.common.Quantity
import com.eventverse.app.domain.common.Ratio
import com.eventverse.app.domain.common.UnitOfMeasure
import com.eventverse.app.domain.invoicing.*
import com.eventverse.app.domain.invoicing.template.*
import com.eventverse.app.domain.invoicing.usecases.CreateInvoiceCommand
import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.infrastructure.api.InvoicingApiClient
import com.eventverse.app.infrastructure.api.InvoicingRemoteDataSource
import com.eventverse.app.presentation.invoicing.InvoicePrefillData
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
    private val initialPrefill: InvoicePrefillData? = null,
    private val remoteDataSource: InvoicingRemoteDataSource = InvoicingApiClient(),
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main)
) {
    private val now = Clock.System.now()

    /**
     * Template awal kanvas.
     *
     * Bila desainer dibuka tanpa `templateId` (alur CRM), kanvas memakai salinan baru dari
     * template standar dengan **ID unik**. Ini wajib: endpoint simpan template melakukan upsert
     * berdasarkan ID, sehingga memakai ID template standar akan menimpa template default tenant.
     * Template hasil alur CRM juga tidak ditandai default dan hanya berlaku untuk jenis faktur
     * yang bersangkutan (Sampling atau DP).
     */
    private val initialTemplate = InvoiceTemplateFactory.standardIndonesianInvoice(
        tenantId = TenantId(tenantSlug),
        now = now
    ).let { standard ->
        val existingTemplateId = initialTemplateId?.takeIf { it.isNotBlank() }
        var tpl = if (existingTemplateId != null) {
            standard.copy(id = InvoiceTemplateId(existingTemplateId))
        } else {
            standard.copy(
                id = InvoiceTemplateId("tpl-${now.toEpochMilliseconds()}"),
                name = "Template Faktur Baru",
                isDefault = false
            )
        }
        if (initialPrefill != null) {
            val prefix = if (initialPrefill.kind == InvoiceKind.SAMPLE) "Template Faktur Sampling" else "Template Faktur DP"
            val client = initialPrefill.clientName.trim()
            tpl = tpl.copy(
                name = if (client.isNotBlank()) "$prefix - $client" else prefix,
                applicableKinds = setOf(initialPrefill.kind)
            )
        }
        tpl
    }

    private val initialPreview = if (initialPrefill != null) {
        TemplateDesignerUiState.fromPrefill(initialPrefill, tenantSlug, now)
    } else {
        TemplateDesignerUiState.createDummyPreviewInvoice()
    }

    private val _uiState = MutableStateFlow(
        TemplateDesignerUiState(
            template = initialTemplate,
            previewInvoice = initialPreview,
            prefillData = initialPrefill,
            activeInspectorTab = if (initialPrefill != null) DesignerInspectorTab.LIVE_DATA else DesignerInspectorTab.LAYOUT
        )
    )
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
            is TemplateDesignerUiEvent.MoveElementBy -> moveElementBy(event)
            is TemplateDesignerUiEvent.UpdateElement -> updateElement(event.updatedElement)
            is TemplateDesignerUiEvent.AddElement -> addElement(event.element)
            is TemplateDesignerUiEvent.DeleteElement -> deleteElement(event.elementId)
            is TemplateDesignerUiEvent.UpdateTemplateName -> _uiState.update {
                it.copy(template = it.template.copy(name = event.name, updatedAt = Clock.System.now()))
            }
            is TemplateDesignerUiEvent.SetZoom -> _uiState.update {
                it.copy(zoomPercent = event.percent.coerceIn(50, 200))
            }
            is TemplateDesignerUiEvent.SetCanvasTool -> _uiState.update {
                it.copy(canvasTool = event.tool)
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
            is TemplateDesignerUiEvent.AutoMapWithAi -> runAiAutoMapping()
            is TemplateDesignerUiEvent.SetInspectorTab -> _uiState.update {
                it.copy(activeInspectorTab = event.tab)
            }
            is TemplateDesignerUiEvent.UpdateLiveInvoice -> _uiState.update {
                it.copy(previewInvoice = event.updatedInvoice)
            }
            is TemplateDesignerUiEvent.UpdateLiveClient -> updateLiveClient(event)
            is TemplateDesignerUiEvent.UpdateLiveItem -> updateLiveItem(event)
            is TemplateDesignerUiEvent.SaveAndCreateInvoice -> saveAndCreateInvoice(event.onSuccess)
            is TemplateDesignerUiEvent.ClosePdfPreview -> _uiState.update {
                it.copy(isPdfPreviewOpen = false)
            }
        }
    }

    private fun runAiAutoMapping() {
        val currentElements = _uiState.value.template.elements
        val summary = InvoiceAiMappingEngine.autoMapAll(currentElements)
        _uiState.update { current ->
            current.copy(
                template = current.template.copy(
                    elements = summary.mappedElements,
                    updatedAt = Clock.System.now()
                ),
                aiMappingSummary = summary,
                successMessage = buildAiMappingMessage(summary)
            )
        }
    }

    private fun buildAiMappingMessage(summary: AiAutoMapSummary): String = when {
        summary.newlyMappedCount > 0 && summary.skippedLabelCount > 0 ->
            "AI Auto-Map: ${summary.newlyMappedCount} teks dipetakan ke token dinamis, " +
                "${summary.skippedLabelCount} label teks dibiarkan statis."

        summary.newlyMappedCount > 0 ->
            "AI Auto-Map: ${summary.newlyMappedCount} teks berhasil dipetakan ke token data faktur."

        summary.skippedLabelCount > 0 ->
            "AI Auto-Map tidak menemukan nilai data baru; ${summary.skippedLabelCount} elemen " +
                "adalah label statis dan dibiarkan apa adanya."

        else ->
            "AI Auto-Map tidak menemukan teks statis yang bisa dipetakan. Tambahkan teks bernilai " +
                "seperti nomor faktur, nama klien, atau nominal tagihan."
    }

    private fun updateLiveClient(event: TemplateDesignerUiEvent.UpdateLiveClient) {
        _uiState.update { current ->
            val updatedBillTo = current.previewInvoice.billTo.copy(
                name = event.name,
                contactPerson = event.contactPerson,
                phone = event.phone,
                email = event.email,
                address = event.address
            )
            current.copy(
                previewInvoice = current.previewInvoice.copy(billTo = updatedBillTo)
            )
        }
    }

    private fun updateLiveItem(event: TemplateDesignerUiEvent.UpdateLiveItem) {
        _uiState.update { current ->
            val qtyInt = (event.quantity * 1_000_000).toLong().coerceAtLeast(1_000_000L)
            val updatedLine = InvoiceLine(
                id = InvoiceLineId("line-live-01"),
                description = event.description.ifBlank { "Rincian Pekerjaan Garmen" },
                quantity = Quantity(qtyInt, UnitOfMeasure.PIECE),
                unitPrice = Money.idr(event.unitPrice.coerceAtLeast(0L)),
                discount = Ratio.ZERO,
                sortOrder = 1
            )
            current.copy(
                previewInvoice = current.previewInvoice.copy(
                    lines = listOf(updatedLine),
                    taxRatio = Ratio.percent(event.taxPercent.coerceIn(0.0, 100.0)),
                    contractValue = updatedLine.amount
                )
            )
        }
    }

    /**
     * Menerapkan perpindahan relatif pada satu elemen.
     *
     * Titik snap dan penjepitan ke kertas **tidak** dihitung di sini: keduanya milik
     * [TemplateRect.movedBy] di lapisan domain, sehingga kanvas (drag) dan tombol panah (nudge)
     * tidak bisa lagi menghasilkan posisi yang berbeda untuk perpindahan yang sama.
     */
    private fun moveElementBy(event: TemplateDesignerUiEvent.MoveElementBy) {
        _uiState.update { current ->
            val paperSize = current.template.paperSize
            val target = current.template.elements.find { it.elementId == event.elementId }
                ?: return@update current

            val newRect = target.rect.movedBy(
                dx = Mm10(event.dxMm10),
                dy = Mm10(event.dyMm10),
                snapMm10 = current.snapGridMm * 10,
                paperWidth = paperSize.width,
                paperHeight = paperSize.height
            )
            if (newRect == target.rect) return@update current

            val updated = current.template.elements.map { el ->
                if (el.elementId == event.elementId) el.withRect(newRect) else el
            }
            current.copy(
                template = current.template.copy(elements = updated, updatedAt = Clock.System.now())
            )
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

    private fun saveAndCreateInvoice(onSuccess: (InvoiceId) -> Unit) {
        scope.launch {
            _uiState.update { it.copy(isSaving = true) }
            val tpl = _uiState.value.template

            // 1. Simpan template kustom terlebih dahulu
            val templateResult = remoteDataSource.saveTemplate(tenantSlug, tpl)
            if (templateResult.isFailure) {
                _uiState.update {
                    it.copy(isSaving = false, error = "Gagal menyimpan template: ${templateResult.exceptionOrNull()?.message}")
                }
                return@launch
            }
            val savedTemplate = templateResult.getOrThrow()

            // 2. Buat invoice resmi dengan data live & template tersambung
            val liveInvoice = _uiState.value.previewInvoice
            val command = CreateInvoiceCommand(
                tenantId = TenantId(tenantSlug),
                kind = liveInvoice.kind,
                billTo = liveInvoice.billTo,
                lines = liveInvoice.lines,
                taxRatio = liveInvoice.taxRatio,
                globalDiscount = liveInvoice.globalDiscount,
                currency = liveInvoice.currency,
                issueDate = liveInvoice.issueDate,
                dueDate = liveInvoice.dueDate,
                templateId = savedTemplate.id,
                sourceKind = liveInvoice.sourceKind,
                sourceRef = liveInvoice.sourceRef,
                parentInvoiceId = liveInvoice.parentInvoiceId,
                contractValue = liveInvoice.contractValue,
                notes = liveInvoice.notes,
                terms = liveInvoice.terms,
                createdBy = "Admin CRM"
            )

            remoteDataSource.createInvoice(tenantSlug, command).onSuccess { created ->
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        template = savedTemplate,
                        createdInvoiceId = created.id,
                        previewInvoice = created,
                        isPdfPreviewOpen = true,
                        successMessage = "Faktur ${created.number.value} dan template '${savedTemplate.name}' berhasil diterbitkan!"
                    )
                }
                onSuccess(created.id)
            }.onFailure { err ->
                _uiState.update {
                    it.copy(isSaving = false, error = "Gagal menerbitkan faktur: ${err.message}")
                }
            }
        }
    }

    fun getPdfUrl(invoiceId: InvoiceId): String =
        remoteDataSource.getPdfUrl(tenantSlug, invoiceId)
}
