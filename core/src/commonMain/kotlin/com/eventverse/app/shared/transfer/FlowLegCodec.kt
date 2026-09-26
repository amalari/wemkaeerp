package com.eventverse.app.shared.transfer

import com.eventverse.app.domain.transfer.FlowLegBoard
import com.eventverse.app.domain.transfer.FlowLegStatus
import com.eventverse.app.domain.transfer.FlowLegView
import com.eventverse.app.domain.transfer.FlowNodeRef
import com.eventverse.app.domain.transfer.FlowTransferLeg
import com.eventverse.app.domain.transfer.LegEndpoint
import com.eventverse.app.domain.transfer.LocationId
import com.eventverse.app.domain.transfer.TransferType
import com.eventverse.app.shared.json.JsonValue
import com.eventverse.app.shared.json.jsonArrayOf
import com.eventverse.app.shared.json.jsonObjectOf
import com.eventverse.app.shared.json.jsonOf

/**
 * Serialisasi papan leg untuk konektor di panel alur.
 *
 * Ujung leg dikirim sebagai `{ kind, ref, label }` alih-alih tiga field nullable, mengikuti
 * bentuk [LegEndpoint] di domain — klien butuh membedakan gedung, vendor, dan pembeli untuk
 * memilih kata pada konektornya.
 */
object FlowLegCodec {

    private const val KIND_SITE = "SITE"
    private const val KIND_VENDOR = "VENDOR"
    private const val KIND_CUSTOMER = "CUSTOMER"

    fun encodeEndpoint(endpoint: LegEndpoint): JsonValue.Obj = when (endpoint) {
        is LegEndpoint.Site -> jsonObjectOf(
            "kind" to jsonOf(KIND_SITE),
            "ref" to jsonOf(endpoint.locationId.value),
            "label" to jsonOf(endpoint.name)
        )
        is LegEndpoint.Vendor -> jsonObjectOf(
            "kind" to jsonOf(KIND_VENDOR),
            "ref" to jsonOf(endpoint.ref),
            "label" to jsonOf(endpoint.displayLabel)
        )
        is LegEndpoint.Customer -> jsonObjectOf(
            "kind" to jsonOf(KIND_CUSTOMER),
            "ref" to jsonOf(endpoint.name),
            "label" to jsonOf(endpoint.displayLabel)
        )
    }

    fun decodeEndpoint(obj: JsonValue.Obj): LegEndpoint? {
        val ref = obj.string("ref")?.takeIf { it.isNotBlank() } ?: return null
        val label = obj.string("label")?.takeIf { it.isNotBlank() } ?: ref
        return when (obj.string("kind")) {
            KIND_SITE -> LegEndpoint.Site(LocationId(ref), label)
            KIND_VENDOR -> LegEndpoint.Vendor(ref)
            KIND_CUSTOMER -> LegEndpoint.Customer(ref)
            else -> null
        }
    }

    fun encodeView(view: FlowLegView): JsonValue.Obj = jsonObjectOf(
        "legKey" to jsonOf(view.leg.legKey),
        "fromNodeKey" to jsonOf(view.leg.fromNode.key),
        "toNodeKey" to jsonOf(view.leg.toNode.key),
        "transferType" to jsonOf(view.leg.transferType.name),
        "origin" to encodeEndpoint(view.leg.origin),
        "destination" to encodeEndpoint(view.leg.destination),
        "status" to jsonOf(view.status.name),
        "isLegacyMatch" to jsonOf(view.isLegacyMatch),
        "manifestId" to (view.manifest?.let { jsonOf(it.id.value) } ?: JsonValue.Null),
        "sjNumber" to (view.manifest?.let { jsonOf(it.sjNumber.value) } ?: JsonValue.Null)
    )

    /**
     * Baris yang simpulnya tidak dikenal lagi dibuang satu per satu, bukan menjatuhkan seluruh
     * papan — layar alur harus tetap terbuka meski satu baris konfigurasi usang.
     */
    fun decodeView(obj: JsonValue.Obj): FlowLegView? {
        val from = obj.string("fromNodeKey")?.let(FlowNodeRef::parse) ?: return null
        val to = obj.string("toNodeKey")?.let(FlowNodeRef::parse) ?: return null
        val origin = obj.obj("origin")?.let(::decodeEndpoint) ?: return null
        val destination = obj.obj("destination")?.let(::decodeEndpoint) ?: return null
        if (origin == destination) return null
        val transferType = runCatching {
            TransferType.valueOf(obj.string("transferType") ?: "")
        }.getOrNull() ?: return null
        val status = runCatching {
            FlowLegStatus.valueOf(obj.string("status") ?: "")
        }.getOrDefault(FlowLegStatus.BELUM_TERBIT)

        return FlowLegView(
            leg = FlowTransferLeg(
                legKey = obj.string("legKey")?.takeIf { it.isNotBlank() }
                    ?: FlowTransferLeg.keyFor(from, to, transferType),
                fromNode = from,
                toNode = to,
                origin = origin,
                destination = destination,
                transferType = transferType
            ),
            status = status,
            isLegacyMatch = obj.boolean("isLegacyMatch") ?: false
        )
    }

    fun encodeBoard(board: FlowLegBoard): JsonValue.Obj = jsonObjectOf(
        "legs" to jsonArrayOf(board.legs.map(::encodeView)),
        "orphanSjNumbers" to jsonArrayOf(board.orphanManifests.map { jsonOf(it.sjNumber.value) })
    )

    fun decodeBoard(obj: JsonValue.Obj): FlowLegBoard = FlowLegBoard(
        legs = obj.objectArray("legs").mapNotNull(::decodeView)
    )
}
