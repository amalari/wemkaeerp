package com.eventverse.app.domain.sampling.storage

import com.eventverse.app.domain.sampling.SamplingOrder
import com.eventverse.app.domain.sampling.SamplingOrderId
import com.eventverse.app.domain.sampling.SamplingOrderRepository
import com.eventverse.app.domain.sampling.SamplingPipelineStage
import com.eventverse.app.domain.sampling.SamplingStatus
import com.eventverse.app.domain.sampling.SpkNumber
import com.eventverse.app.domain.sampling.toStageCode
import com.eventverse.app.domain.sampling.usecases.AdvanceSamplingStageCommand
import com.eventverse.app.domain.sampling.usecases.AdvanceSamplingStageUseCase
import com.eventverse.app.domain.sampling.usecases.ReleaseSampleFromStorageCommand
import com.eventverse.app.domain.sampling.usecases.ReleaseSampleFromStorageUseCase
import com.eventverse.app.domain.sampling.usecases.StoreSampleCommand
import com.eventverse.app.domain.sampling.usecases.StoreSampleUseCase
import com.eventverse.app.domain.tenant.TenantId
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Kustodi penyimpanan: barang selesai kemas selalu disimpan dulu, dan dilepas oleh PIC. */
class SampleStorageFlowTest {

    private val now = Instant.fromEpochMilliseconds(1_700_000_000_000)
    private val tenantId = TenantId("demo-tenant")
    private val packer = StorageCustodian("packer@demo.id", "Sari")
    private val pic = StorageCustodian("gudang@demo.id", "Budi")

    private fun order(
        id: String,
        stage: SamplingPipelineStage,
        dealId: String? = "deal-1",
        status: SamplingStatus = SamplingStatus.IN_PROGRESS
    ) = SamplingOrder(
        id = SamplingOrderId(id),
        tenantId = tenantId,
        spkNumber = SpkNumber("SPK-$id"),
        clientName = "BIANCA",
        styleName = "CARDIGAN",
        status = status,
        stageCode = stage.toStageCode(),
        dealId = dealId,
        createdAt = now,
        updatedAt = now
    )

    private class FakeOrders(vararg initial: SamplingOrder) : SamplingOrderRepository {
        val orders = initial.toMutableList()
        override suspend fun findById(id: SamplingOrderId) = orders.firstOrNull { it.id == id }
        override suspend fun findBySpkNumber(tenantId: TenantId, spkNumber: SpkNumber) = null
        override suspend fun findAll(tenantId: TenantId, status: SamplingStatus?) = orders.toList()
        override suspend fun findByDealId(tenantId: TenantId, dealId: String) = orders.filter { it.dealId == dealId }
        override suspend fun save(order: SamplingOrder): SamplingOrder {
            orders.removeAll { it.id == order.id }
            orders.add(order)
            return order
        }
        override suspend fun nextSpkNumber(tenantId: TenantId) = SpkNumber("SPK-X")
        override suspend fun archive(id: SamplingOrderId) = false
    }

    private class FakeStorage : SampleStorageRecordRepository {
        val records = mutableListOf<SampleStorageRecord>()
        override suspend fun findLatestByOrderId(tenantId: TenantId, orderId: SamplingOrderId) =
            records.lastOrNull { it.orderId == orderId }
        override suspend fun findByDealId(tenantId: TenantId, dealId: String) = records.filter { it.dealId == dealId }
        override suspend fun save(record: SampleStorageRecord): SampleStorageRecord {
            records.removeAll { it.id == record.id }
            records.add(record)
            return record
        }
    }

    private val advance = AdvanceSamplingStageUseCase(legsUseCase = null)

    private fun storeCommand(order: SamplingOrder) = StoreSampleCommand(
        order = order,
        location = StorageLocationLabel("Rak Packing A"),
        qtyPcs = 2,
        custodian = packer,
        now = now
    )

    @Test
    fun `store sample from packing should record custodian and move to storage`() = runTest {
        val orders = FakeOrders(order("a", SamplingPipelineStage.PENGEMASAN))
        val storage = FakeStorage()

        val result = StoreSampleUseCase(orders, storage, advance)(storeCommand(orders.orders.first())).getOrThrow()

        assertEquals(SamplingPipelineStage.STORAGE_HOLDING, result.order.pipelineStage)
        assertEquals("Sari", result.record.storedBy.name)
        assertEquals("Rak Packing A", result.record.location.value)
        assertEquals("packer@demo.id", result.order.stageHistory.last().actorEmail)
    }

    @Test
    fun `store sample when not yet packed should fail`() = runTest {
        val orders = FakeOrders(order("a", SamplingPipelineStage.QC_FINISHING))
        val result = StoreSampleUseCase(orders, FakeStorage(), advance)(storeCommand(orders.orders.first()))
        assertTrue(result.isFailure)
    }

