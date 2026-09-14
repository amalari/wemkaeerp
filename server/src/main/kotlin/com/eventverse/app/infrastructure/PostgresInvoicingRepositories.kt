package com.eventverse.app.infrastructure

import com.eventverse.app.domain.common.*
import com.eventverse.app.domain.invoicing.*
import com.eventverse.app.domain.invoicing.template.*
import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.infrastructure.tables.*
import com.eventverse.app.shared.invoicing.InvoiceCodec
import com.eventverse.app.shared.invoicing.InvoiceTemplateCodec
import com.eventverse.app.shared.json.JsonParser
import com.eventverse.app.shared.json.JsonValue
import kotlinx.datetime.Clock
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.like
import org.jetbrains.exposed.sql.SqlExpressionBuilder.neq
import org.jetbrains.exposed.sql.transactions.TransactionManager

class PostgresInvoiceIssuerProfileRepository : InvoiceIssuerProfileRepository {

    override suspend fun findByTenantId(tenantId: TenantId): IssuerProfile? =
        DatabaseFactory.dbQuery(tenantId) {
            InvoiceIssuerProfilesTable.selectAll()
                .where { InvoiceIssuerProfilesTable.tenantId eq tenantId.value }
                .singleOrNull()
                ?.let(::toProfile)
        }

    override suspend fun save(tenantId: TenantId, profile: IssuerProfile): Unit =
        DatabaseFactory.dbQuery(tenantId) {
            val exists = InvoiceIssuerProfilesTable.selectAll()
                .where { InvoiceIssuerProfilesTable.tenantId eq tenantId.value }
                .count() > 0

            if (exists) {
                InvoiceIssuerProfilesTable.update({ InvoiceIssuerProfilesTable.tenantId eq tenantId.value }) {
                    it[companyName] = profile.companyName
                    it[address] = profile.address
                    it[taxId] = profile.taxId
                    it[phone] = profile.phone
                    it[email] = profile.email
                    it[bankName] = profile.bankName
                    it[bankAccountNumber] = profile.bankAccountNumber
                    it[bankAccountHolder] = profile.bankAccountHolder
                    it[logoUrl] = profile.logoAssetUrl
                    it[updatedAt] = Clock.System.now()
                }
            } else {
                InvoiceIssuerProfilesTable.insert {
                    it[this.tenantId] = tenantId.value
                    it[companyName] = profile.companyName
                    it[address] = profile.address
                    it[taxId] = profile.taxId
                    it[phone] = profile.phone
                    it[email] = profile.email
                    it[bankName] = profile.bankName
                    it[bankAccountNumber] = profile.bankAccountNumber
                    it[bankAccountHolder] = profile.bankAccountHolder
                    it[logoUrl] = profile.logoAssetUrl
                    it[updatedAt] = Clock.System.now()
                }
            }
        }

    private fun toProfile(row: ResultRow): IssuerProfile =
        IssuerProfile(
            companyName = row[InvoiceIssuerProfilesTable.companyName],
            address = row[InvoiceIssuerProfilesTable.address],
            taxId = row[InvoiceIssuerProfilesTable.taxId],
            phone = row[InvoiceIssuerProfilesTable.phone],
            email = row[InvoiceIssuerProfilesTable.email],
            bankName = row[InvoiceIssuerProfilesTable.bankName],
            bankAccountNumber = row[InvoiceIssuerProfilesTable.bankAccountNumber],
            bankAccountHolder = row[InvoiceIssuerProfilesTable.bankAccountHolder],
            logoAssetUrl = row[InvoiceIssuerProfilesTable.logoUrl]
        )
}

class PostgresInvoiceTemplateRepository : InvoiceTemplateRepository {

    override suspend fun findById(id: InvoiceTemplateId): InvoiceTemplate? =
        DatabaseFactory.dbQuery {
            InvoiceTemplatesTable.selectAll()
                .where { (InvoiceTemplatesTable.id eq id.value) and InvoiceTemplatesTable.archivedAt.isNull() }
                .singleOrNull()
                ?.let(::toTemplate)
        }

