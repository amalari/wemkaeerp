package com.eventverse.app.infrastructure.api

import com.eventverse.app.domain.transfer.CartonId
import com.eventverse.app.domain.transfer.LocationId
import com.eventverse.app.domain.transfer.SuratJalanManifest
import com.eventverse.app.domain.transfer.TransferType
import com.eventverse.app.domain.transfer.usecases.CustomerDispatchCartonInput
import com.eventverse.app.domain.transfer.usecases.CustomerShipmentResult
import com.eventverse.app.domain.workqueue.WorkCardId
import com.eventverse.app.domain.workqueue.WorkSubjectRef
import com.eventverse.app.shared.json.JsonParser
import com.eventverse.app.shared.json.JsonValue
import com.eventverse.app.shared.json.jsonArrayOf
import com.eventverse.app.shared.json.jsonObjectOf
import com.eventverse.app.shared.json.jsonOf
import com.eventverse.app.shared.transfer.SuratJalanCodec
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess

interface SuratJalanRemoteDataSource {
    suspend fun fetchManifests(type: TransferType? = null): Result<List<SuratJalanManifest>>
    suspend fun fetchManifestById(id: String): Result<SuratJalanManifest>
    suspend fun createInternalTransfer(
        sjNumber: String,
        subject: WorkSubjectRef,
        originLocationId: LocationId,
        destinationLocationId: LocationId,
        cardIds: List<WorkCardId>,
        carrierName: String? = null,
        driverName: String? = null,
        vehiclePlate: String? = null,
        notes: String = ""
    ): Result<SuratJalanManifest>
    suspend fun createMakloonOutbound(
        sjNumber: String,
        subject: WorkSubjectRef,
        vendorRef: String,
        cardIds: List<WorkCardId>,
        unitServiceFeeIdr: Long,
        expectedReturnDate: String,
        carrierName: String? = null,
        driverName: String? = null,
        vehiclePlate: String? = null,
        notes: String = ""
    ): Result<SuratJalanManifest>
    suspend fun createCustomerDispatch(
        sjNumber: String,
        subject: WorkSubjectRef,
        customerName: String,
        customerAddress: String,
        totalOrderedPcs: Int,
        previouslyShippedPcs: Int,
        cartons: List<CustomerDispatchCartonInput>,
        carrierName: String? = null,
        driverName: String? = null,
        vehiclePlate: String? = null,
        notes: String = ""
    ): Result<CustomerShipmentResult>
    suspend fun receiveManifest(id: String, receiverName: String, notes: String = ""): Result<SuratJalanManifest>
}

