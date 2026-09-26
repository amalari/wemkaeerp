package com.eventverse.app.infrastructure.api

import com.eventverse.app.domain.invoicing.*
import com.eventverse.app.domain.invoicing.template.InvoiceTemplate
import com.eventverse.app.domain.invoicing.usecases.CreateInvoiceCommand
import com.eventverse.app.domain.invoicing.usecases.RecordInvoicePaymentCommand
import com.eventverse.app.domain.invoicing.usecases.UpdateInvoiceDraftCommand
import com.eventverse.app.shared.common.MeasureCodec
import com.eventverse.app.shared.invoicing.InvoiceCodec
import com.eventverse.app.shared.invoicing.InvoicePaymentCodec
import com.eventverse.app.shared.invoicing.InvoiceTemplateCodec
import com.eventverse.app.shared.json.*
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*

interface InvoicingRemoteDataSource {
    suspend fun getInvoices(
        tenantSlug: String,
        status: InvoiceStatus? = null,
        kind: InvoiceKind? = null,
        searchQuery: String? = null,
        page: Int = 1,
        pageSize: Int = 20
    ): Result<InvoicePage>

    suspend fun getInvoice(tenantSlug: String, id: InvoiceId): Result<Invoice>
    suspend fun createInvoice(tenantSlug: String, command: CreateInvoiceCommand): Result<Invoice>
    suspend fun updateDraft(tenantSlug: String, command: UpdateInvoiceDraftCommand): Result<Invoice>
    suspend fun issueInvoice(tenantSlug: String, id: InvoiceId): Result<Invoice>
    suspend fun voidInvoice(tenantSlug: String, id: InvoiceId, reason: String): Result<Invoice>
    suspend fun createSettlement(tenantSlug: String, downPaymentInvoiceId: InvoiceId): Result<Invoice>
    suspend fun prefillFromSampling(tenantSlug: String, samplingId: String): Result<Invoice>

    suspend fun getPayments(tenantSlug: String, invoiceId: InvoiceId): Result<List<InvoicePayment>>
    suspend fun recordPayment(tenantSlug: String, command: RecordInvoicePaymentCommand): Result<InvoicePayment>

    suspend fun getTemplates(tenantSlug: String, includeArchived: Boolean = false): Result<List<InvoiceTemplate>>
    suspend fun getDefaultTemplate(tenantSlug: String): Result<InvoiceTemplate>
    suspend fun getTemplate(tenantSlug: String, id: InvoiceTemplateId): Result<InvoiceTemplate>
    suspend fun saveTemplate(tenantSlug: String, template: InvoiceTemplate): Result<InvoiceTemplate>
    suspend fun setDefaultTemplate(tenantSlug: String, id: InvoiceTemplateId): Result<Unit>
    suspend fun archiveTemplate(tenantSlug: String, id: InvoiceTemplateId): Result<Unit>

    suspend fun getIssuerProfile(tenantSlug: String): Result<IssuerProfile>
    suspend fun saveIssuerProfile(tenantSlug: String, profile: IssuerProfile): Result<IssuerProfile>

    fun getPdfUrl(tenantSlug: String, invoiceId: InvoiceId): String
    suspend fun downloadPdfBytes(tenantSlug: String, invoiceId: InvoiceId): Result<ByteArray>
}