    override suspend fun findAllByTenant(tenantId: TenantId, includeArchived: Boolean): List<InvoiceTemplate> =
        DatabaseFactory.dbQuery(tenantId) {
            val query = InvoiceTemplatesTable.selectAll()
                .where { InvoiceTemplatesTable.tenantId eq tenantId.value }
            if (!includeArchived) {
                query.andWhere { InvoiceTemplatesTable.archivedAt.isNull() }
            }
            query.orderBy(InvoiceTemplatesTable.updatedAt, SortOrder.DESC)
                .map(::toTemplate)
        }

    override suspend fun findDefault(tenantId: TenantId): InvoiceTemplate? =
        DatabaseFactory.dbQuery(tenantId) {
            InvoiceTemplatesTable.selectAll()
                .where {
                    (InvoiceTemplatesTable.tenantId eq tenantId.value) and
                    (InvoiceTemplatesTable.isDefault eq true) and
                    InvoiceTemplatesTable.archivedAt.isNull()
                }
                .singleOrNull()
                ?.let(::toTemplate)
        }

    override suspend fun save(template: InvoiceTemplate): Unit =
        DatabaseFactory.dbQuery(template.tenantId) {
            val encoded = InvoiceTemplateCodec.encode(template)
            val elementsJson = JsonValue.Arr(encoded.array("elements")).encode()
            val applicableKindsJson = JsonValue.Arr(encoded.array("applicableKinds")).encode()

            val exists = InvoiceTemplatesTable.selectAll()
                .where { InvoiceTemplatesTable.id eq template.id.value }
                .count() > 0

            if (template.isDefault) {
                InvoiceTemplatesTable.update({
                    (InvoiceTemplatesTable.tenantId eq template.tenantId.value) and
                    (InvoiceTemplatesTable.id neq template.id.value)
                }) {
                    it[isDefault] = false
                }
            }

            if (exists) {
                InvoiceTemplatesTable.update({ InvoiceTemplatesTable.id eq template.id.value }) {
                    it[name] = template.name
                    it[paperSize] = template.paperSize.name
                    it[marginMm10] = template.marginMm10
                    it[applicableKinds] = applicableKindsJson
                    it[elements] = elementsJson
                    it[isDefault] = template.isDefault
                    it[archivedAt] = template.archivedAt
                    it[updatedAt] = template.updatedAt
                }
            } else {
                InvoiceTemplatesTable.insert {
                    it[id] = template.id.value
                    it[tenantId] = template.tenantId.value
                    it[name] = template.name
                    it[paperSize] = template.paperSize.name
                    it[marginMm10] = template.marginMm10
                    it[applicableKinds] = applicableKindsJson
                    it[elements] = elementsJson
                    it[isDefault] = template.isDefault
                    it[archivedAt] = template.archivedAt
                    it[createdAt] = template.createdAt
                    it[updatedAt] = template.updatedAt
                }
            }
        }

    override suspend fun setDefault(tenantId: TenantId, id: InvoiceTemplateId): Unit =
        DatabaseFactory.dbQuery(tenantId) {
            InvoiceTemplatesTable.update({ InvoiceTemplatesTable.tenantId eq tenantId.value }) {
                it[isDefault] = false
            }
            InvoiceTemplatesTable.update({ (InvoiceTemplatesTable.tenantId eq tenantId.value) and (InvoiceTemplatesTable.id eq id.value) }) {
                it[isDefault] = true
                it[updatedAt] = Clock.System.now()
            }
        }

    override suspend fun archive(id: InvoiceTemplateId): Unit =
        DatabaseFactory.dbQuery {
            InvoiceTemplatesTable.update({ InvoiceTemplatesTable.id eq id.value }) {
                it[archivedAt] = Clock.System.now()
                it[isDefault] = false
            }
        }

