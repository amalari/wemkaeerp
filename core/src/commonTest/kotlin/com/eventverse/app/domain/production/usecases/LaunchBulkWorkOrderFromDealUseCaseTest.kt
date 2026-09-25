package com.eventverse.app.domain.production.usecases

import com.eventverse.app.domain.crm.ContactId
import com.eventverse.app.domain.deal.Deal
import com.eventverse.app.domain.deal.DealId
import com.eventverse.app.domain.deal.DealRepository
import com.eventverse.app.domain.deal.DealTitle
import com.eventverse.app.domain.deal.PoOrigin
import com.eventverse.app.domain.deal.PoNumber
import com.eventverse.app.domain.deal.PurchaseOrder
import com.eventverse.app.domain.deal.PurchaseOrderId
import com.eventverse.app.domain.deal.PurchaseOrderLine
import com.eventverse.app.domain.orgchart.OrgNodeId
import com.eventverse.app.domain.production.BulkProductionStatus
import com.eventverse.app.domain.production.BulkSpkNumber
import com.eventverse.app.domain.production.BulkWorkOrder
import com.eventverse.app.domain.production.BulkWorkOrderId
import com.eventverse.app.domain.production.BulkWorkOrderRepository
import com.eventverse.app.domain.sampling.SAMPLING_QTY_ROW_ID
import com.eventverse.app.domain.sampling.SAMPLING_QTY_ROW_NAME
import com.eventverse.app.domain.sampling.STANDARD_SAMPLING_SIZE_COLUMNS
import com.eventverse.app.domain.sampling.SamplingOrder
import com.eventverse.app.domain.sampling.SamplingOrderRepository
import com.eventverse.app.domain.sampling.SamplingOrderId
import com.eventverse.app.domain.sampling.SamplingStatus
import com.eventverse.app.domain.sampling.SpkNumber
import com.eventverse.app.domain.sampling.SizeChartRow
import com.eventverse.app.domain.tenant.TenantId
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private val NOW = Instant.parse("2026-09-17T10:00:00Z")
private val TENANT = TenantId("tnt-1")
private val DEAL = DealId("deal-1")

private fun purchaseOrder(vararg lines: Pair<String, Double>): List<PurchaseOrder> = listOf(
    PurchaseOrder(
        id = PurchaseOrderId("po-1"),
        tenantId = TENANT,
        dealId = DEAL,
        poNumber = PoNumber("PO-001"),
        poDate = LocalDate(2026, 9, 1),
        origin = PoOrigin.MANUAL,
        lines = lines.map { PurchaseOrderLine(it.first, it.second, 50_000L) },
        recordedBy = "usr-1",
        createdAt = NOW
    )
)

/** Matriks ukuran dengan kolom-kolom [active] yang POM dan qty-nya lengkap. */
private fun matrixWithActive(vararg active: String): List<SizeChartRow> = listOf(
    SizeChartRow(
        id = SAMPLING_QTY_ROW_ID,
        pomName = SAMPLING_QTY_ROW_NAME,
        values = STANDARD_SAMPLING_SIZE_COLUMNS.associateWith { col -> if (col in active) "2" else "" }
    ),
    SizeChartRow(
        id = "pom_lebar_dada",
        pomName = "Lebar Dada",
        values = STANDARD_SAMPLING_SIZE_COLUMNS.associateWith { col -> if (col in active) "50" else "" }
    )
)

private fun goldenSample(matrix: List<SizeChartRow> = matrixWithActive("S", "M", "L")) = SamplingOrder(
    id = SamplingOrderId("smp-acc"),
    tenantId = TENANT,
    spkNumber = SpkNumber("SPK-SMP-0001"),
    clientName = "PT Sinar Jaya",
    styleName = "Kemeja PDH",
    status = SamplingStatus.ACC_APPROVED,
    dealId = DEAL.value,
    sizeMatrix = matrix,
    createdAt = NOW,
    updatedAt = NOW
)

private fun deal() = Deal(
    id = DEAL,
    tenantId = TENANT,
    contactId = ContactId("ct-1"),
    title = DealTitle("Deal Kemeja PDH"),
    createdAt = NOW,
    updatedAt = NOW
)

private class FakeWorkOrderRepository : BulkWorkOrderRepository {
    val saved = mutableListOf<BulkWorkOrder>()
    private var counter = 0

    override suspend fun findById(id: BulkWorkOrderId) = saved.firstOrNull { it.id == id }

    override suspend fun findAll(tenantId: TenantId, status: BulkProductionStatus?) = saved.toList()

    override suspend fun findByDealId(tenantId: TenantId, dealId: String) = saved.filter { it.dealId == dealId }

    override suspend fun save(workOrder: BulkWorkOrder): BulkWorkOrder {
        saved += workOrder
        return workOrder
    }

    override suspend fun nextSpkNumber(tenantId: TenantId): BulkSpkNumber {
        counter += 1
        return BulkSpkNumber("SPK-MSL-${counter.toString().padStart(4, '0')}")
    }

    override suspend fun archive(id: BulkWorkOrderId) = true
}

