package com.eventverse.app.infrastructure

import com.eventverse.app.domain.discovery.handoff.PrototypeRowRepository
import com.eventverse.app.domain.prototype.PrototypeRow
import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.infrastructure.tables.LayananChangeRequestChangeRequestsTable
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update

/** KANDIDAT PR (hasil generator) — layanan_change_request.change_requests. Setiap query lewat `dbQuery(tenantId)` (RLS aktif). */
class PostgresLayananChangeRequestRepository : PrototypeRowRepository {

    private val T = LayananChangeRequestChangeRequestsTable

    override suspend fun list(tenantId: TenantId): List<PrototypeRow> = DatabaseFactory.dbQuery(tenantId) {
        T.selectAll().where { T.tenantId eq tenantId.value }.orderBy(T.createdAt to SortOrder.ASC).map(::hydrate)
    }

    override suspend fun find(tenantId: TenantId, id: String): PrototypeRow? = DatabaseFactory.dbQuery(tenantId) {
        T.selectAll().where { (T.tenantId eq tenantId.value) and (T.id eq id) }.singleOrNull()?.let(::hydrate)
    }

    override suspend fun save(tenantId: TenantId, row: PrototypeRow) {
        DatabaseFactory.dbQuery(tenantId) {
            val exists = T.selectAll().where { (T.tenantId eq tenantId.value) and (T.id eq row.id) }.any()
            val now = Clock.System.now()
            if (exists) {
                T.update({ (T.tenantId eq tenantId.value) and (T.id eq row.id) }) {
                    it[T.judul] = row["judul"]
                    it[T.peminta] = row["peminta"].ifBlank { null }
                    it[T.prioritas] = row["prioritas"].ifBlank { null }
                    it[T.status] = row["status"]
                    it[T.perkiraanJam] = row["perkiraan_jam"].takeIf { it.isNotBlank() }?.toBigDecimal()
                    it[T.targetSelesai] = row["target_selesai"].takeIf { it.isNotBlank() }?.let { LocalDate.parse(it) }
                    it[T.mendesak] = row["mendesak"] == "ya"
                    it[T.catatan] = row["catatan"].ifBlank { null }
                    it[T.updatedAt] = now
                }
            } else {
                T.insert {
                    it[T.id] = row.id
                    it[T.tenantId] = tenantId.value
                    it[T.judul] = row["judul"]
                    it[T.peminta] = row["peminta"].ifBlank { null }
                    it[T.prioritas] = row["prioritas"].ifBlank { null }
                    it[T.status] = row["status"]
                    it[T.perkiraanJam] = row["perkiraan_jam"].takeIf { it.isNotBlank() }?.toBigDecimal()
                    it[T.targetSelesai] = row["target_selesai"].takeIf { it.isNotBlank() }?.let { LocalDate.parse(it) }
                    it[T.mendesak] = row["mendesak"] == "ya"
                    it[T.catatan] = row["catatan"].ifBlank { null }
                    it[T.createdAt] = now
                    it[T.updatedAt] = now
                }
            }
        }
    }

    override suspend fun delete(tenantId: TenantId, id: String): Boolean = DatabaseFactory.dbQuery(tenantId) {
        T.deleteWhere { (T.tenantId eq tenantId.value) and (T.id eq id) } > 0
    }

    private fun hydrate(r: ResultRow): PrototypeRow = PrototypeRow(
        r[T.id],
        mapOf(
            "judul" to r[T.judul],
            "peminta" to (r[T.peminta] ?: ""),
            "prioritas" to (r[T.prioritas] ?: ""),
            "status" to r[T.status],
            "perkiraan_jam" to (r[T.perkiraanJam]?.stripTrailingZeros()?.toPlainString() ?: ""),
            "target_selesai" to (r[T.targetSelesai]?.toString() ?: ""),
            "mendesak" to (if (r[T.mendesak]) "ya" else "tidak"),
            "catatan" to (r[T.catatan] ?: "")
        )
    )
}