    private fun toTemplate(row: ResultRow): InvoiceTemplate {
        val elementsJson = row[InvoiceTemplatesTable.elements]
        val elementsObj = (JsonParser.parse(elementsJson) as? JsonValue.Arr)?.items?.mapNotNull {
            (it as? JsonValue.Obj)?.let(InvoiceTemplateCodec::decodeElement)
        } ?: emptyList()

        val kindsJson = row[InvoiceTemplatesTable.applicableKinds]
        val kinds = (JsonParser.parse(kindsJson) as? JsonValue.Arr)?.items?.mapNotNull {
            (it as? JsonValue.Str)?.value?.let { k -> runCatching { InvoiceKind.valueOf(k) }.getOrNull() }
        }?.toSet() ?: InvoiceKind.entries.toSet()

        return InvoiceTemplate(
            id = InvoiceTemplateId(row[InvoiceTemplatesTable.id]),
            tenantId = TenantId(row[InvoiceTemplatesTable.tenantId]),
            name = row[InvoiceTemplatesTable.name],
            paperSize = PaperSize.fromCode(row[InvoiceTemplatesTable.paperSize]),
            marginMm10 = row[InvoiceTemplatesTable.marginMm10],
            applicableKinds = kinds,
            elements = elementsObj,
            isDefault = row[InvoiceTemplatesTable.isDefault],
            archivedAt = row[InvoiceTemplatesTable.archivedAt],
            createdAt = row[InvoiceTemplatesTable.createdAt],
            updatedAt = row[InvoiceTemplatesTable.updatedAt]
        )
    }
}

class PostgresInvoicePaymentRepository : InvoicePaymentRepository {

    override suspend fun historyFor(invoiceId: InvoiceId): List<InvoicePayment> =
        DatabaseFactory.dbQuery {
            InvoicePaymentsTable.selectAll()
                .where { InvoicePaymentsTable.invoiceId eq invoiceId.value }
                .orderBy(InvoicePaymentsTable.paidAt, SortOrder.ASC)
                .map(::toPayment)
        }

    override suspend fun append(payment: InvoicePayment): Unit =
        DatabaseFactory.dbQuery {
            val invRow = InvoicesTable.selectAll()
                .where { InvoicesTable.id eq payment.invoiceId.value }
                .singleOrNull()
            val tenantIdStr = invRow?.get(InvoicesTable.tenantId) ?: "unknown"

            InvoicePaymentsTable.insert {
                it[id] = payment.id.value
                it[tenantId] = tenantIdStr
                it[invoiceId] = payment.invoiceId.value
                it[amountMinor] = payment.amount.minorUnits
                it[currency] = payment.amount.currency.code
                it[paidAt] = payment.paidAt
                it[method] = payment.method
                it[reference] = payment.reference
                it[note] = payment.note
                it[recordedBy] = payment.recordedBy
                it[createdAt] = payment.paidAt
            }
        }

    override suspend fun totalPaidFor(invoiceId: InvoiceId): Money =
        DatabaseFactory.dbQuery {
            val sumExpression = InvoicePaymentsTable.amountMinor.sum()
            val totalMinor = InvoicePaymentsTable
                .select(sumExpression)
                .where { InvoicePaymentsTable.invoiceId eq invoiceId.value }
                .singleOrNull()
                ?.get(sumExpression) ?: 0L
            Money(totalMinor, CurrencyCode.IDR)
        }

    private fun toPayment(row: ResultRow): InvoicePayment =
        InvoicePayment(
            id = InvoicePaymentId(row[InvoicePaymentsTable.id]),
            invoiceId = InvoiceId(row[InvoicePaymentsTable.invoiceId]),
            amount = Money(
                row[InvoicePaymentsTable.amountMinor],
                CurrencyCode.valueOf(row[InvoicePaymentsTable.currency])
            ),
            paidAt = row[InvoicePaymentsTable.paidAt],
            method = row[InvoicePaymentsTable.method],
            reference = row[InvoicePaymentsTable.reference],
            note = row[InvoicePaymentsTable.note],
            recordedBy = row[InvoicePaymentsTable.recordedBy]
        )
}

class PostgresInvoiceRepository : InvoiceRepository {

