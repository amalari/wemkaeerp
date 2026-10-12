package com.eventverse.app.shared.fulfillment

import com.eventverse.app.domain.fulfillment.carton.CartonId
import com.eventverse.app.domain.fulfillment.carton.CartonNumber
import com.eventverse.app.domain.fulfillment.carton.CartonStatus
import com.eventverse.app.domain.fulfillment.carton.PackingCarton
import com.eventverse.app.domain.fulfillment.carton.SealNumber
import com.eventverse.app.domain.fulfillment.shipment.CarrierInfo
import com.eventverse.app.domain.fulfillment.shipment.DestinationInfo
import com.eventverse.app.domain.fulfillment.shipment.DispatchShipment
import com.eventverse.app.domain.fulfillment.shipment.ShipmentId
import com.eventverse.app.domain.fulfillment.shipment.ShipmentNumber
import com.eventverse.app.domain.fulfillment.shipment.ShipmentStatus
import com.eventverse.app.domain.fulfillment.shipment.StockOwnership
import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.shared.json.JsonParser
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Kontrak JSON Track 0 (TRD-FUL-001 §5): bentuk terkunci + round-trip + penolakan payload korup.
 * Fixture di bawah ini adalah **mock eksternal** — payload seolah-olah ditulis tenant lain/server
 * lain; codec wajib membacanya persis dan menolaknya bila isinya tak sah.
 */
class PackingShipmentCodecTest {

    private val tenant = TenantId("wemade-demo")
    private val t1 = Instant.parse("2026-10-12T08:00:00Z")
    private val t2 = Instant.parse("2026-10-12T09:30:00Z")

    private val openCarton = PackingCarton(
        id = CartonId("ctn-01"),
        tenantId = tenant,
        productionRef = "PO-8817",
        cartonNumber = CartonNumber.generate(2026, 1),
        itemBreakdown = mapOf("S" to 20, "M" to 20),
        quantityPcs = 40,
        grossWeightKg = 12.5,
        unitTicketIds = listOf("TCK-1", "TCK-2"),
        status = CartonStatus.OPEN,
        createdAt = t1,
        updatedAt = t2
    )

    private val issuedShipment = DispatchShipment(
        id = ShipmentId("shp-01"),
        tenantId = tenant,
        shipmentNumber = ShipmentNumber.generate(2026, 1),
        stockOwnership = StockOwnership.OWNED,
        carrier = CarrierInfo(mode = "TRUK", expedition = "JNE Trucking", driver = "Pak Budi"),
        destination = DestinationInfo(buyer = "PT Retail Nusantara", address = "Jl. Sudirman 1", city = "Jakarta"),
        status = ShipmentStatus.ISSUED,
        createdAt = t1,
        updatedAt = t2
    )

    // ── Fixture mock eksternal (bentuk terkunci — ubah hanya lewat Track 0) ──────────────

    private val cartonFixture = """{"id":"ctn-99","tenantId":"bordir-uji","productionRef":"SPK-77","cartonNumber":"CTN-2026-0042","itemBreakdown":{"M":10,"L":5},"quantityPcs":15,"grossWeightKg":8.25,"unitTicketIds":["TCK-A","TCK-B","TCK-C"],"sealNumber":"SL-12345","status":"SEALED","statusLabel":"Karton Disegel","createdAt":"2026-10-01T07:00:00Z","updatedAt":"2026-10-01T07:15:00Z","notes":"fixture Track 0"}"""

    private val shipmentFixture = """{"id":"shp-99","tenantId":"bordir-uji","shipmentNumber":"SJ-2026-0017","stockOwnership":"CONSIGNED","carrier":{"mode":"KURIR","expedition":"","container":"","driver":"","trackingNumber":"RESI-555"},"destination":{"buyer":"Studio Bordir X","address":"Jl. Merdeka 9","contact":"Ibu Ani","city":"Bandung"},"cartonIds":["ctn-99"],"status":"IN_TRANSIT","statusLabel":"Sedang Diantar","shippedAt":"2026-10-02T03:00:00Z","deliveredAt":null,"createdAt":"2026-10-01T07:20:00Z","updatedAt":"2026-10-02T03:00:00Z","notes":""}"""

    // ── Bentuk terkunci ──────────────────────────────────────────────────────────────────

    @Test
    fun `encode - bentuk kontrak karton terkunci`() {
        assertEquals(
            """{"id":"ctn-01","tenantId":"wemade-demo","productionRef":"PO-8817","cartonNumber":"CTN-2026-0001","itemBreakdown":{"S":20,"M":20},"quantityPcs":40,"grossWeightKg":12.5,"unitTicketIds":["TCK-1","TCK-2"],"sealNumber":null,"status":"OPEN","statusLabel":"Karton Dibuka","createdAt":"2026-10-12T08:00:00Z","updatedAt":"2026-10-12T09:30:00Z","notes":""}""",
            PackingCartonCodec.encode(openCarton).encode()
        )
    }

    @Test
    fun `encode - bentuk kontrak shipment terkunci termasuk pivot cartonIds`() {
        assertEquals(
            """{"id":"shp-01","tenantId":"wemade-demo","shipmentNumber":"SJ-2026-0001","stockOwnership":"OWNED","carrier":{"mode":"TRUK","expedition":"JNE Trucking","container":"","driver":"Pak Budi","trackingNumber":""},"destination":{"buyer":"PT Retail Nusantara","address":"Jl. Sudirman 1","contact":"","city":"Jakarta"},"cartonIds":["ctn-01"],"status":"ISSUED","statusLabel":"Surat Jalan Diterbitkan","shippedAt":null,"deliveredAt":null,"createdAt":"2026-10-12T08:00:00Z","updatedAt":"2026-10-12T09:30:00Z","notes":""}""",
            DispatchShipmentCodec.encode(issuedShipment, listOf(CartonId("ctn-01"))).encode()
        )
    }

