package com.eventverse.app.infrastructure.api

import com.eventverse.app.domain.common.UnitOfMeasure
import com.eventverse.app.domain.common.UnitPrice
import com.eventverse.app.domain.masterdata.*
import com.eventverse.app.domain.masterdata.usecases.CreateMaterialCommand
import com.eventverse.app.domain.masterdata.usecases.SetMaterialStandardPriceCommand
import com.eventverse.app.domain.masterdata.usecases.UpdateMaterialCommand
import com.eventverse.app.shared.common.MeasureCodec
import com.eventverse.app.shared.json.*
import com.eventverse.app.shared.masterdata.MaterialCatalogPageCodec
import com.eventverse.app.shared.masterdata.MaterialItemCodec
import com.eventverse.app.shared.masterdata.MaterialPriceCodec
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*

interface MasterDataRemoteDataSource {
    suspend fun searchMaterials(tenantSlug: String, query: MaterialCatalogQuery): Result<MaterialCatalogPage>
    suspend fun getMaterialDetail(tenantSlug: String, materialId: String): Result<MaterialItem>
    suspend fun createMaterial(tenantSlug: String, command: CreateMaterialCommand): Result<MaterialItem>
    suspend fun updateMaterial(tenantSlug: String, command: UpdateMaterialCommand): Result<MaterialItem>
    suspend fun archiveMaterial(tenantSlug: String, materialId: String): Result<MaterialItem>
    suspend fun getPriceHistory(tenantSlug: String, materialId: String): Result<MaterialPriceHistory>
    suspend fun setStandardPrice(tenantSlug: String, command: SetMaterialStandardPriceCommand): Result<MaterialPrice>
    suspend fun resolvePrice(tenantSlug: String, query: PriceQuery): Result<ResolvedPrice>
    suspend fun getPricePolicy(tenantSlug: String): Result<TenantPricePolicy>
    suspend fun savePricePolicy(tenantSlug: String, policy: TenantPricePolicy): Result<TenantPricePolicy>
}

