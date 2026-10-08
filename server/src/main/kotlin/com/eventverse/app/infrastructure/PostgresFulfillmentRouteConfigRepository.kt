package com.eventverse.app.infrastructure

import com.eventverse.app.domain.fulfillment.FulfillmentRouteConfig
import com.eventverse.app.domain.fulfillment.FulfillmentRouteConfigRepository
import com.eventverse.app.domain.fulfillment.HandoverMode
import com.eventverse.app.domain.fulfillment.HandoverRouteCode
import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.infrastructure.tables.FulfillmentRouteSettingsTable
import kotlinx.datetime.Clock
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.notInList
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update

/**
 * Penyimpanan mode serah terima per rute.
 *
 * Mengembalikan `null` hanya saat tenant belum punya satu baris pun — pemanggil menafsirkannya
 * sebagai "seluruh rute ADMIN_HUB", sesuai kontrak [FulfillmentRouteConfigRepository].
 */
class PostgresFulfillmentRouteConfigRepository : FulfillmentRouteConfigRepository {

    override suspend fun findByTenantId(tenantId: TenantId): FulfillmentRouteConfig? =
        DatabaseFactory.dbQuery(tenantId) {
            val rows = FulfillmentRouteSettingsTable.selectAll()
                .where { FulfillmentRouteSettingsTable.tenantId eq tenantId.value }
                .toList()

            if (rows.isEmpty()) return@dbQuery null

            val modes = rows.map { row ->
                // TRD-FLOW-003 B2: baca ketat — kode tak sah atau mode tak dikenal MELEMPAR,
                // bukan melewatkan baris diam-diam (tenant-variability-rules Kontrak 4). Baris
                // hanya bisa masuk lewat PUT route-settings yang menolak kode di luar daftar
                // tenant, dan mode dibatasi CHECK database sejak V61.
                val route = HandoverRouteCode.parse(row[FulfillmentRouteSettingsTable.route]).getOrElse {
                    throw IllegalStateException("Kode rute tersimpan tidak sah: '${row[FulfillmentRouteSettingsTable.route]}'")
                }
                val mode = HandoverMode.entries
                    .firstOrNull { it.name == row[FulfillmentRouteSettingsTable.handoverMode] }
                    ?: throw IllegalStateException(
                        "Mode tersimpan tidak dikenal '${row[FulfillmentRouteSettingsTable.handoverMode]}' untuk rute '${route.value}'"
                    )
                route to mode
            }.toMap()

            FulfillmentRouteConfig(tenantId = tenantId, modes = modes)
        }

    /**
     * Menyimpan konfigurasi secara menyeluruh: rute yang hilang dari [config] dihapus barisnya.
     *
     * Menghapus, bukan menyetelnya ke `ADMIN_HUB`, supaya "belum pernah disentuh" tetap bisa
     * dibedakan dari "sengaja dikembalikan ke meja admin" — perbedaan yang dipakai layar
     * konfigurasi untuk menawarkan pengaturan awal.
     */
    override suspend fun save(config: FulfillmentRouteConfig) {
        DatabaseFactory.dbQuery(config.tenantId) {
            val now = Clock.System.now()
            val keep = config.modes.keys.map { it.value }.toSet()

            FulfillmentRouteSettingsTable.deleteWhere {
                (FulfillmentRouteSettingsTable.tenantId eq config.tenantId.value) and
                    (FulfillmentRouteSettingsTable.route notInList keep)
            }

            config.modes.forEach { (route, mode) ->
                val updated = FulfillmentRouteSettingsTable.update(
                    where = {
                        (FulfillmentRouteSettingsTable.tenantId eq config.tenantId.value) and
                            (FulfillmentRouteSettingsTable.route eq route.value)
                    }
                ) {
                    it[handoverMode] = mode.name
                    it[updatedAt] = now
                }

                if (updated == 0) {
                    FulfillmentRouteSettingsTable.insert {
                        it[tenantId] = config.tenantId.value
                        it[FulfillmentRouteSettingsTable.route] = route.value
                        it[handoverMode] = mode.name
                        it[updatedAt] = now
                    }
                }
            }
        }
    }
}
