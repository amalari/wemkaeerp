package com.eventverse.app.shared.vendor

import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.domain.vendor.Vendor
import com.eventverse.app.domain.vendor.VendorId
import com.eventverse.app.domain.vendor.VendorName
import com.eventverse.app.domain.vendor.VendorPriceUnit
import com.eventverse.app.domain.vendor.VendorServiceRate
import com.eventverse.app.shared.common.DateTimeCodec
import com.eventverse.app.shared.json.JsonParser
import com.eventverse.app.shared.json.JsonValue
import com.eventverse.app.shared.json.jsonArrayOf
import com.eventverse.app.shared.json.jsonObjectOf
import com.eventverse.app.shared.json.jsonOf
import kotlinx.datetime.Clock

/** Codec kontak vendor — dipakai bersama oleh server (respons & kolom JSON) dan klien. */
object VendorCodec {

    fun encodeRate(rate: VendorServiceRate): JsonValue.Obj = jsonObjectOf(
        "serviceCode" to jsonOf(rate.serviceCode),
        "serviceName" to jsonOf(rate.serviceName),
        "priceIdr" to jsonOf(rate.priceIdr),
        "unit" to jsonOf(rate.unit.name),
        "minQuantity" to jsonOf(rate.minQuantity),
        "effectiveFrom" to jsonOf(rate.effectiveFrom.toString()),
        "effectiveTo" to jsonOf(rate.effectiveTo?.toString())
    )

    fun decodeRate(obj: JsonValue.Obj): VendorServiceRate = VendorServiceRate(
        serviceCode = VendorServiceRate.normalizeCode(obj.string("serviceCode") ?: ""),
        serviceName = obj.string("serviceName") ?: "",
        priceIdr = obj.long("priceIdr") ?: 0L,
        unit = decodeUnit(obj.string("unit")) ?: VendorPriceUnit.PER_PIECE,
        minQuantity = obj.int("minQuantity") ?: 0,
        effectiveFrom = DateTimeCodec.parseLocalDateOrNull(obj.string("effectiveFrom"))
            ?: error("effectiveFrom wajib diisi dengan format YYYY-MM-DD"),
        effectiveTo = DateTimeCodec.parseLocalDateOrNull(obj.string("effectiveTo"))
    )

    fun encodeRates(rates: List<VendorServiceRate>): JsonValue.Arr = jsonArrayOf(rates.map(::encodeRate))

    /** Kolom `rates` di database disimpan sebagai teks JSON; baris rusak dilewati, bukan menggagalkan vendor. */
    fun decodeRates(raw: String?): List<VendorServiceRate> {
        if (raw.isNullOrBlank()) return emptyList()
        return JsonParser.parseArray(raw)
            .filterIsInstance<JsonValue.Obj>()
            .mapNotNull { runCatching { decodeRate(it) }.getOrNull() }
    }

    fun encodeVendor(vendor: Vendor): JsonValue.Obj = jsonObjectOf(
        "id" to jsonOf(vendor.id.value),
        "tenantId" to jsonOf(vendor.tenantId.value),
        "name" to jsonOf(vendor.name.value),
        "phone" to jsonOf(vendor.phone),
        "address" to jsonOf(vendor.address),
        "notes" to jsonOf(vendor.notes),
        "rates" to encodeRates(vendor.rates),
        "isActive" to jsonOf(vendor.isActive),
        "createdAt" to jsonOf(vendor.createdAt.toString()),
        "updatedAt" to jsonOf(vendor.updatedAt.toString())
    )

    fun decodeVendor(obj: JsonValue.Obj): Vendor {
        val createdAt = DateTimeCodec.parseInstantOrFallback(obj.string("createdAt"), Clock.System.now())
        return Vendor(
            id = VendorId(obj.string("id") ?: ""),
            tenantId = TenantId(obj.string("tenantId") ?: ""),
            name = VendorName(obj.string("name") ?: ""),
            phone = obj.string("phone") ?: "",
            address = obj.string("address") ?: "",
            notes = obj.string("notes") ?: "",
            rates = obj.objectArray("rates").mapNotNull { runCatching { decodeRate(it) }.getOrNull() },
            isActive = obj.boolean("isActive") ?: true,
            createdAt = createdAt,
            updatedAt = DateTimeCodec.parseInstantOrFallback(obj.string("updatedAt"), createdAt)
        )
    }

    fun encodeVendors(vendors: List<Vendor>): String = jsonArrayOf(vendors.map(::encodeVendor)).encode()

    fun decodeVendors(json: String): List<Vendor> =
        JsonParser.parseArray(json).filterIsInstance<JsonValue.Obj>().map(::decodeVendor)

    fun decodeUnit(raw: String?): VendorPriceUnit? =
        raw?.let { name -> VendorPriceUnit.entries.firstOrNull { it.name.equals(name.trim(), ignoreCase = true) } }
}