class InvoicingApiClient(
    private val httpClient: HttpClient = HttpClient(),
    private val baseUrl: String = "",
    private val tokenProvider: SessionTokenProvider = StoredSessionTokenProvider
) : InvoicingRemoteDataSource {

    private fun resolveUrl(path: String): String =
        if (baseUrl.isNotBlank()) "${baseUrl.trimEnd('/')}$path" else path

    override suspend fun getInvoices(
        tenantSlug: String,
        status: InvoiceStatus?,
        kind: InvoiceKind?,
        searchQuery: String?,
        page: Int,
        pageSize: Int
    ): Result<InvoicePage> = runCatching {
        val params = mutableListOf<String>()
        if (status != null) params.add("status=${status.name}")
        if (kind != null) params.add("kind=${kind.name}")
        if (!searchQuery.isNullOrBlank()) params.add("q=${searchQuery.encodeURLParameter()}")
        params.add("page=$page")
        params.add("pageSize=$pageSize")

        val queryStr = params.joinToString("&")
        val path = "$INVOICING_PATH?$queryStr"

        val response = httpClient.get(resolveUrl(path)) {
            tenantRequest(tenantSlug, tokenProvider)
            accept(ContentType.Application.Json)
        }
        val body = response.requireBody("memuat daftar invoice")
        val parsed = JsonParser.parseObject(body)
        InvoiceCodec.decodePage(parsed)
    }

    override suspend fun getInvoice(tenantSlug: String, id: InvoiceId): Result<Invoice> = runCatching {
        val response = httpClient.get(resolveUrl("$INVOICING_PATH/${id.value}")) {
            tenantRequest(tenantSlug, tokenProvider)
            accept(ContentType.Application.Json)
        }
        val body = response.requireBody("memuat detail invoice")
        InvoiceCodec.decode(JsonParser.parseObject(body))
    }

    override suspend fun createInvoice(tenantSlug: String, command: CreateInvoiceCommand): Result<Invoice> = runCatching {
        val payload = InvoiceCodec.encodeCreateCommand(command).encode()
        val response = httpClient.post(resolveUrl(INVOICING_PATH)) {
            tenantRequest(tenantSlug, tokenProvider)
            contentType(ContentType.Application.Json)
            setBody(payload)
        }
        val body = response.requireBody("membuat draft invoice")
        InvoiceCodec.decode(JsonParser.parseObject(body))
    }

    override suspend fun updateDraft(tenantSlug: String, command: UpdateInvoiceDraftCommand): Result<Invoice> = runCatching {
        val payload = InvoiceCodec.encodeUpdateCommand(command).encode()
        val response = httpClient.put(resolveUrl("$INVOICING_PATH/${command.invoiceId.value}")) {
            tenantRequest(tenantSlug, tokenProvider)
            contentType(ContentType.Application.Json)
            setBody(payload)
        }
        val body = response.requireBody("memperbarui draft invoice")
        InvoiceCodec.decode(JsonParser.parseObject(body))
    }

    override suspend fun issueInvoice(tenantSlug: String, id: InvoiceId): Result<Invoice> = runCatching {
        val response = httpClient.post(resolveUrl("$INVOICING_PATH/${id.value}/issue")) {
            tenantRequest(tenantSlug, tokenProvider)
            accept(ContentType.Application.Json)
        }
        val body = response.requireBody("menerbitkan invoice (issue)")
        InvoiceCodec.decode(JsonParser.parseObject(body))
    }

    override suspend fun voidInvoice(tenantSlug: String, id: InvoiceId, reason: String): Result<Invoice> = runCatching {
        val payload = jsonObjectOf("reason" to jsonOf(reason)).encode()
        val response = httpClient.post(resolveUrl("$INVOICING_PATH/${id.value}/void")) {
            tenantRequest(tenantSlug, tokenProvider)
            contentType(ContentType.Application.Json)
            setBody(payload)
        }
        val body = response.requireBody("membatalkan invoice (void)")
        InvoiceCodec.decode(JsonParser.parseObject(body))
    }

    override suspend fun createSettlement(tenantSlug: String, downPaymentInvoiceId: InvoiceId): Result<Invoice> = runCatching {
        val response = httpClient.post(resolveUrl("$INVOICING_PATH/${downPaymentInvoiceId.value}/create-settlement")) {
            tenantRequest(tenantSlug, tokenProvider)
            accept(ContentType.Application.Json)
        }
        val body = response.requireBody("membuat invoice pelunasan dari DP")
        InvoiceCodec.decode(JsonParser.parseObject(body))
    }

    override suspend fun prefillFromSampling(tenantSlug: String, samplingId: String): Result<Invoice> = runCatching {
        val response = httpClient.post(resolveUrl("$INVOICING_PATH/prefill/sampling/$samplingId")) {
            tenantRequest(tenantSlug, tokenProvider)
            accept(ContentType.Application.Json)
        }
        val body = response.requireBody("membuat invoice dari sampling order")
        InvoiceCodec.decode(JsonParser.parseObject(body))
    }

    override suspend fun getPayments(tenantSlug: String, invoiceId: InvoiceId): Result<List<InvoicePayment>> = runCatching {
        val response = httpClient.get(resolveUrl("$INVOICING_PATH/${invoiceId.value}/payments")) {
            tenantRequest(tenantSlug, tokenProvider)
            accept(ContentType.Application.Json)
        }
        val body = response.requireBody("memuat riwayat pembayaran")
        val parsed = JsonParser.parse(body) as? JsonValue.Arr ?: return@runCatching emptyList()
        parsed.items.filterIsInstance<JsonValue.Obj>().map { InvoicePaymentCodec.decode(it) }
    }

    override suspend fun recordPayment(tenantSlug: String, command: RecordInvoicePaymentCommand): Result<InvoicePayment> = runCatching {
        val payload = jsonObjectOf(
            "amount" to MeasureCodec.encodeMoney(command.amount),
            "method" to jsonOf(command.method),
            "reference" to jsonOf(command.reference),
            "note" to jsonOf(command.note)
        ).encode()

        val response = httpClient.post(resolveUrl("$INVOICING_PATH/${command.invoiceId.value}/payments")) {
            tenantRequest(tenantSlug, tokenProvider)
            contentType(ContentType.Application.Json)
            setBody(payload)
        }
        val body = response.requireBody("mencatat pembayaran invoice")
        InvoicePaymentCodec.decode(JsonParser.parseObject(body))
    }

    override suspend fun getTemplates(tenantSlug: String, includeArchived: Boolean): Result<List<InvoiceTemplate>> = runCatching {
        val path = "$INVOICING_PATH/templates?includeArchived=$includeArchived"
        val response = httpClient.get(resolveUrl(path)) {
            tenantRequest(tenantSlug, tokenProvider)
            accept(ContentType.Application.Json)
        }
        val body = response.requireBody("memuat daftar template invoice")
        val parsed = JsonParser.parse(body) as? JsonValue.Arr ?: return@runCatching emptyList()
        parsed.items.filterIsInstance<JsonValue.Obj>().map { InvoiceTemplateCodec.decode(it) }
    }

    override suspend fun getDefaultTemplate(tenantSlug: String): Result<InvoiceTemplate> = runCatching {
        val response = httpClient.get(resolveUrl("$INVOICING_PATH/templates/default")) {
            tenantRequest(tenantSlug, tokenProvider)
            accept(ContentType.Application.Json)
        }
        val body = response.requireBody("memuat template default")
        InvoiceTemplateCodec.decode(JsonParser.parseObject(body))
    }

    override suspend fun getTemplate(tenantSlug: String, id: InvoiceTemplateId): Result<InvoiceTemplate> = runCatching {
        val response = httpClient.get(resolveUrl("$INVOICING_PATH/templates/${id.value}")) {
            tenantRequest(tenantSlug, tokenProvider)
            accept(ContentType.Application.Json)
        }
        val body = response.requireBody("memuat detail template invoice")
        InvoiceTemplateCodec.decode(JsonParser.parseObject(body))
    }

    override suspend fun saveTemplate(tenantSlug: String, template: InvoiceTemplate): Result<InvoiceTemplate> = runCatching {
        val payload = InvoiceTemplateCodec.encode(template).encode()
        val response = httpClient.post(resolveUrl("$INVOICING_PATH/templates")) {
            tenantRequest(tenantSlug, tokenProvider)
            contentType(ContentType.Application.Json)
            setBody(payload)
        }
        val body = response.requireBody("menyimpan template invoice")
        InvoiceTemplateCodec.decode(JsonParser.parseObject(body))
    }

    override suspend fun setDefaultTemplate(tenantSlug: String, id: InvoiceTemplateId): Result<Unit> = runCatching {
        val response = httpClient.post(resolveUrl("$INVOICING_PATH/templates/${id.value}/set-default")) {
            tenantRequest(tenantSlug, tokenProvider)
        }
        response.requireBody("menjadikan template default")
        Unit
    }

    override suspend fun archiveTemplate(tenantSlug: String, id: InvoiceTemplateId): Result<Unit> = runCatching {
        val response = httpClient.delete(resolveUrl("$INVOICING_PATH/templates/${id.value}")) {
            tenantRequest(tenantSlug, tokenProvider)
        }
        response.requireBody("mengarsipkan template invoice")
        Unit
    }

    override suspend fun getIssuerProfile(tenantSlug: String): Result<IssuerProfile> = runCatching {
        val response = httpClient.get(resolveUrl("$INVOICING_PATH/issuer-profile")) {
            tenantRequest(tenantSlug, tokenProvider)
            accept(ContentType.Application.Json)
        }
        val body = response.requireBody("memuat profil penerbit")
        InvoiceCodec.decodeIssuer(JsonParser.parseObject(body))
    }

    override suspend fun saveIssuerProfile(tenantSlug: String, profile: IssuerProfile): Result<IssuerProfile> = runCatching {
        val payload = InvoiceCodec.encodeIssuer(profile).encode()
        val response = httpClient.put(resolveUrl("$INVOICING_PATH/issuer-profile")) {
            tenantRequest(tenantSlug, tokenProvider)
            contentType(ContentType.Application.Json)
            setBody(payload)
        }
        val body = response.requireBody("menyimpan profil penerbit")
        InvoiceCodec.decodeIssuer(JsonParser.parseObject(body))
    }

    override fun getPdfUrl(tenantSlug: String, invoiceId: InvoiceId): String =
        resolveUrl("$INVOICING_PATH/${invoiceId.value}/pdf")

    override suspend fun downloadPdfBytes(tenantSlug: String, invoiceId: InvoiceId): Result<ByteArray> = runCatching {
        val response = httpClient.get(resolveUrl("$INVOICING_PATH/${invoiceId.value}/pdf")) {
            tenantRequest(tenantSlug, tokenProvider)
            accept(ContentType.Application.Pdf)
        }
        if (!response.status.isSuccess()) {
            error("Gagal mengunduh dokumen PDF (HTTP ${response.status.value})")
        }
        response.bodyAsBytes()
    }

    private suspend fun HttpResponse.requireBody(action: String): String {
        val body = bodyAsText()
        if (!status.isSuccess()) {
            error("Gagal $action (HTTP ${status.value}): $body")
        }
        return body
    }

    private companion object {
        const val INVOICING_PATH = "/api/tenant/invoicing"
    }
}
