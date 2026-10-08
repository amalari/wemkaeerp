package com.eventverse.app.domain.fulfillment

import com.eventverse.app.domain.fulfillment.usecases.SubmitTransferUseCase
import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.domain.traceability.TraceCodec
import com.eventverse.app.domain.traceability.TraceContainer
import com.eventverse.app.domain.traceability.TraceContainerId
import com.eventverse.app.domain.traceability.TraceContainerRepository
import com.eventverse.app.domain.traceability.TraceContainerState
import com.eventverse.app.domain.traceability.TraceTier
import com.eventverse.app.domain.traceability.TraceWorkOrderKind
import com.eventverse.app.domain.traceability.TraceWorkOrderRef
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** TRD-FLOW-003 A4: `SubmitTransferUseCase` menerima kode rute yang sah untuk tenant, dan menolak sisanya. */
class SubmitTransferRouteTest {

    private val tenant = TenantId("bordir-uji")
    private val now = Instant.fromEpochMilliseconds(1_700_000_000_000)
    private val hooping = HandoverRouteCode("HOOPING_TO_QC")
    private val sackCode = TraceCodec.encode(TraceWorkOrderKind.SAMPLING, TraceTier.SACK, 1, 1, 0, 1)

    private val sack = TraceContainer(
        id = TraceContainerId("trc-1"), tenantId = tenant, code = sackCode,
        workOrder = TraceWorkOrderRef(TraceWorkOrderKind.SAMPLING, "smp_1"), tier = TraceTier.SACK, sizeLabel = "M",
        state = TraceContainerState.CLOSED, declaredPcs = 40, recordedAt = now, createdAt = now, updatedAt = now
    )

    private class FakeTransfers : InternalTransferRepository {
        val saved = mutableListOf<InternalTransfer>()
        val events = mutableListOf<String>()
        override suspend fun findById(tenantId: TenantId, id: SackTransferId) = saved.firstOrNull { it.id == id }
        override suspend fun findAll(tenantId: TenantId) = saved.toList()
        override suspend fun findActiveBySack(tenantId: TenantId, sackCode: com.eventverse.app.domain.traceability.TraceCode) = null
        override suspend fun save(transfer: InternalTransfer): InternalTransfer = transfer.also { saved += it }
        override suspend fun recordEvent(tenantId: TenantId, transferId: SackTransferId, eventType: String, actor: String, detail: String, occurredAt: Instant) { events += detail }
    }

    private class FakeContainers(private val sack: TraceContainer) : TraceContainerRepository {
        override suspend fun findByCode(tenantId: TenantId, code: com.eventverse.app.domain.traceability.TraceCode) = sack
        override suspend fun findByWorkOrder(tenantId: TenantId, ref: TraceWorkOrderRef) = TODO()
        override suspend fun findByIds(tenantId: TenantId, ids: List<TraceContainerId>) = TODO()
        override suspend fun openIfAbsent(container: TraceContainer) = TODO()
        override suspend fun save(container: TraceContainer) = TODO()
        override suspend fun linksForWorkOrder(tenantId: TenantId, ref: TraceWorkOrderRef) = TODO()
        override suspend fun linksForParent(tenantId: TenantId, parentId: TraceContainerId) = TODO()
        override suspend fun consumeIntoSack(sack: TraceContainer, links: List<com.eventverse.app.domain.traceability.TraceContainerLink>, bundles: List<TraceContainer>) = TODO()
        override suspend fun ensureWorkOrderOrdinal(tenantId: TenantId, ref: TraceWorkOrderRef) = TODO()
        override suspend fun findWorkOrderByOrdinal(tenantId: TenantId, ordinal: Int, kind: TraceWorkOrderKind) = TODO()
        override suspend fun tenantOrdinal(tenantId: TenantId) = TODO()
    }

    private class FakeConfig(private val config: FulfillmentRouteConfig?) : FulfillmentRouteConfigRepository {
        override suspend fun findByTenantId(tenantId: TenantId) = config
        override suspend fun save(config: FulfillmentRouteConfig) {}
    }

    private val bordirRoutes = TenantHandoverRoutes(
        tenant,
        listOf(HandoverRoute(hooping, "Hooping ke QC"), HandoverRoute(HandoverRouteCode("LAMA"), "Rute lama", sortOrder = 1, active = false))
    )

    private fun useCase(transfers: FakeTransfers, provider: (suspend (TenantId) -> TenantHandoverRoutes)? = null) =
        provider?.let { SubmitTransferUseCase(transfers, FakeContainers(sack), FakeConfig(null), it) }
            ?: SubmitTransferUseCase(transfers, FakeContainers(sack), FakeConfig(null))

    private suspend fun submit(uc: SubmitTransferUseCase, route: HandoverRouteCode) = uc(
        tenantId = tenant, rawSackPayload = sackCode.value, route = route,
        dispatchWeightKg = WeightKg(8.4), dispatchScalePhotoKey = "uploads/timbang.jpg",
        requestedBy = "Rian", now = now
    )

    @Test
    fun `rute bordir yang dikenal tenant diterima dan tersimpan dengan kodenya`() = runTest {
        val transfers = FakeTransfers()
        val result = submit(useCase(transfers) { bordirRoutes }, hooping)
        assertTrue(result.isSuccess, result.exceptionOrNull()?.message)
        assertEquals(hooping, transfers.saved.single().route)
        assertEquals(HandoverMode.ADMIN_HUB, transfers.saved.single().handoverMode)
        assertEquals("route=HOOPING_TO_QC mode=ADMIN_HUB", transfers.events.single())
    }

    @Test
    fun `kode rute yang tidak dikenal tenant ditolak, tidak jatuh ke rute lain`() = runTest {
        val transfers = FakeTransfers()
        val result = submit(useCase(transfers) { bordirRoutes }, SackRoute.QC_RAJUT_TO_FINISHING.toRouteCode())
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("QC_RAJUT_TO_FINISHING"))
        assertTrue(transfers.saved.isEmpty())
    }

    @Test
    fun `rute nonaktif tidak bisa dipakai untuk perjalanan baru`() = runTest {
        val transfers = FakeTransfers()
        val result = submit(useCase(transfers) { bordirRoutes }, HandoverRouteCode("LAMA"))
        assertTrue(result.isFailure)
        assertFalse(transfers.saved.isNotEmpty())
    }

    @Test
    fun `penyedia bawaan memakai isi SackRoute - tenant konveksi berperilaku seperti sebelum migrasi`() = runTest {
        val transfers = FakeTransfers()
        val uc = useCase(transfers)
        SackRoute.entries.forEach { assertTrue(submit(uc, it.toRouteCode()).isSuccess, "rute ${it.name} harus tetap sah") }
        assertTrue(submit(uc, hooping).isFailure, "rute bordir tidak sah pada penyedia bawaan")
    }
}