    override suspend fun findById(id: InvoiceId): Invoice? =
        DatabaseFactory.dbQuery {
            val row = InvoicesTable.selectAll()
                .where { InvoicesTable.id eq id.value }
                .singleOrNull() ?: return@dbQuery null
            loadInvoiceDetails(row)
        }

    override suspend fun findByNumber(tenantId: TenantId, number: InvoiceNumber): Invoice? =
        DatabaseFactory.dbQuery(tenantId) {
            val row = InvoicesTable.selectAll()
                .where { (InvoicesTable.tenantId eq tenantId.value) and (InvoicesTable.invoiceNumber eq number.value) }
                .singleOrNull() ?: return@dbQuery null
            loadInvoiceDetails(row)
        }

    override suspend fun search(query: InvoiceQuery): InvoicePage =
        DatabaseFactory.dbQuery(query.tenantId) {
            var condition: Op<Boolean> = InvoicesTable.tenantId eq query.tenantId.value

            query.status?.let {
                condition = condition and (InvoicesTable.status eq it.name)
            }
            query.kind?.let {
                condition = condition and (InvoicesTable.kind eq it.name)
            }
            query.searchQuery?.takeIf { it.isNotBlank() }?.let { term ->
                val searchPattern = "%${term.trim().lowercase()}%"
                condition = condition and (
                    (InvoicesTable.invoiceNumber.lowerCase().like(searchPattern)) or
                    (InvoicesTable.notes.lowerCase().like(searchPattern))
                )
            }

            val totalCount = InvoicesTable.selectAll().where { condition }.count()
            val offset = ((query.page - 1) * query.pageSize).toLong()

            val rows = InvoicesTable.selectAll()
                .where { condition }
                .orderBy(InvoicesTable.updatedAt, SortOrder.DESC)
                .limit(query.pageSize, offset = offset)
                .toList()

            val items = rows.map { loadInvoiceDetails(it) }
            InvoicePage(items, totalCount, query.page, query.pageSize)
        }

    override suspend fun reserveNextNumber(tenantId: TenantId, kind: InvoiceKind, period: String): InvoiceNumber =
        DatabaseFactory.dbQuery(tenantId) {
            val (year, month) = if (period.contains('/') || period.contains('-')) {
                val parts = period.replace("-", "/").split("/")
                (parts.getOrNull(0)?.toIntOrNull() ?: 2026) to (parts.getOrNull(1)?.toIntOrNull() ?: 1)
            } else if (period.length >= 6) {
                (period.substring(0, 4).toIntOrNull() ?: 2026) to (period.substring(4, 6).toIntOrNull() ?: 1)
            } else {
                2026 to 1
            }
            val prefix = "INV"
            val sql = """
                INSERT INTO invoice_number_sequences (tenant_id, prefix, year, month, current_seq)
                VALUES ('${tenantId.value}', '$prefix', $year, $month, 1)
                ON CONFLICT (tenant_id, prefix, year, month)
                DO UPDATE SET current_seq = invoice_number_sequences.current_seq + 1
                RETURNING current_seq
            """.trimIndent()

            val seq = TransactionManager.current().exec(sql) { rs ->
                if (rs.next()) rs.getLong("current_seq") else 1L
            } ?: 1L

            InvoiceNumber("$prefix/$year/${month.toString().padStart(2, '0')}/${seq.toString().padStart(4, '0')}")
        }

    override suspend fun deleteDraft(id: InvoiceId): Unit =
        DatabaseFactory.dbQuery {
            InvoicesTable.deleteWhere { (InvoicesTable.id eq id.value) and (InvoicesTable.status eq InvoiceStatus.DRAFT.name) }
        }

