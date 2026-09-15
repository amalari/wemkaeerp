package com.eventverse.app.presentation.invoicing.template

import com.eventverse.app.domain.common.CurrencyCode
import com.eventverse.app.domain.common.Money
import com.eventverse.app.domain.common.Quantity
import com.eventverse.app.domain.common.Ratio
import com.eventverse.app.domain.common.UnitOfMeasure
import com.eventverse.app.domain.invoicing.*
import com.eventverse.app.domain.invoicing.template.*
import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.presentation.invoicing.InvoicePrefillData
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

/**
 * Alat interaksi kanvas.
 *
 * Dipisah menjadi dua mode, bukan satu mode pintar, karena kanvas A4 selalu penuh elemen yang
 * menempati hampir seluruh bidang: di [SELECT] setiap tarikan di atas elemen adalah perintah
 * "pindahkan elemen", sehingga tidak ada lagi jalur untuk menggeser tampilan kertasnya.
 * [PAN] mematikan seluruh handler elemen sehingga tarikan di titik mana pun menggeser viewport.
 */
enum class CanvasTool(val label: String) {
    SELECT("Kursor"),
    PAN("Geser Kanvas")
}

/**
 * Batas zoom kanvas.
 *
 * Dipakai bersama oleh ViewModel (yang menjepit permintaan zoom) dan layar (yang menghitung zoom
 * "Muat Layar"), supaya nilai yang dihitung tidak pernah berada di luar rentang yang diterima state.
 */
const val MIN_CANVAS_ZOOM = 50
const val MAX_CANVAS_ZOOM = 200

