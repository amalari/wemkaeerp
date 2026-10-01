package com.eventverse.app.infrastructure

import com.eventverse.app.domain.builder.IpaymuCheckout
import com.eventverse.app.domain.builder.IpaymuTransactionCheck
import com.eventverse.app.domain.builder.IpaymuTransactionStatus
import com.eventverse.app.domain.builder.PaymentGateway
import com.eventverse.app.domain.builder.SubscriptionInvoice
import io.ktor.client.HttpClient
import io.ktor.client.engine.ProxyBuilder
import io.ktor.client.engine.apache5.Apache5
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.Url
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Implementasi [PaymentGateway] untuk iPaymu API v2 (L1 billing, TRD-PAY-001 FR-PAY-3).
 *
 * - **Signature** (rumus *request keluar* — satu-satunya yang terdokumentasi resmi iPaymu):
 *   HMAC-SHA256 atas `sha256hex(body).lowercase() + ':' + METHOD + ':' + VA`, kunci = API key.
 * - **Proxy kondisional** (FR-PAY-3.1): `IPAYMU_OUTBOUND_PROXY` terisi → Apache5 engine memakai
 *   HTTP proxy dengan `CONNECT` untuk HTTPS (jalur bridge TRD-PAY-001); kosong → langsung
 *   (server production ber-IP statis).
 * - **Pemetaan respons** (`trx_id`/`SessionId`, `url`, `Status`, `TransactionStatus`) wajib
 *   dicocokkan dengan dokumentasi iPaymu terbaru saat integrasi live — TRD §6 pertanyaan #2/#3.
 *   Field yang tidak ditemukan = error eksplisit, bukan nilai tebakan.
 */
