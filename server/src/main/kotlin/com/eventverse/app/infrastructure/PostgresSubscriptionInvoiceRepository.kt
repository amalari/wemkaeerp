package com.eventverse.app.infrastructure

import com.eventverse.app.domain.builder.SubscriptionInvoice
import com.eventverse.app.domain.builder.SubscriptionInvoiceId
import com.eventverse.app.domain.builder.SubscriptionInvoiceRepository
import com.eventverse.app.domain.builder.SubscriptionInvoiceStatus
import com.eventverse.app.domain.moduledev.MoneyIdr
import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.infrastructure.tables.SubscriptionInvoicesTable
import kotlinx.datetime.Clock
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update

/** Invoice langganan platform (tabel V85 + V86). Baca tenant difilter `tenant_id` (RLS lapis kedua). */
class PostgresSubscriptionInvoiceRepository(private val clock: Clock = Clock.System) :
    SubscriptionInvoiceRepository {

    override suspend fun findByTenant(tenantId: TenantId): List<SubscriptionInvoice> =
        DatabaseFactory.dbQuery {
            SubscriptionInvoicesTable.selectAll()
                .where { SubscriptionInvoicesTable.tenantId eq tenantId.value }
                .orderBy(SubscriptionInvoicesTable.issuedAt, order = SortOrder.DESC)
                .map(::toInvoice)
        }

    override suspend fun findAll(): List<SubscriptionInvoice> = DatabaseFactory.dbQuery {
        SubscriptionInvoicesTable.selectAll()
            .orderBy(SubscriptionInvoicesTable.issuedAt, order = SortOrder.DESC)
            .map(::toInvoice)
    }

    override suspend fun findByIpaymuTrxId(trxId: String): SubscriptionInvoice? = DatabaseFactory.dbQuery {
        SubscriptionInvoicesTable.selectAll()
            .where { SubscriptionInvoicesTable.ipaymuTrxId eq trxId }
            .firstOrNull()
            ?.let(::toInvoice)
    }

    override suspend fun save(invoice: SubscriptionInvoice): SubscriptionInvoice = DatabaseFactory.dbQuery {
        val existing = SubscriptionInvoicesTable.selectAll()
            .where { SubscriptionInvoicesTable.id eq invoice.id.value }
            .firstOrNull()
        if (existing == null) {
            SubscriptionInvoicesTable.insert {
                it[id] = invoice.id.value
                it[tenantId] = invoice.tenantId.value
                it[number] = invoice.number
                it[period] = invoice.period
                it[linesJson] = SubscriptionInvoiceLinesCodec.toJson(invoice.lines)
                it[totalIdr] = invoice.totalIdr.amount
                it[status] = invoice.status.name
                it[issuedAt] = invoice.issuedAt ?: clock.now()
                it[paidAt] = invoice.paidAt
                it[paidNote] = invoice.paidNote
                it[ipaymuTrxId] = invoice.ipaymuTrxId
            }
        } else {
            // Hanya status pembayaran & trx gateway yang boleh berubah: baris & total adalah
            // snapshot beku, dan menimpanya akan mengubah arti dokumen yang sudah dikirim ke tenant.
            SubscriptionInvoicesTable.update({ SubscriptionInvoicesTable.id eq invoice.id.value }) {
                it[status] = invoice.status.name
                it[paidAt] = invoice.paidAt
                it[paidNote] = invoice.paidNote
                it[ipaymuTrxId] = invoice.ipaymuTrxId
            }
        }
        invoice
    }

    private fun toInvoice(row: ResultRow) = SubscriptionInvoice(
        id = SubscriptionInvoiceId(row[SubscriptionInvoicesTable.id]),
        tenantId = TenantId(row[SubscriptionInvoicesTable.tenantId]),
        number = row[SubscriptionInvoicesTable.number],
        period = row[SubscriptionInvoicesTable.period],
        lines = SubscriptionInvoiceLinesCodec.fromJson(row[SubscriptionInvoicesTable.linesJson]),
        totalIdr = MoneyIdr(row[SubscriptionInvoicesTable.totalIdr]),
        status = SubscriptionInvoiceStatus.valueOf(row[SubscriptionInvoicesTable.status]),
        issuedAt = row[SubscriptionInvoicesTable.issuedAt],
        paidAt = row[SubscriptionInvoicesTable.paidAt],
        paidNote = row[SubscriptionInvoicesTable.paidNote],
        ipaymuTrxId = row[SubscriptionInvoicesTable.ipaymuTrxId]
    )
}
