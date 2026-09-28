package com.eventverse.app.infrastructure.traceability

import com.eventverse.app.domain.sampling.DefaultStageWorkProfile
import com.eventverse.app.domain.sampling.SamplingOrder
import com.eventverse.app.domain.sampling.SamplingOrderId
import com.eventverse.app.domain.sampling.SamplingOrderRepository
import com.eventverse.app.domain.sampling.SamplingPipelineStage
import com.eventverse.app.domain.sampling.samplingRoute
import com.eventverse.app.domain.sampling.SpkUrgencyInput
import com.eventverse.app.domain.sampling.SpkUrgencyLevel
import com.eventverse.app.domain.sampling.StageSectionNames
import com.eventverse.app.domain.sampling.StageWorkProfile
import com.eventverse.app.domain.sampling.assessSamplingUrgency
import com.eventverse.app.domain.sampling.calculateTotalSampleQuantity
import com.eventverse.app.domain.sampling.isQtyRow
import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.domain.traceability.TraceAllocationPlan
import com.eventverse.app.domain.traceability.TraceCodec
import com.eventverse.app.domain.traceability.TraceTier
import com.eventverse.app.domain.traceability.TraceWorkOrderKind
import com.eventverse.app.domain.traceability.TraceWorkOrderSnapshot
import com.eventverse.app.domain.traceability.print.SpkCardContent
import com.eventverse.app.domain.traceability.print.SpkCardLayout
import com.eventverse.app.domain.traceability.print.SpkCardSheet
import com.eventverse.app.domain.traceability.print.SpkMeasurementRow
import com.eventverse.app.domain.traceability.print.SpkSizeCard
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Merakit Kartu SPK A6 dari SPK sampling — lembar pendamping lembar kerja rajut yang menempel di
 * meja/dinding tiap section produksi.
 *
 * Angka urgensi dihitung **saat kartu dicetak**, bukan disimpan: proyeksi seluruh SPK aktif tenant
 * dimuat, dinilai [assessSamplingUrgency] (sampel selalu URGENT, diperingkat menurut deadline), dan
 * posisi SPK ini di antrean itulah yang tercetak di strip bawah kartu. Satu query + aritmetika murni — tidak ada tabel baru, tidak ada state yang basi.
 */
