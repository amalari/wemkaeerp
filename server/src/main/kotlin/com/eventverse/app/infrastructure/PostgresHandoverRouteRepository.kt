package com.eventverse.app.infrastructure

import com.eventverse.app.domain.fulfillment.HandoverRoute
import com.eventverse.app.domain.fulfillment.HandoverRouteCode
import com.eventverse.app.domain.fulfillment.HandoverRouteRepository
import com.eventverse.app.domain.fulfillment.TenantHandoverRoutes
import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.domain.transfer.FlowNodeRef
import com.eventverse.app.infrastructure.tables.FulfillmentRoutesTable
import com.eventverse.app.infrastructure.tables.FulfillmentTransfersTable
import kotlinx.datetime.Clock
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.notInList
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update

/**
 * Penanda pada pesan penolakan penghapusan rute yang pernah dipakai perjalanan.
 * Handler `FulfillmentRouteRoutes` memetakannya ke 409 (TRD-FLOW-003 §4.4).
 */
const val ROUTE_IN_USE = "pernah dipakai perjalanan"

/**
 * Penyimpanan daftar rute serah terima milik tenant (TRD-FLOW-003, B2).
 *
 * Seluruh jalur baca/tulis memakai string kode — tidak ada enum di sini. `null` dari
 * [findByTenantId] berarti tenant belum pernah menyimpan apa pun: pemanggil memakai
 * template pack lewat [TenantHandoverRoutes.resolve], tanpa seed diam-diam (D4).
 */
class PostgresHandoverRouteRepository : HandoverRouteRepository {

    override suspend fun findByTenantId(tenantId: TenantId): TenantHandoverRoutes? =
        DatabaseFactory.dbQuery(tenantId) {
            val rows = FulfillmentRoutesTable.selectAll()
                .where { FulfillmentRoutesTable.tenantId eq tenantId.value }
                .orderBy(FulfillmentRoutesTable.sortOrder)
                .toList()
            if (rows.isEmpty()) null else TenantHandoverRoutes(tenantId, rows.map(::hydrate))
        }

    /**
     * Mengganti seluruh daftar tenant. Rute yang hilang dari [routes] dihapus barisnya —
     * KECUALI pernah dipakai perjalanan karung: penolakan itu kontrak [HandoverRouteRepository]
     * dan ditegakkan di sini, bukan di handler, supaya pemanggil mana pun yang menyimpan
     * tidak bisa memakan riwayat diam-diam. Untuk mematikan rute: `active = false`.
     */
    override suspend fun save(routes: TenantHandoverRoutes) {
        DatabaseFactory.dbQuery(routes.tenantId) {
            val now = Clock.System.now()
            val existing = FulfillmentRoutesTable.selectAll()
                .where { FulfillmentRoutesTable.tenantId eq routes.tenantId.value }
                .toList()
                .map { it[FulfillmentRoutesTable.code] }
                .toSet()
            val kept = routes.routes.map { it.code.value }.toSet()
            val removed = existing - kept
            if (removed.isNotEmpty()) {
                val removedAndUsed = removed.intersect(selectCodesInUse(routes.tenantId).map { it.value })
                require(removedAndUsed.isEmpty()) {
                    "Rute ${removedAndUsed.sorted().joinToString { "'$it'" }} $ROUTE_IN_USE — nonaktifkan, jangan hapus"
                }
                FulfillmentRoutesTable.deleteWhere {
                    (FulfillmentRoutesTable.tenantId eq routes.tenantId.value) and
                        (FulfillmentRoutesTable.code notInList kept.toList())
                }
            }
            upsertAll(routes, now)
        }
    }

    /** Kode yang dipakai minimal satu perjalanan karung — kandidat penghapusannya ditolak. */
    override suspend fun codesInUse(tenantId: TenantId): Set<HandoverRouteCode> =
        DatabaseFactory.dbQuery(tenantId) { selectCodesInUse(tenantId) }

    private suspend fun upsertAll(routes: TenantHandoverRoutes, now: kotlinx.datetime.Instant) {
        routes.routes.forEach { route ->
            val updated = FulfillmentRoutesTable.update(
                where = {
                    (FulfillmentRoutesTable.tenantId eq routes.tenantId.value) and
                        (FulfillmentRoutesTable.code eq route.code.value)
                }
            ) { row ->
                row[label] = route.label
                row[fromNode] = route.from?.key
                row[toNode] = route.to?.key
                row[sortOrder] = route.sortOrder
                row[active] = route.active
                row[updatedAt] = now
            }
            if (updated == 0) {
                FulfillmentRoutesTable.insert { row ->
                    row[tenantId] = routes.tenantId.value
                    row[code] = route.code.value
                    row[label] = route.label
                    row[fromNode] = route.from?.key
                    row[toNode] = route.to?.key
                    row[sortOrder] = route.sortOrder
                    row[active] = route.active
                    row[updatedAt] = now
                }
            }
        }
    }

    /**
     * Ketat: nilai `leg` tersimpan adalah nama enum lama (D3) atau kode baru — keduanya sah
     * sebagai [HandoverRouteCode]. Nilai di luar itu adalah kerusakan data dan dilempar,
     * bukan dilewati (tenant-variability-rules Kontrak 4).
     */
    private fun selectCodesInUse(tenantId: TenantId): Set<HandoverRouteCode> =
        FulfillmentTransfersTable.selectAll()
            .where { FulfillmentTransfersTable.tenantId eq tenantId.value }
            .map { HandoverRouteCode(it[FulfillmentTransfersTable.leg]) }
            .toSet()

    private fun hydrate(row: ResultRow): HandoverRoute = HandoverRoute(
        code = HandoverRouteCode(row[FulfillmentRoutesTable.code]),
        label = row[FulfillmentRoutesTable.label],
        from = row[FulfillmentRoutesTable.fromNode]?.let(::parseNodeOrThrow),
        to = row[FulfillmentRoutesTable.toNode]?.let(::parseNodeOrThrow),
        sortOrder = row[FulfillmentRoutesTable.sortOrder],
        active = row[FulfillmentRoutesTable.active]
    )

    private fun parseNodeOrThrow(key: String): FlowNodeRef =
        FlowNodeRef.parse(key) ?: error("Simpul alur tersimpan tidak dikenal: '$key'")
}
