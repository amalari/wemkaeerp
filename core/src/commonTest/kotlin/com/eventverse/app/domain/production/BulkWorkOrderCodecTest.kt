package com.eventverse.app.domain.production

import com.eventverse.app.domain.pipeline.StockOwnershipSemantics
import com.eventverse.app.domain.sampling.SamplingOrderId
import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.shared.json.JsonParser
import com.eventverse.app.shared.json.JsonValue
import com.eventverse.app.shared.production.BulkWorkOrderCodec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate

class BulkWorkOrderCodecTest {

    private val sample = BulkWorkOrder(
        id = BulkWorkOrderId("bwo-9"),
        tenantId = TenantId("tnt-9"),
        spkNumber = BulkSpkNumber("SPK-MSL-0042"),
        clientName = "CV Amanah",
        styleName = "Seragam PDL",
        status = BulkProductionStatus.SEWING,
        dealId = "deal-7",
        goldenSampleOrderId = SamplingOrderId("smp-77"),
        stockOwnership = StockOwnershipSemantics.CONSIGNED_CLIENT_MATERIAL,
        sizeBreakdown = listOf(BulkSizeLine("S", 100), BulkSizeLine("XL", 250)),
        lineAllocations = listOf(
            MachineLineAllocation(
                lineName = ProductionLineName("Line 3"),
                machineCount = 8,
                assignedPcs = 350,
                startDate = LocalDate(2026, 9, 18),
                targetFinishDate = LocalDate(2026, 9, 25),
                operatorCount = 10,
                notes = "Prioritas ekspor"
            )
        ),
        stageProgress = listOf(
            ProductionStageProgress(ProductionStage.CUTTING, 350, 10, 5, Instant.parse("2026-09-18T03:00:00Z")),
            ProductionStageProgress(ProductionStage.SEWING, 200, 0, 2, Instant.parse("2026-09-19T03:00:00Z")),
            ProductionStageProgress(ProductionStage.FINISHING)
        ),
        targetOutputPerDay = 120,
        plannedStartDate = LocalDate(2026, 9, 18),
        plannedFinishDate = LocalDate(2026, 9, 30),
        notes = "Kain titipan buyer",
        createdAt = Instant.parse("2026-09-17T10:00:00Z"),
        updatedAt = Instant.parse("2026-09-19T03:00:00Z"),
        releasedAt = Instant.parse("2026-09-17T11:00:00Z")
    )

    @Test
    fun `encode then decode should round-trip every field`() {
        val decoded = BulkWorkOrderCodec.decode(
            JsonParser.parse(BulkWorkOrderCodec.encode(sample).encode()) as JsonValue.Obj
        )

        assertEquals(sample, decoded)
    }

    @Test
    fun `jsonb column helpers round-trip their collections`() {
        assertEquals(
            sample.sizeBreakdown,
            BulkWorkOrderCodec.decodeSizeBreakdown(BulkWorkOrderCodec.encodeSizeBreakdown(sample.sizeBreakdown))
        )
        assertEquals(
            sample.lineAllocations,
            BulkWorkOrderCodec.decodeAllocations(BulkWorkOrderCodec.encodeAllocations(sample.lineAllocations))
        )
        assertEquals(
            sample.stageProgress,
            BulkWorkOrderCodec.decodeStageProgress(BulkWorkOrderCodec.encodeStageProgress(sample.stageProgress))
        )
    }

    @Test
    fun `missing stage in stored json should not create phantom wip`() {
        // Data lama yang hanya menyimpan tahap Potong — dua tahap lain hilang dari jsonb.
        val partial = """[{"stage":"CUTTING","completedPcs":100,"reworkPcs":0,"rejectPcs":0}]"""

        val restored = BulkWorkOrderCodec.decodeStageProgress(partial)

        assertEquals(3, restored.size)
        assertNotNull(restored.firstOrNull { it.stage == ProductionStage.FINISHING })
    }

    @Test
    fun `corrupt size line is dropped instead of failing the whole order`() {
        val withBadRow = """[{"sizeLabel":"M","orderedPcs":50},{"sizeLabel":"","orderedPcs":0}]"""

        val decoded = BulkWorkOrderCodec.decodeSizeBreakdown(withBadRow)

        assertEquals(1, decoded.size)
        assertEquals("M", decoded.first().sizeLabel)
    }
}
