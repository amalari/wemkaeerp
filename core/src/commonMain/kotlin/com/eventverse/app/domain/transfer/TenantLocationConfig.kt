package com.eventverse.app.domain.transfer

import com.eventverse.app.domain.workqueue.WorkExecutionMode
import com.eventverse.app.domain.workqueue.WorkStationCode

data class PhysicalLocation(
    val id: LocationId,
    val name: String,
    val address: String = "",
    val isMainWarehouse: Boolean = false
) {
    init {
        require(name.isNotBlank()) { "PhysicalLocation name cannot be blank" }
    }
}

/**
 * Konfigurasi lokasi fisik tenant, dan pemetaan simpul alur ke gedung tempatnya dikerjakan.
 *
 * Mengatur apakah tenant beroperasi dalam satu atap (single-site) atau memiliki cabang/gedung
 * berbeda (multi-site), lalu memetakan tiap [FlowNodeRef] — tahap sampling, stasiun kerja, atau
 * proses opsional — ke lokasi fisiknya.
 *
 * ## Dua hal yang sengaja tidak dilakukan di sini
 *
 * **Simpul subkontrak tidak perlu dipetakan.** Ujungnya adalah vendornya, diturunkan dari
 * `vendorRef`, sehingga tenant hanya mengisi baris untuk simpul yang dikerjakan sendiri.
 * [endpointFor] menegakkan itu: mode [WorkExecutionMode.SUBCONTRACTED] mengalahkan pemetaan
 * lokasi apa pun yang kebetulan ada.
 *
 * **Simpul tanpa pemetaan tidak melahirkan perpindahan** — gagal terbuka, bukan tertutup.
 * Menutup akan mengunci seluruh pabrik pada hari pemasangan, karena belum ada satu baris
 * pemetaan pun yang tersimpan.
 */
data class TenantLocationConfig(
    val tenantId: String,
    val isMultiSiteEnabled: Boolean = false,
    /**
     * Apakah penyerahan ke pembeli di ujung alur wajib berdokumen Surat Jalan. Berlaku juga
     * untuk tenant satu atap — pengiriman sampel ke buyer tetap barang yang keluar pabrik.
     */
    val requireCustomerDispatchSj: Boolean = true,
    val locations: List<PhysicalLocation> = emptyList(),
    val nodeLocations: Map<FlowNodeRef, LocationId> = emptyMap()
) {
    init {
        require(tenantId.isNotBlank()) { "tenantId cannot be blank" }
        if (isMultiSiteEnabled) {
            require(locations.size >= 2) {
                "Multi-site tenant must have at least 2 physical locations configured"
            }
        }
        val known = locations.map { it.id }.toSet()
        if (known.isNotEmpty()) {
            val unknown = nodeLocations.values.filterNot { it in known }.distinct()
            require(unknown.isEmpty()) {
                "Pemetaan menunjuk lokasi yang tidak terdaftar: ${unknown.joinToString { it.value }}"
            }
        }
    }

    fun locationFor(node: FlowNodeRef): LocationId? = nodeLocations[node]

    /** Adaptor tipis untuk pemanggil yang masih berpikir dalam kode stasiun. */
    fun locationFor(station: WorkStationCode): LocationId? =
        locationFor(FlowNodeRef.Station(station))

    fun locationNamed(id: LocationId): String =
        locations.firstOrNull { it.id == id }?.name ?: id.value

    /**
     * Ujung fisik sebuah simpul alur.
     *
     * Mengembalikan `null` bila simpul dikerjakan sendiri tapi belum dipetakan ke gedung mana
     * pun — simpul seperti itu transparan bagi penurunan leg, bukan penghalang.
     */
    fun endpointFor(
        node: FlowNodeRef,
        executionMode: WorkExecutionMode = WorkExecutionMode.IN_HOUSE,
        vendorRef: String? = null
    ): LegEndpoint? {
        if (executionMode == WorkExecutionMode.SUBCONTRACTED) {
            val ref = vendorRef?.takeIf { it.isNotBlank() } ?: return null
            return LegEndpoint.Vendor(ref)
        }
        val locationId = locationFor(node) ?: return null
        return LegEndpoint.Site(locationId, locationNamed(locationId))
    }

    /**
     * Memeriksa apakah perpindahan antar-stasiun memerlukan Surat Jalan Mutasi Internal.
     * Bernilai true hanya jika multi-site aktif DAN kedua stasiun berada di gedung berbeda.
     */
    fun isInterSiteTransfer(fromStation: WorkStationCode, toStation: WorkStationCode): Boolean {
        if (!isMultiSiteEnabled) return false
        val origin = locationFor(fromStation) ?: return false
        val dest = locationFor(toStation) ?: return false
        return origin != dest
    }
}
