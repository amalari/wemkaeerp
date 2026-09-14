package com.eventverse.app.infrastructure.api

import com.eventverse.app.domain.contracts.BomLine
import com.eventverse.app.domain.contracts.LaborOperation
import com.eventverse.app.domain.contracts.SizeYieldFactor
import com.eventverse.app.domain.pipeline.StockOwnershipSemantics
import com.eventverse.app.domain.techpack.*
import com.eventverse.app.shared.contracts.TechPackAndYieldDataCodec
import com.eventverse.app.shared.json.*
import com.eventverse.app.shared.techpack.BomCostPreviewCodec
import com.eventverse.app.shared.techpack.TechPackCodec
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*

interface TechPackRemoteDataSource {
    suspend fun searchTechPacks(tenantSlug: String, query: TechPackQuery): Result<TechPackPage>
    suspend fun getTechPackDetail(tenantSlug: String, id: String): Result<TechPack>
    suspend fun getVersions(tenantSlug: String, styleCode: String): Result<List<TechPack>>
    suspend fun getLatestReleased(tenantSlug: String, styleCode: String): Result<TechPack?>
    suspend fun createBlank(tenantSlug: String, styleName: String, styleCode: String? = null, clientName: String = ""): Result<TechPack>
    suspend fun createFromSampling(tenantSlug: String, samplingOrderId: String, styleCode: String? = null, defaultOwnership: StockOwnershipSemantics? = null, defaultWastePercent: Double? = null): Result<TechPack>
    suspend fun updateBom(tenantSlug: String, id: String, lines: List<BomLine>): Result<TechPack>
    suspend fun updateLabor(tenantSlug: String, id: String, operations: List<LaborOperation>): Result<TechPack>
    suspend fun updateSizeYield(tenantSlug: String, id: String, factors: List<SizeYieldFactor>): Result<TechPack>
    suspend fun resolveMaterials(tenantSlug: String, id: String): Result<TechPack>
    suspend fun release(tenantSlug: String, id: String): Result<TechPack>
    suspend fun revise(tenantSlug: String, id: String): Result<TechPack>
    suspend fun archive(tenantSlug: String, id: String): Result<Boolean>
    suspend fun previewCost(tenantSlug: String, id: String, orderQuantity: Long = 1L): Result<BomCostPreview>
}

