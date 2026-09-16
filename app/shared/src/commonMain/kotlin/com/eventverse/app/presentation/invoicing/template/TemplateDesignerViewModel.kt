package com.eventverse.app.presentation.invoicing.template

import com.eventverse.app.domain.invoicing.*
import com.eventverse.app.domain.invoicing.template.*
import com.eventverse.app.domain.invoicing.usecases.CreateInvoiceCommand
import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.infrastructure.api.CrmApiClient
import com.eventverse.app.infrastructure.api.CrmRemoteDataSource
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
    private val crmDataSource: CrmRemoteDataSource = CrmApiClient(),
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
    } else if (initialTemplate.targetKind == InvoiceKind.SAMPLE) {
        TemplateDesignerUiState.createDummySamplePreviewInvoice(now)
    } else {
        TemplateDesignerUiState.createDummyPreviewInvoice()
    }

    private val _uiState = MutableStateFlow(
        TemplateDesignerUiState(
            template = initialTemplate,
            previewInvoice = initialPreview,
            prefillData = initialPrefill,
            // Modul pertama dibuka agar palet tidak tampak kosong saat pertama kali dibuka.
            expandedModules = setOf(BindingModuleSource.CRM_SALES)
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
                val isOnlySample = tpl.targetKind == InvoiceKind.SAMPLE
                val preview = if (isOnlySample && _uiState.value.prefillData == null) {
                    TemplateDesignerUiState.createDummySamplePreviewInvoice(now)
                } else if (_uiState.value.prefillData == null) {
                    TemplateDesignerUiState.createDummyPreviewInvoice()
                } else {
                    _uiState.value.previewInvoice
                }
                _uiState.update { current ->
                    current.copy(
                        template = measured(tpl, preview),
                        previewInvoice = preview
                    )
                }
            }.onFailure { err ->
                _uiState.update { it.copy(error = "Gagal memuat template: ${err.message}") }
            }
        }
    }

    /**
     * Menyelaraskan tinggi turunan elemen dengan isi teks dan baris faktur.
     *
     * Dipanggil pada **setiap** perubahan template, bukan hanya saat teks berubah: lebar kotak dan
     * jumlah baris faktur sama-sama mengubah jumlah baris teks, sehingga tinggi yang tersimpan bisa
     * basi tanpa ada isian teks yang disentuh.
     */
    private fun measured(template: InvoiceTemplate, invoice: Invoice): InvoiceTemplate =
        InvoiceDocumentLayout.measureHeights(template, invoice)

    /** Satu-satunya jalur perubahan daftar elemen, agar pengukuran ulang tidak pernah terlewat. */
    private fun mutateElements(
        transform: (List<TemplateElement>) -> List<TemplateElement>
    ) {
        _uiState.update { current ->
            val updated = transform(current.template.elements)
            val template = measured(current.template.copy(elements = updated), current.previewInvoice)
            current.copy(template = template.copy(updatedAt = Clock.System.now()))
        }
    }

    fun onEvent(event: TemplateDesignerUiEvent) {
        when (event) {
            is TemplateDesignerUiEvent.SelectElement -> _uiState.update {
                it.copy(selectedElementId = event.elementId, editingTextElementId = null)
            }
            is TemplateDesignerUiEvent.UpdateElementRect -> updateElementRect(event.elementId, event.newBounds)
            is TemplateDesignerUiEvent.MoveElementBy -> moveElementBy(event)
            is TemplateDesignerUiEvent.ResizeElementWidth -> resizeElementWidth(event.elementId, event.widthMm10)
            is TemplateDesignerUiEvent.UpdateElement -> updateElement(event.updatedElement)
            is TemplateDesignerUiEvent.UpdateElementText -> updateElementText(event.elementId, event.text)
            is TemplateDesignerUiEvent.InsertPreset -> insertPreset(event.preset)
            is TemplateDesignerUiEvent.DeleteElement -> deleteElement(event.elementId)
            is TemplateDesignerUiEvent.ToggleModuleExpanded -> _uiState.update { current ->
                val modules = current.expandedModules
                current.copy(
                    expandedModules = if (event.module in modules) modules - event.module else modules + event.module
                )
            }
            is TemplateDesignerUiEvent.BeginTextEdit -> _uiState.update {
                it.copy(selectedElementId = event.elementId, editingTextElementId = event.elementId)
            }
            is TemplateDesignerUiEvent.EndTextEdit -> _uiState.update { it.copy(editingTextElementId = null) }
            is TemplateDesignerUiEvent.UpdateTemplateName -> _uiState.update {
                it.copy(template = it.template.copy(name = event.name, updatedAt = Clock.System.now()))
            }
            is TemplateDesignerUiEvent.SetApplicableKind -> {
                val updatedTemplate = _uiState.value.template.copy(
                    applicableKinds = setOf(event.kind),
                    updatedAt = Clock.System.now()
                )
                val newPreview = if (event.kind == InvoiceKind.SAMPLE && _uiState.value.prefillData == null) {
                    TemplateDesignerUiState.createDummySamplePreviewInvoice(now)
                } else if (_uiState.value.prefillData == null) {
                    TemplateDesignerUiState.createDummyPreviewInvoice()
                } else {
                    _uiState.value.previewInvoice
                }
                _uiState.update { current ->
                    current.copy(
                        template = measured(updatedTemplate, newPreview),
                        previewInvoice = newPreview
                    )
                }
            }
            is TemplateDesignerUiEvent.ToggleApplicableKind -> {
                onEvent(TemplateDesignerUiEvent.SetApplicableKind(event.kind))
            }
            is TemplateDesignerUiEvent.SetZoom -> _uiState.update {
                it.copy(zoomPercent = event.percent.coerceIn(MIN_CANVAS_ZOOM, MAX_CANVAS_ZOOM))
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
            val template = measured(
                current.template.copy(elements = summary.mappedElements),
                current.previewInvoice
            )
            current.copy(
                template = template.copy(updatedAt = Clock.System.now()),
                aiMappingSummary = summary,
                // Pesan lama dibersihkan agar hasil pemetaan terakhir tidak tampil berdampingan
                // dengan kesalahan dari aksi sebelumnya.
                error = null,
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

    /**
     * Menerapkan perpindahan relatif pada satu elemen.
     *
     * Titik snap dan penjepitan ke kertas **tidak** dihitung di sini: keduanya milik
     * [TemplateRect.movedBy] di lapisan domain, sehingga kanvas (drag) dan tombol panah (nudge)
     * tidak bisa lagi menghasilkan posisi yang berbeda untuk perpindahan yang sama.
     */
    private fun moveElementBy(event: TemplateDesignerUiEvent.MoveElementBy) {
        val current = _uiState.value
        val paperSize = current.template.paperSize
        val target = current.template.elements.find { it.elementId == event.elementId } ?: return

        val newRect = target.rect.movedBy(
            dx = Mm10(event.dxMm10),
            dy = Mm10(event.dyMm10),
            snapMm10 = current.snapGridMm * 10,
            paperWidth = paperSize.width,
            paperHeight = paperSize.height
        )
        if (newRect == target.rect) return

        mutateElements { elements ->
            elements.map { el -> if (el.elementId == event.elementId) el.withRect(newRect) else el }
        }
    }

    private fun updateElementRect(elementId: String, newBounds: TemplateRect) {
        mutateElements { elements ->
            elements.map { el -> if (el.elementId == elementId) el.withRect(newBounds) else el }
        }
    }

    /**
     * Mengubah lebar elemen.
     *
     * Selebar apa pun yang diminta pengguna, hasilnya dijepit domain ([TemplateRect.resizedWidth]):
     * tidak lebih sempit dari lebar minimum yang masih terbaca, dan tidak melewati tepi kanan kertas.
     */
    private fun resizeElementWidth(elementId: String, widthMm10: Int) {
        val current = _uiState.value
        val target = current.template.elements.find { it.elementId == elementId } ?: return

        val newRect = target.rect.resizedWidth(
            newWidth = Mm10(widthMm10),
            minWidthMm10 = InvoiceTemplateDefaults.MIN_TEXT_WIDTH_MM10,
            paperWidth = current.template.paperSize.width
        )
        if (newRect == target.rect) return

        mutateElements { elements ->
            elements.map { el -> if (el.elementId == elementId) el.withRect(newRect) else el }
        }
    }

    private fun updateElement(updatedElement: TemplateElement) {
        mutateElements { elements ->
            elements.map { el -> if (el.elementId == updatedElement.elementId) updatedElement else el }
        }
    }

    /**
     * Mengubah isi teks statis.
     *
     * Satu jalur untuk dua tempat pengeditan (editor langsung di kanvas dan isian di panel properti),
     * supaya keduanya tidak bisa menghasilkan aturan yang berbeda — misalnya satu memangkas spasi dan
     * yang lain tidak.
     */
    private fun updateElementText(elementId: String, text: String) {
        mutateElements { elements ->
            elements.map { el ->
                if (el.elementId == elementId && el is TemplateElement.StaticText) el.copy(text = text) else el
            }
        }
    }

    /**
     * Menyisipkan elemen baru dari perpustakaan elemen.
     *
     * Posisi, ukuran awal, dan gaya bawaan seluruhnya berasal dari domain
     * ([InvoiceTemplate.nextFreeRect] dan [TemplateElementFactory]). ViewModel hanya menyediakan ID
     * unik dan urutan tumpuk; kalau nilai bawaan ditulis di UI, tombol yang sama di tempat berbeda
     * akan mulai menyimpang.
     */
    private fun insertPreset(preset: TemplateElementPreset) {
        val current = _uiState.value
        if (preset is TemplateElementPreset.ItemTable && current.template.itemTable != null) {
            _uiState.update {
                it.copy(error = "Template sudah memiliki tabel item. Hapus tabel lama sebelum menambah yang baru.")
            }
            return
        }

        val rect = current.template.nextFreeRect(
            widthMm10 = preset.requestedWidthMm10,
            heightMm10 = preset.requestedHeightMm10,
            snapMm10 = current.snapGridMm * 10
        )
        val elementId = uniqueElementId(current.template.elements)
        val zOrder = (current.template.elements.maxOfOrNull { it.zOrder } ?: 0.0) + 1.0
        val element = TemplateElementFactory.create(preset, elementId, rect, zOrder)

        mutateElements { elements -> elements + element }
        _uiState.update { it.copy(selectedElementId = elementId, error = null) }
    }

    /**
     * Membuat ID elemen yang belum dipakai.
     *
     * Stempel waktu milidetik saja tidak cukup: dua elemen yang disisipkan beruntun dari palet bisa
     * lahir pada milidetik yang sama, dan ID kembar membuat seleksi kanvas selalu menunjuk elemen
     * pertama — elemen kedua seolah tidak bisa dipilih. Karena itu bentrokan diselesaikan dengan
     * akhiran berurutan, bukan dengan mengandalkan jam.
     */
    private fun uniqueElementId(existing: List<TemplateElement>): String {
        val taken = existing.mapTo(mutableSetOf()) { it.elementId }
        val base = "el-${Clock.System.now().toEpochMilliseconds()}"
        if (base !in taken) return base

        var suffix = 2
        while ("$base-$suffix" in taken) suffix++
        return "$base-$suffix"
    }

    private fun deleteElement(elementId: String) {
        mutateElements { elements -> elements.filterNot { it.elementId == elementId } }
        _uiState.update { current ->
            if (current.selectedElementId == elementId) {
                current.copy(selectedElementId = null, editingTextElementId = null)
            } else {
                current
            }
        }
    }

    private fun saveTemplate() {
        scope.launch {
            // Pesan lama dibersihkan saat operasi **dimulai**, bukan hanya ditimpa di akhir.
            //
            // Sebelumnya banner lama bertahan sampai ada yang menutupnya. Akibatnya, simpan yang
            // tadinya gagal lalu diperbaiki tetap memperlihatkan banner merah lama berdampingan
            // dengan banner hijau "berhasil disimpan" — layar menyatakan gagal dan berhasil
            // sekaligus, dan pengguna tidak punya cara tahu mana yang masih berlaku.
            _uiState.update { it.copy(isSaving = true, error = null, successMessage = null) }
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
                    it.copy(
                        isSaving = false,
                        successMessage = null,
                        error = "Gagal menyimpan template: ${err.message}"
                    )
                }
            }
        }
    }

    private fun saveAndCreateInvoice(onSuccess: (InvoiceId) -> Unit) {
        scope.launch {
            // Sama seperti [saveTemplate]: pesan lama dibersihkan di awal supaya hasil terakhir
            // tidak pernah tampil bersama sisa hasil percobaan sebelumnya.
            _uiState.update { it.copy(isSaving = true, error = null, successMessage = null) }
            val tpl = _uiState.value.template

            // 1. Simpan template kustom terlebih dahulu
            val templateResult = remoteDataSource.saveTemplate(tenantSlug, tpl)
            if (templateResult.isFailure) {
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        successMessage = null,
                        error = "Gagal menyimpan template: ${templateResult.exceptionOrNull()?.message}"
                    )
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
                    it.copy(
                        isSaving = false,
                        successMessage = null,
                        error = "Gagal menerbitkan faktur: ${err.message}"
                    )
                }
            }
        }
    }

    fun getPdfUrl(invoiceId: InvoiceId): String =
        remoteDataSource.getPdfUrl(tenantSlug, invoiceId)
}