class SuratJalanApiClient(
    private val httpClient: HttpClient = HttpClient(),
    private val baseUrl: String = "",
    private val tokenProvider: SessionTokenProvider = StoredSessionTokenProvider
) : SuratJalanRemoteDataSource {

    private val tenantSlug: String
        get() = StoredTenantSlugProvider.currentTenantSlug() ?: "demo-tenant"

    override suspend fun fetchManifests(type: TransferType?): Result<List<SuratJalanManifest>> = runCatching {
        val url = buildString {
            append("$baseUrl/api/tenant/transfers/manifests")
            if (type != null) append("?type=${type.name}")
        }
        val response = httpClient.get(url) {
            tenantRequest(tenantSlug, tokenProvider)
        }
        val text = response.bodyAsText()
        if (!response.status.isSuccess()) error("HTTP ${response.status.value}: $text")

        val parsed = JsonParser.parse(text) as? JsonValue.Arr ?: return@runCatching emptyList()
        parsed.items.filterIsInstance<JsonValue.Obj>().map(SuratJalanCodec::decode)
    }

    override suspend fun fetchManifestById(id: String): Result<SuratJalanManifest> = runCatching {
        val response = httpClient.get("$baseUrl/api/tenant/transfers/manifests/$id") {
            tenantRequest(tenantSlug, tokenProvider)
        }
        val text = response.bodyAsText()
        if (!response.status.isSuccess()) error("HTTP ${response.status.value}: $text")
        val obj = JsonParser.parse(text) as? JsonValue.Obj ?: error("Invalid JSON response")
        SuratJalanCodec.decode(obj)
    }

    override suspend fun createInternalTransfer(
        sjNumber: String,
        subject: WorkSubjectRef,
        originLocationId: LocationId,
        destinationLocationId: LocationId,
        cardIds: List<WorkCardId>,
        carrierName: String?,
        driverName: String?,
        vehiclePlate: String?,
        notes: String
    ): Result<SuratJalanManifest> = runCatching {
        val body = jsonObjectOf(
            "sjNumber" to jsonOf(sjNumber),
            "subjectKind" to jsonOf(subject.kind.name),
            "subjectId" to jsonOf(subject.subjectId),
            "orderNumber" to jsonOf(subject.orderNumber),
            "articleName" to jsonOf(subject.articleName),
            "originLocationId" to jsonOf(originLocationId.value),
            "destinationLocationId" to jsonOf(destinationLocationId.value),
            "cardIds" to jsonArrayOf(cardIds.map { jsonOf(it.value) }),
            "carrierName" to (carrierName?.let(::jsonOf) ?: JsonValue.Null),
            "driverName" to (driverName?.let(::jsonOf) ?: JsonValue.Null),
            "vehiclePlate" to (vehiclePlate?.let(::jsonOf) ?: JsonValue.Null),
            "notes" to jsonOf(notes)
        )

        val response = httpClient.post("$baseUrl/api/tenant/transfers/manifests/internal") {
            contentType(ContentType.Application.Json)
            tenantRequest(tenantSlug, tokenProvider)
            setBody(body.encode())
        }
        val text = response.bodyAsText()
        if (!response.status.isSuccess()) error("HTTP ${response.status.value}: $text")
        val obj = JsonParser.parse(text) as? JsonValue.Obj ?: error("Invalid response")
        SuratJalanCodec.decode(obj)
    }

    override suspend fun createMakloonOutbound(
        sjNumber: String,
        subject: WorkSubjectRef,
        vendorRef: String,
        cardIds: List<WorkCardId>,
        unitServiceFeeIdr: Long,
        expectedReturnDate: String,
        carrierName: String?,
        driverName: String?,
        vehiclePlate: String?,
        notes: String
    ): Result<SuratJalanManifest> = runCatching {
        val body = jsonObjectOf(
            "sjNumber" to jsonOf(sjNumber),
            "subjectKind" to jsonOf(subject.kind.name),
            "subjectId" to jsonOf(subject.subjectId),
            "orderNumber" to jsonOf(subject.orderNumber),
            "articleName" to jsonOf(subject.articleName),
            "vendorRef" to jsonOf(vendorRef),
            "cardIds" to jsonArrayOf(cardIds.map { jsonOf(it.value) }),
            "unitServiceFeeIdr" to jsonOf(unitServiceFeeIdr),
            "expectedReturnDate" to jsonOf(expectedReturnDate),
            "carrierName" to (carrierName?.let(::jsonOf) ?: JsonValue.Null),
            "driverName" to (driverName?.let(::jsonOf) ?: JsonValue.Null),
            "vehiclePlate" to (vehiclePlate?.let(::jsonOf) ?: JsonValue.Null),
            "notes" to jsonOf(notes)
        )

        val response = httpClient.post("$baseUrl/api/tenant/transfers/manifests/makloon-outbound") {
            contentType(ContentType.Application.Json)
            tenantRequest(tenantSlug, tokenProvider)
            setBody(body.encode())
        }
        val text = response.bodyAsText()
        if (!response.status.isSuccess()) error("HTTP ${response.status.value}: $text")
        val obj = JsonParser.parse(text) as? JsonValue.Obj ?: error("Invalid response")
        SuratJalanCodec.decode(obj)
    }

    override suspend fun createCustomerDispatch(
        sjNumber: String,
        subject: WorkSubjectRef,
        customerName: String,
        customerAddress: String,
        totalOrderedPcs: Int,
        previouslyShippedPcs: Int,
        cartons: List<CustomerDispatchCartonInput>,
        carrierName: String?,
        driverName: String?,
        vehiclePlate: String?,
        notes: String
    ): Result<CustomerShipmentResult> = runCatching {
        val cartonJsonArray = cartons.map { c ->
            jsonObjectOf(
                "cartonId" to jsonOf(c.cartonId.value),
                "sizeLabel" to jsonOf(c.sizeLabel),
                "colorway" to jsonOf(c.colorway),
                "qtyPcs" to jsonOf(c.qtyPcs),
                "notes" to jsonOf(c.notes)
            )
        }

        val body = jsonObjectOf(
            "sjNumber" to jsonOf(sjNumber),
            "subjectKind" to jsonOf(subject.kind.name),
            "subjectId" to jsonOf(subject.subjectId),
            "orderNumber" to jsonOf(subject.orderNumber),
            "articleName" to jsonOf(subject.articleName),
            "customerName" to jsonOf(customerName),
            "customerAddress" to jsonOf(customerAddress),
            "totalOrderedPcs" to jsonOf(totalOrderedPcs),
            "previouslyShippedPcs" to jsonOf(previouslyShippedPcs),
            "cartons" to jsonArrayOf(cartonJsonArray),
            "carrierName" to (carrierName?.let(::jsonOf) ?: JsonValue.Null),
            "driverName" to (driverName?.let(::jsonOf) ?: JsonValue.Null),
            "vehiclePlate" to (vehiclePlate?.let(::jsonOf) ?: JsonValue.Null),
            "notes" to jsonOf(notes)
        )

        val response = httpClient.post("$baseUrl/api/tenant/transfers/manifests/customer-dispatch") {
            contentType(ContentType.Application.Json)
            tenantRequest(tenantSlug, tokenProvider)
            setBody(body.encode())
        }
        val text = response.bodyAsText()
        if (!response.status.isSuccess()) error("HTTP ${response.status.value}: $text")
        val obj = JsonParser.parse(text) as? JsonValue.Obj ?: error("Invalid response")

        val manifestObj = obj.obj("manifest") ?: error("Missing manifest in response")
        CustomerShipmentResult(
            manifest = SuratJalanCodec.decode(manifestObj),
            thisShipmentPcs = obj.int("thisShipmentPcs") ?: 0,
            totalShippedPcs = obj.int("totalShippedPcs") ?: 0,
            totalOrderedPcs = obj.int("totalOrderedPcs") ?: 0,
            remainingBacklogPcs = obj.int("remainingBacklogPcs") ?: 0,
            isFullyShipped = obj.boolean("isFullyShipped") ?: false
        )
    }

    override suspend fun receiveManifest(id: String, receiverName: String, notes: String): Result<SuratJalanManifest> = runCatching {
        val body = jsonObjectOf(
            "receiverName" to jsonOf(receiverName),
            "notes" to jsonOf(notes)
        )
        val response = httpClient.post("$baseUrl/api/tenant/transfers/manifests/$id/receive") {
            contentType(ContentType.Application.Json)
            tenantRequest(tenantSlug, tokenProvider)
            setBody(body.encode())
        }
        val text = response.bodyAsText()
        if (!response.status.isSuccess()) error("HTTP ${response.status.value}: $text")
        val obj = JsonParser.parse(text) as? JsonValue.Obj ?: error("Invalid response")
        SuratJalanCodec.decode(obj)
    }
}
