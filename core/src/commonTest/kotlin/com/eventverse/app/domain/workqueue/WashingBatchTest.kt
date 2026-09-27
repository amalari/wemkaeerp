package com.eventverse.app.domain.workqueue

import com.eventverse.app.domain.workqueue.usecases.BundlePhotoItemInput
import com.eventverse.app.domain.workqueue.usecases.CompleteWashingSortCommand
import com.eventverse.app.domain.workqueue.usecases.CompleteWashingSortToLotsUseCase
import com.eventverse.app.domain.workqueue.usecases.CreateWashingBatchCommand
import com.eventverse.app.domain.workqueue.usecases.CreateWashingBatchUseCase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant

class FakeWashingBatchRepository : WashingBatchRepository {
    private val batches = mutableMapOf<String, WashingBatch>()

    override suspend fun findById(tenantId: String, batchId: WashingBatchId): WashingBatch? {
        return batches[batchId.value]?.takeIf { it.tenantId == tenantId }
    }

    override suspend fun findByTenant(tenantId: String, limit: Int): List<WashingBatch> {
        return batches.values.filter { it.tenantId == tenantId }.take(limit)
    }

    override suspend fun save(batch: WashingBatch) {
        batches[batch.id.value] = batch
    }
}

class FakeWorkCardRepository : WorkCardRepository {
    val storage = mutableMapOf<WorkCardId, WorkCard>()

    override suspend fun findById(id: WorkCardId): WorkCard? = storage[id]
    override suspend fun findBySubject(tenantId: String, subjectId: String): List<WorkCard> =
        storage.values.filter { it.tenantId == tenantId && it.subject.subjectId == subjectId }
    override suspend fun findByStation(tenantId: String, stationCode: WorkStationCode): List<WorkCard> =
        storage.values.filter { it.tenantId == tenantId && it.stationCode == stationCode }
    override suspend fun save(card: WorkCard) { storage[card.id] = card }
    override suspend fun saveAll(cards: List<WorkCard>) { cards.forEach { storage[it.id] = it } }
}

class WashingBatchTest {
    private val now = Instant.parse("2026-09-28T08:00:00Z")
    private val sampleSubjectA = WorkSubjectRef(
        kind = WorkSubjectKind.BULK_WORK_ORDER,
        subjectId = "po-101",
        orderNumber = "PO-101",
        articleName = "Kaos Polos Premium"
    )
    private val sampleSubjectB = WorkSubjectRef(
        kind = WorkSubjectKind.BULK_WORK_ORDER,
        subjectId = "po-102",
        orderNumber = "PO-102",
        articleName = "Polo Shirt Katun"
    )

