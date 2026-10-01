package com.eventverse.app.infrastructure.tables

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

/**
 * Invoice langganan platform (V85, PLAN-builder-console FR-M2-5).
 *
 * `lines_json` menyimpan **salinan beku** baris tagihan (harga saat terbit). Harga sengaja tidak
 * dinormalisasi ke tabel harga: invoice adalah dokumen historis, dan dokumen historis yang menunjuk
 * ke harga hari ini akan berubah sendiri ketika katalog dinaikkan.
 */
object SubscriptionInvoicesTable : Table("builder.subscription_invoices") {
    val id = varchar("id", 140)
    val tenantId = varchar("tenant_id", 64).references(TenantsTable.id)
    val number = varchar("number", 40)
    val period = varchar("period", 7)
    val linesJson = text("lines_json")
    val totalIdr = long("total_idr")
    val status = varchar("status", 20)
    val issuedAt = timestamp("issued_at")
    val paidAt = timestamp("paid_at").nullable()
    val paidNote = text("paid_note").nullable()
    /** trx_id iPaymu (V86) — kunci masuk callback; NULL = dibayar manual. */
    val ipaymuTrxId = varchar("ipaymu_trx_id", 100).nullable()

    override val primaryKey = PrimaryKey(id)
}
