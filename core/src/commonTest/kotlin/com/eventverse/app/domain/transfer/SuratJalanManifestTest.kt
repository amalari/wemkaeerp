package com.eventverse.app.domain.transfer

import com.eventverse.app.domain.workqueue.WorkCardId
import com.eventverse.app.domain.workqueue.WorkSubjectKind
import com.eventverse.app.domain.workqueue.WorkSubjectRef
import kotlinx.datetime.Clock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SuratJalanManifestTest {

    private val now = Clock.System.now()
    private val sampleSubject = WorkSubjectRef(
        kind = WorkSubjectKind.BULK_WORK_ORDER,
        subjectId = "wo-99",
        orderNumber = "PO-2026-099",
        articleName = "Hoodie Rajut Premium"
    )

    @Test
    fun `internal site transfer with same origin and destination should throw exception`() {
        assertFailsWith<IllegalArgumentException> {
            SuratJalanManifest(
                id = SuratJalanId("sj-1"),
                tenantId = "tenant-1",
                sjNumber = SuratJalanNumber("SJ/INT/2026/001"),
                transferType = TransferType.INTERNAL_SITE_TRANSFER,
                subject = sampleSubject,
                originLocationId = LocationId("gudang-a"),
                destinationLocationId = LocationId("gudang-a"), // Same!
                items = listOf(
                    SuratJalanItem(
                        id = "item-1",
                        bundleNo = 1,
                        sizeLabel = "M",
                        qtyPcs = 20
                    )
                )
            )
        }
    }

    @Test
    fun `internal site transfer without bundle numbers on items should throw exception`() {
        assertFailsWith<IllegalArgumentException> {
            SuratJalanManifest(
                id = SuratJalanId("sj-2"),
                tenantId = "tenant-1",
                sjNumber = SuratJalanNumber("SJ/INT/2026/002"),
                transferType = TransferType.INTERNAL_SITE_TRANSFER,
                subject = sampleSubject,
                originLocationId = LocationId("gudang-a"),
                destinationLocationId = LocationId("gudang-b"),
                items = listOf(
                    SuratJalanItem(
                        id = "item-1",
                        bundleNo = null, // Missing bundle number
                        sizeLabel = "M",
                        qtyPcs = 20
                    )
                )
            )
        }
    }

    @Test
    fun `subcontract outbound without vendorRef should throw exception`() {
        assertFailsWith<IllegalArgumentException> {
            SuratJalanManifest(
                id = SuratJalanId("sj-3"),
                tenantId = "tenant-1",
                sjNumber = SuratJalanNumber("SJ/SUB/2026/001"),
                transferType = TransferType.SUBCONTRACT_OUTBOUND,
                subject = sampleSubject,
                vendorRef = null, // Missing vendor
                items = listOf(
                    SuratJalanItem(
                        id = "item-1",
                        sizeLabel = "L",
                        qtyPcs = 100
                    )
                )
            )
        }
    }

    @Test
    fun `customer dispatch without customerName should throw exception`() {
        assertFailsWith<IllegalArgumentException> {
            SuratJalanManifest(
                id = SuratJalanId("sj-4"),
                tenantId = "tenant-1",
                sjNumber = SuratJalanNumber("SJ/CUS/2026/001"),
                transferType = TransferType.CUSTOMER_DISPATCH,
                subject = sampleSubject,
                customerName = "   ", // Blank customer name
                items = listOf(
                    SuratJalanItem(
                        id = "item-1",
                        sizeLabel = "S",
                        qtyPcs = 50
                    )
                )
            )
        }
    }

    @Test
    fun `manifest lifecycle and dispatch transitions work correctly`() {
        val draft = SuratJalanManifest(
            id = SuratJalanId("sj-life"),
            tenantId = "tenant-1",
            sjNumber = SuratJalanNumber("SJ/CUS/2026/002"),
            transferType = TransferType.CUSTOMER_DISPATCH,
            subject = sampleSubject,
            customerName = "PT Mode Sejahtera",
            customerAddress = "Jl. Sudirman 123",
            items = listOf(
                SuratJalanItem(
                    id = "item-1",
                    cartonId = CartonId("BOX-01"),
                    sizeLabel = "M",
                    qtyPcs = 30
                ),
                SuratJalanItem(
                    id = "item-2",
                    cartonId = CartonId("BOX-02"),
                    sizeLabel = "L",
                    qtyPcs = 30
                )
            )
        )

        assertEquals(TransferStatus.DRAFT, draft.status)
        assertEquals(60, draft.totalPcs)
        assertEquals(2, draft.totalCartons)
        assertFalse(draft.isDispatched)

        val dispatched = draft.dispatch(
            carrier = "Lalamove",
            driver = "Budi Santoso",
            plate = "D 1234 ABC",
            now = now
        )
        assertEquals(TransferStatus.DISPATCHED, dispatched.status)
        assertTrue(dispatched.isDispatched)
        assertEquals("Budi Santoso", dispatched.driverName)

        val received = dispatched.markReceived(now)
        assertEquals(TransferStatus.RECEIVED, received.status)
        assertFalse(received.isDispatched)

        assertFailsWith<IllegalArgumentException> {
            received.cancel("Salah kirim", now)
        }
    }
}
