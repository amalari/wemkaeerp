package com.eventverse.app.infrastructure.tables

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

/**
 * Pola layout Studio (V79, plan §4). Tabel `ops.*`: platform-global, tanpa RLS tenant, tanpa grant
 * `wemade_app` — pola `DiscoveryDraftsTable`. `pattern_json` = objek JSON bebas milik Studio.
 */
object PrototypePatternsTable : Table("ops.prototype_patterns") {
    val id = varchar("id", 64)
    val name = varchar("name", 150)
    val widget = varchar("widget", 30)
    val packCode = varchar("pack_code", 64).nullable()
    val patternJson = jsonbText("pattern_json")

    /** Pembuat pola — identitas token (bisa id superadmin platform); disimpan sebagai data, tanpa FK. */
    val createdByUserId = varchar("created_by_user_id", 64)
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")

    override val primaryKey = PrimaryKey(id)
}
