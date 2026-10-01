package com.eventverse.app.routes

import com.eventverse.app.domain.builder.HandleIpaymuNotificationUseCase
import com.eventverse.app.domain.builder.IpaymuNotification
import io.ktor.http.ContentType
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.contentType
import io.ktor.server.request.header
import io.ktor.server.request.receiveParameters
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Webhook pembayaran iPaymu (L1, FR-PAY-3.3, dicocokkan dengan docs Callback iPaymu 2026-10-01).
 * **Publik tanpa JWT** — aktornya mesin iPaymu — keamanan berlapis:
 *
 * 1. **X-Signature** divalidasi bila [callbackSecret] (Nomor VA) tersedia: normalisasi tipe
 *    (trx_id/status_code/transaction_status_code/paid_off → int; is_escrow → bool),
 *    `additional_info` default `[]`, sort key A-Z, `JSON.stringify`, escape `/`→`\/`,
 *    HMAC-SHA256 dengan secret VA. Mismatch → **tidak diproses**.
 * 2. Urutan verifikasi domain di [HandleIpaymuNotificationUseCase]: nominal vs `totalIdr`,
 *    re-check status per `trx_id` + cocokkan `Data.SessionId` dengan `sid` tersimpan.
 *
 * Docs wajib **selalu membalas HTTP 200** — non-200 memicu retry iPaymu tanpa akhir;
 * penolakan disampaikan lewat body (`accepted:false`) — kecuali error internal (500) yang
 * memang layak di-retry.
 */
fun Route.ipaymuCallbackRoutes(
    handle: HandleIpaymuNotificationUseCase,
    callbackSecret: String? = null
) {
    post("/api/payment/ipaymu/notify") {
        val fields: Map<String, String> = call.receiveFields()

        // Lapis 1 — asal pesan (X-Signature), bila secret terkonfigurasi.
        val signature = call.request.header("X-Signature")
        if (callbackSecret != null && (signature.isNullOrBlank() ||
                    !verifyCallbackSignature(fields, signature, callbackSecret))
        ) {
            call.accepted(processed = false, reason = "X-Signature tidak valid / hilang")
            return@post
        }

        // Lapis 2 — isi pesan (parser ketat + verifikasi domain).
        val notification = runCatching { IpaymuNotification.fromFields(fields) }.fold(
            onSuccess = { it },
            onFailure = { e ->
                call.accepted(processed = false, reason = e.message)
                return@post
            }
        )

        val outcome = runCatching { handle(notification).getOrThrow() }
        outcome.fold(
            onSuccess = { invoice ->
                val paid = invoice.status == com.eventverse.app.domain.builder.SubscriptionInvoiceStatus.PAID
                call.accepted(processed = true, paid = paid)
            },
            onFailure = { e ->
                // Penolakan verifikasi domain = keputusan final → tetap 200 agar iPaymu berhenti retry.
                call.accepted(processed = false, reason = e.message)
            }
        )
    }
}

/** Respons tunggal: selalu HTTP 200 (docs: non-200 memicu retry tanpa akhir). */
private suspend fun ApplicationCall.accepted(processed: Boolean, paid: Boolean = false, reason: String? = null) {
    val escaped = reason?.replace("\\", "\\\\")?.replace("\"", "'")
    respondText(
        buildString {
            append("{\"accepted\":$processed,\"paid\":$paid")
            if (escaped != null) append(",\"reason\":\"$escaped\"")
            append("}")
        },
        ContentType.Application.Json
    )
}

/** Payload callback sebagai peta field datar — dari form-urlencoded atau JSON flat. */
private suspend fun ApplicationCall.receiveFields(): Map<String, String> {
    val contentType = request.contentType()
    return when {
        contentType.match(ContentType.Application.FormUrlEncoded) ->
            receiveParameters().entries().associate { (k, v) -> k to (v.firstOrNull() ?: "") }
        else -> flatJsonFields(receiveText())
    }
}

/**
 * Ekstraktor JSON **datar** (`"k":"v"` / `"k":123`) tanpa dependency serializer — payload
 * callback iPaymu datar; JSON bersarang tidak didukung dan akan ditolak sebagai tanpa field.
 */
private fun flatJsonFields(text: String): Map<String, String> =
    Regex("\"([^\"]+)\"\\s*:\\s*(?:\"((?:[^\"\\\\]|\\\\.)*)\"|(-?\\d+(?:\\.\\d+)?))")
        .findAll(text)
        .associate { m ->
            val key = m.groupValues[1]
            val value = m.groupValues[2].ifEmpty { m.groupValues[3] }
            key to value.replace("\\\"", "\"").replace("\\\\", "\\")
        }

/**
 * Validasi X-Signature sesuai docs Callback iPaymu (2026-10-01), secret = **Nomor VA**:
 * normalisasi tipe → sort key A-Z → JSON.stringify → escape `/` → HMAC-SHA256(json, VA),
 * dibandingkan case-insensitive. Field tak relevan bagi domain (merchant, url, dst) tetap
 * ikut hash sesuai ketentuan docs.
 */
private fun verifyCallbackSignature(
    fields: Map<String, String>,
    signature: String,
    secret: String
): Boolean {
    val normalized = LinkedHashMap<String, Any>()
    fields.forEach { (k, rawValue) ->
        val key = k.trim()
        normalized[key] = when (key) {
            "trx_id", "status_code", "transaction_status_code", "paid_off" ->
                rawValue.toLongOrNull() ?: rawValue
            "is_escrow" -> rawValue == "1" || rawValue.equals("true", ignoreCase = true)
            "additional_info" -> if (rawValue.isBlank() || rawValue.trim() == "[]") emptyList<Any>() else rawValue
            else -> rawValue
        }
    }
    normalized.putIfAbsent("additional_info", emptyList<Any>())

    val json = normalized.toSortedMap(compareBy { it }).entries.joinToString(",", "{", "}") { (k, v) ->
        when (v) {
            is Number -> "\"$k\":$v"
            is Boolean -> "\"$k\":$v"
            is List<*> -> "\"$k\":[]"
            else -> "\"$k\":\"${jsonEscape(v.toString())}\""
        }
    }.replace("/", "\\/")

    val mac = Mac.getInstance("HmacSHA256")
    val key: java.security.Key = SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256")
    mac.init(key)
    val computed = mac.doFinal(json.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
    return computed.equals(signature.trim(), ignoreCase = true)
}

/** Escaping string ala JSON.stringify: backslash, quote, dan karakter kontrol dasar. */
private fun jsonEscape(value: String): String = buildString {
    value.forEach { c ->
        when (c) {
            '\\' -> append("\\\\")
            '"' -> append("\\\"")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> if (c < ' ') append("\\u%04x".format(c.code)) else append(c)
        }
    }
}