    @Test
    fun `creating washing batch without bundle photo should throw exception`() = runTest {
        val cardRepo = FakeWorkCardRepository()
        val batchRepo = FakeWashingBatchRepository()
        val createUseCase = CreateWashingBatchUseCase(cardRepo, batchRepo)

        val bundle1 = WorkCard(
            id = WorkCardId("c1"),
            tenantId = "ten-01",
            subject = sampleSubjectA,
            stationCode = WorkStationCatalog.WASHING.code,
            sizeLabel = "L",
            bundleNo = 1,
            queuedPcs = 20,
            wipPcs = 20,
            trackingUnit = WorkTrackingUnit.BUNDLE,
            createdAt = now
        )
        cardRepo.save(bundle1)

        val cmd = CreateWashingBatchCommand(
            tenantId = "ten-01",
            batchCode = "WB-001",
            machineDrumNo = "Drum 1",
            washRecipe = "Softener",
            operatorName = "Joko",
            bundleInputs = listOf(
                BundlePhotoItemInput(bundle1.id, bundlePhotoKey = "") // KOSONG -> HARUS ERROR
            ),
            now = now
        )

        val result = createUseCase(cmd)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("foto bukti fisik") == true)
    }

    @Test
    fun `creating washing batch and sorting pasca-dryer should split lots per PO and per size`() = runTest {
        val cardRepo = FakeWorkCardRepository()
        val batchRepo = FakeWashingBatchRepository()
        val createUseCase = CreateWashingBatchUseCase(cardRepo, batchRepo)
        val completeUseCase = CompleteWashingSortToLotsUseCase(cardRepo, batchRepo)

        // 2 bundle dari PO-101 (Size L @ 20 pcs dan Size M @ 20 pcs)
        val b1 = WorkCard(
            id = WorkCardId("c1"),
            tenantId = "ten-01",
            subject = sampleSubjectA,
            stationCode = WorkStationCatalog.WASHING.code,
            sizeLabel = "L",
            bundleNo = 1,
            queuedPcs = 20,
            wipPcs = 20,
            trackingUnit = WorkTrackingUnit.BUNDLE,
            createdAt = now
        )
        val b2 = WorkCard(
            id = WorkCardId("c2"),
            tenantId = "ten-01",
            subject = sampleSubjectA,
            stationCode = WorkStationCatalog.WASHING.code,
            sizeLabel = "M",
            bundleNo = 2,
            queuedPcs = 20,
            wipPcs = 20,
            trackingUnit = WorkTrackingUnit.BUNDLE,
            createdAt = now
        )
        // 1 bundle dari PO-102 (Size XL @ 25 pcs)
        val b3 = WorkCard(
            id = WorkCardId("c3"),
            tenantId = "ten-01",
            subject = sampleSubjectB,
            stationCode = WorkStationCatalog.WASHING.code,
            sizeLabel = "XL",
            bundleNo = 1,
            queuedPcs = 25,
            wipPcs = 25,
            trackingUnit = WorkTrackingUnit.BUNDLE,
            createdAt = now
        )
        cardRepo.saveAll(listOf(b1, b2, b3))

        // 1. Buat Batch Cuci Masal (total 65 pcs, dengan foto bukti per bundle)
        val batch = createUseCase(
            CreateWashingBatchCommand(
                tenantId = "ten-01",
                batchCode = "WB-001",
                machineDrumNo = "Drum 1",
                washRecipe = "Softener",
                operatorName = "Joko",
                bundleInputs = listOf(
                    BundlePhotoItemInput(b1.id, "photo/b1.jpg"),
                    BundlePhotoItemInput(b2.id, "photo/b2.jpg"),
                    BundlePhotoItemInput(b3.id, "photo/b3.jpg")
                ),
                now = now
            )
        ).getOrThrow()

        assertEquals(3, batch.totalBundles)
        assertEquals(65, batch.totalInputPcs)
        assertEquals(WashingBatchStatus.IN_WASHER, batch.status)

        // 2. Meja Sortir Pasca-Dryer: Memisahkan kembali per PO dan per Ukuran
        val sortOutputs = listOf(
            WashingSortOutput(
                subjectId = "po-101",
                orderNumber = "PO-101",
                sizeLabel = "L",
                outputPcs = 20
            ),
            WashingSortOutput(
                subjectId = "po-101",
                orderNumber = "PO-101",
                sizeLabel = "M",
                outputPcs = 19, // Hilang 1 pcs di mesin cuci
                scrapPcs = 1
            ),
            WashingSortOutput(
                subjectId = "po-102",
                orderNumber = "PO-102",
                sizeLabel = "XL",
                outputPcs = 25
            )
        )

        val lotCards = completeUseCase(
            CompleteWashingSortCommand(
                tenantId = "ten-01",
                batchId = batch.id,
                sortOutputs = sortOutputs,
                downstreamStationCode = WorkStationCatalog.STEAM.code,
                now = now
            )
        ).getOrThrow()

        // Harus menghasilkan 3 kartu LOT terpisah di stasiun STEAM (Setrika)
        assertEquals(3, lotCards.size)

        // Verifikasi kartu Lot PO-101 Size L
        val lotL = lotCards.first { it.subject.subjectId == "po-101" && it.sizeLabel == "L" }
        assertEquals(WorkStationCatalog.STEAM.code, lotL.stationCode)
        assertEquals(WorkTrackingUnit.LOT_ACCUMULATION, lotL.trackingUnit)
        assertNull(lotL.bundleNo)
        assertEquals(20, lotL.queuedPcs)

        // Verifikasi kartu Lot PO-101 Size M
        val lotM = lotCards.first { it.subject.subjectId == "po-101" && it.sizeLabel == "M" }
        assertEquals(19, lotM.queuedPcs)
        assertEquals(1, lotM.scrapPcs)

        // Verifikasi kartu Lot PO-102 Size XL
        val lotXL = lotCards.first { it.subject.subjectId == "po-102" && it.sizeLabel == "XL" }
        assertEquals(25, lotXL.queuedPcs)

        // Bundle asal harus berstatus MERGED
        assertEquals(WorkCardStatus.MERGED, cardRepo.findById(b1.id)?.status)
        assertEquals(WorkCardStatus.MERGED, cardRepo.findById(b2.id)?.status)
        assertEquals(WorkCardStatus.MERGED, cardRepo.findById(b3.id)?.status)

        // Batch berstatus SORTED_COMPLETED
        val completedBatch = batchRepo.findById("ten-01", batch.id)
        assertEquals(WashingBatchStatus.SORTED_COMPLETED, completedBatch?.status)
        assertEquals(64, completedBatch?.totalOutputPcs)
    }
}
