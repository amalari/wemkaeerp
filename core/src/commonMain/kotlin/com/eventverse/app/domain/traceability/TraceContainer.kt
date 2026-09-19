package com.eventverse.app.domain.traceability

import com.eventverse.app.domain.contracts.GarmentPanel
import com.eventverse.app.domain.tenant.TenantId
import kotlinx.datetime.Instant

/**
 * Satu wadah fisik yang membawa kode telusur: sebuah bundel panel, atau sebuah karung setoran.
 *
 * Dijadikan satu entity untuk dua tier — bukan dua entity — karena keduanya menjalani daur hidup dan
 * aturan kode yang identik; yang berbeda hanya isi yang dicatat (bundel menghitung lembar panel,
 * karung menghitung pcs dan berat). Memecahnya jadi dua akan menduplikasi seluruh mesin kode,
 * idempotensi scan, dan silsilah, demi perbedaan yang muat di dua field.
 *
 * Wadah ini **tidak** menyimpan daftar induknya. Silsilah hidup di [TraceContainerLink] karena satu
 * bundel bisa terpakai sebagian, dan kuantitas konsumsi tidak punya tempat yang jujur di dalam
 * daftar id.
 */
data class TraceContainer(
    val id: TraceContainerId,
    val tenantId: TenantId,
    val code: TraceCode,
    val workOrder: TraceWorkOrderRef,
    val tier: TraceTier,
    val sizeLabel: String,
    val colorway: String = "",
    val state: TraceContainerState = TraceContainerState.OPENED,
    /** Hanya bermakna untuk [TraceTier.BUNDLE]. */
    val panelTallies: List<PanelTally> = emptyList(),
    /** Hanya bermakna untuk [TraceTier.SACK]. */
    val declaredPcs: Int = 0,
    val weightKg: Double = 0.0,
    val operatorName: String = "",
    val shift: ShiftLabel = ShiftLabel(""),
    /** Kapan hal ini terjadi di lantai — boleh lebih awal dari [createdAt] saat jaringan mati. */
    val recordedAt: Instant,
    val createdAt: Instant,
    val updatedAt: Instant,
    val notes: String = ""
) {
    init {
        require(tier.isContainer) {
            "${tier.displayName} adalah dokumen, bukan wadah — kartunya tidak bisa diisi seperti bundel atau karung"
        }
        require(sizeLabel.isNotBlank()) { "Wadah telusur wajib punya label size" }
        require(declaredPcs >= 0) { "Jumlah pcs tidak boleh negatif" }
        require(weightKg >= 0.0) { "Berat timbangan tidak boleh negatif" }
        require(panelTallies.map { it.panel }.distinct().size == panelTallies.size) {
            "Satu panel tidak boleh dihitung dua kali dalam wadah yang sama"
        }
    }

    val isBundle: Boolean get() = tier == TraceTier.BUNDLE
    val isSack: Boolean get() = tier == TraceTier.SACK

    fun countFor(panel: GarmentPanel): Int = panelTallies.firstOrNull { it.panel == panel }?.pieces ?: 0

    val totalPanelPieces: Int get() = panelTallies.sumOf { it.pieces }

    /**
     * Berapa baju utuh yang sebenarnya bisa dirakit dari bundel ini.
     *
     * Bukan `totalPanelPieces / jumlahPanel`: bundel berisi 5 depan, 4 belakang, dan 10 lengan punya
     * 19 lembar, tapi hanya sanggup membentuk **4** baju. Angka terkecil itulah yang menentukan, dan
     * sisanya menunggu shift berikutnya — lihat [leftoverPanels].
     */
    fun completeSets(requirements: List<PanelRequirement>): Int {
        if (!isBundle) return declaredPcs
        val relevant = requirements.filter { it.piecesPerGarment > 0 }
        if (relevant.isEmpty()) return 0
        return relevant.minOf { countFor(it.panel) / it.piecesPerGarment }
    }

    /** Lembar yang tersisa setelah set lengkap diambil — bukan cacat, hanya belum berpasangan. */
    fun leftoverPanels(requirements: List<PanelRequirement>): List<PanelTally> {
        if (!isBundle) return emptyList()
        val sets = completeSets(requirements)
        return requirements.mapNotNull { requirement ->
            val remaining = countFor(requirement.panel) - (sets * requirement.piecesPerGarment)
            if (remaining > 0) PanelTally(requirement.panel, remaining) else null
        }
    }

    fun missingPanels(requirements: List<PanelRequirement>): List<GarmentPanel> =
        requirements.filter { countFor(it.panel) < it.piecesPerGarment }.map { it.panel }

    // ── Transisi ────────────────────────────────────────────────────────────────────────────

    fun recordTally(
        tallies: List<PanelTally>,
        operatorName: String,
        shift: ShiftLabel,
        recordedAt: Instant,
        updatedAt: Instant,
        notes: String = this.notes
    ): TraceContainer {
        require(isBundle) { "Hitungan panel hanya berlaku untuk bundel, bukan ${tier.displayName}" }
        require(!state.isFinal) {
            "Bundel ${TraceCodec.grouped(code)} sudah ${state.displayName} — hitungannya tidak bisa diubah lagi"
        }
        require(tallies.any { it.pieces > 0 }) { "Isi dulu minimal satu hitungan panel" }
        return copy(
            panelTallies = tallies.filter { it.pieces > 0 },
            operatorName = operatorName,
            shift = shift,
            recordedAt = recordedAt,
            updatedAt = updatedAt,
            notes = notes,
            state = TraceContainerState.TALLIED
        )
    }

    fun markConsumed(updatedAt: Instant): TraceContainer {
        require(isBundle) { "Hanya bundel yang dituang ke karung" }
        require(state == TraceContainerState.TALLIED) {
            "Bundel ${TraceCodec.grouped(code)} belum dihitung — hitung dulu sebelum dituang ke karung"
        }
        return copy(state = TraceContainerState.CONSUMED, updatedAt = updatedAt)
    }

    fun closeSack(
        declaredPcs: Int,
        weightKg: Double,
        operatorName: String,
        recordedAt: Instant,
        updatedAt: Instant,
        notes: String = this.notes
    ): TraceContainer {
        require(isSack) { "Penutupan ini hanya berlaku untuk karung" }
        require(declaredPcs > 0) { "Jumlah isi karung minimal 1 pcs" }
        return copy(
            declaredPcs = declaredPcs,
            weightKg = weightKg,
            operatorName = operatorName,
            recordedAt = recordedAt,
            updatedAt = updatedAt,
            notes = notes,
            state = TraceContainerState.CLOSED
        )
    }

    /**
     * Gerbang keseragaman karung: satu SPK, satu size, satu warna.
     *
     * Ditolak, bukan sekadar diperingatkan. Kalau karung boleh campur size, jumlah per size hilang
     * tepat di titik ini dan tidak ada QR mana pun yang bisa memulihkannya — seluruh rantai telusur
     * di hulu jadi sia-sia. Pesannya menyebut nilai yang bertabrakan supaya operator tahu apa yang
     * harus dipisahkan, bukan sekadar bahwa ia salah.
     */
    fun missingUniformityReasons(bundles: List<TraceContainer>): List<String> {
        if (!isSack) return emptyList()
        val reasons = mutableListOf<String>()

        val foreignSpk = bundles.filter { it.workOrder != workOrder }
        if (foreignSpk.isNotEmpty()) {
            reasons += "Bundel dari SPK lain ikut terpilih: " +
                foreignSpk.joinToString(", ") { TraceCodec.grouped(it.code) }
        }

        val sizes = (bundles.map { it.sizeLabel } + sizeLabel).distinctBy { it.uppercase() }
        if (sizes.size > 1) {
            reasons += "Karung ini bercampur size ${sizes.joinToString(" dan ")} — pisahkan dulu per size"
        }

        val colorways = (bundles.map { it.colorway } + colorway)
            .filter { it.isNotBlank() }
            .distinctBy { it.uppercase() }
        if (colorways.size > 1) {
            reasons += "Karung ini bercampur warna ${colorways.joinToString(" dan ")} — pisahkan dulu per warna"
        }

        val notTallied = bundles.filter { it.state != TraceContainerState.TALLIED }
        if (notTallied.isNotEmpty()) {
            reasons += "Bundel belum dihitung atau sudah dituang ke karung lain: " +
                notTallied.joinToString(", ") { "${TraceCodec.grouped(it.code)} (${it.state.displayName})" }
        }
        return reasons
    }
}