    // ── Round-trip ───────────────────────────────────────────────────────────────────────

    @Test
    fun `round-trip - karton OPEN identik setelah encode-decode`() {
        val decoded = PackingCartonCodec.decode(PackingCartonCodec.encode(openCarton))
        assertEquals(openCarton, decoded)
    }

    @Test
    fun `round-trip - karton SEALED membawa seal number`() {
        val sealed = openCarton.seal(SealNumber("SL-9001"), 14.0, "op-packing")
        val decoded = PackingCartonCodec.decode(PackingCartonCodec.encode(sealed))
        assertEquals(sealed, decoded)
        assertEquals(SealNumber("SL-9001"), decoded.sealNumber)
        assertEquals(CartonStatus.SEALED, decoded.status)
    }

    @Test
    fun `round-trip - shipment DELIVERED membawa timestamp`() {
        val delivered = issuedShipment
            .markInTransit(t1)
            .confirmDelivered(t2, "op-packing")
        val decoded = DispatchShipmentCodec.decode(DispatchShipmentCodec.encode(delivered))
        assertEquals(delivered, decoded.shipment)
    }

    @Test
    fun `round-trip - pivot cartonIds utuh dua arah`() {
        val decoded = DispatchShipmentCodec.decode(
            DispatchShipmentCodec.encode(issuedShipment, listOf(CartonId("ctn-01"), CartonId("ctn-02")))
        )
        assertEquals(listOf(CartonId("ctn-01"), CartonId("ctn-02")), decoded.cartonIds)
    }

    // ── Fixture mock eksternal ───────────────────────────────────────────────────────────

    @Test
    fun `decode - fixture karton eksternal terbaca persis`() {
        val carton = PackingCartonCodec.decode(JsonParser.parseObject(cartonFixture))
        assertEquals(CartonId("ctn-99"), carton.id)
        assertEquals(TenantId("bordir-uji"), carton.tenantId)
        assertEquals(mapOf("M" to 10, "L" to 5), carton.itemBreakdown)
        assertEquals(15, carton.quantityPcs)
        assertEquals(SealNumber("SL-12345"), carton.sealNumber)
        assertEquals(CartonStatus.SEALED, carton.status)
        assertEquals("fixture Track 0", carton.notes)
    }

    @Test
    fun `decode - fixture shipment eksternal terbaca persis termasuk pivot`() {
        val decoded = DispatchShipmentCodec.decode(JsonParser.parseObject(shipmentFixture))
        assertEquals(ShipmentNumber("SJ-2026-0017"), decoded.shipment.shipmentNumber)
        assertEquals(StockOwnership.CONSIGNED, decoded.shipment.stockOwnership)
        assertEquals("RESI-555", decoded.shipment.carrier.trackingNumber)
        assertEquals(ShipmentStatus.IN_TRANSIT, decoded.shipment.status)
        assertEquals(Instant.parse("2026-10-02T03:00:00Z"), decoded.shipment.shippedAt)
        assertEquals(listOf(CartonId("ctn-99")), decoded.cartonIds)
    }

    // ── Penolakan payload korup (Kontrak 4: tolak, bukan fallback senyap) ────────────────

    @Test
    fun `decode - status karton tak dikenal ditolak`() {
        val corrupt = PackingCartonCodec.encode(openCarton).encode().replace("\"OPEN\"", "\"LUSUH\"")
        assertFailsWith<IllegalArgumentException> {
            PackingCartonCodec.decode(JsonParser.parseObject(corrupt))
        }
    }

    @Test
    fun `decode - status shipment tak dikenal ditolak`() {
        val corrupt = DispatchShipmentCodec.encode(issuedShipment).encode().replace("\"ISSUED\"", "\"HILANG\"")
        assertFailsWith<IllegalArgumentException> {
            DispatchShipmentCodec.decode(JsonParser.parseObject(corrupt))
        }
    }

    @Test
    fun `decode - nomor karton di luar format ditolak oleh value object`() {
        val corrupt = PackingCartonCodec.encode(openCarton).encode()
            .replace("CTN-2026-0001", "KARDUS-BEBAS")
        assertFailsWith<IllegalArgumentException> {
            PackingCartonCodec.decode(JsonParser.parseObject(corrupt))
        }
    }

    @Test
    fun `decode - qty itemBreakdown bukan angka ditolak`() {
        val corrupt = PackingCartonCodec.encode(openCarton).encode().replace("\"S\":20", "\"S\":\"dua\"")
        assertFailsWith<IllegalArgumentException> {
            PackingCartonCodec.decode(JsonParser.parseObject(corrupt))
        }
    }

    @Test
    fun `decode - field tambahan tak dikenal diabaikan agar payload depan-belakang kompatibel`() {
        val extended = PackingCartonCodec.encode(openCarton).encode()
            .replace("\"notes\":\"\"", "\"notes\":\"\",\"futureField\":123")
        val decoded = PackingCartonCodec.decode(JsonParser.parseObject(extended))
        assertEquals(openCarton, decoded)
        assertTrue(decoded.unitTicketIds.size == 2)
    }
}