    override suspend fun save(invoice: Invoice): Unit =
        DatabaseFactory.dbQuery(invoice.tenantId) {
            val billToJson = InvoiceCodec.encodeBillTo(invoice.billTo).encode()
            val issuerJson = InvoiceCodec.encodeIssuer(invoice.issuer).encode()
            val renderedTemplateJson = invoice.renderedTemplate?.let { InvoiceTemplateCodec.encode(it).encode() }

            val exists = InvoicesTable.selectAll()
                .where { InvoicesTable.id eq invoice.id.value }
                .count() > 0

            if (exists) {
                InvoicesTable.update({ InvoicesTable.id eq invoice.id.value }) {
                    it[invoiceNumber] = invoice.number.value
                    it[kind] = invoice.kind.name
                    it[status] = invoice.status.name
                    it[templateId] = invoice.templateId.value
                    it[sourceKind] = invoice.sourceKind.name
                    it[sourceReferenceId] = invoice.sourceRef
                    it[parentInvoiceId] = invoice.parentInvoiceId?.value
                    it[billTo] = billToJson
                    it[issuerProfile] = issuerJson
                    it[renderedTemplate] = renderedTemplateJson
                    it[subtotalMinor] = invoice.subtotal.minorUnits
                    it[currency] = invoice.currency.code
                    it[contractValueMinor] = invoice.contractValue?.minorUnits
                    it[discountNumerator] = invoice.globalDiscount.numerator
                    it[discountDenominator] = invoice.globalDiscount.denominator
                    it[taxRateNumerator] = invoice.taxRatio.numerator
                    it[taxRateDenominator] = invoice.taxRatio.denominator
                    it[taxAmountMinor] = invoice.taxAmount.minorUnits
                    it[totalMinor] = invoice.total.minorUnits
                    it[issueDate] = invoice.issueDate
                    it[dueDate] = invoice.dueDate
                    it[notes] = invoice.notes
                    it[termsAndConditions] = invoice.terms
                    it[voidReason] = invoice.voidReason
                    it[createdBy] = invoice.createdBy
                    it[updatedAt] = invoice.updatedAt
                }
            } else {
                InvoicesTable.insert {
                    it[id] = invoice.id.value
                    it[tenantId] = invoice.tenantId.value
                    it[invoiceNumber] = invoice.number.value
                    it[kind] = invoice.kind.name
                    it[status] = invoice.status.name
                    it[templateId] = invoice.templateId.value
                    it[sourceKind] = invoice.sourceKind.name
                    it[sourceReferenceId] = invoice.sourceRef
                    it[parentInvoiceId] = invoice.parentInvoiceId?.value
                    it[billTo] = billToJson
                    it[issuerProfile] = issuerJson
                    it[renderedTemplate] = renderedTemplateJson
                    it[subtotalMinor] = invoice.subtotal.minorUnits
                    it[currency] = invoice.currency.code
                    it[contractValueMinor] = invoice.contractValue?.minorUnits
                    it[discountNumerator] = invoice.globalDiscount.numerator
                    it[discountDenominator] = invoice.globalDiscount.denominator
                    it[taxRateNumerator] = invoice.taxRatio.numerator
                    it[taxRateDenominator] = invoice.taxRatio.denominator
                    it[taxAmountMinor] = invoice.taxAmount.minorUnits
                    it[totalMinor] = invoice.total.minorUnits
                    it[paidAmountMinor] = 0L
                    it[issueDate] = invoice.issueDate
                    it[dueDate] = invoice.dueDate
                    it[notes] = invoice.notes
                    it[termsAndConditions] = invoice.terms
                    it[voidReason] = invoice.voidReason
                    it[createdBy] = invoice.createdBy
                    it[createdAt] = invoice.createdAt
                    it[updatedAt] = invoice.updatedAt
                }
            }

            // Sync lines
            InvoiceLinesTable.deleteWhere { InvoiceLinesTable.invoiceId eq invoice.id.value }
            invoice.lines.forEachIndexed { index, line ->
                InvoiceLinesTable.insert {
                    it[id] = line.id.value
                    it[tenantId] = invoice.tenantId.value
                    it[invoiceId] = invoice.id.value
                    it[description] = line.description
                    it[quantityMicros] = line.quantity.micros
                    it[uom] = line.quantity.uom.code
                    it[unitPriceMinor] = line.unitPrice.minorUnits
                    it[discountNumerator] = line.discount.numerator
                    it[discountDenominator] = line.discount.denominator
                    it[amountMinor] = line.amount.minorUnits
                    it[sortOrder] = line.sortOrder.takeIf { s -> s != 0 } ?: index
                    it[createdAt] = invoice.createdAt
                }
            }
        }

