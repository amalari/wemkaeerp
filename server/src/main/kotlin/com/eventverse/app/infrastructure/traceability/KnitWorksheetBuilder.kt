package com.eventverse.app.infrastructure.traceability

import com.eventverse.app.domain.sampling.*
import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.domain.traceability.*
import com.eventverse.app.domain.traceability.print.*

/**
 * Merakit Lembar Kerja Rajut dari SPK sampling.
 *
 * Tinggal di `server/` dan bukan di domain telusur karena ia perlu tahu bentuk `SamplingOrder` —
 * paket `domain/traceability` sengaja tidak boleh mengenal sampling maupun produksi massal, supaya
 * satu ruang kode bisa melayani keduanya tanpa saling menyandera.
 */
class KnitWorksheetBuilder(private val orders: SamplingOrderRepository) {

    suspend fun build(
        tenantId: TenantId,
        snapshot: TraceWorkOrderSnapshot,
        plan: TraceAllocationPlan
    ): KnitWorksheet? {
        if (snapshot.ref.kind != TraceWorkOrderKind.SAMPLING) return null
        val order = orders.findById(SamplingOrderId(snapshot.ref.id)) ?: return null

        val worksheetCodes = plan.labelsFor(TraceTier.WORKSHEET).associateBy { it.sizeLabel.uppercase() }

        val pages = snapshot.sizes.mapNotNull { size ->
            val code = worksheetCodes[size.sizeLabel.uppercase()]?.code ?: return@mapNotNull null
            KnitWorksheetPage(
                sizeLabel = size.sizeLabel,
                orderedPcs = size.orderedPcs,
                code = code,
                panelRows = panelRowsFor(order, size.sizeLabel),
                measurementRows = measurementRowsFor(order, size.sizeLabel),
                feederNotes = order.machineProgram.feederInstructions
                    .map { "#${it.feederNumber} ${it.name} ${it.color}".trim() }
                    .filter { it.isNotBlank() },
                colorway = snapshot.colorways.firstOrNull().orEmpty()
            )
        }
        return KnitWorksheet(order.spkNumber.value, order.styleName, order.clientName, pages)
    }

    private fun panelRowsFor(order: SamplingOrder, sizeLabel: String): List<WorksheetPanelRow> {
        val spec = order.yieldAndTiming.specFor(sizeLabel)
        val programs = spec.programs ?: order.machineProgram
        val inherited = spec.derivedFromSize

        return listOfNotNull(
            panelRow("Badan Depan", spec.weights.front, spec.minutes.front, programs.programFront, inherited),
            panelRow("Badan Belakang", spec.weights.back, spec.minutes.back, programs.programBack, inherited),
            panelRow("Lengan (x2)", spec.weights.sleeve, spec.minutes.sleeve, programs.programSleeve, inherited),
            panelRow("Rib Leher / Kerah", spec.weights.collar, spec.minutes.collar, programs.programCollar, inherited),
            panelRow("Placket", spec.weights.placket, spec.minutes.placket, programs.programPlacket, inherited)
        )
    }

    /** Panel yang nol gramasi DAN nol menit memang tidak dikerjakan; mencetaknya hanya menambah baris kosong. */
    private fun panelRow(
        label: String,
        grams: Double,
        minutes: Int,
        program: String,
        inheritedFrom: String?
    ): WorksheetPanelRow? =
        if (grams <= 0.0 && minutes <= 0 && program.isBlank()) null
        else WorksheetPanelRow(label, grams, minutes, program, inheritedFrom)

    /**
     * Titik ukur diambil dari matriks size chart buyer, kolom milik ukuran ini.
     *
     * Baris kuantitas dibuang: ia hidup di tabel yang sama tapi bukan titik ukur, dan mencetaknya
     * sebagai "titik ukur" akan membuat QC mengukurnya.
     */
    private fun measurementRowsFor(order: SamplingOrder, sizeLabel: String): List<WorksheetMeasurementRow> {
        val finished = order.finishedSizeCharts.firstOrNull { it.sizeLabel.equals(sizeLabel, ignoreCase = true) }
        val raw = order.rawKnitSizeCharts.firstOrNull { it.sizeLabel.equals(sizeLabel, ignoreCase = true) }

        val matrixRows = order.sizeMatrix
            .filter { !it.isQtyRow }
            .mapNotNull { row ->
                val value = row.values[sizeLabel]?.trim()?.toDoubleOrNull()
                if (value == null || value <= 0.0) null
                else WorksheetMeasurementRow(row.pomName, value, null, DEFAULT_TOLERANCE_CM)
            }
        if (matrixRows.isNotEmpty()) return matrixRows

        return listOfNotNull(
            measurementRow("Panjang Baju", finished?.bodyLength, raw?.bodyLength),
            measurementRow("Lebar Dada", finished?.bodyWidth, raw?.bodyWidth),
            measurementRow("Panjang Lengan", finished?.sleeveLength, raw?.sleeveLength),
            measurementRow("Lebar Bahu", finished?.shoulderWidth, raw?.shoulderWidth),
            measurementRow("Lebar Leher", finished?.neckWidth, raw?.neckWidth)
        )
    }

    private fun measurementRow(name: String, finished: Double?, raw: Double?): WorksheetMeasurementRow? =
        if ((finished ?: 0.0) <= 0.0 && (raw ?: 0.0) <= 0.0) null
        else WorksheetMeasurementRow(name, finished, raw, DEFAULT_TOLERANCE_CM)

    private companion object {
        const val DEFAULT_TOLERANCE_CM = 1.0
    }
}
