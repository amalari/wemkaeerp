package com.eventverse.app.domain.sampling

import com.eventverse.app.domain.deal.Deal
import com.eventverse.app.domain.deal.DealId
import com.eventverse.app.domain.deal.DealRepository
import com.eventverse.app.domain.deal.DealStage
import com.eventverse.app.domain.deal.PurchaseOrder
import com.eventverse.app.domain.deal.PurchaseOrderId
import com.eventverse.app.domain.orgchart.OrgNodeId
import com.eventverse.app.domain.sampling.usecases.PublishSamplingSpkCommand
import com.eventverse.app.domain.sampling.usecases.PublishSamplingSpkFromDealUseCase
import com.eventverse.app.domain.tenant.TenantId
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.test.*

class PublishSamplingSpkFromDealUseCaseTest {

    private val tenantId = TenantId("ten-demo-001")
    private val dealId = "deal-001"
    private val now = Clock.System.now()

    private class FakeSamplingRepo(
        initialOrders: List<SamplingOrder> = emptyList()
    ) : SamplingOrderRepository {
        val orders = initialOrders.toMutableList()
        var counter = 50

        override suspend fun findById(id: SamplingOrderId): SamplingOrder? =
            orders.firstOrNull { it.id == id && !it.isArchived }

        override suspend fun findBySpkNumber(tenantId: TenantId, spkNumber: SpkNumber): SamplingOrder? =
            orders.firstOrNull { it.tenantId == tenantId && it.spkNumber == spkNumber && !it.isArchived }

        override suspend fun findAll(tenantId: TenantId, status: SamplingStatus?): List<SamplingOrder> =
            orders.filter { it.tenantId == tenantId && !it.isArchived && (status == null || it.status == status) }

        override suspend fun findByDealId(tenantId: TenantId, dealId: String): List<SamplingOrder> =
            orders.filter { it.tenantId == tenantId && it.dealId == dealId && !it.isArchived }

        override suspend fun save(order: SamplingOrder): SamplingOrder {
            val idx = orders.indexOfFirst { it.id == order.id }
            if (idx >= 0) orders[idx] = order else orders.add(order)
            return order
        }

        override suspend fun nextSpkNumber(tenantId: TenantId): SpkNumber {
            counter++
            val padded = counter.toString().padStart(4, '0')
            return SpkNumber("SPK-SMP-$padded")
        }

        override suspend fun archive(id: SamplingOrderId): Boolean {
            val idx = orders.indexOfFirst { it.id == id }
            if (idx >= 0) {
                orders[idx] = orders[idx].copy(archivedAt = Clock.System.now())
                return true
            }
            return false
        }
    }

    private class FakeDealRepo(var deal: Deal? = null) : DealRepository {
        override suspend fun findById(tenantId: TenantId, id: DealId): Deal? =
            if (deal?.id == id) deal else null

        override suspend fun findBySourceLeadId(tenantId: TenantId, leadId: com.eventverse.app.domain.crm.LeadId): Deal? = null
        override suspend fun findActive(tenantId: TenantId, ownerReachIds: Set<OrgNodeId>?): List<Deal> = listOfNotNull(deal)
        override suspend fun save(deal: Deal): Result<Deal> {
            this.deal = deal
            return Result.success(deal)
        }
        override suspend fun existsForContact(tenantId: TenantId, contactId: com.eventverse.app.domain.crm.ContactId, excludeDealId: DealId?): Boolean = false
        override suspend fun findPurchaseOrders(tenantId: TenantId, dealId: DealId): List<PurchaseOrder> = emptyList()
        override suspend fun findPurchaseOrderById(tenantId: TenantId, poId: PurchaseOrderId): PurchaseOrder? = null
        override suspend fun savePurchaseOrder(po: PurchaseOrder): Result<PurchaseOrder> = Result.success(po)
    }

    private fun sampleOrder(
        id: String = "smp-001",
        spk: String = "SPK-SMP-0050",
        matrix: List<SizeChartRow> = listOf(
            SizeChartRow(id = SAMPLING_QTY_ROW_ID, pomName = SAMPLING_QTY_ROW_NAME, values = mapOf("ALL SIZE" to "2", "S" to "2")),
            SizeChartRow(id = "pom_lebar_dada", pomName = "Lebar Dada", values = mapOf("ALL SIZE" to "60", "S" to "50")),
            SizeChartRow(id = "pom_panjang_baju", pomName = "Panjang Baju", values = mapOf("ALL SIZE" to "72", "S" to "68"))
        )
    ) = SamplingOrder(
        id = SamplingOrderId(id),
        tenantId = tenantId,
        spkNumber = SpkNumber(spk),
        clientName = "Morfeen Studio",
        styleName = "Hoodie Rajut Vintage",
        status = SamplingStatus.DRAFT,
        stageCode = SamplingPipelineStage.NEW_INTAKE.toStageCode(),
        dealId = dealId,
        sampleQuantity = 4,
        sizeMatrix = matrix,
        createdAt = now,
        updatedAt = now
    )

