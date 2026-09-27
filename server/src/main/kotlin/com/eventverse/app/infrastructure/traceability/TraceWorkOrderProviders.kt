package com.eventverse.app.infrastructure.traceability

import com.eventverse.app.domain.contracts.GarmentPanel
import com.eventverse.app.domain.production.BulkWorkOrderId
import com.eventverse.app.domain.production.BulkWorkOrderRepository
import com.eventverse.app.domain.sampling.*
import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.domain.traceability.*

/**
 * Berapa lembar tiap panel dibutuhkan untuk satu baju.
 *
 * Sengaja TIDAK diturunkan dari `panelYields`: `PanelWeightGrams` hanya punya satu field `sleeve`, dan
 * `toApprovedSampleSpecification` memetakannya ke `SLEEVE_LEFT` saja — padahal satu baju butuh dua
 * lengan. Membacanya apa adanya membuat bundel berisi 10 lengan terhitung 10 set lengkap alih-alih 5,
 * dan seluruh angka rekonsiliasi karung ikut salah.
 */
internal object PanelRequirementDefaults {
    fun forKnitwear(weights: PanelWeightGrams, minutes: PanelKnittingMinutes): List<PanelRequirement> =
        buildList {
            if (weights.front > 0.0 || minutes.front > 0) add(PanelRequirement(GarmentPanel.BODY_FRONT, 1))
            if (weights.back > 0.0 || minutes.back > 0) add(PanelRequirement(GarmentPanel.BODY_BACK, 1))
            // Dua lengan per baju. Ini satu-satunya angka di sini yang bukan 1, dan justru inilah
            // yang salah kalau daftar panel dibaca mentah dari spesifikasi sampel.
            if (weights.sleeve > 0.0 || minutes.sleeve > 0) add(PanelRequirement(GarmentPanel.SLEEVE_LEFT, 2))
            if (weights.collar > 0.0 || minutes.collar > 0) add(PanelRequirement(GarmentPanel.COLLAR_RIB, 1))
            if (weights.placket > 0.0 || minutes.placket > 0) add(PanelRequirement(GarmentPanel.PLACKET, 1))
            if (isEmpty()) {
                // SPK yang gramasinya belum diisi tetap harus bisa dicetak kartunya; badan depan &
                // belakang adalah panel minimum yang pasti ada pada setiap baju rajut.
                add(PanelRequirement(GarmentPanel.BODY_FRONT, 1))
                add(PanelRequirement(GarmentPanel.BODY_BACK, 1))
            }
        }
}

class SamplingTraceWorkOrderProvider(
    private val orders: SamplingOrderRepository,
    private val ordinals: TraceContainerRepository
) : TraceWorkOrderProvider {

    override suspend fun snapshot(tenantId: TenantId, ref: TraceWorkOrderRef): TraceWorkOrderSnapshot? {
        if (ref.kind != TraceWorkOrderKind.SAMPLING) return null
        val order = orders.findById(SamplingOrderId(ref.id)) ?: return null

        val matrix = ensureSamplingQtyRow(order.sizeMatrix)
        val qtyRow = matrix.firstOrNull { it.isQtyRow }
        val allSizes = extractSizeColumns(matrix)
            .filter { isSizeColumnActive(matrix, it) }
            .mapNotNull { column ->
                val qty = qtyRow?.values?.get(column)?.trim()?.toIntOrNull() ?: 0
                if (qty <= 0) null else TraceSizeLine(column, qty)
            }
        // SPK hasil split per ukuran tetap membawa matriks lengkap induknya (POM semua ukuran),
        // tapi yang dikerjakan hanya ukurannya sendiri. Tanpa saring ini kartu, label, dan lembar
        // kerja SPK "S" ikut mencetak halaman ukuran saudaranya.
        val ownSize = order.sizeLabel?.trim()?.takeIf { it.isNotBlank() }
        val sizes = ownSize
            ?.let { label -> allSizes.filter { it.sizeLabel.equals(label, ignoreCase = true) } }
            ?.takeIf { it.isNotEmpty() }
            ?: allSizes

        return TraceWorkOrderSnapshot(
            ref = ref,
            tenantId = tenantId,
            tenantOrdinal = ordinals.tenantOrdinal(tenantId),
            ordinal = ordinals.ensureWorkOrderOrdinal(tenantId, ref),
            spkNumber = order.spkNumber.value,
            styleName = order.styleName,
            clientName = order.clientName,
            sizes = sizes,
            panelRequirements = PanelRequirementDefaults.forKnitwear(
                order.yieldAndTiming.panelWeights,
                order.yieldAndTiming.panelMinutes
            ),
            colorways = order.machineProgram.feederInstructions
                .map { it.color }
                .filter { it.isNotBlank() }
                .distinct()
        )
    }
}

/**
 * Jalur SPK massal.
 *
 * `BulkSizeLine` belum punya padanan warna sama sekali, jadi [TraceWorkOrderSnapshot.colorways] di
 * sini kosong dan warna karung diisi operator lalu dicocokkan terhadap warna bundelnya — bukan
 * diturunkan dari SPK seperti di jalur sampling.
 */
class BulkTraceWorkOrderProvider(
    private val orders: BulkWorkOrderRepository,
    private val samplingOrders: SamplingOrderRepository,
    private val ordinals: TraceContainerRepository
) : TraceWorkOrderProvider {

    override suspend fun snapshot(tenantId: TenantId, ref: TraceWorkOrderRef): TraceWorkOrderSnapshot? {
        if (ref.kind != TraceWorkOrderKind.BULK) return null
        val order = orders.findById(BulkWorkOrderId(ref.id)) ?: return null

        val golden = order.goldenSampleOrderId?.let { samplingOrders.findById(it) }
        val yields = golden?.yieldAndTiming ?: YieldAndTiming()

        return TraceWorkOrderSnapshot(
            ref = ref,
            tenantId = tenantId,
            tenantOrdinal = ordinals.tenantOrdinal(tenantId),
            ordinal = ordinals.ensureWorkOrderOrdinal(tenantId, ref),
            spkNumber = order.spkNumber.value,
            styleName = order.styleName,
            clientName = order.clientName,
            sizes = order.sizeBreakdown.map { TraceSizeLine(it.sizeLabel, it.orderedPcs) },
            panelRequirements = PanelRequirementDefaults.forKnitwear(yields.panelWeights, yields.panelMinutes),
            colorways = emptyList()
        )
    }
}

/** Memilih penyedia berdasar jenis SPK; inilah satu-satunya tempat kedua jalur bertemu. */
class CompositeTraceWorkOrderProvider(
    private val sampling: TraceWorkOrderProvider,
    private val bulk: TraceWorkOrderProvider
) : TraceWorkOrderProvider {
    override suspend fun snapshot(tenantId: TenantId, ref: TraceWorkOrderRef): TraceWorkOrderSnapshot? =
        when (ref.kind) {
            TraceWorkOrderKind.SAMPLING -> sampling.snapshot(tenantId, ref)
            TraceWorkOrderKind.BULK -> bulk.snapshot(tenantId, ref)
        }
}