    private fun loadInvoiceDetails(row: ResultRow): Invoice {
        val invId = row[InvoicesTable.id]
        val lineRows = InvoiceLinesTable.selectAll()
            .where { InvoiceLinesTable.invoiceId eq invId }
            .orderBy(InvoiceLinesTable.sortOrder, SortOrder.ASC)
            .toList()

        val lines = lineRows.map { lRow ->
            InvoiceLine(
                id = InvoiceLineId(lRow[InvoiceLinesTable.id]),
                description = lRow[InvoiceLinesTable.description],
                quantity = Quantity(
                    lRow[InvoiceLinesTable.quantityMicros],
                    UnitOfMeasure.fromCode(lRow[InvoiceLinesTable.uom]) ?: UnitOfMeasure.PIECE
                ),
                unitPrice = Money(
                    lRow[InvoiceLinesTable.unitPriceMinor],
                    CurrencyCode.valueOf(row[InvoicesTable.currency])
                ),
                discount = Ratio.of(
                    lRow[InvoiceLinesTable.discountNumerator],
                    lRow[InvoiceLinesTable.discountDenominator]
                ),
                sortOrder = lRow[InvoiceLinesTable.sortOrder]
            )
        }

        val billToObj = JsonParser.parseObjectOrNull(row[InvoicesTable.billTo])
        val billTo = billToObj?.let(InvoiceCodec::decodeBillTo) ?: BillToParty(name = "Klien")

        val issuerObj = JsonParser.parseObjectOrNull(row[InvoicesTable.issuerProfile])
        val issuer = issuerObj?.let(InvoiceCodec::decodeIssuer) ?: IssuerProfile(companyName = "Penerbit")

        val renderedTplObj = row[InvoicesTable.renderedTemplate]?.let { JsonParser.parseObjectOrNull(it) }
        val renderedTpl = renderedTplObj?.let(InvoiceTemplateCodec::decode)

        val contractValue = row[InvoicesTable.contractValueMinor]?.let {
            Money(it, CurrencyCode.valueOf(row[InvoicesTable.currency]))
        }

        return Invoice(
            id = InvoiceId(invId),
            tenantId = TenantId(row[InvoicesTable.tenantId]),
            number = InvoiceNumber(row[InvoicesTable.invoiceNumber]),
            kind = InvoiceKind.fromCode(row[InvoicesTable.kind]),
            status = InvoiceStatus.fromCode(row[InvoicesTable.status]),
            billTo = billTo,
            issuer = issuer,
            lines = lines,
            taxRatio = Ratio.of(
                row[InvoicesTable.taxRateNumerator],
                row[InvoicesTable.taxRateDenominator]
            ),
            globalDiscount = Ratio.of(
                row[InvoicesTable.discountNumerator],
                row[InvoicesTable.discountDenominator]
            ),
            currency = CurrencyCode.valueOf(row[InvoicesTable.currency]),
            issueDate = row[InvoicesTable.issueDate],
            dueDate = row[InvoicesTable.dueDate],
            templateId = InvoiceTemplateId(row[InvoicesTable.templateId]),
            renderedTemplate = renderedTpl,
            sourceKind = InvoiceSourceKind.fromCode(row[InvoicesTable.sourceKind]),
            sourceRef = row[InvoicesTable.sourceReferenceId],
            parentInvoiceId = row[InvoicesTable.parentInvoiceId]?.let(::InvoiceId),
            contractValue = contractValue,
            notes = row[InvoicesTable.notes],
            terms = row[InvoicesTable.termsAndConditions],
            voidReason = row[InvoicesTable.voidReason],
            createdBy = row[InvoicesTable.createdBy],
            createdAt = row[InvoicesTable.createdAt],
            updatedAt = row[InvoicesTable.updatedAt]
        )
    }
}
