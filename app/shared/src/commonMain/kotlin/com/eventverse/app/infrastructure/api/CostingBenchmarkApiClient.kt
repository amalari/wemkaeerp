package com.eventverse.app.infrastructure.api

import com.eventverse.app.domain.costing.CostingProductBenchmark
import com.eventverse.app.domain.costing.DesignVisionHints
import com.eventverse.app.domain.costing.QuickEstimateInput
import com.eventverse.app.domain.costing.QuickQuotationEstimateResult
import com.eventverse.app.domain.pipeline.CostingBehavior
import com.eventverse.app.shared.costing.CostingBenchmarkCodec
import com.eventverse.app.shared.costing.QuickEstimateCodec
import com.eventverse.app.shared.json.*
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*

/** Hasil satu berkas Excel yang diimpor lewat web. */
data class BenchmarkImportResult(
    val importedCount: Int,
    val skippedCount: Int,
    val imported: List<CostingProductBenchmark>,
    val skipped: List<Skipped>,
    val totalBenchmarks: Int
) {
    data class Skipped(val sourceFileName: String, val reason: String)
}

/** Pembacaan AI atas mockup, beserta URL gambar yang sudah tersimpan di server. */
data class MockupAnalysis(
    val hints: DesignVisionHints,
    val mockupImageUrl: String?
)

interface CostingBenchmarkRemoteDataSource {
    suspend fun listBenchmarks(tenantSlug: String, limit: Int = 500): Result<List<CostingProductBenchmark>>

    suspend fun importWorkbook(
        tenantSlug: String,
        fileName: String,
        bytes: ByteArray
    ): Result<BenchmarkImportResult>

    suspend fun analyzeMockup(
        tenantSlug: String,
        fileName: String,
        mimeType: String,
        bytes: ByteArray
    ): Result<MockupAnalysis>

    suspend fun estimateQuick(
        tenantSlug: String,
        input: QuickEstimateInput,
        behavior: CostingBehavior = CostingBehavior.FULL_PACKAGE_COGS,
        visionHints: DesignVisionHints? = null
    ): Result<QuickQuotationEstimateResult>
}

/**
 * Klien Ktor untuk Knowledge Base historis dan estimator cepat.
 *
 * Berkas dikirim sebagai **body mentah dengan metadata di query**, mengikuti konvensi upload PO
 * di `DealRoutes` — satu request, tanpa merakit multipart di lima target KMP.
 */
class CostingBenchmarkApiClient(
    private val httpClient: HttpClient = HttpClient(),
    private val baseUrl: String = "",
    private val tokenProvider: SessionTokenProvider = StoredSessionTokenProvider
) : CostingBenchmarkRemoteDataSource {

    private fun resolveUrl(path: String): String =
        if (baseUrl.isNotBlank()) "${baseUrl.trimEnd('/')}$path" else path

    override suspend fun listBenchmarks(
        tenantSlug: String,
        limit: Int
    ): Result<List<CostingProductBenchmark>> = runCatching {
        val response = httpClient.get(resolveUrl("$BASE_PATH/benchmarks?limit=$limit")) {
            tenantRequest(tenantSlug, tokenProvider)
            accept(ContentType.Application.Json)
        }
        val body = response.requireBody("memuat arsip produk historis")
        val parsed = JsonParser.parse(body) as? JsonValue.Arr
            ?: error("Respons arsip produk tidak valid")
        parsed.items.filterIsInstance<JsonValue.Obj>().map(CostingBenchmarkCodec::decode)
    }

    override suspend fun importWorkbook(
        tenantSlug: String,
        fileName: String,
        bytes: ByteArray
    ): Result<BenchmarkImportResult> = runCatching {
        val response = httpClient.post(
            resolveUrl("$BASE_PATH/benchmarks/import?fileName=${fileName.encodeURLParameter()}")
        ) {
            tenantRequest(tenantSlug, tokenProvider)
            contentType(ContentType.Application.OctetStream)
            setBody(bytes)
        }
        val body = response.requireBody("mengimpor berkas Excel HPP")
        val json = JsonParser.parse(body) as? JsonValue.Obj ?: error("Respons impor tidak valid")

        BenchmarkImportResult(
            importedCount = json.int("importedCount") ?: 0,
            skippedCount = json.int("skippedCount") ?: 0,
            imported = json.objectArray("imported").map(CostingBenchmarkCodec::decode),
            skipped = json.objectArray("skipped").map {
                BenchmarkImportResult.Skipped(
                    sourceFileName = it.string("sourceFileName") ?: "",
                    reason = it.string("reason") ?: ""
                )
            },
            totalBenchmarks = json.int("totalBenchmarks") ?: 0
        )
    }

    override suspend fun analyzeMockup(
        tenantSlug: String,
        fileName: String,
        mimeType: String,
        bytes: ByteArray
    ): Result<MockupAnalysis> = runCatching {
        val query = "fileName=${fileName.encodeURLParameter()}&mimeType=${mimeType.encodeURLParameter()}"
        val response = httpClient.post(resolveUrl("$BASE_PATH/analyze-mockup?$query")) {
            tenantRequest(tenantSlug, tokenProvider)
            contentType(ContentType.Application.OctetStream)
            setBody(bytes)
        }
        val body = response.requireBody("menganalisis gambar mockup")
        val json = JsonParser.parse(body) as? JsonValue.Obj ?: error("Respons analisis mockup tidak valid")

        MockupAnalysis(
            hints = json.obj("hints")?.let(QuickEstimateCodec::decodeVisionHints) ?: DesignVisionHints(),
            mockupImageUrl = json.string("mockupImageUrl")
        )
    }

    override suspend fun estimateQuick(
        tenantSlug: String,
        input: QuickEstimateInput,
        behavior: CostingBehavior,
        visionHints: DesignVisionHints?
    ): Result<QuickQuotationEstimateResult> = runCatching {
        val payload = JsonValue.Obj(
            QuickEstimateCodec.encodeInput(input).entries + linkedMapOf(
                "behavior" to jsonOf(behavior.code),
                "visionHints" to (visionHints?.let(QuickEstimateCodec::encodeVisionHints) ?: JsonValue.Null)
            )
        )
        val response = httpClient.post(resolveUrl("$BASE_PATH/estimate-quick")) {
            tenantRequest(tenantSlug, tokenProvider)
            contentType(ContentType.Application.Json)
            setBody(payload.encode())
        }
        val body = response.requireBody("menghitung estimasi cepat")
        val json = JsonParser.parse(body) as? JsonValue.Obj ?: error("Respons estimasi tidak valid")
        QuickEstimateCodec.decodeResult(json)
    }

    private suspend fun HttpResponse.requireBody(action: String): String {
        val body = bodyAsText()
        if (!status.isSuccess()) {
            error("Gagal $action (HTTP ${status.value}): $body")
        }
        return body
    }

    private companion object {
        const val BASE_PATH = "/api/tenant/costing"
    }
}