class IpaymuClient(
    private val va: String,
    private val apiKey: String,
    private val baseUrl: String,
    private val notifyUrl: String,
    private val returnUrl: String,
    private val buyerEmail: String = "billing@wemakeerp.com",
    proxyUrl: String? = null
) : PaymentGateway {

    private val client = HttpClient(Apache5) {
        engine {
            proxyUrl?.let { proxy = ProxyBuilder.http(Url(it)) }
            followRedirects = false
        }
    }

    companion object {
        /**
         * Bangun client dari environment via [EnvLoader] (satu-satunya pintu env server).
         * `null` bila kredensial belum diisi → fitur checkout off (route menjawab 503),
         * callback route tetap terpasang supaya handler-nya bisa diuji tanpa kredensial.
         */
        fun fromEnv(): IpaymuClient? {
            val va = EnvLoader.get("IPAYMU_VA")
            val apiKey = EnvLoader.get("IPAYMU_API_KEY")
            val baseUrl = EnvLoader.get("IPAYMU_BASE_URL")
            if (va.isBlank() || apiKey.isBlank() || baseUrl.isBlank()) return null
            return IpaymuClient(
                va = va,
                apiKey = apiKey,
                baseUrl = baseUrl.trimEnd('/'),
                notifyUrl = EnvLoader.get("IPAYMU_NOTIFY_URL"),
                returnUrl = EnvLoader.get("IPAYMU_RETURN_URL", "http://localhost:3001/builder/billing"),
                proxyUrl = EnvLoader.get("IPAYMU_OUTBOUND_PROXY").ifBlank { null }
            )
        }
    }

    // ------------------------------------------------------------------ create

    override suspend fun createCheckout(invoice: SubscriptionInvoice): Result<IpaymuCheckout> = runCatching {
        val body = buildString {
            append("{")
            // product/qty/price/description = array WAJIB (docs Redirect Payment, 2026-10-01).
            append("\"product\":[\"Langganan WeMade ERP\"],")
            append("\"qty\":[1],")
            append("\"price\":[${invoice.totalIdr.amount}],")
            append("\"description\":[\"Invoice ${invoice.number} periode ${invoice.period} (tenant ${invoice.tenantId.value})\"],")
            append("\"returnUrl\":\"$returnUrl\",")
            append("\"notifyUrl\":\"$notifyUrl\",")
            append("\"cancelUrl\":\"$returnUrl\",")
            append("\"referenceId\":\"${invoice.id.value}\",")
            append("\"buyerName\":\"${invoice.tenantId.value}\",")
            append("\"buyerEmail\":\"$buyerEmail\"")
            append("}")
        }

        val response = signedPost("/payment", body)
        require(response.status == HttpStatusCode.OK) {
            "iPaymu /payment gagal (HTTP ${response.status.value}): ${response.bodyText().take(300)}"
        }
        // Respons sukses: {"Status":200,"Message":"success","Data":{"SessionID":"...","Url":"..."}}.
        // Baca di dalam objek Data; casing field bervariasi antar halaman docs → case-insensitive.
        val data = dataObject(response.bodyText())
        val trxId = extractString(data, "SessionID")
            ?: error("Respons iPaymu /payment tanpa Data.SessionID: ${response.bodyText().take(300)}")
        val url = extractString(data, "Url")
            ?: error("Respons iPaymu /payment tanpa Data.Url: ${response.bodyText().take(300)}")
        IpaymuCheckout(trxId = trxId, paymentUrl = url)
    }

    // ------------------------------------------------------------------ status

    override suspend fun checkStatus(transactionId: String): Result<IpaymuTransactionCheck> = runCatching {
        // Endpoint resmi: POST /api/v2/transaction, body {"transactionId":"<numeric>"}.
        val body = """{"transactionId":"$transactionId"}"""
        val response = signedPost("/transaction", body)
        require(response.status == HttpStatusCode.OK) {
            "iPaymu /transaction gagal (HTTP ${response.status.value}): ${response.bodyText().take(300)}"
        }
        // Data.Status berupa INT kode (1 Success, 6 Success-Unsettled, dst) — parser ketat,
        // dan Data.SessionId wajib ada untuk diverifikasi domain terhadap sid tersimpan.
        val data = dataObject(response.bodyText())
        val statusCode = extractInt(data, "Status")
            ?: error("Respons iPaymu /transaction tanpa Data.Status: ${response.bodyText().take(300)}")
        val status = IpaymuTransactionStatus.fromApi(statusCode.toString())
        val sessionId = extractString(data, "SessionId")
            ?: error("Respons iPaymu /transaction tanpa Data.SessionId: ${response.bodyText().take(300)}")
        IpaymuTransactionCheck(status = status, sessionId = sessionId)
    }

    // ------------------------------------------------------------------ http

    private suspend fun signedPost(path: String, body: String): HttpResponse =
        client.post("$baseUrl$path") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Accept, ContentType.Application.Json.toString())
            header("va", va)
            // Sample resmi Java memakai epoch millis — pakai itu, bukan epoch detik.
            header("timestamp", System.currentTimeMillis().toString())
            header("signature", signature(body, "POST"))
            setBody(body)
        }

    private fun signature(body: String, method: String): String {
        val bodyHash = MessageDigest.getInstance("SHA-256")
            .digest(body.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
            .lowercase()
        // Rumus resmi (docs Pembuatan Signature + sample Java): METHOD:VA:sha256hex(body):APIKey,
        // lalu HMAC-SHA256(StringToSign, APIKey).
        val stringToSign = "$method:$va:$bodyHash:$apiKey"
        val mac = Mac.getInstance("HmacSHA256")
        // SecretKeySpec mengimplementasikan Key *dan* AlgorithmParameterSpec — resolusi overload
        // init(...) ambigu untuk Kotlin; pilih eksplisit lewat variabel bertipe java.security.Key.
        val key: java.security.Key = SecretKeySpec(apiKey.toByteArray(Charsets.UTF_8), "HmacSHA256")
        mac.init(key)
        return mac.doFinal(stringToSign.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }
}

private suspend fun HttpResponse.bodyText(): String =
    runCatching { bodyAsText() }.getOrDefault("")

/**
 * Ambil isi objek `"Data": { … }` (objek datar di semua respons iPaymu v2) — supaya field
 * level-atasan seperti `Status: 200` tidak ikut tertangkap saat mencari `Data.Status`.
 */
private fun dataObject(json: String): String =
    Regex("(?i)\"Data\"\\s*:\\s*\\{([^}]*)\\}").find(json)?.groupValues?.get(1) ?: ""

/** Casing field bervariasi antar halaman docs (SessionID/SessionId, Url/url) → case-insensitive. */
private fun extractString(json: String, field: String): String? =
    Regex("(?i)\"$field\"\\s*:\\s*\"([^\"]+)\"").find(json)?.groupValues?.get(1)

private fun extractInt(json: String, field: String): Int? =
    Regex("(?i)\"$field\"\\s*:\\s*(-?\\d+)").find(json)?.groupValues?.get(1)?.toIntOrNull()
