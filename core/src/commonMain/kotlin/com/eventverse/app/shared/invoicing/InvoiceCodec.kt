package com.eventverse.app.shared.invoicing

import com.eventverse.app.domain.common.CurrencyCode
import com.eventverse.app.domain.invoicing.*
import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.shared.common.DateTimeCodec
import com.eventverse.app.shared.common.MeasureCodec
import com.eventverse.app.shared.json.*
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate

object InvoiceCodec {

    fun encode(invoice: Invoice): JsonValue.Obj = jsonObjectOf(
        "id" to jsonOf(invoice.id.value),
        "tenantId" to jsonOf(invoice.tenantId.value),
        "number" to jsonOf(invoice.number.value),
        "kind" to jsonOf(invoice.kind.name),
        "status" to jsonOf(invoice.status.name),
        "billTo" to encodeBillTo(invoice.billTo),
        "issuer" to encodeIssuer(invoice.issuer),
        "lines" to jsonArrayOf(invoice.lines.map(::encodeLine)),
        "taxRatio" to MeasureCodec.encodeRatio(invoice.taxRatio),
        "globalDiscount" to MeasureCodec.encodeRatio(invoice.globalDiscount),
        "currency" to jsonOf(invoice.currency.code),
        "issueDate" to jsonOf(invoice.issueDate.toString()),
        "dueDate" to jsonOf(invoice.dueDate?.toString()),
        "templateId" to jsonOf(invoice.templateId.value),
        "renderedTemplate" to (invoice.renderedTemplate?.let(InvoiceTemplateCodec::encode) ?: JsonValue.Null),
        "sourceKind" to jsonOf(invoice.sourceKind.name),
        "sourceRef" to jsonOf(invoice.sourceRef),
        "parentInvoiceId" to jsonOf(invoice.parentInvoiceId?.value),
        "contractValue" to (invoice.contractValue?.let(MeasureCodec::encodeMoney) ?: JsonValue.Null),
        "subtotal" to MeasureCodec.encodeMoney(invoice.subtotal),
        "taxAmount" to MeasureCodec.encodeMoney(invoice.taxAmount),
        "total" to MeasureCodec.encodeMoney(invoice.total),
        "notes" to jsonOf(invoice.notes),
        "terms" to jsonOf(invoice.terms),
        "voidReason" to jsonOf(invoice.voidReason),
        "createdBy" to jsonOf(invoice.createdBy),
        "createdAt" to jsonOf(invoice.createdAt.toString()),
        "updatedAt" to jsonOf(invoice.updatedAt.toString())
    )

