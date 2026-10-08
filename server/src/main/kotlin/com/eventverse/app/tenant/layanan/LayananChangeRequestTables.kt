package com.eventverse.app.tenant.layanan

import com.eventverse.app.infrastructure.tables.TenantsTable
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.date
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

// KANDIDAT PR (hasil generator) — cermin migrasi layanan_change_request.change_requests. Setelah diterapkan milik tim.
object LayananChangeRequestChangeRequestsTable : Table("layanan_change_request.change_requests") {
    val id = varchar("id", 64)
    val tenantId = varchar("tenant_id", 64).references(TenantsTable.id)
    val judul = text("judul")
    val peminta = text("peminta").nullable()
    val prioritas = varchar("prioritas", 120).nullable()
    val status = varchar("status", 120)
    val perkiraanJam = decimal("perkiraan_jam", 18, 4).nullable()
    val targetSelesai = date("target_selesai").nullable()
    val mendesak = bool("mendesak").default(false)
    val catatan = text("catatan").nullable()
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")

    override val primaryKey = PrimaryKey(id)
}