data class TemplateDesignerUiState(
    val template: InvoiceTemplate,
    val selectedElementId: String? = null,
    val zoomPercent: Int = 100, // 50% to 200%
    val showGrid: Boolean = false,
    val snapGridMm: Int = 5, // 5mm snap
    val canvasTool: CanvasTool = CanvasTool.SELECT,
    val isSaving: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null,
    val previewInvoice: Invoice = createDummyPreviewInvoice(),
    val prefillData: InvoicePrefillData? = null,
    val createdInvoiceId: InvoiceId? = null,
    val isPdfPreviewOpen: Boolean = false,
    val aiMappingSummary: AiAutoMapSummary? = null,
    /**
     * Elemen teks yang sedang diedit langsung di kanvas (hasil klik dua kali). Selama tidak null,
     * kanvas menampilkan editor di atas elemen tersebut, bukan teksnya.
     */
    val editingTextElementId: String? = null,
    /** Grup modul yang dibuka di perpustakaan elemen. Modul terlipat secara bawaan. */
    val expandedModules: Set<BindingModuleSource> = emptySet()
) {
    val selectedElement: TemplateElement?
        get() = selectedElementId?.let { id -> template.elements.find { it.elementId == id } }

    /** Elemen yang sedang diedit langsung, bila ada. */
    val editingElement: TemplateElement.StaticText?
        get() = editingTextElementId?.let { id ->
            template.elements.filterIsInstance<TemplateElement.StaticText>().find { it.elementId == id }
        }

    /**
     * True bila kanvas menampilkan contoh data, bukan data faktur sungguhan.
     *
     * Dipakai untuk memberi tahu pengguna asal angka yang mereka lihat. Sejak panel "Isi Data Faktur
     * Live" dihapus, nilai di kanvas **selalu** berasal dari luar desainer — dari modul CRM atau dari
     * contoh bawaan — sehingga tidak ada lagi tempat mengetiknya di sini.
     */
    val isSampleData: Boolean get() = prefillData == null

    /** Keterangan singkat asal data kanvas untuk strip sumber data. */
    val dataSourceLabel: String
        get() {
            val prefill = prefillData
            return if (prefill == null) {
                "Contoh data bawaan desainer"
            } else {
                val client = prefill.clientName.ifBlank { "Tanpa nama klien" }
                "${prefill.sourceKind.name} · $client · ${prefill.sourceRef.ifBlank { "tanpa referensi" }}"
            }
        }

    val itemTableExists: Boolean get() = template.itemTable != null

    companion object {
        fun fromPrefill(prefill: InvoicePrefillData, tenantSlug: String, now: Instant): Invoice {
            // Tanggal faktur diambil dari waktu nyata, bukan tanggal tetap, agar draft yang
            // dihasilkan dari CRM selalu memakai periode penagihan yang sedang berjalan.
            val today = now.toLocalDateTime(TimeZone.currentSystemDefault()).date
            val issueDate = today
            val dueDate = today.plus(14, DateTimeUnit.DAY)
            val qtyInt = (prefill.lineQty * 1_000_000).toLong().coerceAtLeast(1_000_000L)
            val line = InvoiceLine(
                id = InvoiceLineId("line-prefill-01"),
                description = prefill.lineDescription.ifBlank {
                    if (prefill.kind == InvoiceKind.SAMPLE) "Jasa Pembuatan Sample Baju" else "Pemesanan Produksi Garmen (DP)"
                },
                quantity = Quantity(qtyInt, UnitOfMeasure.PIECE),
                unitPrice = Money.idr(prefill.linePrice.coerceAtLeast(100_000L)),
                discount = Ratio.ZERO,
                sortOrder = 1
            )
            return Invoice(
                id = InvoiceId("inv-preview-crm"),
                tenantId = TenantId(tenantSlug),
                number = InvoiceNumber("INV/DRAFT"),
                kind = prefill.kind,
                status = InvoiceStatus.DRAFT,
                billTo = BillToParty(
                    name = prefill.clientName.ifBlank { "Klien Prospek CRM" },
                    contactPerson = prefill.contactPerson,
                    address = prefill.address.ifBlank { "Alamat Klien" },
                    phone = prefill.phone,
                    email = prefill.email,
                    taxId = ""
                ),
                issuer = IssuerProfile(
                    companyName = "PT WeMade Garment Indonesia",
                    address = "Kawasan Industri Rancaekek Kav. 12, Bandung",
                    taxId = "02.345.678.9-429.000",
                    phone = "(022) 8765-4321",
                    email = "billing@wemade.co.id",
                    bankName = "Bank Central Asia (BCA)",
                    bankAccountNumber = "8420-123-999",
                    bankAccountHolder = "PT WEMADE GARMENT INDONESIA"
                ),
                lines = listOf(line),
                taxRatio = Ratio.percent(11.0),
                globalDiscount = Ratio.ZERO,
                currency = CurrencyCode.IDR,
                issueDate = issueDate,
                dueDate = dueDate,
                templateId = InvoiceTemplateId("tpl-std-id-001"),
                renderedTemplate = null,
                sourceKind = prefill.sourceKind,
                sourceRef = prefill.sourceRef,
                parentInvoiceId = null,
                contractValue = line.amount,
                notes = prefill.notes.ifBlank {
                    if (prefill.kind == InvoiceKind.SAMPLE) "Faktur pembayaran biaya pembuatan prototype sample garmen."
                    else "Faktur termin 1 (Uang Muka / Down Payment) sebelum proses potong dan jahit."
                },
                terms = "Pembayaran via transfer bank ke rekening PT WeMade Garment Indonesia.",
                createdBy = "Admin",
                createdAt = now,
                updatedAt = now
            )
        }

        fun createDummyPreviewInvoice(): Invoice {
            val now = Clock.System.now()
            val issueDate = LocalDate(2026, 3, 15)
            val dueDate = LocalDate(2026, 3, 29)
            val line1 = InvoiceLine(
                id = InvoiceLineId("line-01"),
                description = "Kemeja Seragam Lapangan Drill (Termin DP 50%)",
                quantity = Quantity(100_000_000L, UnitOfMeasure.PIECE),
                unitPrice = Money.idr(75_000),
                discount = Ratio.ZERO,
                sortOrder = 1
            )
            val line2 = InvoiceLine(
                id = InvoiceLineId("line-02"),
                description = "Bordir Komputer Logo Perusahaan & Nama Dada",
                quantity = Quantity(100_000_000L, UnitOfMeasure.PIECE),
                unitPrice = Money.idr(15_000),
                discount = Ratio.ZERO,
                sortOrder = 2
            )
            return Invoice(
                id = InvoiceId("inv-preview-001"),
                tenantId = TenantId("ten-demo-001"),
                number = InvoiceNumber("INV/2026/03/0042"),
                kind = InvoiceKind.DOWN_PAYMENT,
                // Contoh data kanvas wajib berstatus DRAFT: domain melarang invoice berstatus
                // terbit tanpa snapshot template beku (renderedTemplate). Dokumen contoh ini
                // murni untuk mengisi token di kanvas, bukan dokumen legal yang sudah diterbitkan.
                status = InvoiceStatus.DRAFT,
                billTo = BillToParty(
                    name = "PT Mitra Usaha Mandiri",
                    contactPerson = "Bapak Hendra Gunawan",
                    address = "Kawasan Industri MM2100 Blok C-4, Cikarang",
                    phone = "0812-9876-5432",
                    email = "finance@mitrausaha.co.id",
                    taxId = "01.234.567.8-412.000"
                ),
                issuer = IssuerProfile(
                    companyName = "PT WeMade Garment Indonesia",
                    address = "Kawasan Industri Rancaekek Kav. 12, Bandung",
                    taxId = "02.345.678.9-429.000",
                    phone = "(022) 8765-4321",
                    email = "billing@wemade.co.id",
                    bankName = "Bank Central Asia (BCA)",
                    bankAccountNumber = "8420-123-999",
                    bankAccountHolder = "PT WEMADE GARMENT INDONESIA"
                ),
                lines = listOf(line1, line2),
                taxRatio = Ratio.percent(11.0),
                globalDiscount = Ratio.ZERO,
                currency = CurrencyCode.IDR,
                issueDate = issueDate,
                dueDate = dueDate,
                templateId = InvoiceTemplateId("tpl-std-id-001"),
                renderedTemplate = null,
                sourceKind = InvoiceSourceKind.MANUAL,
                sourceRef = "PO-MUM-2026/003",
                parentInvoiceId = null,
                contractValue = Money.idr(18_000_000),
                notes = "Termin 1: Down Payment 50% sebelum pengerjaan potong kain.",
                terms = "Pembayaran via transfer bank ke rekening tercantum. Faktur pelunasan diterbitkan setelah QC selesai.",
                createdBy = "Achmad Jamaludin",
                createdAt = now,
                updatedAt = now
            )
        }
    }
}

