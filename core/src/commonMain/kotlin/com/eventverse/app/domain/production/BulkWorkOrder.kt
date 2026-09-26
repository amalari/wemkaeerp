package com.eventverse.app.domain.production

import com.eventverse.app.domain.pipeline.FlowHealthStatus
import com.eventverse.app.domain.pipeline.StockOwnershipSemantics
import com.eventverse.app.domain.sampling.SamplingOrderId
import com.eventverse.app.domain.tenant.TenantId
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate

/**
 * SPK Produksi Massal — instruksi kerja potong-jahit-finishing untuk satu kontrak buyer.
 *
 * Berdiri di slot archetype `PRODUCTION_MRP`: menerima `CuttingOrderWithFabric` dari hulu dan
 * memancarkan `CutPiecesBundle` ke hilir (lihat `OperationalModuleCatalog.ProductionMrpModule`).
 *
 * Dua kaitan ke hulu yang wajib ada sebelum SPK boleh diterbitkan:
 * - [dealId]: kontrak komersialnya, sumber size breakdown dan nilai PO.
 * - [goldenSampleOrderId]: sampel yang sudah di-ACC buyer, acuan spesifikasi yang terkunci.
 *
 * Tanpa Golden Sample, lantai produksi mengerjakan 1.000 pcs tanpa acuan yang disepakati —
 * itulah alasan [missingReleaseRequirements] menolak penerbitan, bukan sekadar memperingatkan.
 */
