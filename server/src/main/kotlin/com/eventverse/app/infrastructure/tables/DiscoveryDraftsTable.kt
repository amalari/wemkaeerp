package com.eventverse.app.infrastructure.tables

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

/**
 * Draf discovery (V78, plan §2 A5). Tabel `ops.*`: platform-global, tanpa RLS tenant — pola
 * `ProspectLeadsTable`. `document` = JSON `DiscoveryDraftCodec` (pack + blueprint + screens).
 */
object DiscoveryDraftsTable : Table("ops.discovery_drafts") {
    val id = varchar("id", 64)

    /** Pemilik draf (T12). Gerbang route: pemilik atau superadmin. */
    val ownerUserId = varchar("owner_user_id", 64).references(UsersTable.id)
    val prospectLeadId = varchar("prospect_lead_id", 64).references(ProspectLeadsTable.id).nullable()
    val status = varchar("status", 16)
    val document = jsonbText("document")
    val schemaVersion = integer("schema_version")
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")
    val lockedAt = timestamp("locked_at").nullable()

    override val primaryKey = PrimaryKey(id)
}
