package com.eventverse.app.domain.sampling

import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.shared.sampling.SamplingOrderCodec
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SamplingMultiDivisionWorkflowTest {

    private val testTenantId = TenantId("tenant-wemade-01")
    private val testOrderId = SamplingOrderId("samp-order-001")
    private val testSpk = SpkNumber("SPK-SMP-2026-001")
    private val now = Clock.System.now()
    private val today = LocalDate(2026, 9, 17)

    private fun createBaseOrder(qty: Int = 75): SamplingOrder = SamplingOrder(
        id = testOrderId,
        tenantId = testTenantId,
        spkNumber = testSpk,
        clientName = "PT Mode Kreatif",
        styleName = "Cardigan Rajut Oversize",
        status = SamplingStatus.IN_PROGRESS,
        pipelineStage = SamplingPipelineStage.MACHINE_KNITTING,
        sampleQuantity = qty,
        createdAt = now,
        updatedAt = now
    )

    @Test
    fun testFinishingPartialDepositingAndAutoAdvance() {
        val base = createBaseOrder(75).advancePipelineStage(SamplingPipelineStage.LINKING_ASSEMBLY, now)
        assertEquals(75, base.remainingFinishingQty)
        assertFalse(base.isFinishingComplete)

        // Setoran tahap 1: 24 pcs
        val dep1 = FinishingDeposit(
            id = "dep-01",
            samplingOrderId = testOrderId.value,
            depositDate = today,
            qtyPcs = 24,
            weightKg = 5.2,
            operatorName = "Kang Cecep"
        )
        val afterDep1 = base.addFinishingDeposit(dep1, now)
        assertEquals(24, afterDep1.totalFinishedDepositedQty)
        assertEquals(51, afterDep1.remainingFinishingQty)
        assertFalse(afterDep1.isFinishingComplete)
        assertEquals(SamplingPipelineStage.LINKING_ASSEMBLY, afterDep1.pipelineStage)

        // Setoran tahap 2: 51 pcs (tuntas)
        val dep2 = FinishingDeposit(
            id = "dep-02",
            samplingOrderId = testOrderId.value,
            depositDate = today,
            qtyPcs = 51,
            weightKg = 11.0,
            operatorName = "Kang Cecep"
        )
        val afterDep2 = afterDep1.addFinishingDeposit(dep2, now)
        assertEquals(75, afterDep2.totalFinishedDepositedQty)
        assertEquals(0, afterDep2.remainingFinishingQty)
        assertTrue(afterDep2.isFinishingComplete)
        // Setoran tuntas memindahkan barang ke meja pemeriksa, bukan sekadar menandai "finishing"
        assertEquals(SamplingPipelineStage.QC_FINISHING, afterDep2.pipelineStage)
    }

    @Test
    fun testQcInspectionAndToleranceValidation() {
        val pom1 = QcPomMeasurement(
            pomName = "Panjang Baju",
            targetCm = 60.0,
            actualCm = 60.5,
            toleranceCm = 1.0
        )
        assertEquals(0.5, pom1.deviationCm, 0.001)
        assertTrue(pom1.isWithinTolerance)

        val pom2 = QcPomMeasurement(
            pomName = "Lebar Dada",
            targetCm = 55.0,
            actualCm = 57.2,
            toleranceCm = 1.0
        )
        assertEquals(2.2, pom2.deviationCm, 0.001)
        assertFalse(pom2.isWithinTolerance)

        val orderInQc = createBaseOrder(2).advancePipelineStage(SamplingPipelineStage.QC_FINISHING, now)
        val qcReport = QcInspectionReport(
            id = "qc-rep-01",
            samplingOrderId = testOrderId.value,
            inspectorName = "Teh Rina QC",
            inspectedAt = now,
            pomMeasurements = listOf(pom1),
            qcResult = QcInspectionResult.PASSED,
            qcNotes = "Ukuran rapi dan jahitan linking kuat"
        )

        val afterQc = orderInQc.completeQcInspection(qcReport, now)
        assertEquals(1, afterQc.qcInspections.size)
        assertEquals(QcInspectionResult.PASSED, afterQc.latestQcReport?.qcResult)
        // QC yang lolos menyerahkan barang ke meja pengemasan — bukan langsung ke pengiriman.
        // Melompatinya berarti menyatakan sampel sudah dilipat dan masuk polybag tanpa ada yang
        // mengerjakannya; jejak kustodinya akan bolong tepat di langkah terakhir.
        assertEquals(SamplingPipelineStage.PENGEMASAN, afterQc.pipelineStage)
    }

    @Test
    fun testMakloonVendorAssignmentAndReturn() {
        val base = createBaseOrder(2)
        assertEquals(FinishingPath.INTERNAL, base.finishingPath)
        assertEquals(VendorFollowUpStatus.NONE, base.vendorInfo.status)

        val vendorInfo = MakloonVendorInfo(
            vendorName = "Pak Asep Makloon",
            vendorPhone = "081234567890",
            sentAt = today,
            expectedReturnAt = LocalDate(2026, 9, 20),
            costPerPcsIdr = 15000L,
            notes = "Linking dan pasang kancing 5 titik"
        )

        val sentToVendor = base.assignMakloonVendor(vendorInfo, now)
        assertEquals(FinishingPath.MAKLOON_VENDOR, sentToVendor.finishingPath)
        assertEquals(VendorFollowUpStatus.WITH_VENDOR, sentToVendor.vendorInfo.status)
        assertEquals("Pak Asep Makloon", sentToVendor.vendorInfo.vendorName)
        assertEquals(SamplingPipelineStage.LINKING_ASSEMBLY, sentToVendor.pipelineStage)

        val returnedOrder = sentToVendor.recordVendorReturn(today, now)
        assertEquals(VendorFollowUpStatus.RETURNED, returnedOrder.vendorInfo.status)
        assertEquals(today, returnedOrder.vendorInfo.returnedAt)
        assertEquals(SamplingPipelineStage.CUCI_SOFTENER, returnedOrder.pipelineStage)
    }

    @Test
    fun testTenselityAndCycleTimeUpdates() {
        val base = createBaseOrder()
        val customTenselity = listOf(
            TenselityEntry(parameter = "1 BS POLY", body = "12", sleeve = "10", collar = "8"),
            TenselityEntry(parameter = "6 RIB", body = "14", sleeve = "14", collar = "12")
        )
        val withTenselity = base.updateTenselity(customTenselity, now)
        assertEquals(2, withTenselity.machineProgram.tenselityEntries.size)
        assertEquals("12", withTenselity.machineProgram.tenselityEntries[0].body)

        val weights = PanelWeightGrams(front = 140.0, back = 135.0, sleeve = 110.0, collar = 30.0, placket = 20.0)
        val minutes = PanelKnittingMinutes(front = 12, back = 11, sleeve = 9, collar = 4, placket = 3)
        val withYield = withTenselity.updateActualGramasiAndTiming(weights, minutes, now)
        assertEquals(435.0, withYield.yieldAndTiming.panelWeights.total, 0.001)
        assertEquals(39, withYield.yieldAndTiming.panelMinutes.total)
    }

    @Test
    fun testSamplingOrderCodecRoundTrip() {
        val base = createBaseOrder(10)
            .advancePipelineStage(SamplingPipelineStage.LINKING_ASSEMBLY, now)
            .addFinishingDeposit(
                FinishingDeposit(
                    id = "dep-codec-1",
                    samplingOrderId = testOrderId.value,
                    depositDate = today,
                    qtyPcs = 5,
                    weightKg = 1.2,
                    operatorName = "Operator A",
                    createdAt = now
                ),
                now
            )
            .completeQcInspection(
                QcInspectionReport(
                    id = "qc-codec-1",
                    samplingOrderId = testOrderId.value,
                    inspectorName = "Inspector B",
                    inspectedAt = now,
                    pomMeasurements = listOf(
                        QcPomMeasurement("Panjang", 60.0, 60.2, 1.0)
                    ),
                    qcResult = QcInspectionResult.PASSED
                ),
                now
            )
            .updateTenselity(
                listOf(TenselityEntry("Rib", "12", "12", "10")),
                now
            )

        val encoded = SamplingOrderCodec.encode(base)
        val decoded = SamplingOrderCodec.decode(encoded)

        assertEquals(base.id, decoded.id)
        assertEquals(base.spkNumber, decoded.spkNumber)
        assertEquals(base.pipelineStage, decoded.pipelineStage)
        assertEquals(base.finishingPath, decoded.finishingPath)
        assertEquals(1, decoded.finishingDeposits.size)
        assertEquals(5, decoded.finishingDeposits[0].qtyPcs)
        assertEquals("Operator A", decoded.finishingDeposits[0].operatorName)
        assertEquals(1, decoded.qcInspections.size)
        assertEquals(QcInspectionResult.PASSED, decoded.qcInspections[0].qcResult)
        assertEquals(1, decoded.machineProgram.tenselityEntries.size)
        assertEquals("Rib", decoded.machineProgram.tenselityEntries[0].parameter)
    }
}
