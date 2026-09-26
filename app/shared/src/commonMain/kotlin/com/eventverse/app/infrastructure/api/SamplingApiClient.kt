package com.eventverse.app.infrastructure.api

import com.eventverse.app.domain.sampling.*
import com.eventverse.app.shared.json.*
import com.eventverse.app.shared.sampling.SamplingOrderCodec
import com.eventverse.app.shared.sampling.StageWorkInputCodec
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*

interface SamplingRemoteDataSource {
    suspend fun getOrders(tenantSlug: String, status: SamplingStatus? = null): Result<List<SamplingOrder>>
    suspend fun getOrderDetail(tenantSlug: String, orderId: String): Result<SamplingOrder>
    suspend fun createOrder(
        tenantSlug: String,
        clientName: String,
        styleName: String,
        sizeMode: SizeMode = SizeMode.ALL_SIZE,
        useFactoryPreset: Boolean = true
    ): Result<SamplingOrder>
    suspend fun updateTechnicalSpec(tenantSlug: String, order: SamplingOrder): Result<SamplingOrder>
    suspend fun toggleMilestone(
        tenantSlug: String,
        orderId: String,
        step: MilestoneStep,
        isCompleted: Boolean
    ): Result<SamplingOrder>
    suspend fun approveOrder(
        tenantSlug: String,
        orderId: String,
        isApproved: Boolean,
        accNotes: String
    ): Result<SamplingOrder>
    suspend fun saveOrder(tenantSlug: String, order: SamplingOrder): Result<SamplingOrder>
    suspend fun advanceStage(
        tenantSlug: String,
        orderId: String,
        targetStage: SamplingPipelineStage,
        stageInputs: List<StageWorkInput> = emptyList()
    ): Result<SamplingOrder>
    suspend fun addFinishingDeposit(tenantSlug: String, orderId: String, deposit: FinishingDeposit): Result<SamplingOrder>
    suspend fun assignMakloonVendor(tenantSlug: String, orderId: String, info: MakloonVendorInfo): Result<SamplingOrder>
    suspend fun confirmVendorReturn(tenantSlug: String, orderId: String, returnedAt: kotlinx.datetime.LocalDate? = null): Result<SamplingOrder>
    suspend fun submitQcInspection(tenantSlug: String, orderId: String, report: QcInspectionReport): Result<SamplingOrder>
    suspend fun requestRevision(tenantSlug: String, orderId: String, notes: String): Result<SamplingOrder>
    /** Meja operator: Antrian → Sedang Dikerjakan. */
    suspend fun startStageWork(tenantSlug: String, orderId: String, operatorName: String): Result<SamplingOrder>
    /** Meja operator: Sedang Dikerjakan → Antrian. */
    suspend fun releaseStageWork(tenantSlug: String, orderId: String): Result<SamplingOrder>
    suspend fun sendBackForRework(
        tenantSlug: String,
        orderId: String,
        target: SamplingPipelineStage,
        reason: String,
        liability: com.eventverse.app.domain.pipeline.DefectLiability
    ): Result<SamplingOrder>
}

