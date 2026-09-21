package com.eventverse.app.domain.transfer

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
 * Konfigurasi multi-lokasi pabrik per tenant.
 *
 * Mengatur apakah tenant beroperasi dalam satu atap (single-site) atau memiliki cabang/gedung
 * berbeda (multi-site), serta memetakan stasiun kerja ke lokasi fisiknya.
 */
data class TenantLocationConfig(
    val tenantId: String,
    val isMultiSiteEnabled: Boolean = false,
    val locations: List<PhysicalLocation> = emptyList(),
    val stationLocationMappings: Map<WorkStationCode, LocationId> = emptyMap()
) {
    init {
        require(tenantId.isNotBlank()) { "tenantId cannot be blank" }
        if (isMultiSiteEnabled) {
            require(locations.size >= 2) {
                "Multi-site tenant must have at least 2 physical locations configured"
            }
        }
    }

    fun locationFor(station: WorkStationCode): LocationId? =
        stationLocationMappings[station]

    /**
     * Memeriksa apakah perpindahan antar-stasiun memerlukan Surat Jalan Mutasi Internal.
     * Bernilai true hanya jika multi-site aktif DAN kedua stasiun berada di lokasi gedung yang berbeda.
     */
    fun isInterSiteTransfer(fromStation: WorkStationCode, toStation: WorkStationCode): Boolean {
        if (!isMultiSiteEnabled) return false
        val origin = locationFor(fromStation) ?: return false
        val dest = locationFor(toStation) ?: return false
        return origin != dest
    }
}