sealed interface TemplateDesignerUiEvent {
    data class SelectElement(val elementId: String?) : TemplateDesignerUiEvent

    /**
     * Memindahkan elemen secara **relatif** sebesar [dxMm10]/[dyMm10] (satuan 1/10 mm).
     *
     * Kanvas dan tombol panah sama-sama memakai event ini supaya aturan snap-to-grid serta
     * penjepitan ke dalam kertas diterapkan satu kali saja, di satu tempat: [TemplateRect.movedBy].
     */
    data class MoveElementBy(
        val elementId: String,
        val dxMm10: Int,
        val dyMm10: Int
    ) : TemplateDesignerUiEvent

    data class UpdateElementRect(val elementId: String, val newBounds: TemplateRect) : TemplateDesignerUiEvent

    /**
     * Mengubah lebar elemen dengan penjepitan domain ([TemplateRect.resizedWidth]).
     *
     * Tinggi **tidak** ikut berubah: ia turunan dari isi teks. Menggeser sudut bawah akan langsung
     * dilawan perhitungan ulang tinggi dan terasa seperti kanvas yang rusak.
     */
    data class ResizeElementWidth(val elementId: String, val widthMm10: Int) : TemplateDesignerUiEvent

    data class UpdateElement(val updatedElement: TemplateElement) : TemplateDesignerUiEvent

    /** Mengubah isi teks statis. Satu jalur untuk editor inline dan isian di panel properti. */
    data class UpdateElementText(val elementId: String, val text: String) : TemplateDesignerUiEvent

    /** Menyisipkan elemen baru dari perpustakaan elemen. Penempatan ditentukan domain. */
    data class InsertPreset(val preset: TemplateElementPreset) : TemplateDesignerUiEvent

    data class DeleteElement(val elementId: String) : TemplateDesignerUiEvent
    data class UpdateTemplateName(val name: String) : TemplateDesignerUiEvent
    data class SetZoom(val percent: Int) : TemplateDesignerUiEvent
    data class SetCanvasTool(val tool: CanvasTool) : TemplateDesignerUiEvent
    data class ToggleGrid(val show: Boolean) : TemplateDesignerUiEvent
    data class SetSnapGrid(val mm: Int) : TemplateDesignerUiEvent
    data object SaveTemplate : TemplateDesignerUiEvent
    data object DismissMessage : TemplateDesignerUiEvent

    /** Membuka/menutup grup modul di perpustakaan elemen. */
    data class ToggleModuleExpanded(val module: BindingModuleSource) : TemplateDesignerUiEvent

    // Pengeditan langsung di kanvas
    data class BeginTextEdit(val elementId: String) : TemplateDesignerUiEvent
    data object EndTextEdit : TemplateDesignerUiEvent

    // AI Auto-Mapping
    data object AutoMapWithAi : TemplateDesignerUiEvent
    data class SaveAndCreateInvoice(val onSuccess: (InvoiceId) -> Unit = {}) : TemplateDesignerUiEvent
    data object ClosePdfPreview : TemplateDesignerUiEvent
}