class SpkCardBuilder(
    private val orders: SamplingOrderRepository,
    private val today: () -> LocalDate = {
        Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
    },
    private val profile: StageWorkProfile = DefaultStageWorkProfile
) {

    suspend fun build(
        tenantId: TenantId,
        snapshot: TraceWorkOrderSnapshot,
        plan: TraceAllocationPlan
    ): SpkCardSheet? {
        if (snapshot.ref.kind != TraceWorkOrderKind.SAMPLING) return null
        val order = orders.findById(SamplingOrderId(snapshot.ref.id)) ?: return null

        val todayDate = today()
        val assessments = assessSamplingUrgency(
            inputs = orders.findAll(tenantId)
                .filter { it.isActiveDesign && !it.isArchived }
                .map { it.toUrgencyInput() },
            today = todayDate
        )
        val mine = assessments.firstOrNull { it.spkId == order.id.value }

        val worksheetCodes = plan.labelsFor(TraceTier.WORKSHEET).associateBy { it.sizeLabel.uppercase() }
        val samplingRows = samplingRowsFor(order)

        val cards = snapshot.sizes.mapNotNull { size ->
            val label = worksheetCodes[size.sizeLabel.uppercase()]?.code ?: return@mapNotNull null
            SpkSizeCard(
                sizeLabel = size.sizeLabel,
                qtyPcs = size.orderedPcs,
                code = label,
                humanCode = TraceCodec.grouped(label),
                pomRows = pomRowsFor(order, size.sizeLabel),
                samplingRows = samplingRows
            )
        }

        return SpkCardLayout.solve(
            SpkCardContent(
                spkNumber = order.spkNumber.value,
                styleName = order.styleName,
                clientName = order.clientName,
                revision = order.revisionCount,
                stageLabel = order.pipelineStage.displayName,
                // Nomor & jumlah tahap mengikuti rute desain: kartu yang melompati Cuci
                // mencetak "8/11", bukan "9/12" dengan satu tahap yang tak pernah dilewati.
                stageNumber = order.samplingRoute.stages.indexOf(order.pipelineStage) + 1,
                stageCount = order.samplingRoute.stages.size,
                deadline = order.deadlineDelivery ?: order.deadlineFinishing ?: order.deadlineProgram,
                urgencyLevel = mine?.level ?: SpkUrgencyLevel.URGENT,
                slackDays = mine?.slackDays,
                rank = mine?.rank ?: 0,
                activeCount = assessments.size,
                colorways = snapshot.colorways,
                printedOn = todayDate,
                cards = cards
            )
        )
    }

    /** Proyeksi ringan satu SPK untuk [assessUrgency] — deadline kirim yang mengejar, lalu finishing, lalu program. */
    private fun SamplingOrder.toUrgencyInput() = SpkUrgencyInput(
        spkId = id.value,
        stage = pipelineStage,
        deadline = deadlineDelivery ?: deadlineFinishing ?: deadlineProgram,
        totalStdMinutes = yieldAndTiming.panelMinutes.total,
        qtyPcs = calculateTotalSampleQuantity(sizeMatrix).takeIf { it > 0 } ?: sampleQuantity
    )

    /**
     * Titik ukur buyer untuk ukuran ini: baris POM matriks, kolom milik ukurannya.
     * Nilai dicetak apa adanya ("56", "60 CM") karena formatnya keputusan buyer, bukan kita.
     */
    private fun pomRowsFor(order: SamplingOrder, sizeLabel: String): List<SpkMeasurementRow> =
        order.sizeMatrix.filter { !it.isQtyRow }.mapNotNull { row ->
            val value = row.values[sizeLabel]?.trim().orEmpty()
            if (value.isBlank()) null else SpkMeasurementRow(row.pomName.trim(), value)
        }

    /**
     * Hasil ukuran tim sampling: section HASIL UKURAN JADI di lembar Program CAM — label bebas
     * ("P BADAN : 55 CM") karena setiap model butuh titik ukur berbeda. SPK lama yang belum punya
     * lembar CAM jatuh ke size chart hasil jadi bawaan.
     */
    private fun samplingRowsFor(order: SamplingOrder): List<SpkMeasurementRow> {
        val section = order.stageInputFor(SamplingPipelineStage.CAM_PROGRAMMING)
            ?.section(StageSectionNames.FINISHED_MEASUREMENTS)
        val filled = section?.rows
            ?.filter { it.isFilled }
            ?.map { SpkMeasurementRow(it.label.trim(), it.value.trim()) }
            .orEmpty()
        if (filled.isNotEmpty()) return filled

        val chart = order.finishedSizeCharts.firstOrNull() ?: return emptyList()
        return listOfNotNull(
            chartRow("P BADAN", chart.bodyLength),
            chartRow("L BADAN", chart.bodyWidth),
            chartRow("P TANGAN", chart.sleeveLength),
            chartRow("ARMHOLE BADAN", chart.armHole),
            chartRow("TURUN KERAH", chart.neckDrop),
            chartRow("BUKAAN KERAH", chart.neckWidth),
            chartRow("TURUN BAHU", chart.shoulderWidth),
            chartRow("RIB", chart.ribHeight),
            chartRow("KERAH", chart.collarHeight),
            chartRow("PLACKET", chart.placketWidth),
            chartRow("BUKAAN TANGAN", chart.sleeveOpening)
        )
    }

    private fun chartRow(label: String, cm: Double): SpkMeasurementRow? =
        if (cm <= 0.0) null else SpkMeasurementRow(label, "${(cm * 10).toLong() / 10.0}")
}