class TechPackApiClient(
    private val httpClient: HttpClient = HttpClient(),
    private val baseUrl: String = "",
    private val tokenProvider: SessionTokenProvider = StoredSessionTokenProvider
) : TechPackRemoteDataSource {

    private fun resolveUrl(path: String): String =
        if (baseUrl.isNotBlank()) "${baseUrl.trimEnd('/')}$path" else path

    override suspend fun searchTechPacks(tenantSlug: String, query: TechPackQuery): Result<TechPackPage> = runCatching {
        val qText = query.queryText
        val status = query.status
        val styleCode = query.styleCode

        val params = buildList {
            if (!qText.isNullOrBlank()) add("q=${qText.encodeURLParameter()}")
            if (status != null) add("status=${status.name}")
            if (styleCode != null) add("styleCode=${styleCode.value.encodeURLParameter()}")
            if (query.includeArchived) add("includeArchived=true")
            if (!query.latestVersionOnly) add("latestVersionOnly=false")
            add("page=${query.page}")
            add("pageSize=${query.pageSize}")
        }.joinToString("&")

        val path = if (params.isNotEmpty()) "$BASE_PATH?$params" else BASE_PATH
        val response = httpClient.get(resolveUrl(path)) {
            tenantRequest(tenantSlug, tokenProvider)
            accept(ContentType.Application.Json)
        }
        val body = response.requireBody("memuat daftar Tech Pack")
        val parsed = JsonParser.parse(body) as? JsonValue.Obj ?: error("Respons daftar Tech Pack tidak valid")
        TechPackCodec.decodePage(parsed)
    }

    override suspend fun getTechPackDetail(tenantSlug: String, id: String): Result<TechPack> = runCatching {
        val response = httpClient.get(resolveUrl("$BASE_PATH/$id")) {
            tenantRequest(tenantSlug, tokenProvider)
            accept(ContentType.Application.Json)
        }
        val body = response.requireBody("memuat detail Tech Pack")
        val parsed = JsonParser.parse(body) as? JsonValue.Obj ?: error("Respons detail Tech Pack tidak valid")
        TechPackCodec.decode(parsed)
    }

    override suspend fun getVersions(tenantSlug: String, styleCode: String): Result<List<TechPack>> = runCatching {
        val response = httpClient.get(resolveUrl("$BASE_PATH/by-style/${styleCode.encodeURLParameter()}/versions")) {
            tenantRequest(tenantSlug, tokenProvider)
            accept(ContentType.Application.Json)
        }
        val body = response.requireBody("memuat versi Tech Pack")
        val parsed = JsonParser.parse(body) as? JsonValue.Arr ?: return@runCatching emptyList()
        parsed.items.filterIsInstance<JsonValue.Obj>().map(TechPackCodec::decode)
    }

    override suspend fun getLatestReleased(tenantSlug: String, styleCode: String): Result<TechPack?> = runCatching {
        val response = httpClient.get(resolveUrl("$BASE_PATH/by-style/${styleCode.encodeURLParameter()}/latest-released")) {
            tenantRequest(tenantSlug, tokenProvider)
            accept(ContentType.Application.Json)
        }
        if (response.status == HttpStatusCode.NotFound) return@runCatching null
        val body = response.requireBody("memuat versi rilis Tech Pack")
        val parsed = JsonParser.parse(body) as? JsonValue.Obj ?: error("Respons rilis Tech Pack tidak valid")
        TechPackCodec.decode(parsed)
    }

    override suspend fun createBlank(
        tenantSlug: String,
        styleName: String,
        styleCode: String?,
        clientName: String
    ): Result<TechPack> = runCatching {
        val payload = jsonObjectOf(
            "styleName" to jsonOf(styleName),
            "styleCode" to jsonOf(styleCode),
            "clientName" to jsonOf(clientName)
        ).encode()

        val response = httpClient.post(resolveUrl("$BASE_PATH/blank")) {
            tenantRequest(tenantSlug, tokenProvider)
            contentType(ContentType.Application.Json)
            setBody(payload)
        }
        val body = response.requireBody("membuat draft Tech Pack baru")
        val parsed = JsonParser.parse(body) as? JsonValue.Obj ?: error("Respons buat Tech Pack tidak valid")
        TechPackCodec.decode(parsed)
    }

    override suspend fun createFromSampling(
        tenantSlug: String,
        samplingOrderId: String,
        styleCode: String?,
        defaultOwnership: StockOwnershipSemantics?,
        defaultWastePercent: Double?
    ): Result<TechPack> = runCatching {
        val payload = jsonObjectOf(
            "samplingOrderId" to jsonOf(samplingOrderId),
            "styleCode" to jsonOf(styleCode),
            "defaultOwnership" to jsonOf(defaultOwnership?.code),
            "defaultWasteAllowancePercent" to (defaultWastePercent?.let { jsonOf(it) } ?: JsonValue.Null)
        ).encode()

        val response = httpClient.post(resolveUrl("$BASE_PATH/from-sampling")) {
            tenantRequest(tenantSlug, tokenProvider)
            contentType(ContentType.Application.Json)
            setBody(payload)
        }
        val body = response.requireBody("membuat Tech Pack dari SPK Sampling")
        val parsed = JsonParser.parse(body) as? JsonValue.Obj ?: error("Respons Tech Pack dari sampling tidak valid")
        TechPackCodec.decode(parsed)
    }

    override suspend fun updateBom(tenantSlug: String, id: String, lines: List<BomLine>): Result<TechPack> = runCatching {
        val payload = jsonObjectOf(
            "lines" to jsonArrayOf(lines.map(TechPackAndYieldDataCodec::encodeBomLine))
        ).encode()

        val response = httpClient.put(resolveUrl("$BASE_PATH/$id/bom")) {
            tenantRequest(tenantSlug, tokenProvider)
            contentType(ContentType.Application.Json)
            setBody(payload)
        }
        val body = response.requireBody("memperbarui BOM")
        val parsed = JsonParser.parse(body) as? JsonValue.Obj ?: error("Respons update BOM tidak valid")
        TechPackCodec.decode(parsed)
    }

    override suspend fun updateLabor(tenantSlug: String, id: String, operations: List<LaborOperation>): Result<TechPack> = runCatching {
        val payload = jsonObjectOf(
            "operations" to jsonArrayOf(operations.map(TechPackAndYieldDataCodec::encodeLaborOperation))
        ).encode()

        val response = httpClient.put(resolveUrl("$BASE_PATH/$id/labor")) {
            tenantRequest(tenantSlug, tokenProvider)
            contentType(ContentType.Application.Json)
            setBody(payload)
        }
        val body = response.requireBody("memperbarui operasi kerja")
        val parsed = JsonParser.parse(body) as? JsonValue.Obj ?: error("Respons update operasi kerja tidak valid")
        TechPackCodec.decode(parsed)
    }

    override suspend fun updateSizeYield(tenantSlug: String, id: String, factors: List<SizeYieldFactor>): Result<TechPack> = runCatching {
        val payload = jsonObjectOf(
            "factors" to jsonArrayOf(factors.map(TechPackAndYieldDataCodec::encodeSizeYieldFactor))
        ).encode()

        val response = httpClient.put(resolveUrl("$BASE_PATH/$id/size-yield")) {
            tenantRequest(tenantSlug, tokenProvider)
            contentType(ContentType.Application.Json)
            setBody(payload)
        }
        val body = response.requireBody("memperbarui faktor ukuran")
        val parsed = JsonParser.parse(body) as? JsonValue.Obj ?: error("Respons update yield ukuran tidak valid")
        TechPackCodec.decode(parsed)
    }

    override suspend fun resolveMaterials(tenantSlug: String, id: String): Result<TechPack> = runCatching {
        val response = httpClient.post(resolveUrl("$BASE_PATH/$id/resolve-materials")) {
            tenantRequest(tenantSlug, tokenProvider)
            accept(ContentType.Application.Json)
        }
        val body = response.requireBody("mencocokkan bahan baku dengan Master Data")
        val parsed = JsonParser.parse(body) as? JsonValue.Obj ?: error("Respons pencocokan material tidak valid")
        TechPackCodec.decode(parsed)
    }

    override suspend fun release(tenantSlug: String, id: String): Result<TechPack> = runCatching {
        val response = httpClient.post(resolveUrl("$BASE_PATH/$id/release")) {
            tenantRequest(tenantSlug, tokenProvider)
            accept(ContentType.Application.Json)
        }
        val body = response.requireBody("merilis Tech Pack")
        val parsed = JsonParser.parse(body) as? JsonValue.Obj ?: error("Respons rilis Tech Pack tidak valid")
        TechPackCodec.decode(parsed)
    }

    override suspend fun revise(tenantSlug: String, id: String): Result<TechPack> = runCatching {
        val response = httpClient.post(resolveUrl("$BASE_PATH/$id/revise")) {
            tenantRequest(tenantSlug, tokenProvider)
            accept(ContentType.Application.Json)
        }
        val body = response.requireBody("membuat revisi Tech Pack")
        val parsed = JsonParser.parse(body) as? JsonValue.Obj ?: error("Respons revisi Tech Pack tidak valid")
        TechPackCodec.decode(parsed)
    }

    override suspend fun archive(tenantSlug: String, id: String): Result<Boolean> = runCatching {
        val response = httpClient.delete(resolveUrl("$BASE_PATH/$id")) {
            tenantRequest(tenantSlug, tokenProvider)
            accept(ContentType.Application.Json)
        }
        response.requireBody("mengarsipkan Tech Pack")
        true
    }

    override suspend fun previewCost(tenantSlug: String, id: String, orderQuantity: Long): Result<BomCostPreview> = runCatching {
        val response = httpClient.get(resolveUrl("$BASE_PATH/$id/cost-preview?orderQuantity=$orderQuantity")) {
            tenantRequest(tenantSlug, tokenProvider)
            accept(ContentType.Application.Json)
        }
        val body = response.requireBody("memuat estimasi biaya bahan")
        val parsed = JsonParser.parse(body) as? JsonValue.Obj ?: error("Respons estimasi biaya bahan tidak valid")
        BomCostPreviewCodec.decode(parsed)
    }

    private suspend fun HttpResponse.requireBody(action: String): String {
        val body = bodyAsText()
        if (!status.isSuccess()) {
            error("Gagal $action (HTTP ${status.value}): $body")
        }
        return body
    }

    private companion object {
        const val BASE_PATH = "/api/tenant/tech-pack"
    }
}
