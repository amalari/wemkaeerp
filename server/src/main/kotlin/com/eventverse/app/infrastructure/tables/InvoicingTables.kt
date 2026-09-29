package com.eventverse.app.infrastructure.tables

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.date
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

object InvoiceNumberSequencesTable : Table("invoicing.invoice_number_sequences") {
    val tenantId = varchar("tenant_id", 64).references(TenantsTable.id)
    val prefix = varchar("prefix", 32).default("INV")
    val year = integer("year")
    val month = integer("month")
    val currentSeq = long("current_seq").default(0L)

    override val primaryKey = PrimaryKey(tenantId, prefix, year, month)
}

object InvoiceIssuerProfilesTable : Table("invoicing.invoice_issuer_profiles") {
    val tenantId = varchar("tenant_id", 64).references(TenantsTable.id)
    val companyName = varchar("company_name", 150)
    val tagline = varchar("tagline", 200).default("")
    val address = text("address").default("")
    val phone = varchar("phone", 50).default("")
    val email = varchar("email", 100).default("")
    val taxId = varchar("tax_id", 50).default("")
    val bankName = varchar("bank_name", 100).default("")
    val bankAccountNumber = varchar("bank_account_number", 100).default("")
    val bankAccountHolder = varchar("bank_account_holder", 150).default("")
    val logoUrl = text("logo_url").nullable()
    val signatureName = varchar("signature_name", 150).default("")
    val signatureTitle = varchar("signature_title", 100).default("")
    val signatureImageUrl = text("signature_image_url").nullable()
    val updatedAt = timestamp("updated_at")

    override val primaryKey = PrimaryKey(tenantId)
}

object InvoiceTemplatesTable : Table("invoicing.invoice_templates") {
    val id = varchar("id", 64)
    val tenantId = varchar("tenant_id", 64).references(TenantsTable.id)
    val name = varchar("name", 150)
    val description = text("description").default("")
    val paperSize = varchar("paper_size", 30).default("A4_PORTRAIT")
    val marginMm10 = integer("margin_mm10").default(150)
    val applicableKinds = jsonbText("applicable_kinds")
    val elements = jsonbText("elements")
    val isDefault = bool("is_default").default(false)
    val archivedAt = timestamp("archived_at").nullable()
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")

    override val primaryKey = PrimaryKey(id)
}

object InvoicesTable : Table("invoicing.invoices") {
    val id = varchar("id", 64)
    val tenantId = varchar("tenant_id", 64).references(TenantsTable.id)
    val invoiceNumber = varchar("invoice_number", 64)
    val kind = varchar("kind", 30)
    val status = varchar("status", 30).default("DRAFT")
    val templateId = varchar("template_id", 64).references(InvoiceTemplatesTable.id)
    val sourceKind = varchar("source_kind", 30).default("MANUAL")
    val sourceReferenceId = varchar("source_reference_id", 64).nullable()
    val parentInvoiceId = varchar("parent_invoice_id", 64).references(id).nullable()
    val billTo = jsonbText("bill_to")
    val issuerProfile = jsonbText("issuer_profile")
    val renderedTemplate = jsonbText("rendered_template").nullable()
    val subtotalMinor = long("subtotal_minor")
    val currency = varchar("currency", 10).default("IDR")
    val contractValueMinor = long("contract_value_minor").nullable()
    val discountNumerator = long("discount_numerator").default(0L)
    val discountDenominator = long("discount_denominator").default(100L)
    val taxRateNumerator = long("tax_rate_numerator").default(0L)
    val taxRateDenominator = long("tax_rate_denominator").default(100L)
    val taxAmountMinor = long("tax_amount_minor").default(0L)
    val totalMinor = long("total_minor")
    val paidAmountMinor = long("paid_amount_minor").default(0L)
    val paymentTermsDays = integer("payment_terms_days").default(14)
    val issueDate = date("issue_date")
    val dueDate = date("due_date").nullable()
    val issuedAt = timestamp("issued_at").nullable()
    val paidAt = timestamp("paid_at").nullable()
    val voidedAt = timestamp("voided_at").nullable()
    val voidReason = text("void_reason").nullable()
    val notes = text("notes").default("")
    val termsAndConditions = text("terms_and_conditions").default("")
    val customAttributes = jsonbText("custom_attributes")
    val createdBy = varchar("created_by", 150).default("system")
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")

    override val primaryKey = PrimaryKey(id)
}

object InvoiceLinesTable : Table("invoicing.invoice_lines") {
    val id = varchar("id", 64)
    val tenantId = varchar("tenant_id", 64).references(TenantsTable.id)
    val invoiceId = varchar("invoice_id", 64).references(InvoicesTable.id)
    val description = varchar("description", 250)
    val quantityMicros = long("quantity_micros")
    val uom = varchar("uom", 20)
    val unitPriceMinor = long("unit_price_minor")
    val discountNumerator = long("discount_numerator").default(0L)
    val discountDenominator = long("discount_denominator").default(100L)
    val amountMinor = long("amount_minor")
    val sortOrder = integer("sort_order").default(0)
    val createdAt = timestamp("created_at")

    override val primaryKey = PrimaryKey(id)
}

object InvoicePaymentsTable : Table("invoicing.invoice_payments") {
    val id = varchar("id", 64)
    val tenantId = varchar("tenant_id", 64).references(TenantsTable.id)
    val invoiceId = varchar("invoice_id", 64).references(InvoicesTable.id)
    val amountMinor = long("amount_minor")
    val currency = varchar("currency", 10).default("IDR")
    val paidAt = timestamp("paid_at")
    val method = varchar("method", 50)
    val reference = varchar("reference", 100).default("")
    val note = text("note").default("")
    val recordedBy = varchar("recorded_by", 150).default("system")
    val createdAt = timestamp("created_at")

    override val primaryKey = PrimaryKey(id)
}
