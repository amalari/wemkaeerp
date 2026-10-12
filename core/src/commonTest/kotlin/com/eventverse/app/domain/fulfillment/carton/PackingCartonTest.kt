package com.eventverse.app.domain.fulfillment.carton

import com.eventverse.app.domain.tenant.TenantId
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class PackingCartonTest {

    private val tenant = TenantId("ten-1")
    private val now = Instant.fromEpochMilliseconds(1_700_000_000_000)

    private fun aCarton(
        status: CartonStatus = CartonStatus.OPEN,
        items: Map<String, Int> = emptyMap(),
        qty: Int = 0,
        ticketIds: List<String> = emptyList(),
        sealNumber: SealNumber? = null
    ): PackingCarton {
        return PackingCarton(
            id = CartonId("ctn-1"),
            tenantId = tenant,
            productionRef = "PO-2026-0001",
            cartonNumber = CartonNumber("CTN-2026-0001"),
            itemBreakdown = items,
            quantityPcs = qty,
            unitTicketIds = ticketIds,
            sealNumber = sealNumber,
            status = status,
            createdAt = now,
            updatedAt = now
        )
    }

    @Test
    fun `create open carton should succeed`() {
        val carton = aCarton()
        assertEquals(CartonStatus.OPEN, carton.status)
        assertTrue(carton.isOpen)
    }

    @Test
    fun `add item to open carton should succeed`() {
        val carton = aCarton()
        val updated = carton.addItem("TKT-001", "L", 10)

        assertEquals(10, updated.quantityPcs)
        assertEquals(1, updated.unitTicketIds.size)
        assertEquals("L", updated.itemBreakdown.keys.first())
    }

    @Test
    fun `add item to sealed carton should throw exception`() {
        val sealed = aCarton(
            status = CartonStatus.SEALED,
            items = mapOf("M" to 20),
            qty = 20,
            ticketIds = listOf("TKT-001"),
            sealNumber = SealNumber.generate()
        )

        assertFailsWith<IllegalArgumentException> {
            sealed.addItem("TKT-002", "L", 10)
        }
    }

    @Test
    fun `seal empty carton should throw exception`() {
        val empty = aCarton()

        assertFailsWith<IllegalArgumentException> {
            empty.seal(SealNumber.generate(), 5.5, "Packer")
        }
    }

    @Test
    fun `seal carton with items should succeed`() {
        val carton = aCarton(
            items = mapOf("M" to 20, "L" to 10),
            qty = 30,
            ticketIds = listOf("TKT-001")
        )
        val sealed = carton.seal(SealNumber.generate(), 8.5, "Packer")

        assertEquals(CartonStatus.SEALED, sealed.status)
        assertEquals(8.5, sealed.grossWeightKg)
    }

    @Test
    fun `reopen sealed carton should succeed with reason`() {
        val sealed = aCarton(
            status = CartonStatus.SEALED,
            items = mapOf("M" to 20),
            qty = 20,
            ticketIds = listOf("TKT-001"),
            sealNumber = SealNumber.generate()
        )
        val reopened = sealed.reopen("Quantity tidak sesuai", "Supervisor", now)

        assertEquals(CartonStatus.REOPENED, reopened.status)
    }

    @Test
    fun `reopen open carton should throw exception`() {
        val open = aCarton()

        assertFailsWith<IllegalArgumentException> {
            open.reopen("Alasan", "Actor", now)
        }
    }

    @Test
    fun `seal again reopened carton should succeed`() {
        val sealed = aCarton(
            status = CartonStatus.SEALED,
            items = mapOf("M" to 20),
            qty = 20,
            ticketIds = listOf("TKT-001"),
            sealNumber = SealNumber.generate()
        )
        val reopened = sealed.reopen("Qty salah", "Super", now)
        val resealed = reopened.sealAgain(SealNumber.generate(), 9.0, "Packer", now)

        assertEquals(CartonStatus.SEALED, resealed.status)
    }

    @Test
    fun `CartonNumber format validation should reject invalid`() {
        assertFailsWith<IllegalArgumentException> {
            CartonNumber("INVALID")
        }
    }

    @Test
    fun `CartonNumber generate should format correctly`() {
        val number = CartonNumber.generate(2026, 1)
        assertEquals("CTN-2026-0001", number.value)
    }

    @Test
    fun `SealNumber generate should produce unique values`() {
        val seal1 = SealNumber.generate()
        val seal2 = SealNumber.generate()
        assertTrue(seal1.value != seal2.value)
    }
}
