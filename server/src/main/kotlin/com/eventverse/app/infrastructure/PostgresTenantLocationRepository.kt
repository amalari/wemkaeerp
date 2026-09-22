package com.eventverse.app.infrastructure

import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.domain.transfer.FlowNodeRef
import com.eventverse.app.domain.transfer.LocationId
import com.eventverse.app.domain.transfer.PhysicalLocation
import com.eventverse.app.domain.transfer.TenantLocationConfig
import com.eventverse.app.domain.transfer.TenantLocationConfigRepository
import com.eventverse.app.infrastructure.tables.TenantFlowNodeLocationsTable
import com.eventverse.app.infrastructure.tables.TenantLocationSettingsTable
import com.eventverse.app.infrastructure.tables.TenantLocationsTable
import kotlinx.datetime.Clock
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update

/**
 * Membaca dan menyimpan konfigurasi lokasi tenant dari tiga tabel yang saling melengkapi:
 * daftar gedung (V55), saklar multi-site, dan pemetaan simpul alur (keduanya V59).
 *
 * Setiap query memfilter `tenant_id` secara eksplisit. RLS di basis data belum efektif —
 * aplikasi terhubung sebagai role yang melewatinya (lihat `docs/tenant-isolation-rls-status.md`)
 * — jadi klausa itulah satu-satunya yang benar-benar memisahkan tenant.
 */
class PostgresTenantLocationRepository : TenantLocationConfigRepository {

    override suspend fun findByTenantId(tenantId: String): TenantLocationConfig? =
        DatabaseFactory.dbQuery(TenantId(tenantId)) {
            val locations = TenantLocationsTable.selectAll()
                .where {
                    (TenantLocationsTable.tenantId eq tenantId) and
                        (TenantLocationsTable.isActive eq true)
                }
                .map { row ->
                    PhysicalLocation(
                        id = LocationId(row[TenantLocationsTable.id]),
                        name = row[TenantLocationsTable.name],
                        address = row[TenantLocationsTable.address]
                    )
                }

            val settings = TenantLocationSettingsTable.selectAll()
                .where { TenantLocationSettingsTable.tenantId eq tenantId }
                .singleOrNull()

            // Tenant tanpa satu pun gedung maupun saklar belum pernah menyetel apa pun.
            // `null` di sini berarti "tidak ada perpindahan", bukan kesalahan.
            if (locations.isEmpty() && settings == null) return@dbQuery null

            val knownIds = locations.map { it.id }.toSet()
            val mappings = TenantFlowNodeLocationsTable.selectAll()
                .where { TenantFlowNodeLocationsTable.tenantId eq tenantId }
                .mapNotNull { row ->
                    val key = "${row[TenantFlowNodeLocationsTable.nodeKind]}" +
                        "${FlowNodeRef.SEPARATOR}${row[TenantFlowNodeLocationsTable.nodeKey]}"
                    // Simpul yang tidak dikenal lagi (mis. tahap yang sudah dihapus dari enum)
                    // dilewati, bukan menjatuhkan seluruh konfigurasi tenant.
                    val node = FlowNodeRef.parse(key) ?: return@mapNotNull null
                    val location = LocationId(row[TenantFlowNodeLocationsTable.locationId])
                    if (location !in knownIds) return@mapNotNull null
                    node to location
                }
                .toMap()

            val multiSite = settings?.get(TenantLocationSettingsTable.isMultiSiteEnabled) ?: false
            TenantLocationConfig(
                tenantId = tenantId,
                // Invarian domain menuntut minimal dua gedung saat multi-site aktif. Data yang
                // melanggar itu diturunkan derajatnya menjadi single-site alih-alih melempar —
                // konfigurasi rusak tidak boleh membuat seluruh panel alur gagal dimuat.
                isMultiSiteEnabled = multiSite && locations.size >= 2,
                requireCustomerDispatchSj =
                    settings?.get(TenantLocationSettingsTable.requireCustomerDispatchSj) ?: true,
                locations = locations,
                nodeLocations = mappings
            )
        }

    override suspend fun save(config: TenantLocationConfig) {
        DatabaseFactory.dbQuery(TenantId(config.tenantId)) {
            val now = Clock.System.now()

            val existing = TenantLocationSettingsTable.selectAll()
                .where { TenantLocationSettingsTable.tenantId eq config.tenantId }
                .singleOrNull()

            if (existing == null) {
                TenantLocationSettingsTable.insert {
                    it[tenantId] = config.tenantId
                    it[isMultiSiteEnabled] = config.isMultiSiteEnabled
                    it[requireCustomerDispatchSj] = config.requireCustomerDispatchSj
                    it[updatedAt] = now
                }
            } else {
                TenantLocationSettingsTable.update({
                    TenantLocationSettingsTable.tenantId eq config.tenantId
                }) {
                    it[isMultiSiteEnabled] = config.isMultiSiteEnabled
                    it[requireCustomerDispatchSj] = config.requireCustomerDispatchSj
                    it[updatedAt] = now
                }
            }

            // Pemetaan diganti utuh: layar pengaturan mengirim gambaran lengkap, dan penggantian
            // utuh adalah satu-satunya cara menghapus baris tanpa melacak selisih di klien.
            TenantFlowNodeLocationsTable.deleteWhere {
                TenantFlowNodeLocationsTable.tenantId eq config.tenantId
            }
            config.nodeLocations.forEach { (node, location) ->
                TenantFlowNodeLocationsTable.insert {
                    it[tenantId] = config.tenantId
                    it[nodeKind] = node.key.substringBefore(FlowNodeRef.SEPARATOR)
                    it[nodeKey] = node.key.substringAfter(FlowNodeRef.SEPARATOR)
                    it[locationId] = location.value
                    it[updatedAt] = now
                }
            }
        }
    }
}