    @Test
    fun `advance stage to storage or delivery without custody path should fail`() = runTest {
        listOf(
            order("a", SamplingPipelineStage.PENGEMASAN) to SamplingPipelineStage.STORAGE_HOLDING,
            order("b", SamplingPipelineStage.STORAGE_HOLDING) to SamplingPipelineStage.IN_DELIVERY,
            order("c", SamplingPipelineStage.PENGEMASAN) to SamplingPipelineStage.IN_DELIVERY
        ).forEach { (order, target) ->
            val result = advance(
                AdvanceSamplingStageCommand(
                    order = order,
                    target = target,
                    stages = SamplingPipelineStage.entries,
                    processes = emptyList(),
                    overrideReason = "admin telat",
                    now = now
                )
            )
            assertTrue(result.isFailure, "${order.pipelineStage} -> $target seharusnya ditolak")
        }
    }

    @Test
    fun `release when deal siblings not yet stored should fail with pending list`() = runTest {
        val stored = order("a", SamplingPipelineStage.PENGEMASAN)
        val orders = FakeOrders(stored, order("b", SamplingPipelineStage.SETRIKA_UAP))
        val storage = FakeStorage()
        val inStorage = StoreSampleUseCase(orders, storage, advance)(storeCommand(stored)).getOrThrow().order

        val release = ReleaseSampleFromStorageUseCase(orders, storage, advance)
        val error = release(ReleaseSampleFromStorageCommand(order = inStorage, pic = pic, now = now)).exceptionOrNull()

        assertNotNull(error)
        assertTrue(error.message.orEmpty().contains("SPK-b"))
    }

    @Test
    fun `release partial with reason should record pic and reason`() = runTest {
        val stored = order("a", SamplingPipelineStage.PENGEMASAN)
        val orders = FakeOrders(stored, order("b", SamplingPipelineStage.SETRIKA_UAP))
        val storage = FakeStorage()
        val inStorage = StoreSampleUseCase(orders, storage, advance)(storeCommand(stored)).getOrThrow().order

        val result = ReleaseSampleFromStorageUseCase(orders, storage, advance)(
            ReleaseSampleFromStorageCommand(order = inStorage, pic = pic, partialReason = "Buyer minta S duluan", now = now)
        ).getOrThrow()

        assertEquals(SamplingPipelineStage.IN_DELIVERY, result.order.pipelineStage)
        assertEquals("Budi", result.record.releasedBy?.name)
        assertEquals("Buyer minta S duluan", result.record.partialReason)
        assertTrue(result.order.stageHistory.last().actorRole.contains("kirim parsial"))
    }

    @Test
    fun `release when all deal siblings stored should pass without reason`() = runTest {
        val a = order("a", SamplingPipelineStage.PENGEMASAN)
        val orders = FakeOrders(a, order("b", SamplingPipelineStage.IN_DELIVERY), order("c", SamplingPipelineStage.MACHINE_KNITTING, status = SamplingStatus.CANCELLED))
        val storage = FakeStorage()
        val inStorage = StoreSampleUseCase(orders, storage, advance)(storeCommand(a)).getOrThrow().order

        val result = ReleaseSampleFromStorageUseCase(orders, storage, advance)(
            ReleaseSampleFromStorageCommand(order = inStorage, pic = pic, now = now)
        )
        assertTrue(result.isSuccess)
    }

    @Test
    fun `release sample without deal should pass`() = runTest {
        val a = order("a", SamplingPipelineStage.PENGEMASAN, dealId = null)
        val orders = FakeOrders(a)
        val storage = FakeStorage()
        val inStorage = StoreSampleUseCase(orders, storage, advance)(storeCommand(a)).getOrThrow().order

        val result = ReleaseSampleFromStorageUseCase(orders, storage, advance)(
            ReleaseSampleFromStorageCommand(order = inStorage, pic = pic, now = now)
        )
        assertTrue(result.isSuccess)
    }

    @Test
    fun `release record twice should throw`() {
        val record = SampleStorageRecord(
            id = SampleStorageRecordId("sto-1"),
            tenantId = tenantId,
            orderId = SamplingOrderId("a"),
            location = StorageLocationLabel("Gudang"),
            qtyPcs = 1,
            storedBy = packer,
            storedAt = now
        ).release(pic, now)
        assertFailsWith<IllegalArgumentException> { record.release(pic, now) }
    }

    @Test
    fun `deal readiness should ignore cancelled orders`() {
        val readiness = dealStorageReadiness(
            listOf(
                order("a", SamplingPipelineStage.STORAGE_HOLDING),
                order("b", SamplingPipelineStage.CAM_PROGRAMMING, status = SamplingStatus.CANCELLED)
            )
        )
        assertTrue(readiness.isComplete)
        assertEquals(1, readiness.total)
    }
}
