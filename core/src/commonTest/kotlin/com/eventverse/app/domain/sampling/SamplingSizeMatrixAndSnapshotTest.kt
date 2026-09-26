package com.eventverse.app.domain.sampling

import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.shared.sampling.SamplingOrderCodec
import kotlinx.datetime.Instant
import kotlin.test.*

class SamplingSizeMatrixAndSnapshotTest {

    private val now = Instant.parse("2026-09-17T05:00:00Z")
    private val later = Instant.parse("2026-09-17T06:00:00Z")
    private val tenantId = TenantId("ten-demo-001")

    @Test
    fun isSizeColumnActive_shouldReturnTrueOnlyWhenAllPomRowsHaveValue() {
        val matrixWithTwoPoms = listOf(
            SizeChartRow(id = SAMPLING_QTY_ROW_ID, pomName = SAMPLING_QTY_ROW_NAME, values = emptyMap()),
            SizeChartRow(id = "pom_lebar_dada", pomName = "Lebar Dada", values = mapOf("S" to "50")),
            SizeChartRow(id = "pom_panjang_baju", pomName = "Panjang Baju", values = emptyMap()) // Panjang Baju S masih kosong!
        )
        // Belum lengkap karena Panjang Baju belum diisi
        assertFalse(isSizeColumnActive(matrixWithTwoPoms, "S"))

        // Isi Panjang Baju S
        val completeMatrix = matrixWithTwoPoms.map { row ->
            if (row.id == "pom_panjang_baju") row.copy(values = mapOf("S" to "70")) else row
        }
        // Sekarang lengkap karena kedua baris POM terisi untuk S
        assertTrue(isSizeColumnActive(completeMatrix, "S"))
        assertFalse(isSizeColumnActive(completeMatrix, "M"))
    }

    @Test
    fun calculateTotalSampleQuantity_shouldSumQuantitiesOfActiveColumnsOnly() {
        val matrix = listOf(
            SizeChartRow(id = SAMPLING_QTY_ROW_ID, pomName = SAMPLING_QTY_ROW_NAME, values = mapOf("S" to "2", "M" to "3", "XL" to "5")),
            SizeChartRow(id = "pom_lebar_dada", pomName = "Lebar Dada", values = mapOf("S" to "50", "M" to "53"))
        )

        // XL has qty 5, but XL has NO POM specification (inactive), so it shouldn't be counted
        val total = calculateTotalSampleQuantity(matrix, fallback = 0)
        assertEquals(5, total) // S(2) + M(3) = 5

        // Jika belum ada qty sama sekali yang diisi, total harus 0
        val emptyQtyMatrix = listOf(
            SizeChartRow(id = SAMPLING_QTY_ROW_ID, pomName = SAMPLING_QTY_ROW_NAME, values = emptyMap()),
            SizeChartRow(id = "pom_lebar_dada", pomName = "Lebar Dada", values = mapOf("S" to "50"))
        )
        assertEquals(0, calculateTotalSampleQuantity(emptyQtyMatrix))
    }

    @Test
    fun sanitizeSamplingMatrix_shouldClearQtyForInactiveColumns() {
        val matrix = listOf(
            SizeChartRow(id = SAMPLING_QTY_ROW_ID, pomName = SAMPLING_QTY_ROW_NAME, values = mapOf("S" to "2", "L" to "4")),
            SizeChartRow(id = "pom_1", pomName = "Lebar Dada", values = mapOf("S" to "50"))
        )

        val sanitized = sanitizeSamplingMatrix(matrix)
        val qtyRow = sanitized.first { it.isQtyRow }

        assertEquals("2", qtyRow.values["S"])
        assertEquals("", qtyRow.values["L"]) // L cleared because no POM specified for L
    }

    @Test
    fun requestRevision_shouldCaptureSnapshotAndAllowRetrieval() {
        val matrix = defaultSamplingSizeMatrix()
        val order = SamplingOrder(
            id = SamplingOrderId("smp_001"),
            tenantId = tenantId,
            spkNumber = SpkNumber("SPK-SMP-0001"),
            clientName = "ACME FASHION",
            styleName = "SUMMER POLO",
            status = SamplingStatus.DRAFT,
            knitSpec = KnitSpec(mockupImageUrls = listOf("front:mockups/front_v0.jpg", "back:mockups/back_v0.jpg")),
            sizeMatrix = matrix,
            sampleQuantity = 3,
            samplingFeeIdr = 350000L,
            notes = "Initial design notes",
            createdAt = now,
            updatedAt = now
        )

        // Buyer requests Revision 1
        val revisedOrder = order.requestRevision(
            notes = "Tolong kerah dibuat lebih lancip dan lebar dada S dinaikkan 2cm",
            updatedAt = later
        )

        assertEquals(1, revisedOrder.revisionCount)
        assertEquals(1, revisedOrder.revisionHistory.size)

        val rev1Feedback = revisedOrder.revisionHistory.first()
        assertEquals(0, rev1Feedback.revision) // snapshot captures revision 0 state
        assertEquals("Tolong kerah dibuat lebih lancip dan lebar dada S dinaikkan 2cm", rev1Feedback.notes)

        // Snapshot of rev 0 should match original state before revision
        val snapshot0 = revisedOrder.snapshotFor(0)
        assertNotNull(snapshot0)
        assertEquals("mockups/front_v0.jpg", snapshot0.mockupFrontKey)
        assertEquals("mockups/back_v0.jpg", snapshot0.mockupBackKey)
        assertEquals(3, snapshot0.sampleQuantity)
        assertEquals(350000L, snapshot0.samplingFeeIdr)
        assertEquals("Initial design notes", snapshot0.notes)
    }

    @Test
    fun codec_shouldSerializeAndDeserializeRevisionSnapshotProperly() {
        val matrix = defaultSamplingSizeMatrix()
        val order = SamplingOrder(
            id = SamplingOrderId("smp_002"),
            tenantId = tenantId,
            spkNumber = SpkNumber("SPK-SMP-0002"),
            clientName = "ACME FASHION",
            styleName = "KNIT VEST",
            status = SamplingStatus.DRAFT,
            knitSpec = KnitSpec(mockupImageUrls = listOf("front:mockups/front_v0.png", "back:mockups/back_v0.png")),
            sizeMatrix = matrix,
            sampleQuantity = 2,
            samplingFeeIdr = 250000L,
            createdAt = now,
            updatedAt = now
        ).requestRevision("Ubah panjang baju", later)

        val json = SamplingOrderCodec.encode(order)
        val decoded = SamplingOrderCodec.decode(json)

        assertEquals(1, decoded.revisionCount)
        assertEquals(1, decoded.revisionHistory.size)

        val decodedSnapshot = decoded.snapshotFor(0)
        assertNotNull(decodedSnapshot)
        assertEquals("mockups/front_v0.png", decodedSnapshot.mockupFrontKey)
        assertEquals("mockups/back_v0.png", decodedSnapshot.mockupBackKey)
        assertEquals(2, decodedSnapshot.sampleQuantity)
        assertEquals(250000L, decodedSnapshot.samplingFeeIdr)
        assertTrue(decodedSnapshot.sizeMatrix.any { it.isQtyRow })
    }
}
