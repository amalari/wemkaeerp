package com.eventverse.app.domain.transfer

import com.eventverse.app.domain.transfer.usecases.CreateInternalTransferCommand
import com.eventverse.app.domain.transfer.usecases.CreateInternalTransferSuratJalanUseCase
import com.eventverse.app.domain.transfer.usecases.CreateMakloonOutboundCommand
import com.eventverse.app.domain.transfer.usecases.CreateMakloonOutboundSuratJalanUseCase
import com.eventverse.app.domain.transfer.usecases.CreatePartialCustomerShipmentCommand
import com.eventverse.app.domain.transfer.usecases.CreatePartialCustomerShipmentUseCase
import com.eventverse.app.domain.transfer.usecases.CustomerDispatchCartonInput
import com.eventverse.app.domain.transfer.usecases.ReceiveSuratJalanCommand
import com.eventverse.app.domain.transfer.usecases.ReceiveSuratJalanUseCase
import com.eventverse.app.domain.workqueue.WorkCard
import com.eventverse.app.domain.workqueue.WorkCardId
import com.eventverse.app.domain.workqueue.WorkCardRepository
import com.eventverse.app.domain.workqueue.WorkCardStatus
import com.eventverse.app.domain.workqueue.WorkExecutionMode
import com.eventverse.app.domain.workqueue.WorkStationCatalog
import com.eventverse.app.domain.workqueue.WorkStationCode
import com.eventverse.app.domain.workqueue.WorkSubjectKind
import com.eventverse.app.domain.workqueue.WorkSubjectRef
import com.eventverse.app.domain.workqueue.WorkTrackingUnit
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TransferUseCaseTest {

    private val now = Clock.System.now()
    private val sampleSubject = WorkSubjectRef(
        kind = WorkSubjectKind.BULK_WORK_ORDER,
        subjectId = "wo-001",
        orderNumber = "PO-2026-088",
        articleName = "Cardigan Rajut"
    )

    private class FakeWorkCardRepository : WorkCardRepository {
        val storage = mutableMapOf<WorkCardId, WorkCard>()
        override suspend fun findById(id: WorkCardId): WorkCard? = storage[id]
        override suspend fun findBySubject(tenantId: String, subjectId: String): List<WorkCard> =
            storage.values.filter { it.tenantId == tenantId && it.subject.subjectId == subjectId }
        override suspend fun findByStation(tenantId: String, stationCode: WorkStationCode): List<WorkCard> =
            storage.values.filter { it.tenantId == tenantId && it.stationCode == stationCode }
        override suspend fun save(card: WorkCard) { storage[card.id] = card }
        override suspend fun saveAll(cards: List<WorkCard>) { cards.forEach { storage[it.id] = it } }
    }

    private class FakeSuratJalanRepository : SuratJalanRepository {
        val storage = mutableMapOf<SuratJalanId, SuratJalanManifest>()
        override suspend fun findById(id: SuratJalanId): SuratJalanManifest? = storage[id]
        override suspend fun findByNumber(tenantId: String, sjNumber: SuratJalanNumber): SuratJalanManifest? =
            storage.values.firstOrNull { it.tenantId == tenantId && it.sjNumber == sjNumber }
        override suspend fun findBySubject(tenantId: String, subjectId: String): List<SuratJalanManifest> =
            storage.values.filter { it.tenantId == tenantId && it.subject.subjectId == subjectId }
        override suspend fun findByTenant(tenantId: String, transferType: TransferType?): List<SuratJalanManifest> =
            storage.values.filter { it.tenantId == tenantId && (transferType == null || it.transferType == transferType) }
        override suspend fun save(manifest: SuratJalanManifest) { storage[manifest.id] = manifest }
    }

    @Test
    fun `create internal transfer preserves bundle numbers and marks dispatched`() = runTest {
        val cardRepo = FakeWorkCardRepository()
        val sjRepo = FakeSuratJalanRepository()
        val useCase = CreateInternalTransferSuratJalanUseCase(cardRepo, sjRepo)

        val card1 = WorkCard(
            id = WorkCardId("c1"),
            tenantId = "t1",
            subject = sampleSubject,
            stationCode = WorkStationCatalog.OBRAS.code,
            sizeLabel = "M",
            bundleNo = 1,
            queuedPcs = 20,
            wipPcs = 20,
            trackingUnit = WorkTrackingUnit.BUNDLE,
            createdAt = now
        )
        val card2 = WorkCard(
            id = WorkCardId("c2"),
            tenantId = "t1",
            subject = sampleSubject,
            stationCode = WorkStationCatalog.OBRAS.code,
            sizeLabel = "M",
            bundleNo = 2,
            queuedPcs = 20,
            wipPcs = 20,
            trackingUnit = WorkTrackingUnit.BUNDLE,
            createdAt = now
        )
        cardRepo.saveAll(listOf(card1, card2))

        val result = useCase(
            CreateInternalTransferCommand(
                tenantId = "t1",
                sjNumber = SuratJalanNumber("SJ-INT-001"),
                subject = sampleSubject,
                originLocationId = LocationId("gedung-rajut"),
                destinationLocationId = LocationId("gedung-finishing"),
                cardIdsToTransfer = listOf(card1.id, card2.id),
                carrierName = "Mobil Operasional 1",
                driverName = "Pak Asep",
                vehiclePlate = "D 8888 WMD",
                now = now
            )
        )

        assertTrue(result.isSuccess)
        val manifest = result.getOrThrow()
        assertEquals(TransferType.INTERNAL_SITE_TRANSFER, manifest.transferType)
        assertEquals(TransferStatus.DISPATCHED, manifest.status)
        assertEquals(40, manifest.totalPcs)
        assertEquals(2, manifest.items.size)

        // Invarian kunci: Nomor bundle tidak boleh hilang/melebur!
        assertEquals(1, manifest.items[0].bundleNo)
        assertEquals(card1.id, manifest.items[0].workCardId)
        assertEquals(2, manifest.items[1].bundleNo)
        assertEquals(card2.id, manifest.items[1].workCardId)
    }

    @Test
    fun `create makloon outbound aggregates bundles into lot and updates cards to subcontracted`() = runTest {
        val cardRepo = FakeWorkCardRepository()
        val sjRepo = FakeSuratJalanRepository()
        val useCase = CreateMakloonOutboundSuratJalanUseCase(cardRepo, sjRepo)

        // 3 bundle ukuran L @ 20 pcs = 60 pcs, 2 bundle ukuran XL @ 20 pcs = 40 pcs
        val cards = listOf(
            WorkCard(WorkCardId("c-l1"), "t1", sampleSubject, WorkStationCatalog.PASANG_KANCING.code, "L", 1, 20, 20, trackingUnit = WorkTrackingUnit.BUNDLE, createdAt = now),
            WorkCard(WorkCardId("c-l2"), "t1", sampleSubject, WorkStationCatalog.PASANG_KANCING.code, "L", 2, 20, 20, trackingUnit = WorkTrackingUnit.BUNDLE, createdAt = now),
            WorkCard(WorkCardId("c-l3"), "t1", sampleSubject, WorkStationCatalog.PASANG_KANCING.code, "L", 3, 20, 20, trackingUnit = WorkTrackingUnit.BUNDLE, createdAt = now),
            WorkCard(WorkCardId("c-xl1"), "t1", sampleSubject, WorkStationCatalog.PASANG_KANCING.code, "XL", 4, 20, 20, trackingUnit = WorkTrackingUnit.BUNDLE, createdAt = now),
            WorkCard(WorkCardId("c-xl2"), "t1", sampleSubject, WorkStationCatalog.PASANG_KANCING.code, "XL", 5, 20, 20, trackingUnit = WorkTrackingUnit.BUNDLE, createdAt = now)
        )
        cardRepo.saveAll(cards)

        val result = useCase(
            CreateMakloonOutboundCommand(
                tenantId = "t1",
                sjNumber = SuratJalanNumber("SJ-MAK-001"),
                subject = sampleSubject,
                vendorRef = "Konveksi Makloon Berkah",
                cardIdsToSubcontract = cards.map { it.id },
                unitServiceFeeIdr = 2500L,
                expectedReturnDate = LocalDate(2026, 9, 30),
                now = now
            )
        )

        assertTrue(result.isSuccess)
        val manifest = result.getOrThrow()
        assertEquals(TransferType.SUBCONTRACT_OUTBOUND, manifest.transferType)
        assertEquals("Konveksi Makloon Berkah", manifest.vendorRef)
        assertEquals(100, manifest.totalPcs)

        // Invarian kunci: Item digabung masal per size tanpa bundle individual
        assertEquals(2, manifest.items.size)
        val lItem = manifest.items.first { it.sizeLabel == "L" }
        assertEquals(60, lItem.qtyPcs)
        assertNull(lItem.bundleNo)

        val xlItem = manifest.items.first { it.sizeLabel == "XL" }
        assertEquals(40, xlItem.qtyPcs)
        assertNull(xlItem.bundleNo)

        // Kartu kerja di database harus berganti mode ke SUBCONTRACTED
        val reloadedCard = cardRepo.findById(WorkCardId("c-l1"))!!
        assertEquals(WorkExecutionMode.SUBCONTRACTED, reloadedCard.executionMode)
        assertEquals("Konveksi Makloon Berkah", reloadedCard.vendorRef)
    }

    @Test
    fun `partial customer shipment tracks backlog correctly across multiple deliveries`() = runTest {
        val sjRepo = FakeSuratJalanRepository()
        val useCase = CreatePartialCustomerShipmentUseCase(sjRepo)

        // Pengiriman tahap 1: 100 pcs dari total 200 pcs
        val shipment1 = useCase(
            CreatePartialCustomerShipmentCommand(
                tenantId = "t1",
                sjNumber = SuratJalanNumber("SJ-CUS-001"),
                subject = sampleSubject,
                customerName = "Brand Z Fashion",
                customerAddress = "Jakarta Selatan",
                totalOrderedPcs = 200,
                previouslyShippedPcs = 0,
                cartons = listOf(
                    CustomerDispatchCartonInput(CartonId("BOX-01"), "M", "Hitam", 50),
                    CustomerDispatchCartonInput(CartonId("BOX-02"), "L", "Hitam", 50)
                ),
                now = now
            )
        ).getOrThrow()

        assertEquals(100, shipment1.thisShipmentPcs)
        assertEquals(100, shipment1.totalShippedPcs)
        assertEquals(100, shipment1.remainingBacklogPcs)
        assertFalse(shipment1.isFullyShipped)
        assertEquals(2, shipment1.manifest.totalCartons)

        // Pengiriman tahap 2: 100 pcs sisa selesai
        val shipment2 = useCase(
            CreatePartialCustomerShipmentCommand(
                tenantId = "t1",
                sjNumber = SuratJalanNumber("SJ-CUS-002"),
                subject = sampleSubject,
                customerName = "Brand Z Fashion",
                customerAddress = "Jakarta Selatan",
                totalOrderedPcs = 200,
                previouslyShippedPcs = 100,
                cartons = listOf(
                    CustomerDispatchCartonInput(CartonId("BOX-03"), "XL", "Hitam", 100)
                ),
                now = now
            )
        ).getOrThrow()

        assertEquals(100, shipment2.thisShipmentPcs)
        assertEquals(200, shipment2.totalShippedPcs)
        assertEquals(0, shipment2.remainingBacklogPcs)
        assertTrue(shipment2.isFullyShipped)
    }

    @Test
    fun `receive surat jalan transitions manifest to received and reactivates internal cards`() = runTest {
        val cardRepo = FakeWorkCardRepository()
        val sjRepo = FakeSuratJalanRepository()
        val receiveUseCase = ReceiveSuratJalanUseCase(sjRepo, cardRepo)

        val card = WorkCard(
            id = WorkCardId("c-transit"),
            tenantId = "t1",
            subject = sampleSubject,
            stationCode = WorkStationCatalog.OBRAS.code,
            sizeLabel = "M",
            bundleNo = 1,
            queuedPcs = 20,
            wipPcs = 20,
            status = WorkCardStatus.IN_PROGRESS,
            trackingUnit = WorkTrackingUnit.BUNDLE,
            createdAt = now
        )
        cardRepo.save(card)

        val manifest = SuratJalanManifest(
            id = SuratJalanId("sj-recv-1"),
            tenantId = "t1",
            sjNumber = SuratJalanNumber("SJ-INT-RECV"),
            transferType = TransferType.INTERNAL_SITE_TRANSFER,
            subject = sampleSubject,
            originLocationId = LocationId("gudang-a"),
            destinationLocationId = LocationId("gudang-b"),
            status = TransferStatus.DISPATCHED,
            items = listOf(
                SuratJalanItem(
                    id = "item-1",
                    workCardId = card.id,
                    bundleNo = 1,
                    sizeLabel = "M",
                    qtyPcs = 20
                )
            ),
            dispatchedAt = now
        )
        sjRepo.save(manifest)

        val result = receiveUseCase(
            ReceiveSuratJalanCommand(
                manifestId = manifest.id,
                receiverName = "Kepala Finishing Gudang B",
                now = now
            )
        )

        assertTrue(result.isSuccess)
        val reloadedManifest = sjRepo.findById(manifest.id)!!
        assertEquals(TransferStatus.RECEIVED, reloadedManifest.status)

        // Kartu diaktifkan kembali ke status QUEUED di stasiun tujuan
        val reloadedCard = cardRepo.findById(card.id)!!
        assertEquals(WorkCardStatus.QUEUED, reloadedCard.status)
    }
}
