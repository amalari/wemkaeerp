package com.eventverse.app.infrastructure.tables

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

/**
 * Deployment WeMake Builder (V81, PLAN-builder-console M0). Tabel `builder.*`: milik tenant — RLS via
 * `apply_tenant_rls_in('builder', 'deployments')` (pola V76). M0 hanya mengisi status `IMPORTED`;
 * status deploy lain menyusul di M2.
 */
object DeploymentsTable : Table("builder.deployments") {
    val id = varchar("id", 80)
    val tenantId = varchar("tenant_id", 64).references(TenantsTable.id)
    val number = integer("number")
    val packCode = varchar("pack_code", 64)

    /** Versi pack data terkunci. `NULL` hanya untuk IMPORTED/FAILED/VALIDATING (pack shipped belum punya versi). */
    val packVersion = integer("pack_version").nullable()

    /** Versi build aplikasi — khusus snapshot IMPORTED. */
    val appBuild = varchar("app_build", 64).nullable()
    val blueprintRevision = integer("blueprint_revision").default(1)
    val status = varchar("status", 32)
    val draftId = varchar("draft_id", 64).nullable()
    val createdAt = timestamp("created_at")
    val activatedAt = timestamp("activated_at").nullable()

    override val primaryKey = PrimaryKey(id)
}
