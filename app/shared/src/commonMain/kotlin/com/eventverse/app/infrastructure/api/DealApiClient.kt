package com.eventverse.app.infrastructure.api

import com.eventverse.app.domain.deal.Deal
import com.eventverse.app.domain.deal.DealStage
import com.eventverse.app.domain.deal.PurchaseOrder
import com.eventverse.app.shared.deal.DealCodec
import com.eventverse.app.shared.json.JsonParser
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Client for the Deals API. Uses [DealCodec] — the SAME codec the server encodes with,
 * matching [CrmApiClient]'s reasoning.
 */
class DealApiClient(
    private val httpClient: HttpClient = HttpClient(),
    private val baseUrl: String = "",
    private val tokenProvider: SessionTokenProvider = StoredSessionTokenProvider
) : DealRemoteDataSource {

    private fun resolveUrl(path: String): String =
        if (baseUrl.isNotBlank()) "${baseUrl.trimEnd('/')}$path" else path

    override suspend fun getDeals(tenantSlug: String): Result<List<Deal>> = runCatching {
        val response = httpClient.get(resolveUrl(DEALS_PATH)) {
            tenantRequest(tenantSlug, tokenProvider)
            accept(ContentType.Application.Json)
        }
        DealCodec.decodeDeals(response.requireBody("memuat daftar deal"), resolveTenant(tenantSlug))
    }

    override suspend fun getDealDetail(tenantSlug: String, dealId: String): Result<DealDetailResponse> = runCatching {
        val response = httpClient.get(resolveUrl("$DEALS_PATH/$dealId")) {
            tenantRequest(tenantSlug, tokenProvider)
            accept(ContentType.Application.Json)
        }
        val body = response.requireBody("memuat detail deal")
        val root = JsonParser.parseObject(body)
        val tenant = resolveTenant(tenantSlug)
        val deal = root.obj("deal")?.let { DealCodec.decodeDeal(it, tenant) }
            ?: error("Respons detail deal tidak valid")
        DealDetailResponse(
            deal = deal,
            contact = root.obj("contact")?.let { DealCodec.decodeContact(it, tenant) },
            purchaseOrders = root.objectArray("purchaseOrders").mapNotNull { DealCodec.decodePurchaseOrder(it, tenant) }
        )
    }

    override suspend fun updateStage(tenantSlug: String, dealId: String, stage: DealStage): Result<Deal> = runCatching {
        val response = httpClient.post(resolveUrl("$DEALS_PATH/$dealId/stage")) {
            tenantRequest(tenantSlug, tokenProvider)
            contentType(ContentType.Application.Json)
            setBody(DealCodec.encodeStageRequest(stage))
        }
        val body = response.requireBody("mengubah tahap deal")
        DealCodec.decodeDeal(JsonParser.parseObject(body), resolveTenant(tenantSlug))
            ?: error("Respons deal tidak valid")
    }

    override suspend fun attachManualPurchaseOrder(
        tenantSlug: String,
        dealId: String,
        request: DealCodec.AttachManualPoRequest
    ): Result<PurchaseOrder> = runCatching {
        val response = httpClient.post(resolveUrl("$DEALS_PATH/$dealId/purchase-orders")) {
            tenantRequest(tenantSlug, tokenProvider)
            contentType(ContentType.Application.Json)
            setBody(DealCodec.encodeAttachManualPoRequest(request))
        }
        val body = response.requireBody("menambahkan PO")
        DealCodec.decodePurchaseOrder(JsonParser.parseObject(body), resolveTenant(tenantSlug))
            ?: error("Respons PO tidak valid")
    }

    override suspend fun uploadPurchaseOrder(
        tenantSlug: String,
        dealId: String,
        poNumber: String,
        fileName: String,
        mimeType: String,
        bytes: ByteArray
    ): Result<PurchaseOrder> = runCatching {
        val today = kotlinx.datetime.Clock.System.now()
            .toLocalDateTime(TimeZone.currentSystemDefault()).date.toString()
        val response = httpClient.post(
            resolveUrl("$DEALS_PATH/$dealId/purchase-orders/upload")
        ) {
            tenantRequest(tenantSlug, tokenProvider)
            parameter("fileName", fileName)
            parameter("mimeType", mimeType)
            parameter("poNumber", poNumber)
            parameter("poDate", today)
            contentType(ContentType.Application.OctetStream)
            setBody(bytes)
        }
        val body = response.requireBody("mengunggah PO")
        DealCodec.decodePurchaseOrder(JsonParser.parseObject(body), resolveTenant(tenantSlug))
            ?: error("Respons PO tidak valid")
    }

    override suspend fun purchaseOrderDownloadUrl(
        tenantSlug: String,
        dealId: String,
        poId: String
    ): Result<String> = runCatching {
        val response = httpClient.get(resolveUrl("$DEALS_PATH/$dealId/purchase-orders/$poId/download")) {
            tenantRequest(tenantSlug, tokenProvider)
            accept(ContentType.Application.Json)
        }
        val body = response.requireBody("membuat tautan unduhan")
        JsonParser.parseObject(body).string("url")
            ?: error("Respons tautan unduhan tidak valid")
    }

    private fun resolveTenant(tenantSlug: String) =
        com.eventverse.app.domain.tenant.TenantId(tenantSlug)

    private suspend fun HttpResponse.requireBody(action: String): String {
        val body = bodyAsText()
        if (!status.isSuccess()) {
            error("Gagal $action (HTTP ${status.value}): $body")
        }
        return body
    }

    private companion object {
        const val DEALS_PATH = "/api/tenant/deals"
    }
}
