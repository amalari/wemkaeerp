package com.eventverse.app.presentation.invoicing.template

import com.eventverse.app.domain.common.Money
import com.eventverse.app.domain.invoicing.InvoiceKind
import com.eventverse.app.domain.invoicing.InvoiceSourceKind
import com.eventverse.app.domain.invoicing.InvoiceStatus
import com.eventverse.app.domain.invoicing.template.Mm10
import com.eventverse.app.domain.invoicing.template.TemplateElement
import com.eventverse.app.domain.invoicing.template.TemplateRect
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
        assertEquals(DesignerInspectorTab.LIVE_DATA, state.activeInspectorTab)

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
    fun designerOpenedForTemplateEditing_hasNoPrefillAndStartsOnLayoutTab() {
        val state = designer(prefill = null, templateId = "tpl-existing").uiState.value

        assertNull(state.prefillData)
        assertEquals("tpl-existing", state.template.id.value)
        assertEquals(DesignerInspectorTab.LAYOUT, state.activeInspectorTab)
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
        viewModel.onEvent(
            TemplateDesignerUiEvent.AddElement(
                TemplateElement.StaticText(
                    elementId = "note-1",
                    rect = TemplateRect(Mm10(150), Mm10(1700), Mm10(1000), Mm10(80)),
                    text = "Catatan: Bahan Katun Combed 30s"
                )
            )
        )

        viewModel.onEvent(TemplateDesignerUiEvent.AutoMapWithAi)

        val state = viewModel.uiState.value
        val mappedNote = state.template.elements.first { it.elementId == "note-1" }
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
    fun updateLiveClientAndItem_flowStraightIntoDraftInvoice() = testScope.runTest {
        val viewModel = designer(samplingPrefill())

        viewModel.onEvent(
            TemplateDesignerUiEvent.UpdateLiveClient(
                name = "PT Baru Sejahtera",
                contactPerson = "Bu Rina",
                phone = "0811-2233",
                email = "ar@baru.co.id",
                address = "Jl. Melati No. 1, Bandung"
            )
        )
        viewModel.onEvent(
            TemplateDesignerUiEvent.UpdateLiveItem(
                description = "Kemeja Drill 100 pcs",
                quantity = 100.0,
                unitPrice = 150_000L,
                taxPercent = 11.0
            )
        )

        val draft = viewModel.uiState.value.previewInvoice
        assertEquals("PT Baru Sejahtera", draft.billTo.name)
        assertEquals("Bu Rina", draft.billTo.contactPerson)
        assertEquals("Jl. Melati No. 1, Bandung", draft.billTo.address)
        assertEquals("Kemeja Drill 100 pcs", draft.lines.first().description)
        assertEquals(Money.idr(15_000_000), draft.subtotal)
        assertEquals(Money.idr(1_650_000), draft.taxAmount)
        assertEquals(Money.idr(16_650_000), draft.total)
        assertEquals(Money.idr(15_000_000), draft.contractValue)
    }

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
