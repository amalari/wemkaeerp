package com.eventverse.app.presentation.invoicing.template

import com.eventverse.app.domain.invoicing.*
import com.eventverse.app.domain.invoicing.template.InvoiceTemplate
import com.eventverse.app.domain.invoicing.usecases.CreateInvoiceCommand
import com.eventverse.app.domain.invoicing.usecases.RecordInvoicePaymentCommand
import com.eventverse.app.domain.invoicing.usecases.UpdateInvoiceDraftCommand
import com.eventverse.app.infrastructure.api.InvoicingRemoteDataSource
import kotlinx.datetime.Clock

/**
 * Fake data source untuk menguji [TemplateDesignerViewModel] tanpa jaringan.
 *
 * Hanya dua operasi yang dipakai alur desainer kanvas (simpan template & buat invoice) yang
 * diberi perilaku nyata; sisanya sengaja gagal keras supaya pemakaian tak terduga langsung
 * terlihat di test, bukan diam-diam lolos.
 */
class FakeInvoicingRemoteDataSource(
    var saveTemplateOutcome: (InvoiceTemplate) -> Result<InvoiceTemplate> = { Result.success(it) },
    var createInvoiceOutcome: ((CreateInvoiceCommand) -> Result<Invoice>)? = null
) : InvoicingRemoteDataSource {

    var saveTemplateCallCount: Int = 0
        private set
    var lastSavedTemplate: InvoiceTemplate? = null
        private set
    var lastCreateCommand: CreateInvoiceCommand? = null
        private set

    override suspend fun saveTemplate(tenantSlug: String, template: InvoiceTemplate): Result<InvoiceTemplate> {
        saveTemplateCallCount++
        lastSavedTemplate = template
        return saveTemplateOutcome(template)
    }

    override suspend fun createInvoice(tenantSlug: String, command: CreateInvoiceCommand): Result<Invoice> {
        lastCreateCommand = command
        createInvoiceOutcome?.let { return it(command) }
        val now = Clock.System.now()
        return Result.success(
            Invoice(
                id = InvoiceId("inv-created-1"),
                tenantId = command.tenantId,
                number = InvoiceNumber("INV/2026/03/0001"),
                kind = command.kind,
                status = InvoiceStatus.DRAFT,
                billTo = command.billTo,
                issuer = IssuerProfile(companyName = "PT WeMade Garment Indonesia"),
                lines = command.lines,
                taxRatio = command.taxRatio,
                globalDiscount = command.globalDiscount,
                currency = command.currency,
                issueDate = command.issueDate,
                dueDate = command.dueDate,
                templateId = command.templateId,
                sourceKind = command.sourceKind,
                sourceRef = command.sourceRef,
                parentInvoiceId = command.parentInvoiceId,
                contractValue = command.contractValue,
                notes = command.notes,
                terms = command.terms,
                createdBy = command.createdBy,
                createdAt = now,
                updatedAt = now
            )
        )
    }

    override fun getPdfUrl(tenantSlug: String, invoiceId: InvoiceId): String =
        "/api/tenant/invoicing/${invoiceId.value}/pdf"

    override suspend fun getInvoices(
        tenantSlug: String,
        status: InvoiceStatus?,
        kind: InvoiceKind?,
        searchQuery: String?,
        page: Int,
        pageSize: Int
    ): Result<InvoicePage> = unimplemented("getInvoices")

    override suspend fun getInvoice(tenantSlug: String, id: InvoiceId): Result<Invoice> =
        unimplemented("getInvoice")

    override suspend fun updateDraft(tenantSlug: String, command: UpdateInvoiceDraftCommand): Result<Invoice> =
        unimplemented("updateDraft")

    override suspend fun issueInvoice(tenantSlug: String, id: InvoiceId): Result<Invoice> =
        unimplemented("issueInvoice")

    override suspend fun voidInvoice(tenantSlug: String, id: InvoiceId, reason: String): Result<Invoice> =
        unimplemented("voidInvoice")

    override suspend fun createSettlement(tenantSlug: String, downPaymentInvoiceId: InvoiceId): Result<Invoice> =
        unimplemented("createSettlement")

    override suspend fun prefillFromSampling(tenantSlug: String, samplingId: String): Result<Invoice> =
        unimplemented("prefillFromSampling")

    override suspend fun getPayments(tenantSlug: String, invoiceId: InvoiceId): Result<List<InvoicePayment>> =
        unimplemented("getPayments")

    override suspend fun recordPayment(
        tenantSlug: String,
        command: RecordInvoicePaymentCommand
    ): Result<InvoicePayment> = unimplemented("recordPayment")

    override suspend fun getTemplates(tenantSlug: String, includeArchived: Boolean): Result<List<InvoiceTemplate>> =
        unimplemented("getTemplates")

    override suspend fun getDefaultTemplate(tenantSlug: String): Result<InvoiceTemplate> =
        unimplemented("getDefaultTemplate")

    override suspend fun getTemplate(tenantSlug: String, id: InvoiceTemplateId): Result<InvoiceTemplate> =
        unimplemented("getTemplate")

    override suspend fun setDefaultTemplate(tenantSlug: String, id: InvoiceTemplateId): Result<Unit> =
        unimplemented("setDefaultTemplate")

    override suspend fun archiveTemplate(tenantSlug: String, id: InvoiceTemplateId): Result<Unit> =
        unimplemented("archiveTemplate")

    override suspend fun getIssuerProfile(tenantSlug: String): Result<IssuerProfile> =
        unimplemented("getIssuerProfile")

    override suspend fun saveIssuerProfile(tenantSlug: String, profile: IssuerProfile): Result<IssuerProfile> =
        unimplemented("saveIssuerProfile")

    override suspend fun downloadPdfBytes(tenantSlug: String, invoiceId: InvoiceId): Result<ByteArray> =
        unimplemented("downloadPdfBytes")

    private fun <T> unimplemented(operation: String): Result<T> =
        Result.failure(IllegalStateException("Operasi '$operation' tidak dipakai pada alur desainer kanvas."))
}