class SamplingApiClient(
    private val httpClient: HttpClient = HttpClient(),
    private val baseUrl: String = "",
    private val tokenProvider: SessionTokenProvider = StoredSessionTokenProvider
) : SamplingRemoteDataSource {

    private fun resolveUrl(path: String): String =
        if (baseUrl.isNotBlank()) "${baseUrl.trimEnd('/')}$path" else path

    override suspend fun getOrders(tenantSlug: String, status: SamplingStatus?): Result<List<SamplingOrder>> = runCatching {
        val path = if (status != null) "$ORDERS_PATH?status=${status.name}" else ORDERS_PATH
        val response = httpClient.get(resolveUrl(path)) {
            tenantRequest(tenantSlug, tokenProvider)
            accept(ContentType.Application.Json)
        }
        val body = response.requireBody("memuat daftar SPK sample")
        val parsed = JsonParser.parse(body) as? JsonValue.Arr ?: return@runCatching emptyList()
        parsed.items.filterIsInstance<JsonValue.Obj>().map { SamplingOrderCodec.decode(it) }
    }

    override suspend fun getOrderDetail(tenantSlug: String, orderId: String): Result<SamplingOrder> = runCatching {
        val response = httpClient.get(resolveUrl("$ORDERS_PATH/$orderId")) {
            tenantRequest(tenantSlug, tokenProvider)
            accept(ContentType.Application.Json)
        }
        val body = response.requireBody("memuat detail SPK sample")
        val parsed = JsonParser.parse(body) as? JsonValue.Obj ?: error("Respons SPK tidak valid")
        SamplingOrderCodec.decode(parsed)
    }

    override suspend fun createOrder(
        tenantSlug: String,
        clientName: String,
        styleName: String,
        sizeMode: SizeMode,
        useFactoryPreset: Boolean
    ): Result<SamplingOrder> = runCatching {
        val payload = jsonObjectOf(
            "clientName" to jsonOf(clientName),
            "styleName" to jsonOf(styleName),
            "sizeMode" to jsonOf(sizeMode.name),
            "useFactoryAllSizePreset" to jsonOf(useFactoryPreset)
        ).encode()

        val response = httpClient.post(resolveUrl(ORDERS_PATH)) {
            tenantRequest(tenantSlug, tokenProvider)
            contentType(ContentType.Application.Json)
            setBody(payload)
        }
        val body = response.requireBody("membuat SPK sample")
        val parsed = JsonParser.parse(body) as? JsonValue.Obj ?: error("Respons SPK tidak valid")
        SamplingOrderCodec.decode(parsed)
    }

    override suspend fun updateTechnicalSpec(tenantSlug: String, order: SamplingOrder): Result<SamplingOrder> = runCatching {
        val payload = SamplingOrderCodec.encode(order).encode()
        val response = httpClient.put(resolveUrl("$ORDERS_PATH/${order.id.value}/technical-spec")) {
            tenantRequest(tenantSlug, tokenProvider)
            contentType(ContentType.Application.Json)
            setBody(payload)
        }
        val body = response.requireBody("menyimpan spesifikasi teknis SPK")
        val parsed = JsonParser.parse(body) as? JsonValue.Obj ?: error("Respons SPK tidak valid")
        SamplingOrderCodec.decode(parsed)
    }

    override suspend fun toggleMilestone(
        tenantSlug: String,
        orderId: String,
        step: MilestoneStep,
        isCompleted: Boolean
    ): Result<SamplingOrder> = runCatching {
        val payload = jsonObjectOf(
            "isCompleted" to jsonOf(isCompleted)
        ).encode()

        val response = httpClient.patch(resolveUrl("$ORDERS_PATH/$orderId/milestones/${step.name}")) {
            tenantRequest(tenantSlug, tokenProvider)
            contentType(ContentType.Application.Json)
            setBody(payload)
        }
        val body = response.requireBody("mengubah progres milestone")
        val parsed = JsonParser.parse(body) as? JsonValue.Obj ?: error("Respons milestone tidak valid")
        SamplingOrderCodec.decode(parsed)
    }

    override suspend fun approveOrder(
        tenantSlug: String,
        orderId: String,
        isApproved: Boolean,
        accNotes: String
    ): Result<SamplingOrder> = runCatching {
        val payload = jsonObjectOf(
            "isApproved" to jsonOf(isApproved),
            "accNotes" to jsonOf(accNotes)
        ).encode()

        val response = httpClient.post(resolveUrl("$ORDERS_PATH/$orderId/approve")) {
            tenantRequest(tenantSlug, tokenProvider)
            contentType(ContentType.Application.Json)
            setBody(payload)
        }
        val body = response.requireBody("memproses persetujuan ACC produksi")
        val parsed = JsonParser.parse(body) as? JsonValue.Obj ?: error("Respons ACC tidak valid")
        SamplingOrderCodec.decode(parsed)
    }

    override suspend fun saveOrder(tenantSlug: String, order: SamplingOrder): Result<SamplingOrder> = runCatching {
        val payload = SamplingOrderCodec.encode(order).encode()
        val response = httpClient.put(resolveUrl("$ORDERS_PATH/${order.id.value}")) {
            tenantRequest(tenantSlug, tokenProvider)
            contentType(ContentType.Application.Json)
            setBody(payload)
        }
        val body = response.requireBody("menyimpan data SPK")
        val parsed = JsonParser.parse(body) as? JsonValue.Obj ?: error("Respons SPK tidak valid")
        SamplingOrderCodec.decode(parsed)
    }

    override suspend fun advanceStage(
        tenantSlug: String,
        orderId: String,
        targetStage: SamplingPipelineStage,
        stageInputs: List<StageWorkInput>
    ): Result<SamplingOrder> = runCatching {
        val payload = if (stageInputs.isEmpty()) {
            jsonObjectOf("targetStage" to jsonOf(targetStage.name))
        } else {
            jsonObjectOf(
                "targetStage" to jsonOf(targetStage.name),
                "stageInputs" to jsonArrayOf(stageInputs.map { StageWorkInputCodec.encodeInput(it) })
            )
        }.encode()
        val response = httpClient.post(resolveUrl("$ORDERS_PATH/$orderId/stage")) {
            tenantRequest(tenantSlug, tokenProvider)
            contentType(ContentType.Application.Json)
            setBody(payload)
        }
        val body = response.requireBody("memperbarui tahapan pipeline")
        val parsed = JsonParser.parse(body) as? JsonValue.Obj ?: error("Respons tahapan tidak valid")
        SamplingOrderCodec.decode(parsed)
    }

    override suspend fun addFinishingDeposit(tenantSlug: String, orderId: String, deposit: FinishingDeposit): Result<SamplingOrder> = runCatching {
        val payload = jsonObjectOf(
            "depositDate" to jsonOf(deposit.depositDate.toString()),
            "qtyPcs" to jsonOf(deposit.qtyPcs),
            "weightKg" to jsonOf(deposit.weightKg),
            "scalePhotoKey" to jsonOf(deposit.scalePhotoKey),
            "garmentPhotoKey" to jsonOf(deposit.garmentPhotoKey),
            "operatorName" to jsonOf(deposit.operatorName),
            "notes" to jsonOf(deposit.notes)
        ).encode()
        val response = httpClient.post(resolveUrl("$ORDERS_PATH/$orderId/finishing/deposits")) {
            tenantRequest(tenantSlug, tokenProvider)
            contentType(ContentType.Application.Json)
            setBody(payload)
        }
        val body = response.requireBody("mencatat setoran finishing")
        val parsed = JsonParser.parse(body) as? JsonValue.Obj ?: error("Respons setoran tidak valid")
        SamplingOrderCodec.decode(parsed)
    }

    override suspend fun assignMakloonVendor(tenantSlug: String, orderId: String, info: MakloonVendorInfo): Result<SamplingOrder> = runCatching {
        val payload = jsonObjectOf(
            "vendorName" to jsonOf(info.vendorName),
            "vendorPhone" to jsonOf(info.vendorPhone),
            "sentAt" to jsonOf(info.sentAt?.toString()),
            "expectedReturnAt" to jsonOf(info.expectedReturnAt?.toString()),
            "costPerPcsIdr" to jsonOf(info.costPerPcsIdr),
            "notes" to jsonOf(info.notes)
        ).encode()
        val response = httpClient.post(resolveUrl("$ORDERS_PATH/$orderId/finishing/vendor")) {
            tenantRequest(tenantSlug, tokenProvider)
            contentType(ContentType.Application.Json)
            setBody(payload)
        }
        val body = response.requireBody("menugaskan vendor makloon")
        val parsed = JsonParser.parse(body) as? JsonValue.Obj ?: error("Respons vendor tidak valid")
        SamplingOrderCodec.decode(parsed)
    }

    override suspend fun confirmVendorReturn(tenantSlug: String, orderId: String, returnedAt: kotlinx.datetime.LocalDate?): Result<SamplingOrder> = runCatching {
        val payload = jsonObjectOf(
            "returnedAt" to jsonOf(returnedAt?.toString())
        ).encode()
        val response = httpClient.post(resolveUrl("$ORDERS_PATH/$orderId/finishing/vendor-receive")) {
            tenantRequest(tenantSlug, tokenProvider)
            contentType(ContentType.Application.Json)
            setBody(payload)
        }
        val body = response.requireBody("mengonfirmasi pengembalian dari vendor")
        val parsed = JsonParser.parse(body) as? JsonValue.Obj ?: error("Respons konfirmasi vendor tidak valid")
        SamplingOrderCodec.decode(parsed)
    }

    override suspend fun submitQcInspection(tenantSlug: String, orderId: String, report: QcInspectionReport): Result<SamplingOrder> = runCatching {
        val payload = jsonObjectOf(
            "kind" to jsonOf(report.kind.name),
            "inspectorName" to jsonOf(report.inspectorName),
            "pieceNo" to jsonOf(report.pieceNo),
            "inspectedQty" to jsonOf(report.inspectedQty),
            "pomMeasurements" to jsonArrayOf(report.pomMeasurements.map {
                jsonObjectOf(
                    "pomName" to jsonOf(it.pomName),
                    "targetCm" to jsonOf(it.targetCm),
                    "actualCm" to jsonOf(it.actualCm),
                    "toleranceCm" to jsonOf(it.toleranceCm),
                    "notes" to jsonOf(it.notes),
                    "carriedOver" to jsonOf(it.carriedOver)
                )
            }),
            "defectsFound" to jsonArrayOf(report.defectsFound.map { jsonOf(it) }),
            "qcResult" to jsonOf(report.qcResult.name),
            "qcNotes" to jsonOf(report.qcNotes),
            "verifiedPhotoFrontKey" to jsonOf(report.verifiedPhotoFrontKey),
            "verifiedPhotoBackKey" to jsonOf(report.verifiedPhotoBackKey)
        ).encode()
        val response = httpClient.post(resolveUrl("$ORDERS_PATH/$orderId/qc/inspect")) {
            tenantRequest(tenantSlug, tokenProvider)
            contentType(ContentType.Application.Json)
            setBody(payload)
        }
        val body = response.requireBody("mengirim hasil inspeksi QC")
        val parsed = JsonParser.parse(body) as? JsonValue.Obj ?: error("Respons QC tidak valid")
        SamplingOrderCodec.decode(parsed)
    }

    override suspend fun requestRevision(tenantSlug: String, orderId: String, notes: String): Result<SamplingOrder> = runCatching {
        val payload = jsonObjectOf("notes" to jsonOf(notes)).encode()
        val response = httpClient.post(resolveUrl("$ORDERS_PATH/$orderId/revision")) {
            tenantRequest(tenantSlug, tokenProvider)
            contentType(ContentType.Application.Json)
            setBody(payload)
        }
        val body = response.requireBody("mengajukan revisi sample")
        val parsed = JsonParser.parse(body) as? JsonValue.Obj ?: error("Respons revisi tidak valid")
        SamplingOrderCodec.decode(parsed)
    }

    override suspend fun startStageWork(tenantSlug: String, orderId: String, operatorName: String) =
        postOrder(tenantSlug, "$orderId/work/start", jsonObjectOf("operatorName" to jsonOf(operatorName)), "mengambil SPK")

    override suspend fun releaseStageWork(tenantSlug: String, orderId: String) =
        postOrder(tenantSlug, "$orderId/work/release", jsonObjectOf(), "mengembalikan SPK ke antrian")

    override suspend fun sendBackForRework(
        tenantSlug: String,
        orderId: String,
        target: SamplingPipelineStage,
        reason: String,
        liability: com.eventverse.app.domain.pipeline.DefectLiability
    ) = postOrder(
        tenantSlug,
        "$orderId/rework",
        jsonObjectOf(
            "targetStage" to jsonOf(target.name),
            "reason" to jsonOf(reason),
            "liability" to jsonOf(liability.name)
        ),
        "mengirim rework"
    )

    private suspend fun postOrder(
        tenantSlug: String,
        subPath: String,
        payload: JsonValue.Obj,
        action: String
    ): Result<SamplingOrder> = runCatching {
        val response = httpClient.post(resolveUrl("$ORDERS_PATH/$subPath")) {
            tenantRequest(tenantSlug, tokenProvider)
            contentType(ContentType.Application.Json)
            setBody(payload.encode())
        }
        val parsed = JsonParser.parse(response.requireBody(action)) as? JsonValue.Obj ?: error("Respons tidak valid")
        SamplingOrderCodec.decode(parsed)
    }

    private suspend fun HttpResponse.requireBody(action: String): String {
        val body = bodyAsText()
        if (!status.isSuccess()) {
            error("Gagal $action (HTTP ${status.value}): $body")
        }
        return body
    }

    private companion object {
        const val ORDERS_PATH = "/api/tenant/sampling/orders"
    }
}
