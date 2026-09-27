package com.eventverse.app.infrastructure.api

import com.eventverse.app.domain.traceability.*
import com.eventverse.app.shared.json.*
import com.eventverse.app.shared.traceability.TraceAllocationCodec
import com.eventverse.app.shared.traceability.TraceContainerCodec
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.datetime.Instant

/** Hasil pemindaian satu kartu, sebagaimana dilihat klien. */
data class TraceScanView(
    val code: TraceCode,
    val humanCode: String,
    val tier: TraceTier,
    val tierLabel: String,
    val sizeLabel: String,
    val sequence: Int,
    val isNewCard: Boolean,
    val snapshot: TraceWorkOrderSnapshot?,
    val container: TraceContainer?
)

data class SackCloseResult(
    val humanCode: String,
    val declaredPcs: Int,
    val consumedBundleCount: Int,
    val shrinkagePcs: Int
)

interface TraceabilityRemoteDataSource {
    suspend fun resolve(tenantSlug: String, rawCode: String): Result<TraceScanView>
    suspend fun open(tenantSlug: String, rawCode: String, colorway: String): Result<TraceContainer>
    suspend fun recordTally(
        tenantSlug: String,
        code: TraceCode,
        tallies: List<PanelTally>,
        operatorName: String,
        shift: String,
        recordedAt: Instant?,
        notes: String
    ): Result<TraceContainer>

    suspend fun closeSack(
        tenantSlug: String,
        sackCode: TraceCode,
        bundleCodes: List<TraceCode>,
        declaredPcs: Int,
        weightKg: Double,
        operatorName: String,
        recordedAt: Instant?,
        notes: String
    ): Result<SackCloseResult>

    suspend fun containers(tenantSlug: String, ref: TraceWorkOrderRef): Result<List<TraceContainer>>
    suspend fun reconciliation(tenantSlug: String, ref: TraceWorkOrderRef): Result<TraceReconciliation>

    /** URL berkas PDF; dibuka langsung oleh browser, tidak diunduh ke memori aplikasi. */
    fun labelsPdfUrl(ref: TraceWorkOrderRef, tier: TraceTier, sizeLabel: String?): String
    fun worksheetPdfUrl(ref: TraceWorkOrderRef): String

    /** Kartu SPK A6 — satu halaman per ukuran, urgensi dihitung saat dibuka. */
    fun spkCardPdfUrl(ref: TraceWorkOrderRef): String
}