class MasterDataApiClient(
    private val httpClient: HttpClient = HttpClient(),
    private val baseUrl: String = "",
    private val tokenProvider: SessionTokenProvider = StoredSessionTokenProvider
) : MasterDataRemoteDataSource {

    private fun resolveUrl(path: String): String =
        if (baseUrl.isNotBlank()) "${baseUrl.trimEnd('/')}$path" else path

    override suspend fun searchMaterials(tenantSlug: String, query: MaterialCatalogQuery): Result<MaterialCatalogPage> = runCatching {
        val qText = query.queryText
        val cat = query.category
        val own = query.ownership

        val params = buildList {
            if (!qText.isNullOrBlank()) add("q=${qText.encodeURLParameter()}")
            if (cat != null) add("category=${cat.name}")
            if (own != null) add("ownership=${own.name}")
            if (query.includeArchived) add("activeOnly=false")
            add("page=${query.page}")
            add("pageSize=${query.pageSize}")
        }.joinToString("&")

        val path = if (params.isNotEmpty()) "$MATERIALS_PATH?$params" else MATERIALS_PATH
        val response = httpClient.get(resolveUrl(path)) {
            tenantRequest(tenantSlug, tokenProvider)
            accept(ContentType.Application.Json)
        }
        val body = response.requireBody("memuat katalog material")
        val parsed = JsonParser.parse(body) as? JsonValue.Obj ?: error("Respons katalog tidak valid")
        MaterialCatalogPageCodec.decode(parsed)
    }

    override suspend fun getMaterialDetail(tenantSlug: String, materialId: String): Result<MaterialItem> = runCatching {
        val response = httpClient.get(resolveUrl("$MATERIALS_PATH/$materialId")) {
            tenantRequest(tenantSlug, tokenProvider)
            accept(ContentType.Application.Json)
        }
        val body = response.requireBody("memuat detail material")
        val parsed = JsonParser.parse(body) as? JsonValue.Obj ?: error("Respons material tidak valid")
        MaterialItemCodec.decodeMaterial(parsed)
    }

    override suspend fun createMaterial(tenantSlug: String, command: CreateMaterialCommand): Result<MaterialItem> = runCatching {
        val customMap = LinkedHashMap<String, JsonValue>()
        command.customValues.forEach { (k, v) ->
            customMap[k.value] = v ?: JsonValue.Null
        }

        val map = LinkedHashMap<String, JsonValue>()
        map["name"] = jsonOf(command.name)
        val cCode = command.code
        if (!cCode.isNullOrBlank()) map["code"] = jsonOf(cCode)
        map["category"] = jsonOf(command.category.name)
        val bUom = command.baseUom
        if (bUom != null) map["baseUom"] = jsonOf(bUom.code)
        map["alternateUoms"] = jsonArrayOf(command.alternateUoms.map(MaterialItemCodec::encodeConversion))
        map["defaultOwnership"] = jsonOf(command.defaultOwnership.name)
        map["description"] = jsonOf(command.description)
        map["customAttributes"] = JsonValue.Obj(customMap)

        val payload = JsonValue.Obj(map).encode()

        val response = httpClient.post(resolveUrl(MATERIALS_PATH)) {
            tenantRequest(tenantSlug, tokenProvider)
            contentType(ContentType.Application.Json)
            setBody(payload)
        }
        val body = response.requireBody("menambahkan material baru")
        val parsed = JsonParser.parse(body) as? JsonValue.Obj ?: error("Respons simpan material tidak valid")
        MaterialItemCodec.decodeMaterial(parsed)
    }

    override suspend fun updateMaterial(tenantSlug: String, command: UpdateMaterialCommand): Result<MaterialItem> = runCatching {
        val map = LinkedHashMap<String, JsonValue>()
        val name = command.name
        if (name != null) map["name"] = jsonOf(name)
        val desc = command.description
        if (desc != null) map["description"] = jsonOf(desc)
        val cat = command.category
        if (cat != null) map["category"] = jsonOf(cat.name)
        val own = command.defaultOwnership
        if (own != null) map["defaultOwnership"] = jsonOf(own.name)
        val altUoms = command.alternateUoms
        if (altUoms != null) {
            map["alternateUoms"] = jsonArrayOf(altUoms.map(MaterialItemCodec::encodeConversion))
        }
        if (command.customValues.isNotEmpty()) {
            val customMap = LinkedHashMap<String, JsonValue>()
            command.customValues.forEach { (k, v) ->
                customMap[k.value] = v ?: JsonValue.Null
            }
            map["customAttributes"] = JsonValue.Obj(customMap)
        }

        val payload = JsonValue.Obj(map).encode()
        val response = httpClient.put(resolveUrl("$MATERIALS_PATH/${command.materialId.value}")) {
            tenantRequest(tenantSlug, tokenProvider)
            contentType(ContentType.Application.Json)
            setBody(payload)
        }
        val body = response.requireBody("memperbarui material")
        val parsed = JsonParser.parse(body) as? JsonValue.Obj ?: error("Respons update material tidak valid")
        MaterialItemCodec.decodeMaterial(parsed)
    }

    override suspend fun archiveMaterial(tenantSlug: String, materialId: String): Result<MaterialItem> = runCatching {
        val response = httpClient.post(resolveUrl("$MATERIALS_PATH/$materialId/archive")) {
            tenantRequest(tenantSlug, tokenProvider)
            accept(ContentType.Application.Json)
        }
        val body = response.requireBody("mengarsipkan material")
        val parsed = JsonParser.parse(body) as? JsonValue.Obj ?: error("Respons arsip material tidak valid")
        MaterialItemCodec.decodeMaterial(parsed)
    }

    override suspend fun getPriceHistory(tenantSlug: String, materialId: String): Result<MaterialPriceHistory> = runCatching {
        val response = httpClient.get(resolveUrl("$MATERIALS_PATH/$materialId/prices")) {
            tenantRequest(tenantSlug, tokenProvider)
            accept(ContentType.Application.Json)
        }
        val body = response.requireBody("memuat riwayat harga material")
        val parsed = JsonParser.parse(body) as? JsonValue.Obj ?: error("Respons riwayat harga tidak valid")
        MaterialPriceCodec.decodeHistory(parsed)
    }

    override suspend fun setStandardPrice(
        tenantSlug: String,
        command: SetMaterialStandardPriceCommand
    ): Result<MaterialPrice> = runCatching {
        val payload = jsonObjectOf(
            "unitPrice" to MeasureCodec.encodeUnitPrice(command.unitPrice),
            "effectiveFrom" to jsonOf(command.effectiveFrom.toString()),
            "note" to jsonOf(command.note),
            "recordedByUserId" to jsonOf(command.recordedByUserId)
        ).encode()

        val response = httpClient.post(resolveUrl("$MATERIALS_PATH/${command.materialId.value}/prices")) {
            tenantRequest(tenantSlug, tokenProvider)
            contentType(ContentType.Application.Json)
            setBody(payload)
        }
        val body = response.requireBody("menetapkan tarif harga standar")
        val parsed = JsonParser.parse(body) as? JsonValue.Obj ?: error("Respons simpan harga tidak valid")
        MaterialPriceCodec.decodePrice(parsed)
    }

    override suspend fun resolvePrice(tenantSlug: String, query: PriceQuery): Result<ResolvedPrice> = runCatching {
        val targetUom = query.targetUom
        val params = buildList {
            add("materialId=${query.materialId.value.encodeURLParameter()}")
            add("at=${query.at.toString().encodeURLParameter()}")
            add("ownership=${query.ownership.name}")
            if (targetUom != null) add("targetUom=${targetUom.code}")
        }.joinToString("&")

        val response = httpClient.get(resolveUrl("$PRICE_RESOLUTION_PATH?$params")) {
            tenantRequest(tenantSlug, tokenProvider)
            accept(ContentType.Application.Json)
        }
        val body = response.requireBody("menyelesaikan tarif harga material")
        val parsed = JsonParser.parse(body) as? JsonValue.Obj ?: error("Respons harga aktif tidak valid")
        MaterialPriceCodec.decodeResolvedPrice(parsed)
    }

    override suspend fun getPricePolicy(tenantSlug: String): Result<TenantPricePolicy> = runCatching {
        val response = httpClient.get(resolveUrl(PRICE_POLICY_PATH)) {
            tenantRequest(tenantSlug, tokenProvider)
            accept(ContentType.Application.Json)
        }
        val body = response.requireBody("memuat kebijakan harga")
        val parsed = JsonParser.parse(body) as? JsonValue.Obj ?: error("Respons kebijakan harga tidak valid")
        MaterialPriceCodec.decodePolicy(parsed)
    }

    override suspend fun savePricePolicy(tenantSlug: String, policy: TenantPricePolicy): Result<TenantPricePolicy> = runCatching {
        val payload = MaterialPriceCodec.encodePolicy(policy).encode()
        val response = httpClient.put(resolveUrl(PRICE_POLICY_PATH)) {
            tenantRequest(tenantSlug, tokenProvider)
            contentType(ContentType.Application.Json)
            setBody(payload)
        }
        val body = response.requireBody("menyimpan kebijakan harga")
        val parsed = JsonParser.parse(body) as? JsonValue.Obj ?: error("Respons simpan kebijakan tidak valid")
        MaterialPriceCodec.decodePolicy(parsed)
    }

    private suspend fun HttpResponse.requireBody(action: String): String {
        val body = bodyAsText()
        if (!status.isSuccess()) {
            error("Gagal $action (HTTP ${status.value}): $body")
        }
        return body
    }

    private companion object {
        const val MATERIALS_PATH = "/api/tenant/master-data/materials"
        const val PRICE_RESOLUTION_PATH = "/api/tenant/master-data/price-resolution"
        const val PRICE_POLICY_PATH = "/api/tenant/master-data/price-policy"
    }
}
