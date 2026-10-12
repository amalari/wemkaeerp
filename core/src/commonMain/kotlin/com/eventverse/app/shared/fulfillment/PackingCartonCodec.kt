package com.eventverse.app.shared.fulfillment

import com.eventverse.app.domain.fulfillment.carton.CartonId
import com.eventverse.app.domain.fulfillment.carton.CartonNumber
import com.eventverse.app.domain.fulfillment.carton.CartonStatus
import com.eventverse.app.domain.fulfillment.carton.PackingCarton
import com.eventverse.app.domain.fulfillment.carton.SealNumber
import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.shared.common.DateTimeCodec
import com.eventverse.app.shared.json.JsonValue
import com.eventverse.app.shared.json.jsonArrayOf
import com.eventverse.app.shared.json.jsonObjectOf
import com.eventverse.app.shared.json.jsonOf
import kotlinx.datetime.Instant

/**
 * Codec karton packing. Cermin domain, bukan sebaliknya — kalau bentuk JSON dan domain
 * berselisih, yang salah adalah codec-nya.
 *
 * Status tak dikenal **ditolak**, bukan jatuh ke default (Kontrak 4 variability): payload
 * dengan status basi adalah data korup dan gagal terbaca di sini, bukan diam-diam berubah.
 */
object PackingCartonCodec {

    private val EPOCH = Instant.fromEpochMilliseconds(0)

    fun encode(carton: PackingCarton): JsonValue.Obj = jsonObjectOf(
        "id" to jsonOf(carton.id.value),
        "tenantId" to jsonOf(carton.tenantId.value),
        "productionRef" to jsonOf(carton.productionRef),
        "cartonNumber" to jsonOf(carton.cartonNumber.value),
        "itemBreakdown" to JsonValue.Obj(carton.itemBreakdown.entries.associate { (k, v) -> k to jsonOf(v) }),
        "quantityPcs" to jsonOf(carton.quantityPcs),
        "grossWeightKg" to jsonOf(carton.grossWeightKg),
        "unitTicketIds" to jsonArrayOf(carton.unitTicketIds.map(::jsonOf)),
        "sealNumber" to (carton.sealNumber?.let { jsonOf(it.value) } ?: JsonValue.Null),
        "status" to jsonOf(carton.status.name),
        "statusLabel" to jsonOf(carton.status.displayName),
        "createdAt" to jsonOf(carton.createdAt.toString()),
        "updatedAt" to jsonOf(carton.updatedAt.toString()),
        "notes" to jsonOf(carton.notes)
    )

    fun decode(obj: JsonValue.Obj): PackingCarton {
        val updatedAt = DateTimeCodec.parseInstantOrFallback(obj.string("updatedAt"), EPOCH)
        return PackingCarton(
            id = CartonId(obj.string("id") ?: ""),
            tenantId = TenantId(obj.string("tenantId") ?: ""),
            productionRef = obj.string("productionRef") ?: "",
            // Format nomor tidak sah ditolak oleh value object — bukan dipaksa kosong.
            cartonNumber = CartonNumber(obj.string("cartonNumber") ?: ""),
            itemBreakdown = obj.obj("itemBreakdown")?.entries
                ?.map { (k, v) ->
                    k to ((v as? JsonValue.Num)?.asInt
                        ?: throw IllegalArgumentException("itemBreakdown.$k: qty wajib angka"))
                }
                ?.toMap()
                ?: emptyMap(),
            quantityPcs = obj.int("quantityPcs") ?: 0,
            grossWeightKg = obj.double("grossWeightKg") ?: 0.0,
            unitTicketIds = obj.stringArray("unitTicketIds"),
            sealNumber = obj.string("sealNumber")?.takeIf { it.isNotBlank() }?.let(::SealNumber),
            status = enumOrNull<CartonStatus>(obj.string("status"))
                ?: throw IllegalArgumentException("Status karton tak dikenal: ${obj.string("status")}"),
            createdAt = DateTimeCodec.parseInstantOrFallback(obj.string("createdAt"), updatedAt),
            updatedAt = updatedAt,
            notes = obj.string("notes") ?: ""
        )
    }

    private inline fun <reified T : Enum<T>> enumOrNull(raw: String?): T? =
        raw?.let { value -> enumValues<T>().find { it.name == value } }
}