    fun decode(obj: JsonValue.Obj): Invoice {
        val id = InvoiceId(obj.string("id") ?: "")
        val tenantId = TenantId(obj.string("tenantId") ?: "")
        val number = InvoiceNumber(obj.string("number") ?: "")
        val kind = InvoiceKind.fromCode(obj.string("kind"))
        val status = InvoiceStatus.fromCode(obj.string("status"))
        val billTo = obj.obj("billTo")?.let(::decodeBillTo) ?: BillToParty(name = "Klien")
        val issuer = obj.obj("issuer")?.let(::decodeIssuer) ?: IssuerProfile(companyName = "Penerbit")
        val lines = obj.objectArray("lines").map(::decodeLine)
        val taxRatio = MeasureCodec.decodeRatio(obj.obj("taxRatio"))
        val globalDiscount = MeasureCodec.decodeRatio(obj.obj("globalDiscount"))
        val currency = obj.string("currency")?.let { runCatching { CurrencyCode.valueOf(it) }.getOrNull() } ?: CurrencyCode.IDR
        val issueDate = DateTimeCodec.parseLocalDateOrFallback(obj.string("issueDate"), LocalDate(2026, 1, 1))
        val dueDate = DateTimeCodec.parseLocalDateOrNull(obj.string("dueDate"))
        val templateId = InvoiceTemplateId(obj.string("templateId") ?: "")
        val renderedTemplate = obj.obj("renderedTemplate")?.let(InvoiceTemplateCodec::decode)
        val sourceKind = InvoiceSourceKind.fromCode(obj.string("sourceKind"))
        val sourceRef = obj.string("sourceRef")
        val parentInvoiceId = obj.string("parentInvoiceId")?.let { InvoiceId(it) }
        val contractValue = obj.obj("contractValue")?.let(MeasureCodec::decodeMoney)
        val notes = obj.string("notes") ?: ""
        val terms = obj.string("terms") ?: ""
        val voidReason = obj.string("voidReason")
        val createdBy = obj.string("createdBy") ?: "system"
        val createdAt = DateTimeCodec.parseInstantOrFallback(obj.string("createdAt"), Instant.fromEpochMilliseconds(0))
        val updatedAt = DateTimeCodec.parseInstantOrFallback(obj.string("updatedAt"), createdAt)

        return Invoice(
            id = id,
            tenantId = tenantId,
            number = number,
            kind = kind,
            status = status,
            billTo = billTo,
            issuer = issuer,
            lines = lines,
            taxRatio = taxRatio,
            globalDiscount = globalDiscount,
            currency = currency,
            issueDate = issueDate,
            dueDate = dueDate,
            templateId = templateId,
            renderedTemplate = renderedTemplate,
            sourceKind = sourceKind,
            sourceRef = sourceRef,
            parentInvoiceId = parentInvoiceId,
            contractValue = contractValue,
            notes = notes,
            terms = terms,
            voidReason = voidReason,
            createdBy = createdBy,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }

    // ── Supporting Codecs ─────────────────────────────────────────────────────────────────────

    fun encodeBillTo(party: BillToParty): JsonValue.Obj = jsonObjectOf(
        "name" to jsonOf(party.name),
        "contactPerson" to jsonOf(party.contactPerson),
        "address" to jsonOf(party.address),
        "phone" to jsonOf(party.phone),
        "email" to jsonOf(party.email),
        "taxId" to jsonOf(party.taxId)
    )

    fun decodeBillTo(obj: JsonValue.Obj): BillToParty = BillToParty(
        name = obj.string("name") ?: "Klien",
        contactPerson = obj.string("contactPerson") ?: "",
        address = obj.string("address") ?: "",
        phone = obj.string("phone") ?: "",
        email = obj.string("email") ?: "",
        taxId = obj.string("taxId") ?: ""
    )

    fun encodeIssuer(issuer: IssuerProfile): JsonValue.Obj = jsonObjectOf(
        "companyName" to jsonOf(issuer.companyName),
        "address" to jsonOf(issuer.address),
        "taxId" to jsonOf(issuer.taxId),
        "phone" to jsonOf(issuer.phone),
        "email" to jsonOf(issuer.email),
        "bankName" to jsonOf(issuer.bankName),
        "bankAccountNumber" to jsonOf(issuer.bankAccountNumber),
        "bankAccountHolder" to jsonOf(issuer.bankAccountHolder),
        "logoAssetUrl" to jsonOf(issuer.logoAssetUrl)
    )

    fun decodeIssuer(obj: JsonValue.Obj): IssuerProfile = IssuerProfile(
        companyName = obj.string("companyName") ?: "Penerbit",
        address = obj.string("address") ?: "",
        taxId = obj.string("taxId") ?: "",
        phone = obj.string("phone") ?: "",
        email = obj.string("email") ?: "",
        bankName = obj.string("bankName") ?: "",
        bankAccountNumber = obj.string("bankAccountNumber") ?: "",
        bankAccountHolder = obj.string("bankAccountHolder") ?: "",
        logoAssetUrl = obj.string("logoAssetUrl")
    )

    fun encodeLine(line: InvoiceLine): JsonValue.Obj = jsonObjectOf(
        "id" to jsonOf(line.id.value),
        "description" to jsonOf(line.description),
        "quantity" to MeasureCodec.encodeQuantity(line.quantity),
        "unitPrice" to MeasureCodec.encodeMoney(line.unitPrice),
        "discount" to MeasureCodec.encodeRatio(line.discount),
        "sortOrder" to jsonOf(line.sortOrder),
        "amount" to MeasureCodec.encodeMoney(line.amount)
    )

    fun decodeLine(obj: JsonValue.Obj): InvoiceLine = InvoiceLine(
        id = InvoiceLineId(obj.string("id") ?: ""),
        description = obj.string("description") ?: "",
        quantity = MeasureCodec.decodeQuantity(obj.obj("quantity")),
        unitPrice = MeasureCodec.decodeMoney(obj.obj("unitPrice")),
        discount = MeasureCodec.decodeRatio(obj.obj("discount")),
        sortOrder = obj.int("sortOrder") ?: 0
    )

    fun decodePage(obj: JsonValue.Obj): InvoicePage = InvoicePage(
        items = obj.objectArray("items").map(::decode),
        totalCount = obj.long("totalCount") ?: 0L,
        page = obj.int("page") ?: 1,
        pageSize = obj.int("pageSize") ?: 20
    )

    fun encodeCreateCommand(cmd: com.eventverse.app.domain.invoicing.usecases.CreateInvoiceCommand): JsonValue.Obj = jsonObjectOf(
        "kind" to jsonOf(cmd.kind.name),
        "templateId" to jsonOf(cmd.templateId.value),
        "billTo" to encodeBillTo(cmd.billTo),
        "lines" to jsonArrayOf(cmd.lines.map(::encodeLine)),
        "taxRatio" to MeasureCodec.encodeRatio(cmd.taxRatio),
        "globalDiscount" to MeasureCodec.encodeRatio(cmd.globalDiscount),
        "currency" to jsonOf(cmd.currency.code),
        "issueDate" to jsonOf(cmd.issueDate.toString()),
        "dueDate" to jsonOf(cmd.dueDate?.toString()),
        "sourceKind" to jsonOf(cmd.sourceKind.name),
        "sourceRef" to jsonOf(cmd.sourceRef),
        "parentInvoiceId" to jsonOf(cmd.parentInvoiceId?.value),
        "contractValue" to (cmd.contractValue?.let(MeasureCodec::encodeMoney) ?: JsonValue.Null),
        "notes" to jsonOf(cmd.notes),
        "terms" to jsonOf(cmd.terms)
    )

    fun encodeUpdateCommand(cmd: com.eventverse.app.domain.invoicing.usecases.UpdateInvoiceDraftCommand): JsonValue.Obj = jsonObjectOf(
        "kind" to jsonOf(cmd.kind.name),
        "templateId" to jsonOf(cmd.templateId.value),
        "billTo" to encodeBillTo(cmd.billTo),
        "lines" to jsonArrayOf(cmd.lines.map(::encodeLine)),
        "taxRatio" to MeasureCodec.encodeRatio(cmd.taxRatio),
        "globalDiscount" to MeasureCodec.encodeRatio(cmd.globalDiscount),
        "issueDate" to jsonOf(cmd.issueDate.toString()),
        "dueDate" to jsonOf(cmd.dueDate?.toString()),
        "notes" to jsonOf(cmd.notes),
        "terms" to jsonOf(cmd.terms)
    )
}