    private fun testDeal() = Deal(
        id = DealId(dealId),
        tenantId = tenantId,
        contactId = com.eventverse.app.domain.crm.ContactId("con-001"),
        title = com.eventverse.app.domain.deal.DealTitle("PO Hoodie"),
        stage = DealStage.OPEN,
        createdAt = now,
        updatedAt = now
    )

    @Test
    fun publish_withMultiSize_shouldSplitIntoMultipleSpks() = runTest {
        val root = sampleOrder()
        val samplingRepo = FakeSamplingRepo(listOf(root))
        val dealRepo = FakeDealRepo(testDeal())

        val useCase = PublishSamplingSpkFromDealUseCase(
            samplingRepository = samplingRepo,
            dealRepository = dealRepo,
            idGenerator = { "smp-generated" }
        )

        val result = useCase(
            PublishSamplingSpkCommand(
                tenantId = tenantId,
                dealId = dealId,
                samplingOrderId = root.id
            )
        )

        assertTrue(result.isSuccess)
        val orders = result.getOrThrow()
        assertEquals(2, orders.size, "Harus menghasilkan 2 SPK terpisah untuk ALL SIZE dan S")

        // SPK 1 (Root)
        val spk1 = orders[0]
        assertEquals(root.id, spk1.id)
        assertEquals(SpkNumber("SPK-SMP-0050"), spk1.spkNumber)
        assertEquals("ALL SIZE", spk1.sizeLabel)
        assertEquals(2, spk1.sampleQuantity)
        assertEquals(SizeMode.ALL_SIZE, spk1.sizeMode)
        assertEquals(SamplingStatus.IN_PROGRESS, spk1.status)
        assertNull(spk1.parentSamplingOrderId)

        // SPK 2 (Child)
        val spk2 = orders[1]
        assertNotEquals(root.id, spk2.id)
        assertEquals(SpkNumber("SPK-SMP-0051"), spk2.spkNumber)
        assertEquals("S", spk2.sizeLabel)
        assertEquals(2, spk2.sampleQuantity)
        assertEquals(SizeMode.MULTI_SIZE, spk2.sizeMode)
        assertEquals(SamplingStatus.IN_PROGRESS, spk2.status)
        assertEquals(root.id, spk2.parentSamplingOrderId)

        // Deal stage transitioned to PO_RECEIVED
        assertEquals(DealStage.PO_RECEIVED, dealRepo.deal?.stage)
    }

    @Test
    fun publish_isIdempotent_shouldNotDuplicateSpks() = runTest {
        val root = sampleOrder()
        val samplingRepo = FakeSamplingRepo(listOf(root))
        val dealRepo = FakeDealRepo(testDeal())

        val useCase = PublishSamplingSpkFromDealUseCase(
            samplingRepository = samplingRepo,
            dealRepository = dealRepo,
            idGenerator = { "smp-generated" }
        )

        val firstCall = useCase(PublishSamplingSpkCommand(tenantId, dealId, root.id)).getOrThrow()
        assertEquals(2, firstCall.size)
        assertEquals(2, samplingRepo.orders.size)

        // Panggil kedua kali
        val secondCall = useCase(PublishSamplingSpkCommand(tenantId, dealId, root.id)).getOrThrow()
        assertEquals(2, secondCall.size)
        assertEquals(2, samplingRepo.orders.size, "Jumlah order di repo tidak boleh bertambah")
    }

    @Test
    fun publish_withSingleSize_shouldPublishSingleSpk() = runTest {
        val singleSizeMatrix = listOf(
            SizeChartRow(id = SAMPLING_QTY_ROW_ID, pomName = SAMPLING_QTY_ROW_NAME, values = mapOf("ALL SIZE" to "2")),
            SizeChartRow(id = "pom_lebar_dada", pomName = "Lebar Dada", values = mapOf("ALL SIZE" to "60")),
            SizeChartRow(id = "pom_panjang_baju", pomName = "Panjang Baju", values = mapOf("ALL SIZE" to "72"))
        )
        val root = sampleOrder(matrix = singleSizeMatrix)
        val samplingRepo = FakeSamplingRepo(listOf(root))
        val dealRepo = FakeDealRepo(testDeal())

        val useCase = PublishSamplingSpkFromDealUseCase(samplingRepo, dealRepo)
        val result = useCase(PublishSamplingSpkCommand(tenantId, dealId, root.id)).getOrThrow()

        assertEquals(1, result.size)
        assertEquals("ALL SIZE", result[0].sizeLabel)
        assertEquals(2, result[0].sampleQuantity)
        assertEquals(SamplingStatus.IN_PROGRESS, result[0].status)
    }
}
