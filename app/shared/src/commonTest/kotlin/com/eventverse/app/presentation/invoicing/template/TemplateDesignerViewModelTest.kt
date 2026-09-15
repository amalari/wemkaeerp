package com.eventverse.app.presentation.invoicing.template

import com.eventverse.app.domain.common.Money
import com.eventverse.app.domain.invoicing.InvoiceKind
import com.eventverse.app.domain.invoicing.InvoiceSourceKind
import com.eventverse.app.domain.invoicing.InvoiceStatus
import com.eventverse.app.domain.invoicing.template.*
import com.eventverse.app.presentation.invoicing.InvoicePrefillData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.plus
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class TemplateDesignerViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private lateinit var fakeRemote: FakeInvoicingRemoteDataSource

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeRemote = FakeInvoicingRemoteDataSource()
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun samplingPrefill(
        kind: InvoiceKind = InvoiceKind.SAMPLE,
        clientName: String = "PT Mitra Usaha Mandiri"
    ) = InvoicePrefillData(
        kind = kind,
        clientName = clientName,
        contactPerson = "Bapak Hendra",
        phone = "0812-9876-5432",
        email = "finance@mitrausaha.co.id",
        address = "Kawasan Industri MM2100 Blok C-4",
        sourceKind = InvoiceSourceKind.CRM_LEAD,
        sourceRef = "lead-42",
        lineDescription = "Jasa Pembuatan Prototype Sample Baju",
        lineQty = 1.0,
        linePrice = 150_000L,
        openDesignerDirectly = true
    )

    private fun designer(
        prefill: InvoicePrefillData? = null,
        templateId: String? = null
    ) = TemplateDesignerViewModel(
        tenantSlug = "wemade-demo",
        initialTemplateId = templateId,
        initialPrefill = prefill,
        remoteDataSource = fakeRemote
    )

    @Test
    fun designerOpenedFromSamplingFlow_seedsCrmTemplateAndLiveInvoice() {
        val state = designer(samplingPrefill()).uiState.value

        assertEquals("Template Faktur Sampling - PT Mitra Usaha Mandiri", state.template.name)
        assertFalse(state.template.isDefault, "Template hasil alur CRM tidak boleh menandai dirinya default")
        assertTrue(
            state.template.id.value != "tpl-std-id-001",
            "ID template CRM wajib unik agar tidak menimpa template standar tenant"
        )
        assertEquals(setOf(InvoiceKind.SAMPLE), state.template.applicableKinds)
        assertFalse(state.isSampleData, "Alur CRM membawa data faktur sungguhan, bukan contoh")

        val draft = state.previewInvoice
        assertEquals(InvoiceKind.SAMPLE, draft.kind)
        assertEquals(InvoiceSourceKind.CRM_LEAD, draft.sourceKind)
        assertEquals("lead-42", draft.sourceRef)
        assertEquals("PT Mitra Usaha Mandiri", draft.billTo.name)
        assertEquals("Bapak Hendra", draft.billTo.contactPerson)
        assertEquals(Money.idr(150_000), draft.subtotal)
        assertEquals(Money.idr(150_000), draft.contractValue)
        assertNull(state.createdInvoiceId)
        assertFalse(state.isPdfPreviewOpen)
    }

    @Test
    fun designerOpenedFromDirectOrderFlow_seedsDownPaymentKindAndContractValue() {
        val prefill = samplingPrefill(kind = InvoiceKind.DOWN_PAYMENT, clientName = "Brand Lokal Jaya")
            .copy(lineQty = 100.0, linePrice = 150_000L)

        val state = designer(prefill).uiState.value

        assertEquals("Template Faktur DP - Brand Lokal Jaya", state.template.name)
        assertEquals(setOf(InvoiceKind.DOWN_PAYMENT), state.template.applicableKinds)
        assertEquals(InvoiceKind.DOWN_PAYMENT, state.previewInvoice.kind)
        assertEquals(Money.idr(15_000_000), state.previewInvoice.subtotal)
        assertEquals(Money.idr(15_000_000), state.previewInvoice.contractValue)

        val dueDate = assertNotNull(state.previewInvoice.dueDate)
        assertEquals(
            state.previewInvoice.issueDate.plus(14, DateTimeUnit.DAY),
            dueDate,
            "Jatuh tempo draft CRM harus 14 hari setelah tanggal terbit"
        )
    }

    @Test
    fun designerOpenedForTemplateEditing_carriesNoPrefillAndShowsSampleData() {
        val state = designer(prefill = null, templateId = "tpl-existing").uiState.value

        assertNull(state.prefillData)
        assertEquals("tpl-existing", state.template.id.value)
        assertTrue(state.isSampleData, "Tanpa prefill, kanvas menampilkan contoh bawaan")
        assertEquals(InvoiceStatus.DRAFT, state.previewInvoice.status)
    }

    @Test
    fun canvasSampleInvoice_isAlwaysAValidDraftInvoice() {
        // createDummyPreviewInvoice() dijalankan setiap kali kanvas dibuka dalam mode "Desain
        // Template". Sebelumnya contoh ini berstatus ISSUED tanpa snapshot template, sehingga
        // invariant Invoice melempar IllegalArgumentException dan seluruh layar desainer mati.
        val sample = TemplateDesignerUiState.createDummyPreviewInvoice()

        assertEquals(InvoiceStatus.DRAFT, sample.status)
        assertNull(sample.renderedTemplate)
        assertTrue(sample.lines.isNotEmpty())
    }

    @Test
    fun autoMapWithAi_convertsValueTextAndKeepsPureLabelsStatic() = testScope.runTest {
        val viewModel = designer(samplingPrefill())
        viewModel.onEvent(TemplateDesignerUiEvent.InsertPreset(TemplateElementPreset.StaticText))
        val insertedId = assertNotNull(viewModel.uiState.value.selectedElementId)
        viewModel.onEvent(
            TemplateDesignerUiEvent.UpdateElementText(insertedId, "Catatan: Bahan Katun Combed 30s")
        )

        viewModel.onEvent(TemplateDesignerUiEvent.AutoMapWithAi)

        val state = viewModel.uiState.value
        val mappedNote = state.template.elements.first { it.elementId == insertedId }
        assertTrue(mappedNote is TemplateElement.BoundField)
        assertEquals("invoice.notes", mappedNote.binding.value)
        assertEquals("Catatan: ", mappedNote.prefix)

        val untouchedLabel = state.template.elements.first { it.elementId == "bill-to-label" }
        assertTrue(untouchedLabel is TemplateElement.StaticText, "Label murni tidak boleh ikut dipetakan")

        val summary = assertNotNull(state.aiMappingSummary)
        assertEquals(1, summary.newlyMappedCount)
        assertTrue(
            state.successMessage?.contains("1 teks dipetakan ke token dinamis") == true,
            "Pesan sukses harus merangkum hasil pemetaan, bukan pesan kosong"
        )
    }

    @Test
    fun insertPreset_moduleField_carriesLabelAndFontFromTheRegistry() {
        val viewModel = designer(samplingPrefill())
        val descriptor = assertNotNull(InvoiceBindingRegistry.descriptorFor("billTo.phone"))

        viewModel.onEvent(
            TemplateDesignerUiEvent.InsertPreset(TemplateElementPreset.ModuleField(descriptor))
        )

        val insertedId = assertNotNull(viewModel.uiState.value.selectedElementId)
        val inserted = assertNotNull(
            viewModel.uiState.value.template.elements.find { it.elementId == insertedId }
        )

        assertTrue(inserted is TemplateElement.BoundField, "Isian modul harus lahir sebagai kolom dinamis")
        assertEquals("billTo.phone", inserted.binding.value)
        assertEquals("Telp: ", inserted.prefix, "Label bawaan diambil dari registry, bukan ditulis di UI")
        assertEquals(descriptor.defaultFontSizePt, inserted.style.fontSizePt)
    }

    @Test
    fun insertPreset_placesNewElementBelowTheLowestElement() {
        val viewModel = designer(prefill = null, templateId = "tpl-existing")
        val before = viewModel.uiState.value.template
        val lowestBottom = before.elements.maxOf { it.rect.y.value + it.rect.height.value }

        viewModel.onEvent(TemplateDesignerUiEvent.InsertPreset(TemplateElementPreset.Divider))

        val insertedId = assertNotNull(viewModel.uiState.value.selectedElementId)
        val inserted = assertNotNull(viewModel.uiState.value.template.elements.find { it.elementId == insertedId })

        assertTrue(
            inserted.rect.y.value >= lowestBottom,
            "Elemen baru muncul di bawah elemen terendah, bukan menimpa isi kertas yang sudah ada"
        )
        assertTrue(inserted.rect.bottom <= before.paperSize.height, "Elemen baru tidak boleh keluar dari kertas")
    }

    @Test
    fun insertPreset_secondItemTable_isRejectedWithMessage() {
        val viewModel = designer(prefill = null, templateId = "tpl-existing")

        viewModel.onEvent(TemplateDesignerUiEvent.InsertPreset(TemplateElementPreset.ItemTable))

        val state = viewModel.uiState.value
        assertEquals(1, state.template.elements.count { it is TemplateElement.ItemTable })
        assertTrue(
            state.error?.contains("sudah memiliki tabel item") == true,
            "Menambah tabel kedua harus ditolak dengan pesan yang menjelaskan sebabnya"
        )
    }

    @Test
    fun updateElementText_growsTheBoxHeightFollowingTheContent() {
        val viewModel = designer(samplingPrefill())
        viewModel.onEvent(TemplateDesignerUiEvent.InsertPreset(TemplateElementPreset.StaticText))
        val elementId = assertNotNull(viewModel.uiState.value.selectedElementId)

        val singleLineHeight = heightOf(viewModel, elementId)
        viewModel.onEvent(
            TemplateDesignerUiEvent.UpdateElementText(
                elementId,
                "Syarat pembayaran: pelunasan dilakukan paling lambat 14 hari setelah faktur diterbitkan."
            )
        )

        assertTrue(
            heightOf(viewModel, elementId) > singleLineHeight,
            "Tinggi kotak teks harus tumbuh mengikuti isinya, bukan tetap seperti saat dibuat"
        )
    }

    @Test
    fun resizeElementWidth_isClampedToMinimumAndToThePaperEdge() {
        val viewModel = designer(samplingPrefill())
        val elementId = "issuer-name"
        val paperWidth = viewModel.uiState.value.template.paperSize.width.value
        val start = assertNotNull(viewModel.uiState.value.template.elements.find { it.elementId == elementId })

        viewModel.onEvent(TemplateDesignerUiEvent.ResizeElementWidth(elementId, 1))
        assertEquals(
            InvoiceTemplateDefaults.MIN_TEXT_WIDTH_MM10,
            widthOf(viewModel, elementId),
            "Lebar di bawah batas minimum akan memecah teks per karakter, jadi harus dijepit"
        )

        viewModel.onEvent(TemplateDesignerUiEvent.ResizeElementWidth(elementId, 999_999))
        assertEquals(
            paperWidth - start.rect.x.value,
            widthOf(viewModel, elementId),
            "Lebar tidak boleh melewati tepi kanan kertas"
        )
    }

    @Test
    fun resizeElementWidth_keepsTheDerivedHeightInSyncWithTheNewLineCount() {
        val viewModel = designer(samplingPrefill())
        viewModel.onEvent(TemplateDesignerUiEvent.InsertPreset(TemplateElementPreset.StaticText))
        val elementId = assertNotNull(viewModel.uiState.value.selectedElementId)
        viewModel.onEvent(
            TemplateDesignerUiEvent.UpdateElementText(
                elementId,
                "Keterangan panjang yang sengaja dibuat agar memakan lebih dari satu baris pada lebar sempit."
            )
        )

        val wideHeight = heightOf(viewModel, elementId)
        viewModel.onEvent(TemplateDesignerUiEvent.ResizeElementWidth(elementId, 250))

        assertTrue(
            heightOf(viewModel, elementId) > wideHeight,
            "Menyempitkan kotak menambah jumlah baris, dan tingginya wajib ikut menyesuaikan"
        )
    }

    @Test
    fun beginAndEndTextEdit_toggleTheInlineEditorTarget() {
        val viewModel = designer(samplingPrefill())

        viewModel.onEvent(TemplateDesignerUiEvent.BeginTextEdit("bill-to-label"))
        assertNotNull(viewModel.uiState.value.editingElement)
        assertEquals("bill-to-label", viewModel.uiState.value.editingTextElementId)

        viewModel.onEvent(TemplateDesignerUiEvent.SelectElement("issuer-name"))
        assertNull(
            viewModel.uiState.value.editingTextElementId,
            "Memilih elemen lain harus menutup editor langsung yang sedang terbuka"
        )

        viewModel.onEvent(TemplateDesignerUiEvent.BeginTextEdit("bill-to-label"))
        viewModel.onEvent(TemplateDesignerUiEvent.EndTextEdit)
        assertNull(viewModel.uiState.value.editingTextElementId)
    }

    @Test
    fun toggleModuleExpanded_opensAndClosesPaletteGroups() {
        val viewModel = designer(samplingPrefill())
        val module = BindingModuleSource.ISSUER_TENANT
        assertFalse(module in viewModel.uiState.value.expandedModules)

        viewModel.onEvent(TemplateDesignerUiEvent.ToggleModuleExpanded(module))
        assertTrue(module in viewModel.uiState.value.expandedModules)

        viewModel.onEvent(TemplateDesignerUiEvent.ToggleModuleExpanded(module))
        assertFalse(module in viewModel.uiState.value.expandedModules)
    }

    private fun heightOf(viewModel: TemplateDesignerViewModel, elementId: String): Int =
        assertNotNull(viewModel.uiState.value.template.elements.find { it.elementId == elementId })
            .rect.height.value

    private fun widthOf(viewModel: TemplateDesignerViewModel, elementId: String): Int =
        assertNotNull(viewModel.uiState.value.template.elements.find { it.elementId == elementId })
            .rect.width.value

    @Test
    fun saveAndCreateInvoice_persistsTemplateThenCreatesDraftAndOpensPreview() = testScope.runTest {
        val viewModel = designer(samplingPrefill())
        var createdInvoiceId: String? = null

        viewModel.onEvent(TemplateDesignerUiEvent.SaveAndCreateInvoice { createdInvoiceId = it.value })
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(1, fakeRemote.saveTemplateCallCount)
        assertEquals(
            "Template Faktur Sampling - PT Mitra Usaha Mandiri",
            fakeRemote.lastSavedTemplate?.name
        )
        assertEquals(fakeRemote.lastSavedTemplate?.id, fakeRemote.lastCreateCommand?.templateId)
        assertEquals(InvoiceKind.SAMPLE, fakeRemote.lastCreateCommand?.kind)
        assertEquals(InvoiceSourceKind.CRM_LEAD, fakeRemote.lastCreateCommand?.sourceKind)
        assertEquals("lead-42", fakeRemote.lastCreateCommand?.sourceRef)

        assertEquals("inv-created-1", state.createdInvoiceId?.value)
        assertEquals("inv-created-1", createdInvoiceId)
        assertTrue(state.isPdfPreviewOpen, "Pratinjau PDF harus otomatis terbuka setelah faktur dibuat")
        assertFalse(state.isSaving)
        assertNull(state.error)

        viewModel.onEvent(TemplateDesignerUiEvent.ClosePdfPreview)
        assertFalse(viewModel.uiState.value.isPdfPreviewOpen)
        assertEquals("inv-created-1", viewModel.uiState.value.createdInvoiceId?.value)
    }

    @Test
    fun saveAndCreateInvoice_whenInvoiceCreationRejected_surfacesErrorAndKeepsPreviewClosed() = testScope.runTest {
        fakeRemote.createInvoiceOutcome = {
            Result.failure(IllegalStateException("HTTP 400: sourceKind tidak dikenal"))
        }
        val viewModel = designer(samplingPrefill())

        viewModel.onEvent(TemplateDesignerUiEvent.SaveAndCreateInvoice())
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNull(state.createdInvoiceId)
        assertFalse(state.isPdfPreviewOpen)
        assertFalse(state.isSaving)
        assertTrue(state.error?.contains("Gagal menerbitkan faktur") == true)
    }

    @Test
    fun saveAndCreateInvoice_whenTemplateSaveFails_neverCallsCreateInvoice() = testScope.runTest {
        fakeRemote.saveTemplateOutcome = { Result.failure(IllegalStateException("HTTP 500")) }
        val viewModel = designer(samplingPrefill())

        viewModel.onEvent(TemplateDesignerUiEvent.SaveAndCreateInvoice())
        advanceUntilIdle()

        assertNull(fakeRemote.lastCreateCommand)
        assertTrue(viewModel.uiState.value.error?.contains("Gagal menyimpan template") == true)
        assertFalse(viewModel.uiState.value.isSaving)
    }
    @Test
    fun saveTemplate_afterAPreviousFailure_clearsTheStaleErrorMessage() = testScope.runTest {
        // Banner pesan tidak punya batas waktu: ia bertahan sampai ada yang menutupnya. Tanpa
        // pembersihan di awal operasi, simpan yang tadinya gagal lalu diperbaiki akan menampilkan
        // banner merah lama berdampingan dengan banner hijau "berhasil disimpan" — dan pengguna
        // tidak punya cara tahu mana yang masih berlaku.
        fakeRemote.saveTemplateOutcome = { Result.failure(IllegalStateException("HTTP 500")) }
        val viewModel = designer(prefill = null, templateId = "tpl-existing")

        viewModel.onEvent(TemplateDesignerUiEvent.SaveTemplate)
        advanceUntilIdle()
        assertTrue(
            viewModel.uiState.value.error?.contains("HTTP 500") == true,
            "Kegagalan simpan harus terlihat lebih dulu supaya test ini benar-benar menguji banner lama"
        )

        fakeRemote.saveTemplateOutcome = { Result.success(it) }
        viewModel.onEvent(TemplateDesignerUiEvent.SaveTemplate)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(2, fakeRemote.saveTemplateCallCount)
        assertNull(state.error, "Kesalahan lama wajib hilang begitu simpan berhasil")
        assertTrue(state.successMessage?.contains("berhasil disimpan") == true)
        assertFalse(state.isSaving)
    }



    @Test
    fun designerTemplate_neverOpensWithABlankPaper() {
        // Kanvas A4 kosong tanpa elemen = tidak ada satu pun kolom isian faktur yang tampil.
        // Ini pernah terjadi di produksi karena reader template membuang seluruh elemen seed.
        val state = designer(prefill = null, templateId = "tpl-existing").uiState.value

        assertTrue(state.template.elements.isNotEmpty(), "Kanvas A4 tidak boleh terbuka tanpa elemen")
        assertTrue(state.template.elements.any { it.elementId == "invoice-table" })
    }

    @Test
    fun canvasNudge_snapsToMagnetGrid() {
        val viewModel = designer(samplingPrefill())
        val start = viewModel.uiState.value.template.elements.first { it.elementId == "issuer-name" }

        viewModel.onEvent(
            TemplateDesignerUiEvent.MoveElementBy(elementId = "issuer-name", dxMm10 = 50, dyMm10 = 0)
        )

        val moved = viewModel.uiState.value.template.elements.first { it.elementId == "issuer-name" }
        assertEquals(start.rect.x.value + 50, moved.rect.x.value)
        assertEquals(0, moved.rect.x.value % (viewModel.uiState.value.snapGridMm * 10))
        assertEquals(start.rect.y, moved.rect.y, "Nudge horizontal tidak boleh menggeser sumbu Y")
    }

    @Test
    fun canvasNudge_pastThePaperEdge_pinsToTheEdgeInsteadOfEscapingTheSheet() {
        val viewModel = designer(samplingPrefill())
        val paperWidth = viewModel.uiState.value.template.paperSize.width.value
        val start = viewModel.uiState.value.template.elements.first { it.elementId == "issuer-name" }

        viewModel.onEvent(
            TemplateDesignerUiEvent.MoveElementBy(elementId = "issuer-name", dxMm10 = 999_999, dyMm10 = 0)
        )

        val moved = viewModel.uiState.value.template.elements.first { it.elementId == "issuer-name" }
        assertEquals(paperWidth - start.rect.width.value, moved.rect.x.value)
    }

    @Test
    fun canvasNudge_unknownElement_leavesTemplateUntouched() {
        val viewModel = designer(samplingPrefill())
        val before = viewModel.uiState.value.template.elements

        viewModel.onEvent(TemplateDesignerUiEvent.MoveElementBy(elementId = "tidak-ada", dxMm10 = 50, dyMm10 = 50))

        assertEquals(before, viewModel.uiState.value.template.elements)
    }

    @Test
    fun canvasTool_switchToPan_keepsTheCurrentSelection() {
        val viewModel = designer(samplingPrefill())

        viewModel.onEvent(TemplateDesignerUiEvent.SelectElement("issuer-name"))
        assertEquals(CanvasTool.SELECT, viewModel.uiState.value.canvasTool)

        viewModel.onEvent(TemplateDesignerUiEvent.SetCanvasTool(CanvasTool.PAN))

        val state = viewModel.uiState.value
        assertEquals(CanvasTool.PAN, state.canvasTool)
        assertEquals("issuer-name", state.selectedElementId)
    }
}
