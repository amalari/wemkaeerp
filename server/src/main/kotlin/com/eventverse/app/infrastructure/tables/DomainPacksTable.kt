package com.eventverse.app.infrastructure.tables

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

/** Pack data per versi (V75). Tabel platform tanpa RLS; `definition` = JSON `DomainPackCodec`. */
object DomainPacksTable : Table("domain_packs") {
    val code = varchar("code", 64)
    val version = integer("version")
    val status = varchar("status", 16)
    val ownerTenantId = varchar("owner_tenant_id", 64).references(TenantsTable.id).nullable()
    val definition = jsonbText("definition")
    val createdAt = timestamp("created_at")

    override val primaryKey = PrimaryKey(code, version)
}
