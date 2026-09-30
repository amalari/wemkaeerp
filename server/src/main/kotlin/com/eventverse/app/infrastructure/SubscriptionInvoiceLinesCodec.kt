package com.eventverse.app.infrastructure

import com.eventverse.app.domain.builder.SubscriptionInvoiceLine
import com.eventverse.app.domain.moduledev.MoneyIdr
import com.eventverse.app.shared.json.JsonParser
import com.eventverse.app.shared.json.JsonValue

/**
 * Codec baris invoice langganan (`builder.subscription_invoices.lines_json`).
 *
 * Satu baris per field, tanpa percabangan — pola yang sama dengan codec lain di repo ini. Parser
 * **menolak**, bukan membuang: baris yang tidak terbaca menggagalkan pembacaan invoice. Baris harga
 * yang hilang tanpa suara akan tampak sebagai tagihan yang lebih murah dari seharusnya — kesalahan
 * yang menguntungkan pelanggan dan merugikan platform, jadi justru paling mudah lolos review.
 */
object SubscriptionInvoiceLinesCodec {

    fun toJson(lines: List<SubscriptionInvoiceLine>): String =
        lines.joinToString(prefix = "[", postfix = "]") { line ->
            "{\"moduleId\":\"${line.moduleId}\",\"displayName\":\"${escape(line.displayName)}\"," +
                "\"monthlyPrice\":${line.monthlyPrice.amount},\"kind\":\"${line.kind}\"}"
        }

    fun fromJson(raw: String): List<SubscriptionInvoiceLine> {
        val array = JsonParser.parseArray(raw)
        return array.map { item ->
            val obj = item as? JsonValue.Obj
                ?: error("Baris invoice bukan objek JSON: ${raw.take(80)}")
            val moduleId = (obj.get("moduleId") as? JsonValue.Str)?.value
                ?: error("Baris invoice tanpa moduleId: ${raw.take(80)}")
            val displayName = (obj.get("displayName") as? JsonValue.Str)?.value ?: moduleId
            val price = (obj.get("monthlyPrice") as? JsonValue.Num)?.asLong
                ?: error("Baris invoice ${moduleId} tanpa harga yang terbaca")
            val kind = (obj.get("kind") as? JsonValue.Str)?.value ?: "SUBSCRIPTION"
            SubscriptionInvoiceLine(moduleId, displayName, MoneyIdr(price), kind)
        }
    }

    /**
     * Escape kutip ganda dengan `\"` — bentuk yang diterima parser JSON repo ini. Versi pertama
     * memakai `\'` (kebiasaan SQL) dan langsung ditolak `JsonParser`; nama modul yang mengandung
     * tanda kutip adalah satu-satunya yang selama ini menyembunyikannya.
     */
    private fun escape(raw: String) = raw.replace("\\", "\\\\").replace("\"", "\\\"")
}
