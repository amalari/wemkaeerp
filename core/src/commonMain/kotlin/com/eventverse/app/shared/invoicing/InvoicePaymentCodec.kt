package com.eventverse.app.shared.invoicing

import com.eventverse.app.domain.common.Money
import com.eventverse.app.domain.invoicing.InvoiceId
import com.eventverse.app.domain.invoicing.InvoicePayment
import com.eventverse.app.domain.invoicing.InvoicePaymentId
import com.eventverse.app.shared.common.DateTimeCodec
import com.eventverse.app.shared.common.MeasureCodec
import com.eventverse.app.shared.json.*
import kotlinx.datetime.Instant

object InvoicePaymentCodec {

    fun encode(payment: InvoicePayment): JsonValue.Obj = jsonObjectOf(
        "id" to jsonOf(payment.id.value),
        "invoiceId" to jsonOf(payment.invoiceId.value),
        "amount" to MeasureCodec.encodeMoney(payment.amount),
        "paidAt" to jsonOf(payment.paidAt.toString()),
        "method" to jsonOf(payment.method),
        "reference" to jsonOf(payment.reference),
        "note" to jsonOf(payment.note),
        "recordedBy" to jsonOf(payment.recordedBy)
    )

    fun decode(obj: JsonValue.Obj): InvoicePayment = InvoicePayment(
        id = InvoicePaymentId(obj.string("id") ?: ""),
        invoiceId = InvoiceId(obj.string("invoiceId") ?: ""),
        amount = MeasureCodec.decodeMoney(obj.obj("amount")),
        paidAt = DateTimeCodec.parseInstantOrFallback(obj.string("paidAt"), Instant.fromEpochMilliseconds(0)),
        method = obj.string("method") ?: "TRANSFER",
        reference = obj.string("reference") ?: "",
        note = obj.string("note") ?: "",
        recordedBy = obj.string("recordedBy") ?: "system"
    )
}