data class BulkWorkOrder(
    val id: BulkWorkOrderId,
    val tenantId: TenantId,
    val spkNumber: BulkSpkNumber,
    val clientName: String,
    val styleName: String,
    val status: BulkProductionStatus = BulkProductionStatus.DRAFT,
    val dealId: String? = null,
    /** Sampel ACC yang menjadi acuan spesifikasi — kunci emas produksi massal. */
    val goldenSampleOrderId: SamplingOrderId? = null,
    val sizeBreakdown: List<BulkSizeLine> = emptyList(),
    /**
     * Ukuran yang dikerjakan SPK ini, atau `null` untuk SPK legacy multi-size.
     *
     * Aturan "1 SPK = 1 ukuran": PO buyer boleh multi-size, tetapi saat pecah ke SPK tiap
     * ukuran mendapat SPK sendiri karena program CAM, gramasi, dan progres lantai semuanya
     * per ukuran. `null` hanya dimiliki SPK yang lahir sebelum aturan ini.
     */
    val sizeLabel: String? = null,
    /**
     * Semantik kepemilikan bahan (Kontrak 3). Pada tenant makloon (CMT) nilainya
     * [StockOwnershipSemantics.CONSIGNED_CLIENT_MATERIAL]: kain milik buyer, nilai Rp 0 di neraca
     * pabrik, dan sisa kain wajib direkonsiliasi.
     */
    val stockOwnership: StockOwnershipSemantics = StockOwnershipSemantics.OWNED_RAW_MATERIAL,
    val lineAllocations: List<MachineLineAllocation> = emptyList(),
    val stageProgress: List<ProductionStageProgress> = ProductionStage.entries.map { ProductionStageProgress(it) },
    val targetOutputPerDay: Int = 0,
    val plannedStartDate: LocalDate? = null,
    val plannedFinishDate: LocalDate? = null,
    val notes: String = "",
    val createdAt: Instant,
    val updatedAt: Instant,
    val releasedAt: Instant? = null,
    val archivedAt: Instant? = null
) {
    init {
        if (sizeLabel != null) {
            require(sizeLabel.isNotBlank()) { "Label ukuran SPK tidak boleh kosong" }
            require(
                sizeBreakdown.size == 1 &&
                    sizeBreakdown.single().sizeLabel.equals(sizeLabel, ignoreCase = true)
            ) {
                "SPK per ukuran \"$sizeLabel\" wajib memiliki tepat satu baris size breakdown " +
                    "dengan label yang sama."
            }
        }
    }

    // ── Angka turunan ───────────────────────────────────────────────────────────────────────

    val totalOrderedPcs: Int get() = sizeBreakdown.sumOf { it.orderedPcs }

    val allocatedPcs: Int get() = lineAllocations.sumOf { it.assignedPcs }

    val unallocatedPcs: Int get() = (totalOrderedPcs - allocatedPcs).coerceAtLeast(0)

    val totalMachineCount: Int get() = lineAllocations.sumOf { it.machineCount }

    val isArchived: Boolean get() = archivedAt != null

    fun progressFor(stage: ProductionStage): ProductionStageProgress =
        stageProgress.firstOrNull { it.stage == stage } ?: ProductionStageProgress(stage)

    /**
     * Barang yang tertahan antara satu tahap dan tahap berikutnya (Work In Progress).
     *
     * Ini angka telemetri Kontrak 6 — yang membuat node berkedip kuning di kanvas Alur Pabrik
     * saat potongan menumpuk di depan meja jahit.
     */
    val wipPieces: Int
        get() = ProductionStage.entries.sumOf { stage ->
            val upstream = stage.previous?.let { progressFor(it).passedPcs } ?: totalOrderedPcs
            (upstream - progressFor(stage).completedPcs).coerceAtLeast(0)
        }

    val completedPcs: Int get() = progressFor(ProductionStage.FINISHING).passedPcs

    val totalRejectPcs: Int get() = stageProgress.sumOf { it.rejectPcs }

    val totalReworkPcs: Int get() = stageProgress.sumOf { it.reworkPcs }

    val completionRatio: Float
        get() = if (totalOrderedPcs <= 0) 0f else (completedPcs.toFloat() / totalOrderedPcs).coerceIn(0f, 1f)

    /**
     * Tumpukan terbesar di satu titik serah-terima antar tahap.
     *
     * Dipakai sebagai dasar sinyal kemacetan, bukan [wipPieces] total: 600 pcs yang tersebar
     * merata di tiga tahap adalah aliran yang sehat, sedangkan 600 pcs yang semuanya menunggu
     * di depan meja jahit adalah kemacetan — dan keduanya punya [wipPieces] yang sama persis.
     */
    val largestStageBacklog: Int
        get() = ProductionStage.entries.maxOfOrNull { stage ->
            val upstream = stage.previous?.let { progressFor(it).passedPcs } ?: totalOrderedPcs
            (upstream - progressFor(stage).completedPcs).coerceAtLeast(0)
        } ?: 0

    /**
     * Telemetri kesehatan node (Kontrak 6).
     *
     * Ambangnya relatif terhadap ukuran pesanan, bukan angka mutlak: 200 pcs menganggur di
     * pesanan 10.000 pcs itu normal, di pesanan 300 pcs itu kemacetan.
     *
     * Dua hal yang sengaja TIDAK dianggap alert:
     * - SPK yang baru diterbitkan dan belum disentuh. Seluruh pesanannya memang masih menunggu
     *   di meja potong; itu antrean normal, bukan kemacetan. Tanpa pengecualian ini setiap SPK
     *   baru lahir langsung berkedip kuning, dan lampu yang selalu menyala berhenti dibaca orang.
     * - Stagnasi berbasis waktu. Entity ini tidak memegang jam kerja pabrik, jadi ia tidak bisa
     *   membedakan "diam 2 jam" dari "diam 3 hari". [CRITICAL] di sini hanya dipakai untuk hal
     *   yang benar-benar bisa dibuktikan dari angkanya: tingkat reject.
     */
    val healthStatus: FlowHealthStatus
        get() = when {
            status == BulkProductionStatus.CANCELLED -> FlowHealthStatus.BYPASSED
            status == BulkProductionStatus.COMPLETED -> FlowHealthStatus.HEALTHY
            totalOrderedPcs <= 0 -> FlowHealthStatus.HEALTHY
            totalRejectPcs * 10 > totalOrderedPcs -> FlowHealthStatus.CRITICAL
            status == BulkProductionStatus.DRAFT || status == BulkProductionStatus.RELEASED ->
                FlowHealthStatus.HEALTHY
            largestStageBacklog * 2 > totalOrderedPcs -> FlowHealthStatus.BOTTLENECK
            else -> FlowHealthStatus.HEALTHY
        }

    // ── Perilaku domain ─────────────────────────────────────────────────────────────────────

    /**
     * Syarat wajib penerbitan SPK yang belum terpenuhi. List kosong = siap diluncurkan.
     *
     * Ketiganya adalah hal yang, kalau dilewat, baru ketahuan setelah kain terlanjur dipotong.
     */
    val missingReleaseRequirements: List<String>
        get() = buildList {
            if (sizeBreakdown.isEmpty()) {
                add("Size breakdown massal wajib diisi minimal satu ukuran.")
            }
            if (goldenSampleOrderId == null) {
                add("SPK massal wajib mengacu pada sampel yang sudah di-ACC buyer (Golden Sample).")
            }
            if (clientName.isBlank() || styleName.isBlank()) {
                add("Nama klien dan nama style wajib terisi.")
            }
        }

    val isReadyForRelease: Boolean get() = missingReleaseRequirements.isEmpty()

    /** Menerbitkan SPK ke lantai produksi. Idempotent: SPK yang sudah lepas draft tidak berubah. */
    fun release(releasedAt: Instant): BulkWorkOrder {
        if (status != BulkProductionStatus.DRAFT) return this
        val blockers = missingReleaseRequirements
        require(blockers.isEmpty()) { blockers.joinToString(" ") }
        return copy(
            status = BulkProductionStatus.RELEASED,
            releasedAt = releasedAt,
            updatedAt = releasedAt
        )
    }

    fun updateSizeBreakdown(lines: List<BulkSizeLine>, updatedAt: Instant): BulkWorkOrder {
        require(status == BulkProductionStatus.DRAFT) {
            "Size breakdown hanya bisa diubah selama SPK masih draft — SPK ${spkNumber.value} sudah ${status.displayName}."
        }
        val merged = lines
            .groupBy { it.sizeLabel.trim().uppercase() }
            .map { (label, group) -> BulkSizeLine(label, group.sumOf { it.orderedPcs }) }
        return copy(sizeBreakdown = merged, updatedAt = updatedAt)
    }

    /**
     * Menambah atau mengganti alokasi satu lini. Beban total tidak boleh melebihi pesanan —
     * kalau boleh, laporan "selesai 120%" akan muncul dan tidak ada yang tahu asalnya dari mana.
     */
    fun allocateLine(allocation: MachineLineAllocation, updatedAt: Instant): BulkWorkOrder {
        require(!status.isTerminal) { "SPK ${spkNumber.value} sudah ${status.displayName}." }
        val others = lineAllocations.filterNot { it.lineName == allocation.lineName }
        val newTotal = others.sumOf { it.assignedPcs } + allocation.assignedPcs
        require(newTotal <= totalOrderedPcs) {
            "Total alokasi $newTotal pcs melebihi pesanan $totalOrderedPcs pcs."
        }
        return copy(lineAllocations = others + allocation, updatedAt = updatedAt)
    }

    fun removeLineAllocation(lineName: ProductionLineName, updatedAt: Instant): BulkWorkOrder =
        copy(lineAllocations = lineAllocations.filterNot { it.lineName == lineName }, updatedAt = updatedAt)

    /**
     * Mencatat realisasi kumulatif satu tahap dan memajukan status SPK.
     *
     * Dua batas yang dijaga:
     * 1. Tahap tidak boleh melampaui pesanan.
     * 2. Tahap tidak boleh melampaui hasil yang lolos dari tahap sebelumnya — meja jahit tidak
     *    bisa menyelesaikan 500 pcs kalau meja potong baru meloloskan 300.
     */
    fun recordStageProgress(
        stage: ProductionStage,
        completedPcs: Int,
        reworkPcs: Int = 0,
        rejectPcs: Int = 0,
        updatedAt: Instant
    ): BulkWorkOrder {
        require(status != BulkProductionStatus.CANCELLED) { "SPK ${spkNumber.value} sudah dibatalkan." }
        require(status != BulkProductionStatus.DRAFT) {
            "SPK ${spkNumber.value} belum diterbitkan — terbitkan dulu sebelum mencatat hasil produksi."
        }
        require(completedPcs <= totalOrderedPcs) {
            "Hasil ${stage.displayName} ($completedPcs pcs) melebihi pesanan $totalOrderedPcs pcs."
        }
        val upstreamLimit = stage.previous?.let { progressFor(it).passedPcs }
        if (upstreamLimit != null) {
            require(completedPcs <= upstreamLimit) {
                "Hasil ${stage.displayName} ($completedPcs pcs) melebihi hasil ${stage.previous?.displayName} " +
                    "yang lolos ($upstreamLimit pcs)."
            }
        }

        val updated = ProductionStageProgress(
            stage = stage,
            completedPcs = completedPcs,
            reworkPcs = reworkPcs,
            rejectPcs = rejectPcs,
            lastUpdatedAt = updatedAt
        )
        val newProgress = stageProgress.map { if (it.stage == stage) updated else it }
        val newCompleted = newProgress.first { it.stage == ProductionStage.FINISHING }.passedPcs

        val newStatus = when {
            totalOrderedPcs > 0 && newCompleted >= totalOrderedPcs -> BulkProductionStatus.COMPLETED
            // Status mengikuti tahap terjauh yang sudah punya hasil, bukan tahap yang baru dilapor —
            // koreksi angka potong tidak boleh menarik SPK mundur dari Jahit ke Potong.
            else -> newProgress
                .filter { it.completedPcs > 0 }
                .maxByOrNull { it.stage.order }
                ?.stage?.runningStatus
                ?: status
        }

        return copy(
            stageProgress = newProgress,
            status = if (newStatus.order > status.order || newStatus == BulkProductionStatus.COMPLETED) newStatus else status,
            updatedAt = updatedAt
        )
    }

    fun cancel(reason: String, updatedAt: Instant): BulkWorkOrder = copy(
        status = BulkProductionStatus.CANCELLED,
        notes = reason.ifBlank { notes },
        updatedAt = updatedAt
    )

    companion object {
        /** Tahap progres kosong untuk SPK baru. */
        fun emptyProgress(): List<ProductionStageProgress> =
            ProductionStage.entries.map { ProductionStageProgress(it) }
    }
}
