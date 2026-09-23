package com.eventverse.app.infrastructure.api

import com.eventverse.app.domain.vendor.Vendor
import com.eventverse.app.domain.vendor.VendorAssignment
import com.eventverse.app.domain.vendor.VendorPriceUnit
import com.eventverse.app.domain.vendor.VendorQueueItem
import com.eventverse.app.domain.vendor.VendorServiceRate
import com.eventverse.app.shared.json.JsonParser
import com.eventverse.app.shared.json.JsonValue
import com.eventverse.app.shared.json.jsonObjectOf
import com.eventverse.app.shared.json.jsonOf
import com.eventverse.app.shared.vendor.VendorAssignmentCodec
import com.eventverse.app.shared.vendor.VendorCodec
import io.ktor.client.HttpClient
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.datetime.LocalDate

/** Isian profil vendor dari form; dipakai untuk tambah maupun ubah. */
data class VendorProfileInput(
    val name: String,
    val phone: String,
    val address: String,
    val notes: String,
    val isActive: Boolean? = null
)

/** Isian dialog penunjukan vendor. `null` pada harga/satuan = pakai daftar harga vendor. */
data class VendorAssignmentInput(
    val subjectId: String,
    val processCode: String,
    val vendorId: String,
    val pricePerUnitIdr: Long?,
    val unit: VendorPriceUnit?,
    val unitsPerPiece: Int,
    val quantityPcs: Int?,
    val expectedReturnAt: LocalDate?,
    val notes: String
)

interface VendorRemoteDataSource {
    suspend fun listVendors(tenantSlug: String, includeInactive: Boolean): Result<List<Vendor>>
    suspend fun registerVendor(tenantSlug: String, input: VendorProfileInput): Result<Vendor>
    suspend fun updateVendor(tenantSlug: String, vendorId: String, input: VendorProfileInput): Result<Vendor>
    suspend fun setRate(tenantSlug: String, vendorId: String, rate: VendorServiceRate): Result<Vendor>
    suspend fun vendorAssignments(tenantSlug: String, vendorId: String): Result<List<VendorAssignment>>
    suspend fun queue(tenantSlug: String): Result<List<VendorQueueItem>>
    suspend fun assign(tenantSlug: String, input: VendorAssignmentInput): Result<VendorAssignment>
    suspend fun cancelAssignment(tenantSlug: String, assignmentId: String): Result<VendorAssignment>
}

class VendorApiClient(
    private val httpClient: HttpClient = HttpClient(),
    private val baseUrl: String = "",
    private val tokenProvider: SessionTokenProvider = StoredSessionTokenProvider
) : VendorRemoteDataSource {

    private fun url(path: String): String = if (baseUrl.isNotBlank()) "${baseUrl.trimEnd('/')}$path" else path

    override suspend fun listVendors(tenantSlug: String, includeInactive: Boolean) = runCatching {
        val response = httpClient.get(url("$VENDORS_PATH?includeInactive=$includeInactive")) {
            tenantRequest(tenantSlug, tokenProvider)
            accept(ContentType.Application.Json)
        }
        VendorCodec.decodeVendors(response.requireBody("memuat kontak vendor"))
    }

    override suspend fun registerVendor(tenantSlug: String, input: VendorProfileInput) = runCatching {
        val response = httpClient.post(url(VENDORS_PATH)) {
            tenantRequest(tenantSlug, tokenProvider)
            contentType(ContentType.Application.Json)
            setBody(input.toJson().encode())
        }
        VendorCodec.decodeVendor(response.requireObject("menambah vendor"))
    }

    override suspend fun updateVendor(tenantSlug: String, vendorId: String, input: VendorProfileInput) = runCatching {
        val response = httpClient.put(url("$VENDORS_PATH/$vendorId")) {
            tenantRequest(tenantSlug, tokenProvider)
            contentType(ContentType.Application.Json)
            setBody(input.toJson().encode())
        }
        VendorCodec.decodeVendor(response.requireObject("menyimpan vendor"))
    }

    override suspend fun setRate(tenantSlug: String, vendorId: String, rate: VendorServiceRate) = runCatching {
        val response = httpClient.post(url("$VENDORS_PATH/$vendorId/rates")) {
            tenantRequest(tenantSlug, tokenProvider)
            contentType(ContentType.Application.Json)
            setBody(VendorCodec.encodeRate(rate).encode())
        }
        VendorCodec.decodeVendor(response.requireObject("menyimpan harga layanan"))
    }

    override suspend fun vendorAssignments(tenantSlug: String, vendorId: String) = runCatching {
        val response = httpClient.get(url("$VENDORS_PATH/$vendorId/assignments")) {
            tenantRequest(tenantSlug, tokenProvider)
            accept(ContentType.Application.Json)
        }
        VendorAssignmentCodec.decodeAssignments(response.requireBody("memuat riwayat order vendor"))
    }

    override suspend fun queue(tenantSlug: String) = runCatching {
        val response = httpClient.get(url(QUEUE_PATH)) {
            tenantRequest(tenantSlug, tokenProvider)
            accept(ContentType.Application.Json)
        }
        VendorAssignmentCodec.decodeQueue(response.requireBody("memuat antrean vendor"))
    }

    override suspend fun assign(tenantSlug: String, input: VendorAssignmentInput) = runCatching {
        val payload = jsonObjectOf(
            "subjectId" to jsonOf(input.subjectId),
            "processCode" to jsonOf(input.processCode),
            "vendorId" to jsonOf(input.vendorId),
            "pricePerUnitIdr" to (input.pricePerUnitIdr?.let(::jsonOf) ?: JsonValue.Null),
            "unit" to jsonOf(input.unit?.name),
            "unitsPerPiece" to jsonOf(input.unitsPerPiece),
            "quantityPcs" to (input.quantityPcs?.let(::jsonOf) ?: JsonValue.Null),
            "expectedReturnAt" to jsonOf(input.expectedReturnAt?.toString()),
            "notes" to jsonOf(input.notes)
        )
        val response = httpClient.post(url(ASSIGNMENTS_PATH)) {
            tenantRequest(tenantSlug, tokenProvider)
            contentType(ContentType.Application.Json)
            setBody(payload.encode())
        }
        VendorAssignmentCodec.decodeAssignment(response.requireObject("menunjuk vendor"))
    }

    override suspend fun cancelAssignment(tenantSlug: String, assignmentId: String) = runCatching {
        val response = httpClient.post(url("$ASSIGNMENTS_PATH/$assignmentId/cancel")) {
            tenantRequest(tenantSlug, tokenProvider)
        }
        VendorAssignmentCodec.decodeAssignment(response.requireObject("membatalkan penunjukan vendor"))
    }

    private fun VendorProfileInput.toJson(): JsonValue.Obj = jsonObjectOf(
        "name" to jsonOf(name),
        "phone" to jsonOf(phone),
        "address" to jsonOf(address),
        "notes" to jsonOf(notes),
        "isActive" to (isActive?.let(::jsonOf) ?: JsonValue.Null)
    )

    private suspend fun HttpResponse.requireBody(action: String): String {
        val body = bodyAsText()
        if (!status.isSuccess()) error("Gagal $action: $body")
        return body
    }

    private suspend fun HttpResponse.requireObject(action: String): JsonValue.Obj =
        JsonParser.parse(requireBody(action)) as? JsonValue.Obj ?: error("Respons $action tidak valid")

    private companion object {
        const val VENDORS_PATH = "/api/tenant/vendors"
        const val QUEUE_PATH = "/api/tenant/vendor-queue"
        const val ASSIGNMENTS_PATH = "/api/tenant/vendor-assignments"
    }
}
