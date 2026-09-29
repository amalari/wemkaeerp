package com.eventverse.app.domain.sampling

import com.eventverse.app.domain.pipeline.FlowHealthStatus
import com.eventverse.app.domain.stageflow.IndustryStageTemplates
import com.eventverse.app.domain.stageflow.IndustryTemplateCode
import com.eventverse.app.domain.stageflow.StageCode
import com.eventverse.app.domain.tenant.TenantId
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

/** Telemetri Sampling dihitung pada kerangka **bordir** (tenant-variability-rules Kontrak 6). */
class SamplingTelemetryProviderTest {

    private val now = Instant.parse("2026-09-29T00:00:00Z")
    private val tenant = TenantId("ten-bordir")
    private val frame = IndustryStageTemplates.instantiate(tenant, IndustryTemplateCode.EMBROIDERY)

    private fun order(n: Int, stage: String, qty: Int = 2) = SamplingOrder(
        id = SamplingOrderId("smp_$n"),
        tenantId = tenant,
        spkNumber = SpkNumber("SPK-BRD-000$n"),
        clientName = "Buyer",
        styleName = "Logo",
        sampleQuantity = qty,
        stageCode = StageCode(stage),
        createdAt = now,
        updatedAt = now
    ).freezeStageFlow(frame)

    private class Repo(private val orders: List<SamplingOrder>) : SamplingOrderRepository {
        override suspend fun findById(id: SamplingOrderId) = orders.firstOrNull { it.id == id }
        override suspend fun findBySpkNumber(tenantId: TenantId, spkNumber: SpkNumber) = null
        override suspend fun findAll(tenantId: TenantId, status: SamplingStatus?) = orders
        override suspend fun findByDealId(tenantId: TenantId, dealId: String) = emptyList<SamplingOrder>()
        override suspend fun save(order: SamplingOrder) = order
        override suspend fun nextSpkNumber(tenantId: TenantId) = SpkNumber("SPK-X")
        override suspend fun archive(id: SamplingOrderId) = false
    }

    private val clock = object : Clock { override fun now() = now }

    @Test
    fun read_embroideryOrders_shouldCountWipPerWorkStageAndSkipAnchors() = runTest {
        val repo = Repo(listOf(order(1, "DIGITIZING", 3), order(2, "MACHINE_EMBROIDERY"), order(3, "MACHINE_EMBROIDERY"), order(4, "NEW_INTAKE", 9)))

        val t = SamplingTelemetryProvider(repo, clock).read(tenant).getOrThrow()

        assertEquals(7, t.wipPieces)
        assertEquals(mapOf(StageCode("DIGITIZING") to 1, StageCode("MACHINE_EMBROIDERY") to 2), t.stageWip)
        assertEquals(FlowHealthStatus.HEALTHY, t.healthStatus)
    }

    @Test
    fun read_noOrders_shouldBeHealthyWithZeroWip() = runTest {
        val t = SamplingTelemetryProvider(Repo(emptyList()), clock).read(tenant).getOrThrow()
        assertEquals(0, t.wipPieces)
        assertEquals(emptyMap(), t.stageWip)
    }
}
