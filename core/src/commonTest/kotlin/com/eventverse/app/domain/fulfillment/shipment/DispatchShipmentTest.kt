package com.eventverse.app.domain.fulfillment.shipment

import com.eventverse.app.domain.tenant.TenantId
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DispatchShipmentTest {

    private val tenant = TenantId("ten-1")
    private val now = Instant.fromEpochMilliseconds(1_700_000_000_000)

    private fun aShipment(
        status: ShipmentStatus = ShipmentStatus.ISSUED,
        carrier: CarrierInfo = CarrierInfo(),
        destination: DestinationInfo = DestinationInfo()
    ): DispatchShipment {
        return DispatchShipment(
            id = ShipmentId("sj-1"),
            tenantId = tenant,
            shipmentNumber = ShipmentNumber("SJ-2026-0001"),
            stockOwnership = StockOwnership.OWNED,
            carrier = carrier,
            destination = destination,
            status = status,
            shippedAt = if (status == ShipmentStatus.IN_TRANSIT) now else null,
            deliveredAt = if (status == ShipmentStatus.DELIVERED) now else null,
            createdAt = now,
            updatedAt = now
        )
    }

    @Test
    fun `create issued shipment should succeed`() {
        val shipment = aShipment()
        assertEquals(ShipmentStatus.ISSUED, shipment.status)
        assertTrue(shipment.isActive)
    }

    @Test
    fun `mark in transit should succeed from issued`() {
        val shipment = aShipment()
        val inTransit = shipment.markInTransit(now)

        assertEquals(ShipmentStatus.IN_TRANSIT, inTransit.status)
        assertEquals(now, inTransit.shippedAt)
    }

    @Test
    fun `mark in transit from delivered should throw exception`() {
        val delivered = aShipment(status = ShipmentStatus.DELIVERED)

        assertFailsWith<IllegalArgumentException> {
            delivered.markInTransit(now)
        }
    }

    @Test
    fun `confirm delivered should succeed from in transit`() {
        val inTransit = aShipment(status = ShipmentStatus.IN_TRANSIT)
        val delivered = inTransit.confirmDelivered(now, "Penerima")

        assertEquals(ShipmentStatus.DELIVERED, delivered.status)
        assertEquals(now, delivered.deliveredAt)
    }

    @Test
    fun `confirm delivered from issued should throw exception`() {
        val issued = aShipment()

        assertFailsWith<IllegalArgumentException> {
            issued.confirmDelivered(now, "Penerima")
        }
    }

    @Test
    fun `cancel shipment should succeed from issued`() {
        val shipment = aShipment()
        val cancelled = shipment.cancel("Batal karena stok kosong", "Manager", now)

        assertEquals(ShipmentStatus.CANCELLED, cancelled.status)
    }

    @Test
    fun `cancel shipment from in transit should throw exception`() {
        val inTransit = aShipment(status = ShipmentStatus.IN_TRANSIT)

        assertFailsWith<IllegalArgumentException> {
            inTransit.cancel("Alasan", "Manager", now)
        }
    }

    @Test
    fun `ShipmentNumber format validation should reject invalid`() {
        assertFailsWith<IllegalArgumentException> {
            ShipmentNumber("INVALID")
        }
    }

    @Test
    fun `ShipmentNumber generate should format correctly`() {
        val number = ShipmentNumber.generate(2026, 1)
        assertEquals("SJ-2026-0001", number.value)
    }

    @Test
    fun `ShipmentStatus fromString should parse correctly`() {
        assertEquals(ShipmentStatus.ISSUED, ShipmentStatus.fromString("ISSUED"))
        assertEquals(ShipmentStatus.DELIVERED, ShipmentStatus.fromString("DELIVERED"))
    }

    @Test
    fun `CarrierInfo and DestinationInfo should have defaults`() {
        val shipment = aShipment()
        assertEquals("", shipment.carrier.mode)
        assertEquals("", shipment.destination.buyer)
    }
}