class TraceabilityApiClient(
    private val httpClient: HttpClient = HttpClient(),
    private val baseUrl: String = "",
    private val tokenProvider: SessionTokenProvider = StoredSessionTokenProvider
) : TraceabilityRemoteDataSource {

    private fun resolveUrl(path: String): String =
        if (baseUrl.isNotBlank()) "${baseUrl.trimEnd('/')}$path" else path

    private fun workOrderPath(ref: TraceWorkOrderRef): String =
        "$BASE_PATH/work-orders/${ref.kind.name}/${ref.id}"

    override suspend fun resolve(tenantSlug: String, rawCode: String): Result<TraceScanView> = runCatching {
        val normalized = TraceCodec.normalize(rawCode)
        val response = httpClient.get(resolveUrl("$BASE_PATH/codes/$normalized")) {
            tenantRequest(tenantSlug, tokenProvider)
            accept(ContentType.Application.Json)
        }
        decodeScan(JsonParser.parseObject(response.requireBody("membaca kartu")))
    }

    override suspend fun open(tenantSlug: String, rawCode: String, colorway: String): Result<TraceContainer> =
        runCatching {
            val normalized = TraceCodec.normalize(rawCode)
            val response = httpClient.post(resolveUrl("$BASE_PATH/codes/$normalized/open")) {
                tenantRequest(tenantSlug, tokenProvider)
                contentType(ContentType.Application.Json)
                setBody(jsonObjectOf("colorway" to jsonOf(colorway)).encode())
            }
            TraceContainerCodec.decode(JsonParser.parseObject(response.requireBody("membuka kartu")))
        }

    override suspend fun recordTally(
        tenantSlug: String,
        code: TraceCode,
        tallies: List<PanelTally>,
        operatorName: String,
        shift: String,
        recordedAt: Instant?,
        notes: String
    ): Result<TraceContainer> = runCatching {
        val payload = jsonObjectOf(
            "panelTallies" to jsonArrayOf(
                tallies.map { jsonObjectOf("panel" to jsonOf(it.panel.name), "pieces" to jsonOf(it.pieces)) }
            ),
            "operatorName" to jsonOf(operatorName),
            "shift" to jsonOf(shift),
            "recordedAt" to jsonOf(recordedAt?.toString()),
            "notes" to jsonOf(notes)
        )
        val response = httpClient.post(resolveUrl("$BASE_PATH/codes/${code.value}/tally")) {
            tenantRequest(tenantSlug, tokenProvider)
            contentType(ContentType.Application.Json)
            setBody(payload.encode())
        }
        TraceContainerCodec.decode(JsonParser.parseObject(response.requireBody("menyimpan hitungan bundel")))
    }

    override suspend fun closeSack(
        tenantSlug: String,
        sackCode: TraceCode,
        bundleCodes: List<TraceCode>,
        declaredPcs: Int,
        weightKg: Double,
        operatorName: String,
        recordedAt: Instant?,
        notes: String
    ): Result<SackCloseResult> = runCatching {
        val payload = jsonObjectOf(
            "bundleCodes" to jsonArrayOf(bundleCodes.map { jsonOf(it.value) }),
            "declaredPcs" to jsonOf(declaredPcs),
            "weightKg" to jsonOf(weightKg),
            "operatorName" to jsonOf(operatorName),
            "recordedAt" to jsonOf(recordedAt?.toString()),
            "notes" to jsonOf(notes)
        )
        val response = httpClient.post(resolveUrl("$BASE_PATH/sacks/${sackCode.value}/close")) {
            tenantRequest(tenantSlug, tokenProvider)
            contentType(ContentType.Application.Json)
            setBody(payload.encode())
        }
        val obj = JsonParser.parseObject(response.requireBody("menutup karung"))
        SackCloseResult(
            humanCode = obj.string("code")?.let { TraceCodec.grouped(TraceCode(it)) } ?: "",
            declaredPcs = obj.int("declaredPcs") ?: 0,
            consumedBundleCount = obj.int("consumedBundleCount") ?: 0,
            shrinkagePcs = obj.int("shrinkagePcs") ?: 0
        )
    }

    override suspend fun containers(tenantSlug: String, ref: TraceWorkOrderRef): Result<List<TraceContainer>> =
        runCatching {
            val response = httpClient.get(resolveUrl("${workOrderPath(ref)}/containers")) {
                tenantRequest(tenantSlug, tokenProvider)
                accept(ContentType.Application.Json)
            }
            val parsed = JsonParser.parse(response.requireBody("memuat wadah telusur")) as? JsonValue.Arr
                ?: return@runCatching emptyList()
            parsed.items.filterIsInstance<JsonValue.Obj>().map(TraceContainerCodec::decode)
        }

    override suspend fun reconciliation(
        tenantSlug: String,
        ref: TraceWorkOrderRef
    ): Result<TraceReconciliation> = runCatching {
        val response = httpClient.get(resolveUrl("${workOrderPath(ref)}/reconciliation")) {
            tenantRequest(tenantSlug, tokenProvider)
            accept(ContentType.Application.Json)
        }
        decodeReconciliation(ref, JsonParser.parseObject(response.requireBody("memuat rekonsiliasi")))
    }

    override fun labelsPdfUrl(ref: TraceWorkOrderRef, tier: TraceTier, sizeLabel: String?): String {
        val size = sizeLabel?.takeIf { it.isNotBlank() }?.let { "&size=$it" }.orEmpty()
        return resolveUrl("${workOrderPath(ref)}/labels.pdf?tier=${tier.name}$size")
    }

    override fun worksheetPdfUrl(ref: TraceWorkOrderRef): String =
        resolveUrl("${workOrderPath(ref)}/worksheet.pdf")

    override fun spkCardPdfUrl(ref: TraceWorkOrderRef): String =
        resolveUrl("${workOrderPath(ref)}/spk-card.pdf")

    private fun decodeScan(obj: JsonValue.Obj): TraceScanView {
        val code = TraceCode(obj.string("code") ?: "")
        return TraceScanView(
            code = code,
            humanCode = obj.string("humanCode") ?: TraceCodec.grouped(code),
            tier = TraceTier.entries.firstOrNull { it.name == obj.string("tier") } ?: TraceTier.BUNDLE,
            tierLabel = obj.string("tierLabel") ?: "",
            sizeLabel = obj.string("sizeLabel") ?: "",
            sequence = obj.int("sequence") ?: 0,
            isNewCard = obj.boolean("isNewCard") ?: false,
            snapshot = obj.obj("snapshot")?.let(TraceAllocationCodec::decodeSnapshot),
            container = obj.obj("container")?.let(TraceContainerCodec::decode)
        )
    }

    private fun decodeReconciliation(ref: TraceWorkOrderRef, obj: JsonValue.Obj): TraceReconciliation =
        TraceReconciliation(
            workOrder = ref,
            spkNumber = obj.string("spkNumber") ?: "",
            perSize = obj.objectArray("perSize").map { row ->
                SizeReconciliation(
                    sizeLabel = row.string("sizeLabel") ?: "",
                    orderedPcs = row.int("orderedPcs") ?: 0,
                    bundledSets = row.int("bundledSets") ?: 0,
                    consumedSets = row.int("consumedSets") ?: 0,
                    sackPcs = row.int("sackPcs") ?: 0,
                    leftoverPanels = row.objectArray("leftoverPanels").mapNotNull { leftover ->
                        val panel = com.eventverse.app.domain.contracts.GarmentPanel.entries
                            .firstOrNull { it.name == leftover.string("panel") } ?: return@mapNotNull null
                        PanelTally(panel, leftover.int("pieces") ?: 0)
                    }
                )
            }
        )

    private suspend fun HttpResponse.requireBody(action: String): String {
        val body = bodyAsText()
        if (!status.isSuccess()) {
            // Pesan dari server sudah menyebut angkanya (size yang bertabrakan, bundel yang belum
            // dihitung); membungkusnya dengan "terjadi kesalahan" justru membuang informasi itu.
            error(body.ifBlank { "Gagal $action (HTTP ${status.value})" })
        }
        return body
    }

    private companion object {
        const val BASE_PATH = "/api/tenant/traceability"
    }
}
