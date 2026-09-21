package com.eventverse.app.domain.workqueue

import com.eventverse.app.domain.workqueue.usecases.CloseBundlesAtMergeGateCommand
import com.eventverse.app.domain.workqueue.usecases.CloseBundlesAtMergeGateUseCase
import com.eventverse.app.domain.workqueue.usecases.InitializeWorkCardsCommand
import com.eventverse.app.domain.workqueue.usecases.InitializeWorkCardsFromCuttingUseCase
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WorkQueueBoardTest {

    private val now = Clock.System.now()
    private val sampleSubject = WorkSubjectRef(
        kind = WorkSubjectKind.BULK_WORK_ORDER,
        subjectId = "wo-001",
        orderNumber = "PO-2026-088",
        articleName = "Cardigan Rajut"
    )

    // In-memory fake repository for testing use cases
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

    @Test
    fun `defect code should route ticket to the station declared in the catalog`() {
        assertEquals(WorkStationCatalog.OBRAS.code, WorkDefectCatalog.route(DefectCode("obras_lepas")))
        assertEquals(WorkStationCatalog.JAHIT_LURUS.code, WorkDefectCatalog.route(DefectCode("jahitan_melintir")))
        assertEquals(WorkStationCatalog.SUNTEK.code, WorkDefectCatalog.route(DefectCode("sambungan_rajut_lepas")))
        assertEquals(WorkStationCatalog.PASANG_KANCING.code, WorkDefectCatalog.route(DefectCode("kancing_copot")))
        assertEquals(WorkStationCatalog.STEAM.code, WorkDefectCatalog.route(DefectCode("noda_oli")))
    }

    @Test
    fun `nextAfter with activeStations filter should skip bypassed stations`() {
        // Misal kaos oblong polos: hanya aktif CUTTING, JAHIT_LURUS, OBRAS, STEAM, QC_FINAL, PACKAGING
        // Stasiun SUNTEK, LUBANG_KANCING, PASANG_KANCING, PASANG_ZIPER, WASHING di-skip!
        val activeStations = setOf(
            WorkStationCatalog.CUTTING.code,
            WorkStationCatalog.JAHIT_LURUS.code,
            WorkStationCatalog.OBRAS.code,
            WorkStationCatalog.STEAM.code,
            WorkStationCatalog.QC_FINAL.code,
            WorkStationCatalog.PACKAGING.code
        )

        val nextAfterObras = WorkStationCatalog.nextAfter(
            current = WorkStationCatalog.OBRAS.code,
            activeStations = activeStations
        )

        // Langsung melompat ke STEAM, melewati SUNTEK s/d WASHING!
        assertEquals(WorkStationCatalog.STEAM.code, nextAfterObras)

        val nextAfterPackaging = WorkStationCatalog.nextAfter(
            current = WorkStationCatalog.PACKAGING.code,
            activeStations = activeStations
        )
        assertNull(nextAfterPackaging)
    }

    @Test
    fun `initialize work cards from cutting should split into 20 pcs bundles correctly`() = runTest {
        val repo = FakeWorkCardRepository()
        val useCase = InitializeWorkCardsFromCuttingUseCase(repo)

        // Order 45 pcs Size M (seharusnya 20 + 20 + 5 = 3 bundle)
        val cards = useCase(
            InitializeWorkCardsCommand(
                tenantId = "tenant-1",
                subject = sampleSubject,
                quantitiesBySize = mapOf("M" to 45),
                bundleCapacity = 20,
                now = now
            )
        ).getOrThrow()

        assertEquals(3, cards.size)
        assertEquals(20, cards[0].queuedPcs)
        assertEquals(1, cards[0].bundleNo)
        assertEquals(20, cards[1].queuedPcs)
        assertEquals(2, cards[1].bundleNo)
        assertEquals(5, cards[2].queuedPcs)
        assertEquals(3, cards[2].bundleNo)
    }

    @Test
    fun `washing should merge bundle cards into a single lot card per size`() = runTest {
        val repo = FakeWorkCardRepository()
        val mergeUseCase = CloseBundlesAtMergeGateUseCase(repo)

        // Buat 2 bundle Size L @ 20 pcs yang sudah selesai di meja washing
        val bundle1 = WorkCard(
            id = WorkCardId("wo-001-WASHING-L-b1"),
            tenantId = "tenant-1",
            subject = sampleSubject,
            stationCode = WorkStationCatalog.WASHING.code,
            sizeLabel = "L",
            bundleNo = 1,
            queuedPcs = 20,
            wipPcs = 0,
            trackingUnit = WorkTrackingUnit.BUNDLE,
            status = WorkCardStatus.COMPLETED,
            createdAt = now
        )
        val bundle2 = WorkCard(
            id = WorkCardId("wo-001-WASHING-L-b2"),
            tenantId = "tenant-1",
            subject = sampleSubject,
            stationCode = WorkStationCatalog.WASHING.code,
            sizeLabel = "L",
            bundleNo = 2,
            queuedPcs = 20,
            wipPcs = 0,
            trackingUnit = WorkTrackingUnit.BUNDLE,
            status = WorkCardStatus.COMPLETED,
            createdAt = now
        )
        repo.saveAll(listOf(bundle1, bundle2))

        val lotCards = mergeUseCase(
            CloseBundlesAtMergeGateCommand(
                tenantId = "tenant-1",
                subjectId = "wo-001",
                now = now
            )
        ).getOrThrow()

        // Harus menghasilkan 1 kartu LOT_ACCUMULATION dengan total 40 pcs untuk Size L di stasiun STEAM
        assertEquals(1, lotCards.size)
        val lotCard = lotCards.first()
        assertEquals(WorkStationCatalog.STEAM.code, lotCard.stationCode)
        assertEquals(WorkTrackingUnit.LOT_ACCUMULATION, lotCard.trackingUnit)
        assertNull(lotCard.bundleNo)
        assertEquals(40, lotCard.queuedPcs)
        assertEquals(40, lotCard.wipPcs)

        // Status kartu bundle asal harus berubah menjadi MERGED
        val updatedBundle1 = repo.findById(bundle1.id)
        val updatedBundle2 = repo.findById(bundle2.id)
        assertEquals(WorkCardStatus.MERGED, updatedBundle1?.status)
        assertEquals(WorkCardStatus.MERGED, updatedBundle2?.status)
    }

    @Test
    fun `closing merge gate with unfinished bundle should throw exception`() = runTest {
        val repo = FakeWorkCardRepository()
        val mergeUseCase = CloseBundlesAtMergeGateUseCase(repo)

        // Masih ada 5 pcs WIP belum selesai cuci
        val unfinishedBundle = WorkCard(
            id = WorkCardId("wo-001-WASHING-M-b1"),
            tenantId = "tenant-1",
            subject = sampleSubject,
            stationCode = WorkStationCatalog.WASHING.code,
            sizeLabel = "M",
            bundleNo = 1,
            queuedPcs = 20,
            wipPcs = 5,
            trackingUnit = WorkTrackingUnit.BUNDLE,
            status = WorkCardStatus.IN_PROGRESS,
            createdAt = now
        )
        repo.save(unfinishedBundle)

        assertFailsWith<IllegalArgumentException> {
            mergeUseCase(
                CloseBundlesAtMergeGateCommand(
                    tenantId = "tenant-1",
                    subjectId = "wo-001",
                    now = now
                )
            ).getOrThrow()
        }
    }

    @Test
    fun `rework deposit should not trigger duplicate piecerate tariff`() {
        val regularDeposit = WorkDeposit(
            id = WorkDepositId("dep-reg"),
            cardId = WorkCardId("card-1"),
            operatorId = "op-budi",
            operatorName = "Budi",
            qtyPcs = 20,
            tariffSnapshotIdr = 2000L,
            isReworkDeposit = false,
            submittedAt = now
        )
        assertEquals(40000L, regularDeposit.earnedPayIdr)

        val reworkDeposit = WorkDeposit(
            id = WorkDepositId("dep-rew"),
            cardId = WorkCardId("card-1"),
            operatorId = "op-budi",
            operatorName = "Budi",
            qtyPcs = 4,
            tariffSnapshotIdr = 2000L,
            isReworkDeposit = true, // Setoran perbaikan cacat
            submittedAt = now
        )
        // Upah borongan perbaikan = 0 agar tidak klaim ganda atas kesalahannya
        assertEquals(0L, reworkDeposit.earnedPayIdr)

        val progress = listOf(regularDeposit, reworkDeposit).progressAgainst(20)
        assertEquals(24, progress.depositedPcs)
        assertEquals(4, progress.reworkPcs)
        assertEquals(20, progress.regularPcs)
        assertEquals(40000L, progress.totalEarnedPayIdr)
        assertTrue(progress.isComplete)
    }
}