private class FakeDealRepository(
    private val deal: Deal?,
    private val purchaseOrders: List<PurchaseOrder> = emptyList()
) : DealRepository {
    override suspend fun findById(tenantId: TenantId, id: DealId) = deal?.takeIf { it.id == id }

    override suspend fun findBySourceLeadId(
        tenantId: TenantId,
        leadId: com.eventverse.app.domain.crm.LeadId
    ): Deal? = null

    override suspend fun findActive(tenantId: TenantId, ownerReachIds: Set<OrgNodeId>?): List<Deal> = emptyList()

    override suspend fun save(deal: Deal): Result<Deal> = Result.success(deal)

    override suspend fun existsForContact(
        tenantId: TenantId,
        contactId: com.eventverse.app.domain.crm.ContactId,
        excludeDealId: DealId?
    ): Boolean = false

    override suspend fun findPurchaseOrders(tenantId: TenantId, dealId: DealId) = purchaseOrders

    override suspend fun findPurchaseOrderById(tenantId: TenantId, poId: PurchaseOrderId): PurchaseOrder? = null

    override suspend fun savePurchaseOrder(po: PurchaseOrder): Result<PurchaseOrder> = Result.success(po)
}

private class FakeSamplingOrderRepository(private val orders: List<SamplingOrder>) : SamplingOrderRepository {
    override suspend fun findById(id: SamplingOrderId) = orders.firstOrNull { it.id == id }

    override suspend fun findBySpkNumber(tenantId: TenantId, spkNumber: SpkNumber) =
        orders.firstOrNull { it.spkNumber == spkNumber }

    override suspend fun findAll(tenantId: TenantId, status: SamplingStatus?) = orders.toList()

    override suspend fun findByDealId(tenantId: TenantId, dealId: String) = orders.filter { it.dealId == dealId }

    override suspend fun save(order: SamplingOrder) = order

    override suspend fun nextSpkNumber(tenantId: TenantId) = SpkNumber("SPK-SMP-9999")

    override suspend fun archive(id: SamplingOrderId) = true
}

private fun useCase(
    workOrders: FakeWorkOrderRepository = FakeWorkOrderRepository(),
    deal: Deal? = deal(),
    purchaseOrders: List<PurchaseOrder> = emptyList(),
    samplingOrders: List<SamplingOrder> = listOf(goldenSample())
): LaunchBulkWorkOrderFromDealUseCase = LaunchBulkWorkOrderFromDealUseCase(
    workOrderRepository = workOrders,
    dealRepository = FakeDealRepository(deal, purchaseOrders),
    samplingOrderRepository = FakeSamplingOrderRepository(samplingOrders)
)

private fun command() = LaunchBulkWorkOrderCommand(tenantId = TENANT, dealId = DEAL)

class LaunchBulkWorkOrderFromDealUseCaseTest {

    @Test
    fun `launch from multi size po should emit one spk per size`() = runTest {
        val workOrders = FakeWorkOrderRepository()
        val useCase = useCase(
            workOrders = workOrders,
            purchaseOrders = purchaseOrder("S" to 100.0, "M" to 200.0, "L" to 150.0)
        )

        val result = useCase(command()).getOrThrow()

        assertEquals(listOf("S", "M", "L"), result.map { it.sizeLabel })
        result.forEach { order ->
            assertEquals(1, order.sizeBreakdown.size)
            assertEquals(order.sizeLabel, order.sizeBreakdown.single().sizeLabel)
        }
        assertEquals(3, workOrders.saved.size)
    }

    @Test
    fun `launch twice should only create spk for sizes not yet released`() = runTest {
        val workOrders = FakeWorkOrderRepository()
        val useCase = useCase(
            workOrders = workOrders,
            purchaseOrders = purchaseOrder("S" to 100.0, "M" to 200.0, "L" to 150.0)
        )
        useCase(command()).getOrThrow()

        val second = useCase(command()).getOrThrow()

        assertEquals(3, second.size)
        assertEquals(3, workOrders.saved.size)
    }

    @Test
    fun `launch when golden sample lacks one size should fail before saving anything`() = runTest {
        val workOrders = FakeWorkOrderRepository()
        val useCase = useCase(
            workOrders = workOrders,
            purchaseOrders = purchaseOrder("S" to 100.0, "M" to 200.0),
            samplingOrders = listOf(goldenSample(matrixWithActive("S")))
        )

        val result = useCase(command())

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("tidak mencakup ukuran M"))
        assertEquals(0, workOrders.saved.size)
    }

    @Test
    fun `launch without acc approved sample should fail`() = runTest {
        val useCase = useCase(
            purchaseOrders = purchaseOrder("S" to 100.0),
            samplingOrders = listOf(goldenSample().copy(status = SamplingStatus.IN_PROGRESS))
        )

        val result = useCase(command())

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("belum punya sampel ber-ACC"))
    }

    @Test
    fun `non standard size label should launch without matrix check`() = runTest {
        val workOrders = FakeWorkOrderRepository()
        val useCase = useCase(
            workOrders = workOrders,
            purchaseOrders = purchaseOrder("KEMEJA PDH UKURAN L" to 120.0),
            samplingOrders = listOf(goldenSample(matrixWithActive("ALL SIZE")))
        )

        val result = useCase(command()).getOrThrow()

        assertEquals(1, result.size)
        assertEquals("KEMEJA PDH UKURAN L", result.single().sizeLabel)
        assertEquals(120, result.single().totalOrderedPcs)
    }
}
