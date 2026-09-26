package com.eventverse.app.shared.transfer

import com.eventverse.app.domain.transfer.FlowNodeRef
import com.eventverse.app.domain.transfer.LocationId
import com.eventverse.app.domain.transfer.PhysicalLocation
import com.eventverse.app.domain.transfer.TenantLocationConfig
import com.eventverse.app.shared.json.JsonValue
import com.eventverse.app.shared.json.jsonArrayOf
import com.eventverse.app.shared.json.jsonObjectOf
import com.eventverse.app.shared.json.jsonOf

/**
 * Serialisasi konfigurasi lokasi tenant. Satu sumber kebenaran untuk server dan klien.
 *
 * Pemetaan simpul dikirim sebagai daftar `{ nodeKey, locationId }`, bukan objek berkunci bebas,
 * supaya kunci yang tidak dikenal bisa dilewati satu per satu tanpa menjatuhkan seluruh muatan.
 */
object TenantLocationCodec {

    fun encodeLocation(location: PhysicalLocation): JsonValue.Obj = jsonObjectOf(
        "id" to jsonOf(location.id.value),
        "name" to jsonOf(location.name),
        "address" to jsonOf(location.address),
        "isMainWarehouse" to jsonOf(location.isMainWarehouse)
    )

    fun decodeLocation(obj: JsonValue.Obj): PhysicalLocation? {
        val id = obj.string("id")?.takeIf { it.isNotBlank() } ?: return null
        val name = obj.string("name")?.takeIf { it.isNotBlank() } ?: return null
        return PhysicalLocation(
            id = LocationId(id),
            name = name,
            address = obj.string("address") ?: "",
            isMainWarehouse = obj.boolean("isMainWarehouse") ?: false
        )
    }

    fun encode(config: TenantLocationConfig): JsonValue.Obj = jsonObjectOf(
        "tenantId" to jsonOf(config.tenantId),
        "isMultiSiteEnabled" to jsonOf(config.isMultiSiteEnabled),
        "requireCustomerDispatchSj" to jsonOf(config.requireCustomerDispatchSj),
        "locations" to jsonArrayOf(config.locations.map(::encodeLocation)),
        "nodeLocations" to jsonArrayOf(
            config.nodeLocations.map { (node, location) ->
                jsonObjectOf(
                    "nodeKey" to jsonOf(node.key),
                    "nodeLabel" to jsonOf(node.displayName),
                    "locationId" to jsonOf(location.value)
                )
            }
        )
    )

    /**
     * @param tenantId tenant dari sesi, bukan dari muatan. Klien tidak boleh menentukan
     *   konfigurasi tenant mana yang sedang ia tulis.
     */
    fun decode(obj: JsonValue.Obj, tenantId: String): TenantLocationConfig {
        val locations = obj.objectArray("locations").mapNotNull(::decodeLocation)
        val known = locations.map { it.id }.toSet()

        val nodeLocations = obj.objectArray("nodeLocations").mapNotNull { entry ->
            val node = entry.string("nodeKey")?.let(FlowNodeRef::parse) ?: return@mapNotNull null
            val location = entry.string("locationId")?.takeIf { it.isNotBlank() }
                ?.let(::LocationId) ?: return@mapNotNull null
            // Pemetaan ke gedung yang tidak ada dibuang di sini, karena invarian
            // TenantLocationConfig menolaknya — dan muatan yang sedikit kotor tidak boleh
            // membuat seluruh penyimpanan gagal.
            if (location !in known) return@mapNotNull null
            node to location
        }.toMap()

        val multiSite = obj.boolean("isMultiSiteEnabled") ?: false
        return TenantLocationConfig(
            tenantId = tenantId,
            isMultiSiteEnabled = multiSite && locations.size >= 2,
            requireCustomerDispatchSj = obj.boolean("requireCustomerDispatchSj") ?: true,
            locations = locations,
            nodeLocations = nodeLocations
        )
    }
}
