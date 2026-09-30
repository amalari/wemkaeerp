package com.eventverse.app.infrastructure.tables

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

/**
 * Buku demand discovery (V80, plan §6 E2). Tabel `ops.*`: platform-global, tanpa RLS tenant —
 * pola `DiscoveryDraftsTable`. `narrative` = narasi prospek **verbatim**; dua kolom JSONB
 * (matched/unmatched) dihitung `DemandLedger` saat draf dibuat.
 */
object DiscoveryDemandsTable : Table("ops.discovery_demands") {
    val id = varchar("id", 80)

    /** Draf yang melahirkan demand ini; draf terhapus → demand ikut (ON DELETE CASCADE). */
    val draftId = varchar("draft_id", 64).references(DiscoveryDraftsTable.id)
    val ownerUserId = varchar("owner_user_id", 64)
    val narrative = text("narrative")
    val industryHint = varchar("industry_hint", 80).nullable()
    val agentRef = varchar("agent_ref", 80)
    val matchedModules = jsonbText("matched_modules")
    val unmatchedTerms = jsonbText("unmatched_terms")
    val createdAt = timestamp("created_at")

    override val primaryKey = PrimaryKey(id)
}
